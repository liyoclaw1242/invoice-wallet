package config

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// Config is the relay's runtime configuration, resolved from environment variables
// with sensible defaults. The MCP secret URL suffix is generated once and persisted.
type Config struct {
	Port      int    // listen port (cloudflared fronts this); default 8080
	StatePath string // JSON state file
	MCPSecret string // 32-char random suffix for the /mcp-<secret>/ endpoint
}

// Load resolves configuration, generating + persisting the MCP secret on first run.
//
//	RELAY_DATA_DIR  directory for state + secret files (default ".")
//	RELAY_PORT      listen port (default 8080)
func Load() (Config, error) {
	dataDir := envOr("RELAY_DATA_DIR", ".")
	if err := os.MkdirAll(dataDir, 0o700); err != nil {
		return Config{}, err
	}
	port := 8080
	if raw := os.Getenv("RELAY_PORT"); raw != "" {
		p, err := strconv.Atoi(raw)
		if err != nil {
			return Config{}, err
		}
		port = p
	}
	secret, err := ensureSecret(filepath.Join(dataDir, "mcp_secret"))
	if err != nil {
		return Config{}, err
	}
	return Config{
		Port:      port,
		StatePath: filepath.Join(dataDir, "state.json"),
		MCPSecret: secret,
	}, nil
}

// ensureSecret returns the persisted secret, generating + writing one on first call.
func ensureSecret(path string) (string, error) {
	if bytes, err := os.ReadFile(path); err == nil {
		if s := strings.TrimSpace(string(bytes)); s != "" {
			return s, nil
		}
	} else if !os.IsNotExist(err) {
		return "", err
	}
	secret := RandomURLToken(24) // 32 base64url chars
	if err := os.WriteFile(path, []byte(secret), 0o600); err != nil {
		return "", err
	}
	return secret, nil
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
