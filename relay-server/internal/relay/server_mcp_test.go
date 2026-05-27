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

func TestMCPWrongSecretIs404(t *testing.T) {
	srv := newTestServer(t)
	pairDevice(t, srv)
	rec := do(t, srv, http.MethodPost, "/mcp-not-the-secret/", "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("want 404, got %d", rec.Code)
	}
}

func TestMCPForwardsToConnectedPhone(t *testing.T) {
	srv := newTestServer(t)
	secret := pairDevice(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	conn, _, err := websocket.DefaultDialer.Dial(wsURL(ts.URL), http.Header{"Authorization": {"Bearer " + secret}})
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()
	// Stand-in phone echoes a result for whatever it receives.
	go func() {
		for {
			var env session.Envelope
			if conn.ReadJSON(&env) != nil {
				return
			}
			_ = conn.WriteJSON(session.Envelope{
				Type:      session.TypeResponse,
				RequestID: env.RequestID,
				Status:    "ok",
				Payload:   json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{"tools":["list_invoices"]}}`),
			})
		}
	}()
	waitFor(t, func() bool { return srv.Sessions().Connected() })

	resp, err := http.Post(ts.URL+mcpPath(srv), "application/json",
		strings.NewReader(`{"jsonrpc":"2.0","method":"tools/list","id":1}`))
	if err != nil {
		t.Fatalf("post: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK || !strings.Contains(string(body), "list_invoices") {
		t.Fatalf("unexpected: %d %s", resp.StatusCode, body)
	}
}

func TestMCPGetReturns405(t *testing.T) {
	srv := newTestServer(t)
	if rec := do(t, srv, http.MethodGet, mcpPath(srv), "", ""); rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("want 405, got %d", rec.Code)
	}
}

func TestMCPNotificationReturns202WithoutPhone(t *testing.T) {
	srv := newTestServer(t)
	pairDevice(t, srv) // device paired but no WS connected
	body := `{"jsonrpc":"2.0","method":"notifications/initialized"}`
	// Slash and no-slash both route to the handler and ack without blocking on the phone.
	if rec := do(t, srv, http.MethodPost, mcpPath(srv), "", body); rec.Code != http.StatusAccepted {
		t.Fatalf("slash: want 202, got %d", rec.Code)
	}
	noSlash := "/mcp-" + srv.cfg.MCPSecret
	if rec := do(t, srv, http.MethodPost, noSlash, "", body); rec.Code != http.StatusAccepted {
		t.Fatalf("no-slash: want 202, got %d", rec.Code)
	}
}

func TestMCPInitializeSetsSessionIdAndSSE(t *testing.T) {
	srv := newTestServer(t)
	secret := pairDevice(t, srv)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()
	conn, _, err := websocket.DefaultDialer.Dial(wsURL(ts.URL), http.Header{"Authorization": {"Bearer " + secret}})
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()
	go func() {
		for {
			var env session.Envelope
			if conn.ReadJSON(&env) != nil {
				return
			}
			_ = conn.WriteJSON(session.Envelope{
				Type:      session.TypeResponse,
				RequestID: env.RequestID,
				Status:    "ok",
				Payload:   json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}`),
			})
		}
	}()
	waitFor(t, func() bool { return srv.Sessions().Connected() })

	// JSON path: initialize → 200 + Mcp-Session-Id header.
	resp, err := http.Post(ts.URL+mcpPath(srv), "application/json",
		strings.NewReader(`{"jsonrpc":"2.0","method":"initialize","id":1}`))
	if err != nil {
		t.Fatalf("post: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("want 200, got %d", resp.StatusCode)
	}
	if resp.Header.Get("Mcp-Session-Id") == "" {
		t.Fatal("expected Mcp-Session-Id header on initialize")
	}

	// SSE path: Accept text/event-stream → event-stream body.
	req, _ := http.NewRequest(http.MethodPost, ts.URL+mcpPath(srv),
		strings.NewReader(`{"jsonrpc":"2.0","method":"tools/list","id":2}`))
	req.Header.Set("Accept", "text/event-stream")
	sse, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("sse post: %v", err)
	}
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

	rec := do(t, srv, http.MethodPost, mcpPath(srv), "", `{"jsonrpc":"2.0","method":"tools/list","id":1}`)
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("want 503, got %d", rec.Code)
	}
	if waker.calls.Load() != 1 {
		t.Fatalf("want waker called once, got %d", waker.calls.Load())
	}
}
