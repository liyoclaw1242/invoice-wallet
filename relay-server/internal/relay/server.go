// Package relay wires the relay's HTTP surface from the sub-packages (pairing,
// session/WebSocket, MCP secret endpoint) into a single handler.
package relay

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/liyoclaw/invoice-relay/internal/config"
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
	mux.HandleFunc("POST /mcp-"+s.cfg.MCPSecret+"/{$}", s.handleMCP)
	return mux
}

// handleMCP forwards a raw JSON-RPC request to the phone (waking it if needed) and
// returns the phone's response. The secret path is already validated by routing.
func (s *Server) handleMCP(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(io.LimitReader(r.Body, 1<<20))
	if err != nil {
		writeError(w, http.StatusBadRequest, "read error")
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
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(resp.Payload)
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
