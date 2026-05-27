package relay

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
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

func do(t *testing.T, srv *Server, method, path, auth, body string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	if auth != "" {
		req.Header.Set("Authorization", "Bearer "+auth)
	}
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)
	return rec
}

func TestHealth(t *testing.T) {
	rec := do(t, newTestServer(t), http.MethodGet, "/health", "", "")
	if rec.Code != http.StatusOK {
		t.Fatalf("want 200, got %d", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), `"status":"ok"`) {
		t.Fatalf("unexpected body: %q", rec.Body.String())
	}
}

func TestClaimHappyPathReturnsSecret(t *testing.T) {
	srv := newTestServer(t)
	p, _ := srv.Pairing().Create()

	rec := do(t, srv, http.MethodPost, "/pair/claim", "",
		`{"pairing_code":"`+p.Code+`","fcm_token":"fcm-1","device_name":"Mi MIX 2"}`)
	if rec.Code != http.StatusOK {
		t.Fatalf("want 200, got %d (%s)", rec.Code, rec.Body.String())
	}
	var resp map[string]string
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	if len(resp["device_secret"]) < 32 {
		t.Fatalf("weak/missing secret: %q", resp["device_secret"])
	}
}

func TestClaimUnknownCodeIs404(t *testing.T) {
	rec := do(t, newTestServer(t), http.MethodPost, "/pair/claim", "",
		`{"pairing_code":"NOPE-NOPE","fcm_token":"f","device_name":"d"}`)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("want 404, got %d", rec.Code)
	}
}

func TestClaimReuseIs409(t *testing.T) {
	srv := newTestServer(t)
	p, _ := srv.Pairing().Create()
	body := `{"pairing_code":"` + p.Code + `","fcm_token":"f","device_name":"d"}`
	_ = do(t, srv, http.MethodPost, "/pair/claim", "", body)
	rec := do(t, srv, http.MethodPost, "/pair/claim", "", body)
	if rec.Code != http.StatusConflict {
		t.Fatalf("want 409, got %d", rec.Code)
	}
}

func TestRefreshRequiresValidSecret(t *testing.T) {
	srv := newTestServer(t)
	p, _ := srv.Pairing().Create()
	rec := do(t, srv, http.MethodPost, "/pair/claim", "",
		`{"pairing_code":"`+p.Code+`","fcm_token":"old","device_name":"d"}`)
	var resp map[string]string
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	secret := resp["device_secret"]

	if got := do(t, srv, http.MethodPost, "/device/refresh", "wrong", `{"fcm_token":"new"}`); got.Code != http.StatusUnauthorized {
		t.Fatalf("want 401, got %d", got.Code)
	}
	if got := do(t, srv, http.MethodPost, "/device/refresh", secret, `{"fcm_token":"new"}`); got.Code != http.StatusOK {
		t.Fatalf("want 200, got %d", got.Code)
	}
}
