// Package relay wires the relay's HTTP surface from the sub-packages (pairing,
// session/WebSocket, MCP secret endpoint) into a single handler.
package relay

import (
	"net/http"

	"github.com/liyoclaw/invoice-relay/internal/config"
	"github.com/liyoclaw/invoice-relay/internal/storage"
)

// Server holds the relay's shared dependencies.
type Server struct {
	cfg   config.Config
	store storage.Store
}

// New builds a Server from its configuration and store.
func New(cfg config.Config, store storage.Store) *Server {
	return &Server{cfg: cfg, store: store}
}

// Handler returns the relay's HTTP routes.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.handleHealth)
	return mux
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"ok"}`))
}
