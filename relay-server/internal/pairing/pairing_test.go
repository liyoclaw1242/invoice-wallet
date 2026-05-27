package pairing

import (
	"testing"
	"time"

	"github.com/liyoclaw/invoice-relay/internal/storage"
)

func newService(t *testing.T, now time.Time) (*Service, storage.Store) {
	t.Helper()
	store, err := storage.Open("")
	if err != nil {
		t.Fatalf("store: %v", err)
	}
	return NewService(store, func() time.Time { return now }), store
}

func TestClaimSucceedsAndRegistersDevice(t *testing.T) {
	now := time.Date(2026, 2, 1, 12, 0, 0, 0, time.UTC)
	svc, store := newService(t, now)
	p, _ := svc.Create()

	secret, err := svc.Claim(p.Code, "fcm-tok", "Mi MIX 2")
	if err != nil {
		t.Fatalf("claim: %v", err)
	}
	if len(secret) < 32 {
		t.Fatalf("weak secret: %q", secret)
	}
	device, err := store.GetDevice()
	if err != nil || device.Secret != secret || device.FCMToken != "fcm-tok" {
		t.Fatalf("device not registered: %+v %v", device, err)
	}
}

func TestClaimRejectsUnknownCode(t *testing.T) {
	svc, _ := newService(t, time.Now())
	if _, err := svc.Claim("NOPE-NOPE", "f", "d"); err != ErrInvalidCode {
		t.Fatalf("want ErrInvalidCode, got %v", err)
	}
}

func TestClaimRejectsExpiredCode(t *testing.T) {
	now := time.Date(2026, 2, 1, 12, 0, 0, 0, time.UTC)
	svc, _ := newService(t, now)
	p, _ := svc.Create()
	svc.now = func() time.Time { return now.Add(TTL + time.Second) }

	if _, err := svc.Claim(p.Code, "f", "d"); err != ErrExpired {
		t.Fatalf("want ErrExpired, got %v", err)
	}
}

func TestClaimRejectsReuse(t *testing.T) {
	now := time.Date(2026, 2, 1, 12, 0, 0, 0, time.UTC)
	svc, _ := newService(t, now)
	p, _ := svc.Create()
	if _, err := svc.Claim(p.Code, "f", "d"); err != nil {
		t.Fatalf("first claim: %v", err)
	}
	if _, err := svc.Claim(p.Code, "f", "d"); err != ErrConsumed {
		t.Fatalf("want ErrConsumed, got %v", err)
	}
}

func TestRefreshFCMRequiresMatchingSecret(t *testing.T) {
	now := time.Date(2026, 2, 1, 12, 0, 0, 0, time.UTC)
	svc, store := newService(t, now)
	p, _ := svc.Create()
	secret, _ := svc.Claim(p.Code, "old", "d")

	if err := svc.RefreshFCM("wrong-secret", "new"); err != ErrUnauthorized {
		t.Fatalf("want ErrUnauthorized, got %v", err)
	}
	if err := svc.RefreshFCM(secret, "new"); err != nil {
		t.Fatalf("refresh: %v", err)
	}
	device, _ := store.GetDevice()
	if device.FCMToken != "new" {
		t.Fatalf("fcm not updated: %q", device.FCMToken)
	}
}
