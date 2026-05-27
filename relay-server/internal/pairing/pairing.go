// Package pairing implements the one-shot QR pairing flow (ARCHITECTURE §7): the relay
// mints a short-lived code, the phone claims it with its FCM token and gets back a
// device_secret used to authenticate the WebSocket.
package pairing

import (
	"errors"
	"time"

	"github.com/liyoclaw/invoice-relay/internal/config"
	"github.com/liyoclaw/invoice-relay/internal/storage"
)

// TTL is how long a pairing code stays valid.
const TTL = 5 * time.Minute

var (
	ErrInvalidCode  = errors.New("pairing: invalid code")
	ErrExpired      = errors.New("pairing: code expired")
	ErrConsumed     = errors.New("pairing: code already used")
	ErrUnauthorized = errors.New("pairing: unauthorized")
	ErrNotPaired    = errors.New("pairing: no device paired")
)

// Service runs pairing operations against the store.
type Service struct {
	store storage.Store
	now   func() time.Time
}

// NewService builds a Service; pass nil for now to use the wall clock.
func NewService(store storage.Store, now func() time.Time) *Service {
	if now == nil {
		now = time.Now
	}
	return &Service{store: store, now: now}
}

// Create mints and persists a fresh pairing code valid for [TTL].
func (s *Service) Create() (storage.PairingRequest, error) {
	p := storage.PairingRequest{
		Code:      config.PairingCode(),
		ExpiresAt: s.now().Add(TTL),
	}
	if err := s.store.SavePairing(p); err != nil {
		return storage.PairingRequest{}, err
	}
	return p, nil
}

// Claim validates the code and registers the device, returning a new device_secret.
func (s *Service) Claim(code, fcmToken, deviceName string) (string, error) {
	p, err := s.store.GetPairing(code)
	if errors.Is(err, storage.ErrNotFound) {
		return "", ErrInvalidCode
	}
	if err != nil {
		return "", err
	}
	if p.ConsumedAt != nil {
		return "", ErrConsumed
	}
	now := s.now()
	if !now.Before(p.ExpiresAt) {
		return "", ErrExpired
	}
	if err := s.store.ConsumePairing(code, now); err != nil {
		return "", err
	}
	secret := config.RandomURLToken(24)
	device := storage.Device{
		DeviceName: deviceName,
		FCMToken:   fcmToken,
		Secret:     secret,
		PairedAt:   now,
		LastSeen:   now,
	}
	if err := s.store.SaveDevice(device); err != nil {
		return "", err
	}
	return secret, nil
}

// RefreshFCM updates the paired device's FCM token, authenticated by its secret.
func (s *Service) RefreshFCM(secret, fcmToken string) error {
	device, err := s.store.GetDevice()
	if errors.Is(err, storage.ErrNotFound) {
		return ErrNotPaired
	}
	if err != nil {
		return err
	}
	if !secretEqual(device.Secret, secret) {
		return ErrUnauthorized
	}
	device.FCMToken = fcmToken
	device.LastSeen = s.now()
	return s.store.SaveDevice(*device)
}

// secretEqual is a length-independent constant-time comparison.
func secretEqual(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	var diff byte
	for i := 0; i < len(a); i++ {
		diff |= a[i] ^ b[i]
	}
	return diff == 0
}
