package relay

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/liyoclaw/invoice-relay/internal/config"
	"github.com/liyoclaw/invoice-relay/internal/storage"
)

func newTestServer(t *testing.T) *Server {
	t.Helper()
	store, err := storage.Open("")
	if err != nil {
		t.Fatalf("store: %v", err)
	}
	return New(config.Config{Port: 8080, MCPSecret: "test-secret"}, store)
}

func TestHealth(t *testing.T) {
	srv := newTestServer(t)
	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("want 200, got %d", rec.Code)
	}
	if body := rec.Body.String(); body != `{"status":"ok"}` {
		t.Fatalf("unexpected body: %q", body)
	}
}
