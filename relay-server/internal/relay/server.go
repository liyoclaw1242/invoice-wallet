// Package relay wires the relay's HTTP surface from the sub-packages (pairing,
// session/WebSocket, MCP secret endpoint) into a single handler.
package relay

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/liyoclaw/invoice-relay/internal/config"
	"github.com/liyoclaw/invoice-relay/internal/oauth"
	"github.com/liyoclaw/invoice-relay/internal/pairing"
	"github.com/liyoclaw/invoice-relay/internal/session"
	"github.com/liyoclaw/invoice-relay/internal/storage"
	"github.com/liyoclaw/invoice-relay/internal/transport"
)

// phoneResponseTimeout bounds how long the relay waits for the phone to answer.
const phoneResponseTimeout = 30 * time.Second

// wakeConnectTimeout bounds how long to wait for the phone to (re)connect after a wake.
const wakeConnectTimeout = 20 * time.Second

// Server holds the relay's shared dependencies.
type Server struct {
	cfg         config.Config
	store       storage.Store
	pairing     *pairing.Service
	sessions    *session.Manager
	waker       transport.Waker
	wakeTimeout time.Duration
	upgrader    websocket.Upgrader
	oauth       *oauth.Provider
}

// New builds a Server from its configuration and store.
func New(cfg config.Config, store storage.Store) *Server {
	return &Server{
		cfg:         cfg,
		store:       store,
		pairing:     pairing.NewService(store, nil),
		sessions:    session.NewManager(phoneResponseTimeout),
		waker:       transport.NoopWaker{},
		wakeTimeout: wakeConnectTimeout,
		// Auth is the Bearer device_secret, so the WS origin is not a trust boundary.
		upgrader: websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }},
		oauth:    oauth.New(nil),
	}
}

// SetWaker overrides the FCM waker (real FCM in production, a stub in dev/tests).
func (s *Server) SetWaker(w transport.Waker) { s.waker = w }

// Pairing exposes the pairing service (used by the CLI to mint a code at startup).
func (s *Server) Pairing() *pairing.Service { return s.pairing }

// Sessions exposes the phone session manager (used by the MCP endpoint to forward).
func (s *Server) Sessions() *session.Manager { return s.sessions }

// Handler returns the relay's HTTP routes.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.handleHealth)
	mux.HandleFunc("POST /pair/claim", s.handleClaim)
	mux.HandleFunc("POST /device/refresh", s.handleRefresh)
	mux.HandleFunc("GET /ws", s.handleWS)
	// Secret-path MCP endpoint (ARCHITECTURE §8.1): a wrong secret hits no route → 404.
	// Accept both with and without the trailing slash — Claude's connector posts to
	// the no-slash form first. GET is answered 405 (no server→client SSE stream).
	base := "/mcp-" + s.cfg.MCPSecret
	mux.HandleFunc("POST "+base, s.handleMCP)
	mux.HandleFunc("POST "+base+"/{$}", s.handleMCP)
	mux.HandleFunc("GET "+base, s.handleMCPGet)
	mux.HandleFunc("GET "+base+"/{$}", s.handleMCPGet)
	// OAuth 2.1 (Claude's connector mandates the handshake; ARCHITECTURE §7 secret URL
	// remains the real gate, auth auto-approves for the single user).
	mux.HandleFunc("GET /.well-known/oauth-protected-resource", s.handleProtectedResourceMeta)
	mux.HandleFunc("GET /.well-known/oauth-protected-resource/{rest...}", s.handleProtectedResourceMeta)
	mux.HandleFunc("GET /.well-known/oauth-authorization-server", s.handleAuthServerMeta)
	mux.HandleFunc("GET /.well-known/oauth-authorization-server/{rest...}", s.handleAuthServerMeta)
	mux.HandleFunc("POST /register", s.handleRegister)
	mux.HandleFunc("GET /authorize", s.handleAuthorize)
	mux.HandleFunc("POST /token", s.handleToken)
	// Metadata-only request log (diagnostic): set RELAY_LOG_REQUESTS=1 to enable.
	if os.Getenv("RELAY_LOG_REQUESTS") == "1" {
		return logRequests(mux)
	}
	return mux
}

