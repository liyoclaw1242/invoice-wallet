package config

import (
	"regexp"
	"testing"
)

func TestLoadGeneratesStableSecret(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("RELAY_DATA_DIR", dir)

	first, err := Load()
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if len(first.MCPSecret) < 32 {
		t.Fatalf("secret too short: %q", first.MCPSecret)
	}
	if first.Port != 8080 {
		t.Fatalf("want default port 8080, got %d", first.Port)
	}

	second, err := Load()
	if err != nil {
		t.Fatalf("reload: %v", err)
	}
	if second.MCPSecret != first.MCPSecret {
		t.Fatalf("secret should be stable across loads: %q vs %q", first.MCPSecret, second.MCPSecret)
	}
}

func TestPortFromEnv(t *testing.T) {
	t.Setenv("RELAY_DATA_DIR", t.TempDir())
	t.Setenv("RELAY_PORT", "9090")
	cfg, err := Load()
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if cfg.Port != 9090 {
		t.Fatalf("want 9090, got %d", cfg.Port)
	}
}

func TestPairingCodeFormat(t *testing.T) {
	re := regexp.MustCompile(`^[A-Z2-9]{4}-[A-Z2-9]{4}$`)
	for i := 0; i < 50; i++ {
		code := PairingCode()
		if !re.MatchString(code) {
			t.Fatalf("bad pairing code: %q", code)
		}
	}
}

func TestRandomURLTokenLength(t *testing.T) {
	if got := RandomURLToken(24); len(got) != 32 {
		t.Fatalf("want 32 chars, got %d (%q)", len(got), got)
	}
}
