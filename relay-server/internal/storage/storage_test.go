package storage

import (
	"path/filepath"
	"testing"
	"time"
)

func TestDeviceRoundTripsAcrossReopen(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	s, err := Open(path)
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	now := time.Now().UTC().Truncate(time.Second)
	if err := s.SaveDevice(Device{DeviceName: "Mi MIX 2", FCMToken: "fcm-1", Secret: "sec", PairedAt: now}); err != nil {
		t.Fatalf("save: %v", err)
	}

	reopened, err := Open(path)
	if err != nil {
		t.Fatalf("reopen: %v", err)
	}
	got, err := reopened.GetDevice()
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if got.DeviceName != "Mi MIX 2" || got.Secret != "sec" || got.FCMToken != "fcm-1" {
		t.Fatalf("unexpected device: %+v", got)
	}
}

func TestGetDeviceMissing(t *testing.T) {
	s, _ := Open("")
	if _, err := s.GetDevice(); err != ErrNotFound {
		t.Fatalf("want ErrNotFound, got %v", err)
	}
}

func TestPairingSaveGetConsume(t *testing.T) {
	s, _ := Open("")
	code := "A3F9-K2P7"
	exp := time.Now().Add(5 * time.Minute)
	if err := s.SavePairing(PairingRequest{Code: code, ExpiresAt: exp}); err != nil {
		t.Fatalf("save pairing: %v", err)
	}
	p, err := s.GetPairing(code)
	if err != nil || p.ConsumedAt != nil {
		t.Fatalf("get pairing: %v %+v", err, p)
	}
	at := time.Now()
	if err := s.ConsumePairing(code, at); err != nil {
		t.Fatalf("consume: %v", err)
	}
	p, _ = s.GetPairing(code)
	if p.ConsumedAt == nil {
		t.Fatalf("expected consumed")
	}
}

func TestAuditAppendAssignsIncrementingIDsAndRecentTrims(t *testing.T) {
	s, _ := Open("")
	for i := 0; i < 5; i++ {
		a, err := s.AppendAudit(RelayAuditLog{EventType: EventForward, ToolName: "list_invoices", Success: true})
		if err != nil {
			t.Fatalf("append: %v", err)
		}
		if a.ID != int64(i+1) {
			t.Fatalf("want id %d, got %d", i+1, a.ID)
		}
	}
	recent, _ := s.RecentAudit(2)
	if len(recent) != 2 || recent[0].ID != 4 || recent[1].ID != 5 {
		t.Fatalf("unexpected recent: %+v", recent)
	}
}