// logRequests logs request metadata only — no bodies, no Authorization values — to
// reveal exactly how a client (e.g. Claude's connector) probes the server.
func logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		log.Printf("[req] %s %s accept=%q ct=%q auth=%t session=%q protoVer=%q ua=%q",
			r.Method, r.URL.Path,
			r.Header.Get("Accept"), r.Header.Get("Content-Type"),
			r.Header.Get("Authorization") != "",
			r.Header.Get("Mcp-Session-Id"), r.Header.Get("MCP-Protocol-Version"),
			r.Header.Get("User-Agent"))
		next.ServeHTTP(w, r)
	})
}

// acceptToken accepts either an OAuth-issued token or the static API token (the
// header-injection path that bypasses claude.ai's known OAuth bug, issue #155).
func (s *Server) acceptToken(token string) bool {
	if s.oauth.Validate(token) {
		return true
	}
	return s.cfg.APIToken != "" && token != "" &&
		subtle.ConstantTimeCompare([]byte(token), []byte(s.cfg.APIToken)) == 1
}

// handleMCPGet answers the Streamable HTTP GET (server→client SSE stream). We don't
// offer server-initiated streams, so 405 per spec.
func (s *Server) handleMCPGet(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Allow", "POST")
	writeError(w, http.StatusMethodNotAllowed, "no server-initiated stream")
}

// handleMCP implements the Streamable HTTP POST: it forwards a JSON-RPC request to the
// phone (waking it if needed) and returns the phone's response as JSON or SSE. Pure
// notifications get 202 with no body; `initialize` responses carry an Mcp-Session-Id.
func (s *Server) handleMCP(w http.ResponseWriter, r *http.Request) {
	if !s.acceptToken(bearerToken(r)) {
		w.Header().Set("WWW-Authenticate",
			`Bearer resource_metadata="`+baseURL(r)+`/.well-known/oauth-protected-resource"`)
		writeError(w, http.StatusUnauthorized, "missing or invalid access token")
		return
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, 1<<20))
	if err != nil {
		writeError(w, http.StatusBadRequest, "read error")
		return
	}

	method, isRequest := inspectRPC(body)

	// Notifications (no id) expect no response — ack and don't block on the phone.
	if !isRequest {
		w.WriteHeader(http.StatusAccepted)
		return
	}

	if !s.sessions.Connected() {
		device, derr := s.store.GetDevice()
		if derr != nil {
			s.audit(storage.EventError, "", false, "no device paired")
			writeError(w, http.StatusServiceUnavailable, "no device paired")
			return
		}
		s.audit(storage.EventWake, "", true, "")
		_ = s.waker.Wake(r.Context(), device.FCMToken)
		ctx, cancel := context.WithTimeout(r.Context(), s.wakeTimeout)
		defer cancel()
		if !s.sessions.WaitConnected(ctx) {
			s.audit(storage.EventError, "", false, "phone unavailable")
			writeError(w, http.StatusServiceUnavailable, "phone unavailable")
			return
		}
	}

	ctx, cancel := context.WithTimeout(r.Context(), phoneResponseTimeout)
	defer cancel()
	resp, err := s.sessions.Forward(ctx, body)
	if err != nil {
		s.audit(storage.EventError, "", false, err.Error())
		writeError(w, http.StatusBadGateway, "phone error: "+err.Error())
		return
	}
	s.audit(storage.EventForward, "", resp.Status != "error", resp.Error)

	// New session on initialize (Streamable HTTP session management).
	if method == "initialize" {
		w.Header().Set("Mcp-Session-Id", config.RandomURLToken(16))
	}
	if acceptsSSE(r) {
		writeSSE(w, resp.Payload)
	} else {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(resp.Payload)
	}
}

