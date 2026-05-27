package session

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"
)

type fakeConn struct {
	toPhone   chan []byte
	fromPhone chan []byte
	closed    chan struct{}
}

func newFakeConn() *fakeConn {
	return &fakeConn{
		toPhone:   make(chan []byte, 4),
		fromPhone: make(chan []byte, 4),
		closed:    make(chan struct{}),
	}
}

func (c *fakeConn) WriteJSON(v any) error {
	b, _ := json.Marshal(v)
	select {
	case c.toPhone <- b:
		return nil
	case <-c.closed:
		return errors.New("closed")
	}
}

func (c *fakeConn) ReadJSON(v any) error {
	select {
	case b := <-c.fromPhone:
		return json.Unmarshal(b, v)
	case <-c.closed:
		return errors.New("closed")
	}
}

func (c *fakeConn) Close() error {
	select {
	case <-c.closed:
	default:
		close(c.closed)
	}
	return nil
}

func TestForwardWithoutConnection(t *testing.T) {
	m := NewManager(time.Second)
	if _, err := m.Forward(context.Background(), json.RawMessage(`{}`)); err != ErrNotConnected {
		t.Fatalf("want ErrNotConnected, got %v", err)
	}
}

func TestForwardRoundTrip(t *testing.T) {
	m := NewManager(2 * time.Second)
	conn := newFakeConn()
	go m.Serve(conn)
	waitConnected(t, m)

	// Stand-in phone: read the request, echo a response with the same request_id.
	go func() {
		var req Envelope
		_ = json.Unmarshal(<-conn.toPhone, &req)
		resp := Envelope{
			Type:      TypeResponse,
			RequestID: req.RequestID,
			Status:    "ok",
			Payload:   json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{"ok":true}}`),
		}
		b, _ := json.Marshal(resp)
		conn.fromPhone <- b
	}()

	resp, err := m.Forward(context.Background(), json.RawMessage(`{"jsonrpc":"2.0","method":"tools/list","id":1}`))
	if err != nil {
		t.Fatalf("forward: %v", err)
	}
	if resp.Status != "ok" || !contains(string(resp.Payload), `"ok":true`) {
		t.Fatalf("unexpected response: %+v", resp)
	}
}

func TestForwardTimesOut(t *testing.T) {
	m := NewManager(100 * time.Millisecond)
	conn := newFakeConn()
	go m.Serve(conn)
	waitConnected(t, m)
	// Phone reads but never replies.
	go func() { <-conn.toPhone }()

	if _, err := m.Forward(context.Background(), json.RawMessage(`{}`)); err != ErrTimeout {
		t.Fatalf("want ErrTimeout, got %v", err)
	}
}

func waitConnected(t *testing.T, m *Manager) {
	t.Helper()
	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		if m.Connected() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatal("manager never connected")
}

func contains(s, sub string) bool {
	return len(s) >= len(sub) && (s == sub || indexOf(s, sub) >= 0)
}

func indexOf(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}
