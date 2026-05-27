// Command relay is the in-home MCP relay server (ARCHITECTURE §8/§9). It sits behind
// a Cloudflare Tunnel, accepts MCP-over-HTTPS at a secret path, and forwards each call
// to the paired phone over a WebSocket (waking it via FCM when disconnected).
//
// Send SIGHUP (`kill -HUP <pid>`) to mint a fresh pairing code without restarting.
package main

import (
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"

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

	// First run (no device paired) → mint a pairing code automatically.
	if _, err := store.GetDevice(); errors.Is(err, storage.ErrNotFound) {
		mintAndPrint(srv)
	} else {
		fmt.Fprintf(os.Stderr, "已配對裝置；需重新配對請送 SIGHUP（kill -HUP %d）取得新碼。\n", os.Getpid())
	}

	go func() {
		if err := http.ListenAndServe(addr, srv.Handler()); err != nil {
			log.Fatalf("server: %v", err)
		}
	}()

	// SIGHUP → mint a fresh pairing code on demand; INT/TERM → exit.
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGHUP, syscall.SIGINT, syscall.SIGTERM)
	for sig := range sigs {
		if sig != syscall.SIGHUP {
			fmt.Fprintln(os.Stderr, "shutting down")
			return
		}
		mintAndPrint(srv)
	}
}

func mintAndPrint(srv *relay.Server) {
	p, err := srv.Pairing().Create()
	if err != nil {
		fmt.Fprintf(os.Stderr, "無法產生配對碼: %v\n", err)
		return
	}
	relayURL := os.Getenv("RELAY_PUBLIC_URL")
	fmt.Fprintf(os.Stderr, "\n配對：App「配對 Relay」掃描以下 QR 內容（%v 內有效）：\n", pairing.TTL)
	fmt.Fprintf(os.Stderr, `{"relay_url":%q,"pairing_code":%q}`+"\n\n", relayURL, p.Code)
}
