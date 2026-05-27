// Command relay is the in-home MCP relay server (ARCHITECTURE §8/§9). It sits behind
// a Cloudflare Tunnel, accepts MCP-over-HTTPS at a secret path, and forwards each call
// to the paired phone over a WebSocket (waking it via FCM when disconnected).
package main

import (
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/liyoclaw/invoice-relay/internal/config"
	"github.com/liyoclaw/invoice-relay/internal/pairing"
	"github.com/liyoclaw/invoice-relay/internal/relay"
	"github.com/liyoclaw/invoice-relay/internal/storage"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}
	store, err := storage.Open(cfg.StatePath)
	if err != nil {
		log.Fatalf("storage: %v", err)
	}

	srv := relay.New(cfg, store)
	addr := fmt.Sprintf(":%d", cfg.Port)

	fmt.Fprintf(os.Stderr, "invoice-relay listening on %s\n", addr)
	fmt.Fprintf(os.Stderr, "MCP endpoint: /mcp-%s/\n", cfg.MCPSecret)

	// First run (no device paired) → mint a pairing code and print the QR content.
	if _, err := store.GetDevice(); errors.Is(err, storage.ErrNotFound) {
		if p, perr := srv.Pairing().Create(); perr == nil {
			relayURL := os.Getenv("RELAY_PUBLIC_URL")
			fmt.Fprintf(os.Stderr,
				"\n配對：在 App 內選「配對 Relay」並掃描以下 QR 內容（%v 內有效）：\n", pairing.TTL)
			fmt.Fprintf(os.Stderr, `{"relay_url":%q,"pairing_code":%q}`+"\n\n", relayURL, p.Code)
		}
	}

	if err := http.ListenAndServe(addr, srv.Handler()); err != nil {
		log.Fatalf("server: %v", err)
	}
}
