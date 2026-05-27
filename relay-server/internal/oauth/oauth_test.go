package oauth

import (
	"crypto/sha256"
	"encoding/base64"
	"testing"
	"time"
)

func challengeFor(verifier string) string {
	sum := sha256.Sum256([]byte(verifier))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

func TestFullAuthorizationCodeFlowWithPKCE(t *testing.T) {
	p := New(nil)
	clientID := p.Register([]string{"https://claude.ai/callback"})

	verifier := "the-code-verifier-1234567890"
	code, err := p.Authorize(clientID, "https://claude.ai/callback", challengeFor(verifier), "S256")
	if err != nil {
		t.Fatalf("authorize: %v", err)
	}

	token, expiresIn, err := p.Exchange(code, verifier, clientID, "https://claude.ai/callback")
	if err != nil || token == "" || expiresIn <= 0 {
		t.Fatalf("exchange: %v token=%q exp=%d", err, token, expiresIn)
	}
	if !p.Validate(token) {
		t.Fatal("token should validate")
	}
}

func TestAuthorizeRejectsUnknownClient(t *testing.T) {
	p := New(nil)
	if _, err := p.Authorize("ghost", "https://x", challengeFor("v"), "S256"); err != ErrUnknownClient {
		t.Fatalf("want ErrUnknownClient, got %v", err)
	}
}

func TestAuthorizeRejectsNonS256(t *testing.T) {
	p := New(nil)
	id := p.Register(nil)
	if _, err := p.Authorize(id, "https://x", "chal", "plain"); err != ErrUnsupportedPKCE {
		t.Fatalf("want ErrUnsupportedPKCE, got %v", err)
	}
}

func TestExchangeRejectsWrongVerifier(t *testing.T) {
	p := New(nil)
	id := p.Register([]string{"https://x"})
	code, _ := p.Authorize(id, "https://x", challengeFor("right"), "S256")
	if _, _, err := p.Exchange(code, "wrong", id, "https://x"); err != ErrPKCEFailed {
		t.Fatalf("want ErrPKCEFailed, got %v", err)
	}
}

func TestExchangeIsSingleUse(t *testing.T) {
	p := New(nil)
	id := p.Register([]string{"https://x"})
	code, _ := p.Authorize(id, "https://x", challengeFor("v"), "S256")
	if _, _, err := p.Exchange(code, "v", id, "https://x"); err != nil {
		t.Fatalf("first exchange: %v", err)
	}
	if _, _, err := p.Exchange(code, "v", id, "https://x"); err != ErrInvalidGrant {
		t.Fatalf("want ErrInvalidGrant on reuse, got %v", err)
	}
}

func TestExchangeRejectsExpiredCode(t *testing.T) {
	now := time.Now()
	p := New(func() time.Time { return now })
	id := p.Register([]string{"https://x"})
	code, _ := p.Authorize(id, "https://x", challengeFor("v"), "S256")
	p.now = func() time.Time { return now.Add(codeTTL + time.Second) }
	if _, _, err := p.Exchange(code, "v", id, "https://x"); err != ErrInvalidGrant {
		t.Fatalf("want ErrInvalidGrant, got %v", err)
	}
}

func TestValidateRejectsUnknownAndExpired(t *testing.T) {
	now := time.Now()
	p := New(func() time.Time { return now })
	if p.Validate("nope") {
		t.Fatal("unknown token must not validate")
	}
	id := p.Register([]string{"https://x"})
	code, _ := p.Authorize(id, "https://x", challengeFor("v"), "S256")
	token, _, _ := p.Exchange(code, "v", id, "https://x")
	p.now = func() time.Time { return now.Add(tokenTTL + time.Second) }
	if p.Validate(token) {
		t.Fatal("expired token must not validate")
	}
}
