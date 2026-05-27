// Package storage holds the relay's tiny persistent state: the single paired device,
// one-shot pairing codes, and a metadata-only audit log (ARCHITECTURE §5.2). It is
// backed by an in-memory map persisted to a JSON file — adequate for a single-user
// relay and dependency-free; a SQLite backend could replace it behind the same API.
package storage

import (
	"encoding/json"
	"errors"
	"os"
	"sync"
	"time"
)

// Audit event types (ARCHITECTURE §5.2).
const (
	EventWake    = "WAKE"
	EventForward = "FORWARD"
	EventError   = "ERROR"
)

// ErrNotFound is returned when a requested record does not exist.
var ErrNotFound = errors.New("storage: not found")

// Device is the single paired phone (single-user relay).
type Device struct {
	DeviceName string    `json:"device_name"`
	FCMToken   string    `json:"fcm_token"`
	PublicKey  string    `json:"public_key,omitempty"`
	Secret     string    `json:"secret"` // device_secret; the WS Bearer credential
	PairedAt   time.Time `json:"paired_at"`
	LastSeen   time.Time `json:"last_seen"`
}

// PairingRequest is a one-shot code; expires after 5 minutes, consumed on claim.
type PairingRequest struct {
	Code       string     `json:"code"`
	ExpiresAt  time.Time  `json:"expires_at"`
	ConsumedAt *time.Time `json:"consumed_at,omitempty"`
}

// RelayAuditLog records a relay event — metadata only, never payload.
type RelayAuditLog struct {
	ID           int64     `json:"id"`
	OccurredAt   time.Time `json:"occurred_at"`
	EventType    string    `json:"event_type"`
	ToolName     string    `json:"tool_name,omitempty"`
	Success      bool      `json:"success"`
	ErrorMessage string    `json:"error_message,omitempty"`
}

// Store is the persistence API the relay handlers depend on.
type Store interface {
	SaveDevice(d Device) error
	GetDevice() (*Device, error)
	TouchLastSeen(t time.Time) error

	SavePairing(p PairingRequest) error
	GetPairing(code string) (*PairingRequest, error)
	ConsumePairing(code string, at time.Time) error

	AppendAudit(a RelayAuditLog) (RelayAuditLog, error)
	RecentAudit(limit int) ([]RelayAuditLog, error)
}

type state struct {
	Device   *Device                   `json:"device"`
	Pairings map[string]PairingRequest `json:"pairings"`
	Audit    []RelayAuditLog           `json:"audit"`
	AuditSeq int64                     `json:"audit_seq"`
}

// JSONStore is a thread-safe Store. With an empty path it stays purely in memory.
type JSONStore struct {
	mu   sync.Mutex
	path string
	data state
}

// Open loads the store from path (if it exists); an empty path means in-memory only.
func Open(path string) (*JSONStore, error) {
	s := &JSONStore{path: path, data: state{Pairings: map[string]PairingRequest{}}}
	if path == "" {
		return s, nil
	}
	bytes, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(bytes, &s.data); err != nil {
		return nil, err
	}
	if s.data.Pairings == nil {
		s.data.Pairings = map[string]PairingRequest{}
	}
	return s, nil
}

func (s *JSONStore) SaveDevice(d Device) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data.Device = &d
	return s.persist()
}

func (s *JSONStore) GetDevice() (*Device, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.data.Device == nil {
		return nil, ErrNotFound
	}
	clone := *s.data.Device
	return &clone, nil
}

func (s *JSONStore) TouchLastSeen(t time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.data.Device == nil {
		return ErrNotFound
	}
	s.data.Device.LastSeen = t
	return s.persist()
}

func (s *JSONStore) SavePairing(p PairingRequest) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data.Pairings[p.Code] = p
	return s.persist()
}

func (s *JSONStore) GetPairing(code string) (*PairingRequest, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.data.Pairings[code]
	if !ok {
		return nil, ErrNotFound
	}
	return &p, nil
}

func (s *JSONStore) ConsumePairing(code string, at time.Time) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.data.Pairings[code]
	if !ok {
		return ErrNotFound
	}
	p.ConsumedAt = &at
	s.data.Pairings[code] = p
	return s.persist()
}

func (s *JSONStore) AppendAudit(a RelayAuditLog) (RelayAuditLog, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data.AuditSeq++
	a.ID = s.data.AuditSeq
	s.data.Audit = append(s.data.Audit, a)
	return a, s.persist()
}

func (s *JSONStore) RecentAudit(limit int) ([]RelayAuditLog, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	n := len(s.data.Audit)
	if limit > 0 && limit < n {
		out := make([]RelayAuditLog, limit)
		copy(out, s.data.Audit[n-limit:])
		return out, nil
	}
	out := make([]RelayAuditLog, n)
	copy(out, s.data.Audit)
	return out, nil
}

// persist writes state to disk; caller must hold the lock. No-op when in-memory.
func (s *JSONStore) persist() error {
	if s.path == "" {
		return nil
	}
	bytes, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(s.path, bytes, 0o600)
}
