// Package session manages the single phone WebSocket connection and correlates
// request/response envelopes by request_id (ARCHITECTURE §8.2).
//
// The envelope tunnels the *raw* JSON-RPC payload (rather than only tool_name +
// arguments) so the phone's MCP dispatcher handles initialize / tools/list /
// tools/call uniformly — a small, deliberate generalisation of the doc's format.
package session

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"github.com/liyoclaw/invoice-relay/internal/config"
)

const (
	TypeRequest  = "mcp_request"
	TypeResponse = "mcp_response"
)

var (
	ErrNotConnected = errors.New("session: phone not connected")
	ErrTimeout      = errors.New("session: phone response timed out")
)

// Envelope is the WebSocket message exchanged with the phone.
type Envelope struct {
	Type      string          `json:"type"`
	RequestID string          `json:"request_id"`
	Payload   json.RawMessage `json:"payload,omitempty"`
	Status    string          `json:"status,omitempty"`
	Error     string          `json:"error,omitempty"`
}

// Conn is the subset of a WebSocket connection the manager needs (satisfied by
// *gorilla/websocket.Conn), abstracted so the manager is testable without a socket.
type Conn interface {
	WriteJSON(v any) error
	ReadJSON(v any) error
	Close() error
}

// Manager owns the active phone connection and pending request correlations.
type Manager struct {
	timeout time.Duration

	mu      sync.Mutex
	conn    Conn
	writeMu sync.Mutex
	pending map[string]chan Envelope
}

// NewManager builds a Manager with the given per-request response timeout.
func NewManager(timeout time.Duration) *Manager {
	return &Manager{timeout: timeout, pending: map[string]chan Envelope{}}
}

// Connected reports whether a phone WebSocket is currently registered.
func (m *Manager) Connected() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.conn != nil
}

// Serve registers conn as the active phone and reads responses until the socket
// closes. Blocks until the read loop ends; callers run it in a goroutine.
func (m *Manager) Serve(conn Conn) {
	m.mu.Lock()
	m.conn = conn
	m.mu.Unlock()

	defer func() {
		m.mu.Lock()
		if m.conn == conn {
			m.conn = nil
		}
		m.mu.Unlock()
	}()

	for {
		var env Envelope
		if err := conn.ReadJSON(&env); err != nil {
			return
		}
		if env.Type == TypeResponse {
			m.deliver(env)
		}
	}
}

// Forward sends a JSON-RPC payload to the phone and waits for the matching response.
func (m *Manager) Forward(ctx context.Context, payload json.RawMessage) (Envelope, error) {
	m.mu.Lock()
	conn := m.conn
	m.mu.Unlock()
	if conn == nil {
		return Envelope{}, ErrNotConnected
	}

	id := config.RandomURLToken(12)
	ch := make(chan Envelope, 1)
	m.mu.Lock()
	m.pending[id] = ch
	m.mu.Unlock()
	defer func() {
		m.mu.Lock()
		delete(m.pending, id)
		m.mu.Unlock()
	}()

	m.writeMu.Lock()
	err := conn.WriteJSON(Envelope{Type: TypeRequest, RequestID: id, Payload: payload})
	m.writeMu.Unlock()
	if err != nil {
		return Envelope{}, err
	}

	select {
	case resp := <-ch:
		return resp, nil
	case <-time.After(m.timeout):
		return Envelope{}, ErrTimeout
	case <-ctx.Done():
		return Envelope{}, ctx.Err()
	}
}

func (m *Manager) deliver(env Envelope) {
	m.mu.Lock()
	ch := m.pending[env.RequestID]
	m.mu.Unlock()
	if ch != nil {
		select {
		case ch <- env:
		default:
		}
	}
}