// inspectRPC reports the method and whether the message is a request (has an id).
// Batches and unparseable bodies are treated as requests (forwarded, response awaited).
func inspectRPC(body []byte) (method string, isRequest bool) {
	var msg struct {
		Method string          `json:"method"`
		ID     json.RawMessage `json:"id"`
	}
	if err := json.Unmarshal(body, &msg); err != nil {
		return "", true
	}
	hasID := len(msg.ID) > 0 && string(msg.ID) != "null"
	return msg.Method, hasID
}

func acceptsSSE(r *http.Request) bool {
	return strings.Contains(r.Header.Get("Accept"), "text/event-stream")
}

// writeSSE emits the JSON-RPC response as a single Server-Sent Event, then closes.
func writeSSE(w http.ResponseWriter, payload []byte) {
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte("event: message\ndata: "))
	_, _ = w.Write(payload)
	_, _ = w.Write([]byte("\n\n"))
	if f, ok := w.(http.Flusher); ok {
		f.Flush()
	}
}

// --- OAuth 2.1 endpoints (minimal, single-user, auto-approving) ---

func baseURL(r *http.Request) string {
	scheme := r.Header.Get("X-Forwarded-Proto")
	if scheme == "" {
		scheme = "https"
	}
	return scheme + "://" + r.Host
}

func (s *Server) handleProtectedResourceMeta(w http.ResponseWriter, r *http.Request) {
	base := baseURL(r)
	// Canonical resource URI WITHOUT a trailing slash — must match the `resource`
	// parameter Claude derives from the connector URL (RFC 8707 / MCP).
	writeJSON(w, http.StatusOK, map[string]any{
		"resource":              base + "/mcp-" + s.cfg.MCPSecret,
		"authorization_servers": []string{base},
	})
}

