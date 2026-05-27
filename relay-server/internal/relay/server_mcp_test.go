package relay

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/liyoclaw/invoice-relay/internal/session"
)

type recordingWaker struct{ calls atomic.Int32 }

func (w *recordingWaker) Wake(context.Context, string) error {
	w.calls.Add(1)
	return nil
}

func mcpPath(srv *Server) string { return "/mcp-" + srv.cfg.MCPSecret + "/" }

// mintToken runs the in-process OAuth flow to obtain a valid access token.
func mintToken(t *testing.T, srv *Server) string {
	t.Helper()
	id := srv.oauth.Register([]string{"https://client/cb"})
	code, err := srv.oauth.Authorize(id, "https://client/cb", "", "")
	if err != nil {
		t.Fatalf("authorize: %v", err)
	}
	token, _, err := srv.oauth.Exchange(code, "", id, "https://client/cb")
	if err != nil {
		t.Fatalf("exchange: %v", err)
	}
	return token
}

// echoPhone dials a phone WebSocket that answers every request with a fixed result.
func echoPhone(t *testing.T, srv *Server, ts *httptest.Server, secret, payload string) *websocket.Conn {
	t.Helper()
	conn, _, err := websocket.DefaultDialer.Dial(wsURL(ts.URL), http.Header{"Authorization": {"Bearer " + secret}})
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	go func() {
		for {
			var env session.Envelope
			if conn.ReadJSON(&env) != nil {
				return
			}
			_ = conn.WriteJSON(session.Envelope{
				Type: session.TypeResponse, RequestID: env.RequestID, Status: "ok",
				Payload: json.RawMessage(payload),
			})
		}
	}()
	waitFor(t, func() bool { return srv.Sessions().Connected() })
	return conn
}

func postMCP(t *testing.T, ts *httptest.Server, srv *Server, token, accept, body string) *http.Response {
	t.Helper()
	req, _ := http.NewRequest(http.MethodPost, ts.URL+mcpPath(srv), strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	if accept != "" {
		req.Header.Set("Accept", accept)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("post: %v", err)
	}
	return resp
}

func TestMCPWrongSecretIs404(t *testing.T) {
	srv := newTestServer(t)
	pairDevice(t, srv)
	rec := do(t, srv, http.MethodPost, "/mcp-not-the-secret/", "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("want 404, got %d", rec.Code)
	}
}

func TestMCPWithoutTokenIs401(t *testing.T) {
	srv := newTestServer(t)
	pairDevice(t, srv)
	rec := do(t, srv, http.MethodPost, mcpPath(srv), "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("want 401, got %d", rec.Code)
	}
	if !strings.Contains(rec.Header().Get("WWW-Authenticate"), "resource_metadata=") {
		t.Fatalf("expected WWW-Authenticate with resource_metadata, got %q", rec.Header().Get("WWW-Authenticate"))
	}
}

func TestMCPForwardsToConnectedPhone(t *testing.T) {
	srv := newTestServer(t)
	secret := pairDevice(t, srv)
	token := mintToken(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()
	conn := echoPhone(t, srv, ts, secret, `{"jsonrpc":"2.0","id":1,"result":{"tools":["list_invoices"]}}`)
	defer conn.Close()

	resp := postMCP(t, ts, srv, token, "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK || !strings.Contains(string(body), "list_invoices") {
		t.Fatalf("unexpected: %d %s", resp.StatusCode, body)
	}
}

func TestMCPStaticAPITokenAccepted(t *testing.T) {
	srv := newTestServer(t)
	secret := pairDevice(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()
	conn := echoPhone(t, srv, ts, secret, `{"jsonrpc":"2.0","id":1,"result":{"ok":true}}`)
	defer conn.Close()

	// Static API token (header-injection path) is accepted without any OAuth.
	resp := postMCP(t, ts, srv, "test-api-token", "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("static token: want 200, got %d", resp.StatusCode)
	}
	// A wrong static token is rejected.
	bad := postMCP(t, ts, srv, "wrong-token", "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	defer bad.Body.Close()
	if bad.StatusCode != http.StatusUnauthorized {
		t.Fatalf("wrong token: want 401, got %d", bad.StatusCode)
	}
}

func TestMCPGetReturns405(t *testing.T) {
	srv := newTestServer(t)
	if rec := do(t, srv, http.MethodGet, mcpPath(srv), "", ""); rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("want 405, got %d", rec.Code)
	}
}

func TestMCPNotificationReturns202(t *testing.T) {
	srv := newTestServer(t)
	pairDevice(t, srv)
	token := mintToken(t, srv)
	body := `{"jsonrpc":"2.0","method":"notifications/initialized"}`
	// Slash and no-slash both route to the handler and ack without blocking on the phone.
	if rec := do(t, srv, http.MethodPost, mcpPath(srv), token, body); rec.Code != http.StatusAccepted {
		t.Fatalf("slash: want 202, got %d", rec.Code)
	}
	noSlash := "/mcp-" + srv.cfg.MCPSecret
	if rec := do(t, srv, http.MethodPost, noSlash, token, body); rec.Code != http.StatusAccepted {
		t.Fatalf("no-slash: want 202, got %d", rec.Code)
	}
}

func TestMCPInitializeSetsSessionIdAndSSE(t *testing.T) {
	srv := newTestServer(t)
	secret := pairDevice(t, srv)
	token := mintToken(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()
	conn := echoPhone(t, srv, ts, secret, `{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}`)
	defer conn.Close()

	// JSON path: initialize → 200 + Mcp-Session-Id header.
	resp := postMCP(t, ts, srv, token, "", `{"jsonrpc":"2.0","method":"initialize","id":1}`)
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("want 200, got %d", resp.StatusCode)
	}
	if resp.Header.Get("Mcp-Session-Id") == "" {
		t.Fatal("expected Mcp-Session-Id header on initialize")
	}

	// SSE path: Accept text/event-stream → event-stream body.
	sse := postMCP(t, ts, srv, token, "text/event-stream", `{"jsonrpc":"2.0","method":"tools/list","id":2}`)
	defer sse.Body.Close()
	if ct := sse.Header.Get("Content-Type"); !strings.HasPrefix(ct, "text/event-stream") {
		t.Fatalf("want event-stream, got %q", ct)
	}
	b, _ := io.ReadAll(sse.Body)
	if !strings.Contains(string(b), "event: message") || !strings.Contains(string(b), "result") {
		t.Fatalf("unexpected SSE body: %q", b)
	}
}

func TestMCPWakesPhoneWhenDisconnected(t *testing.T) {
	srv := newTestServer(t)
	srv.wakeTimeout = 200 * time.Millisecond
	waker := &recordingWaker{}
	srv.SetWaker(waker)
	pairDevice(t, srv) // device exists, but no WS connected
	token := mintToken(t, srv)

	rec := do(t, srv, http.MethodPost, mcpPath(srv), token, `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("want 503, got %d", rec.Code)
	}
	if waker.calls.Load() != 1 {
		t.Fatalf("want waker called once, got %d", waker.calls.Load())
	}
}
