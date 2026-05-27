// Package relay wires the relay's HTTP surface from the sub-packages (pairing,
// session/WebSocket, MCP secret endpoint) into a single handler.
package relay

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/liyoclaw/invoice-relay/internal/config"
	"github.com/liyoclaw/invoice-relay/internal/pairing"
	"github.com/liyoclaw/invoice-relay/internal/session"
	"github.com/liyoclaw/invoice-relay/internal/storage"
)

// phoneResponseTimeout bounds how long the relay waits for the phone to answer.
const phoneResponseTimeout = 30 * time.Second

// Server holds the relay's shared dependencies.
type Server struct {
	cfg      config.Config
	store    storage.Store
	pairing  *pairing.Service
	sessions *session.Manager
	upgrader websocket.Upgrader
}

// New builds a Server from its configuration and store.
func New(cfg config.Config, store storage.Store) *Server {
	return &Server{
		cfg:      cfg,
		store:    store,
		pairing:  pairing.NewService(store, nil),
		sessions: session.NewManager(phoneResponseTimeout),
		// Auth is the Bearer device_secret, so the WS origin is not a trust boundary.
		upgrader: websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }},
	}
}

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
	return mux
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
