// Package oauth is a minimal single-user OAuth 2.1 authorization server, just enough
// for Claude's remote MCP connector: Dynamic Client Registration (RFC 7591), an
// auto-approving authorization endpoint, and PKCE (S256) code exchange.
//
// The relay's secret URL path is the real gate; this layer exists because the Claude
// connector mandates the OAuth handshake. Auto-approval is acceptable for a single
// user who controls the relay — there is no other party to consent.
package oauth

import (
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"sync"
	"time"

	"github.com/liyoclaw/invoice-relay/internal/config"
)

const (
	codeTTL  = 5 * time.Minute
	tokenTTL = 24 * time.Hour
)

var (
	ErrUnknownClient   = errors.New("oauth: unknown client")
	ErrBadRedirect     = errors.New("oauth: redirect_uri mismatch")
	ErrUnsupportedPKCE = errors.New("oauth: only S256 PKCE supported")
	ErrInvalidGrant    = errors.New("oauth: invalid or expired code")
	ErrPKCEFailed      = errors.New("oauth: PKCE verification failed")
)

type client struct {
	redirectURIs []string
}

type authCode struct {
	clientID    string
	redirectURI string
	challenge   string
	expiresAt   time.Time
}

// Provider holds OAuth state in memory (single user; resets on restart, which simply
// means the connector re-registers).
type Provider struct {
	mu      sync.Mutex
	clients map[string]client
	codes   map[string]authCode
	tokens  map[string]time.Time
	now     func() time.Time
}

func New(now func() time.Time) *Provider {
	if now == nil {
		now = time.Now
	}
	return &Provider{
		clients: map[string]client{},
		codes:   map[string]authCode{},
		tokens:  map[string]time.Time{},
		now:     now,
	}
}

// Register implements Dynamic Client Registration: stores the redirect URIs and
// returns a fresh client_id (public client — token_endpoint_auth_method "none").
func (p *Provider) Register(redirectURIs []string) string {
	id := "client-" + config.RandomURLToken(12)
	p.mu.Lock()
	p.clients[id] = client{redirectURIs: redirectURIs}
	p.mu.Unlock()
	return id
}

// Authorize validates the request and issues a single-use authorization code
// (auto-approved). codeChallengeMethod must be S256.
func (p *Provider) Authorize(clientID, redirectURI, codeChallenge, codeChallengeMethod string) (string, error) {
	if codeChallengeMethod != "" && codeChallengeMethod != "S256" {
		return "", ErrUnsupportedPKCE
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	c, ok := p.clients[clientID]
	if !ok {
		return "", ErrUnknownClient
	}
	if !redirectAllowed(c.redirectURIs, redirectURI) {
		return "", ErrBadRedirect
	}
	code := config.RandomURLToken(24)
	p.codes[code] = authCode{
		clientID:    clientID,
		redirectURI: redirectURI,
		challenge:   codeChallenge,
		expiresAt:   p.now().Add(codeTTL),
	}
	return code, nil
}

// Exchange swaps an authorization code for an access token, verifying PKCE.
func (p *Provider) Exchange(code, codeVerifier, clientID, redirectURI string) (string, int, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	ac, ok := p.codes[code]
	if !ok || p.now().After(ac.expiresAt) {
		return "", 0, ErrInvalidGrant
	}
	if ac.clientID != clientID || ac.redirectURI != redirectURI {
		return "", 0, ErrInvalidGrant
	}
	if ac.challenge != "" && !verifyPKCE(codeVerifier, ac.challenge) {
		return "", 0, ErrPKCEFailed
	}
	delete(p.codes, code) // single use
	token := config.RandomURLToken(32)
	p.tokens[token] = p.now().Add(tokenTTL)
	return token, int(tokenTTL.Seconds()), nil
}

// Validate reports whether token is a live access token.
func (p *Provider) Validate(token string) bool {
	if token == "" {
		return false
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	exp, ok := p.tokens[token]
	if !ok {
		return false
	}
	if p.now().After(exp) {
		delete(p.tokens, token)
		return false
	}
	return true
}

func redirectAllowed(registered []string, redirectURI string) bool {
	if len(registered) == 0 {
		return true // client registered without redirect URIs — accept what it presents
	}
	for _, u := range registered {
		if u == redirectURI {
			return true
		}
	}
	return false
}

func verifyPKCE(verifier, challenge string) bool {
	sum := sha256.Sum256([]byte(verifier))
	computed := base64.RawURLEncoding.EncodeToString(sum[:])
	return subtle.ConstantTimeCompare([]byte(computed), []byte(challenge)) == 1
}
