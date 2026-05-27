package relay

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/liyoclaw/invoice-relay/internal/session"
)

// pairs a device through the HTTP API and returns its device_secret.
func pairDevice(t *testing.T, srv *Server) string {
	t.Helper()
	p, _ := srv.Pairing().Create()
	rec := do(t, srv, http.MethodPost, "/pair/claim", "",
		`{"pairing_code":"`+p.Code+`","fcm_token":"f","device_name":"d"}`)
	var resp map[string]string
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	return resp["device_secret"]
}

func wsURL(httpURL string) string { return "ws" + strings.TrimPrefix(httpURL, "http") + "/ws" }

func TestWSRejectsWrongSecret(t *testing.T) {
	srv := newTestServer(t)
	pairDevice(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	_, resp, err := websocket.DefaultDialer.Dial(wsURL(ts.URL), http.Header{"Authorization": {"Bearer wrong"}})
	if err == nil {
		t.Fatal("expected handshake to fail")
	}
	if resp == nil || resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("want 401, got %v", resp)
	}
}

func TestWSConnectAndForward(t *testing.T) {
	srv := newTestServer(t)
	secret := pairDevice(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	conn, _, err := websocket.DefaultDialer.Dial(wsURL(ts.URL), http.Header{"Authorization": {"Bearer " + secret}})
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()

	// Stand-in phone: answer the first mcp_request.
	go func() {
		var env session.Envelope
		if err := conn.ReadJSON(&env); err != nil {
			return
		}
		_ = conn.WriteJSON(session.Envelope{
			Type:      session.TypeResponse,
			RequestID: env.RequestID,
			Status:    "ok",
			Payload:   json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}`),
		})
	}()

	waitFor(t, func() bool { return srv.Sessions().Connected() })

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	resp, err := srv.Sessions().Forward(ctx, json.RawMessage(`{"jsonrpc":"2.0","method":"tools/list","id":1}`))
	if err != nil {
		t.Fatalf("forward: %v", err)
	}
	if resp.Status != "ok" || !strings.Contains(string(resp.Payload), `"tools"`) {
		t.Fatalf("unexpected: %+v", resp)
	}
}

func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("condition not met in time")
}