func (s *Server) handleAuthServerMeta(w http.ResponseWriter, r *http.Request) {
	base := baseURL(r)
	writeJSON(w, http.StatusOK, map[string]any{
		"issuer":                                base,
		"authorization_endpoint":                base + "/authorize",
		"token_endpoint":                        base + "/token",
		"registration_endpoint":                 base + "/register",
		"response_types_supported":              []string{"code"},
		"grant_types_supported":                 []string{"authorization_code", "client_credentials"},
		"code_challenge_methods_supported":      []string{"S256"},
		"token_endpoint_auth_methods_supported": []string{"none"},
	})
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req struct {
		RedirectURIs []string `json:"redirect_uris"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req) // tolerate empty/partial bodies
	clientID := s.oauth.Register(req.RedirectURIs)
	writeJSON(w, http.StatusCreated, map[string]any{
		"client_id":                  clientID,
		"redirect_uris":              req.RedirectURIs,
		"token_endpoint_auth_method": "none",
		"grant_types":                []string{"authorization_code"},
		"response_types":             []string{"code"},
	})
}

func (s *Server) handleAuthorize(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	if os.Getenv("RELAY_LOG_REQUESTS") == "1" {
		log.Printf("[authorize] query=%q", r.URL.RawQuery)
	}
	redirectURI := q.Get("redirect_uri")
	code, err := s.oauth.Authorize(
		q.Get("client_id"), redirectURI, q.Get("code_challenge"), q.Get("code_challenge_method"),
	)
	if err != nil {
		if os.Getenv("RELAY_LOG_REQUESTS") == "1" {
			log.Printf("[authorize] error: %v", err)
		}
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if os.Getenv("RELAY_LOG_REQUESTS") == "1" {
		log.Printf("[authorize] issued code=%q client=%q redirect=%q", code, q.Get("client_id"), redirectURI)
	}
	sep := "?"
	if strings.Contains(redirectURI, "?") {
		sep = "&"
	}
	location := redirectURI + sep + "code=" + url.QueryEscape(code)
	if state := q.Get("state"); state != "" {
		location += "&state=" + url.QueryEscape(state)
	}
	http.Redirect(w, r, location, http.StatusFound)
}

func (s *Server) handleToken(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_request"})
		return
	}
	grant := r.PostFormValue("grant_type")
	if os.Getenv("RELAY_LOG_REQUESTS") == "1" {
		log.Printf("[token] grant_type=%q client_id=%q code=%q redirect_uri=%q verifier=%t scope=%q",
			grant, r.PostFormValue("client_id"), r.PostFormValue("code"),
			r.PostFormValue("redirect_uri"), r.PostFormValue("code_verifier") != "",
			r.PostFormValue("scope"))
	}

	var (
		token     string
		expiresIn int
		err       error
	)
	switch grant {
	case "client_credentials":
		token, expiresIn, err = s.oauth.IssueToken(r.PostFormValue("client_id"))
	default: // authorization_code
		token, expiresIn, err = s.oauth.Exchange(
			r.PostFormValue("code"), r.PostFormValue("code_verifier"),
			r.PostFormValue("client_id"), r.PostFormValue("redirect_uri"),
		)
	}
	if os.Getenv("RELAY_LOG_REQUESTS") == "1" {
		log.Printf("[token] result ok=%t err=%v", err == nil, err)
	}
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_grant"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"access_token": token,
		"token_type":   "Bearer",
		"expires_in":   expiresIn,
	})
}

func (s *Server) audit(event, tool string, success bool, errMsg string) {
	_, _ = s.store.AppendAudit(storage.RelayAuditLog{
		OccurredAt:   time.Now(),
		EventType:    event,
		ToolName:     tool,
		Success:      success,
		ErrorMessage: errMsg,
	})
}

// handleWS authenticates the phone by its device_secret, then hands the socket to
// the session manager for the lifetime of the connection.
func (s *Server) handleWS(w http.ResponseWriter, r *http.Request) {
	secret := bearerToken(r)
	device, err := s.store.GetDevice()
	if err != nil || secret == "" ||
		subtle.ConstantTimeCompare([]byte(secret), []byte(device.Secret)) != 1 {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		return // Upgrade already wrote the error response
	}
	_ = s.store.TouchLastSeen(time.Now())
	s.sessions.Serve(conn) // blocks until the socket closes
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

type claimRequest struct {
	PairingCode string `json:"pairing_code"`
	FCMToken    string `json:"fcm_token"`
	DeviceName  string `json:"device_name"`
}

func (s *Server) handleClaim(w http.ResponseWriter, r *http.Request) {
	var req claimRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	secret, err := s.pairing.Claim(req.PairingCode, req.FCMToken, req.DeviceName)
	if err != nil {
		writeError(w, claimStatus(err), err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"device_secret": secret})
}

type refreshRequest struct {
	FCMToken string `json:"fcm_token"`
}

func (s *Server) handleRefresh(w http.ResponseWriter, r *http.Request) {
	secret := bearerToken(r)
	if secret == "" {
		writeError(w, http.StatusUnauthorized, "missing bearer token")
		return
	}
	var req refreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	if err := s.pairing.RefreshFCM(secret, req.FCMToken); err != nil {
		status := http.StatusInternalServerError
		switch {
		case errors.Is(err, pairing.ErrUnauthorized):
			status = http.StatusUnauthorized
		case errors.Is(err, pairing.ErrNotPaired):
			status = http.StatusNotFound
		}
		writeError(w, status, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func claimStatus(err error) int {
	switch {
	case errors.Is(err, pairing.ErrInvalidCode):
		return http.StatusNotFound
	case errors.Is(err, pairing.ErrExpired):
		return http.StatusGone
	case errors.Is(err, pairing.ErrConsumed):
		return http.StatusConflict
	default:
		return http.StatusInternalServerError
	}
}

func bearerToken(r *http.Request) string {
	const prefix = "Bearer "
	h := r.Header.Get("Authorization")
	if !strings.HasPrefix(h, prefix) {
		return ""
	}
	return strings.TrimSpace(strings.TrimPrefix(h, prefix))
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
