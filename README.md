# Invoice Wallet

> Local-first Taiwan e-invoice wallet — your receipts stay on your phone, and a
> Pi-sized relay lets your own AI clients ask about them, under a key only you hold.
>
> 📖 [繁體中文 README](README.zh.md) · [Architecture](docs/ARCHITECTURE.md) · [Progress](docs/PROGRESS.md)

Taiwan issues **e-invoices** (電子發票) for almost every retail transaction, and
every two months runs a public lottery that pays out real money against the
invoice number. People accumulate hundreds per month across paper receipts,
"carrier" (載具) accounts, mobile wallets, and gas-station POSes. Existing apps
either centralise that data on someone else's server or barely categorise it.

This repo is a personal wallet that takes the opposite stance: **the receipts
never leave the phone**. A second piece — a tiny Go relay you run on a Raspberry
Pi or laptop — lets your own AI assistants (Claude Desktop, Claude Mobile,
others) ask "how much did I spend on coffee in March?" without anyone in the
middle ever holding your data.

## What it does today

- **Scan e-invoice QR codes** — both QRs from a single receipt, decoded
  on-device with zxing-cpp. Handles ROC and Gregorian date variants, CP950
  encoding, gas-station POSes that omit the 自定區 segment, and decimal
  quantities (`30.32 L × NT$33.9`).
- **Continuous scanning** with viewfinder reticle, a dynamic polygon that
  tracks the detected QR's corners in real time, and a notification-stream
  beep on successful read.
- **Monthly hero** showing total spend, invoice count, and pending-lottery
  count for the focused month — swipe ‹ › to walk back through history,
  clamped to the wallet's actual range.
- **Heuristic categorisation** — merchant-name regex maps invoices to eight
  watercolour-illustrated categories (food, drink, convstore, tech, medical,
  transit, clothing, home). Filter chips on the list, illustration badge in
  the detail hero.
- **Lottery checking** — every invoice carries its lottery status and the
  hero surfaces the pending count.
- **Vocabulary-by-Acumotion editorial design language** — serif display
  type, sage-teal accents, watercolour stamps, three-tab bottom nav
  (發票 / 對獎 / 設定). Custom adaptive launcher icon (wax seal).
- **Encrypted local storage** — Room over SQLCipher; no telemetry, no
  account, no cloud.

Not yet wired in: carrier (載具) CSV import, item-level export, FCM wake-up,
Android Content Provider for IPC, the in-app MCP server in the Ktor module
(stubs land, runtime endpoints come in iteration 6/7). See
[`docs/PROGRESS.md`](docs/PROGRESS.md) for the canonical progress board.

## Architecture in one diagram

```
┌───────────────┐   ┌──────────────────┐   ┌─────────────────┐
│ Claude Mobile │   │ Claude Desktop   │   │  Other Android  │
│   (cloud)     │   │   (your PC)      │   │     AI app      │
└──────┬────────┘   └────────┬─────────┘   └────────┬────────┘
       │ HTTPS               │ ADB reverse          │ Android IPC
       ▼                     │ (or LAN)             │
┌───────────────────┐        │                      │
│ Cloudflare Tunnel │        │                      │
│  relay.your.tld   │        │                      │
└────────┬──────────┘        │                      │
         ▼                   │                      │
┌────────────────────┐       │                      │
│  Go relay-server   │       │                      │
│  (your Pi/laptop)  │       │                      │
│  pure message      │       │                      │
│  forwarder, no DB  │       │                      │
└────────┬───────────┘       │                      │
         │ WSS / FCM wake    │                      │
         ▼                   ▼                      ▼
┌────────────────────────────────────────────────────────────┐
│  Android app  (on your phone)                              │
│  ┌──────────┐  ┌────────────┐  ┌──────────────────┐        │
│  │  Relay   │─▶│ MCP server │  │ Content Provider │        │
│  │  client  │  │   (Ktor)   │  │                  │        │
│  └──────────┘  └─────┬──────┘  └────────┬─────────┘        │
│                      ▼                  ▼                  │
│              ┌──────────────────────────────────┐          │
│              │   Room  (SQLCipher-encrypted)    │          │
│              └────────────────┬─────────────────┘          │
│                               ▲                            │
│              ┌────────────────┴───────────────────┐        │
│              │  QR scan · OCR · 載具 · manual     │        │
│              └────────────────────────────────────┘        │
└────────────────────────────────────────────────────────────┘
```

Three independent paths reach the data:

| Path | Client | Transport | Auth |
|---|---|---|---|
| A | Claude Mobile / Web | Cloudflare Tunnel → relay → phone over WSS | secret URL segment + paired `device_secret` |
| B | Claude Desktop | ADB reverse or same-LAN to in-app Ktor server | local token |
| C | Other Android AI | Content Provider IPC | custom Android permission |

The relay is a single Go binary (`gorilla/websocket` is the only non-stdlib
dep), state in one JSON file, no SQL. It can hold one phone's session at a
time — it's deliberately not multi-tenant.

## Repo layout

```
invoice-app/
├── android/                  Kotlin / Compose / Hilt / Room monorepo
│   ├── app/                  :app  — entry point + nav scaffold
│   ├── core/
│   │   ├── model/            Invoice domain model
│   │   ├── database/         Room + SQLCipher + repository
│   │   ├── design-system/    WalletTheme, components, illustrations, icons
│   │   └── testing/          shared MainDispatcher rules, fixtures, TestClock
│   ├── feature/
│   │   ├── scan/             CameraX + zxing-cpp + QR parser + viewfinder
│   │   ├── invoice-list/     hero stats, month switcher, category chips
│   │   ├── invoice-detail/   line items, lottery status, category badge
│   │   ├── lottery/          period draws, claim state
│   │   ├── pairing/          QR-code pairing with the relay
│   │   ├── settings/
│   │   └── export/
│   ├── data-source/
│   │   ├── authz/            device-side auth tokens
│   │   ├── mcp-server/       in-app Ktor MCP server (path B / C)
│   │   └── relay-client/     WSS client talking to relay-server
│   ├── build-logic/          Gradle convention plugins
│   └── gradle/libs.versions.toml   version catalog (single source of truth)
├── relay-server/             Go relay (path A)
│   ├── cmd/relay/            main binary
│   ├── internal/             session/pair/ws/mcp handlers
│   └── deploy/               systemd + cloudflared examples
├── spec/                     mcp-tools.md, pairing-protocol.md
├── docs/                     ARCHITECTURE.md, PROGRESS.md, QUESTIONS.md
└── temp-asset/               (gitignored) raw illustrations + process.py pipeline
```

## Build & run

### Android

```bash
cd android
./gradlew check                # ktlint + Android lint + unit tests
./gradlew testDebugUnitTest    # unit + Robolectric Compose tests
./gradlew assembleDebug        # debug APK at app/build/outputs/apk/debug/
./gradlew connectedDebugAndroidTest   # needs an attached device/emulator
```

Requirements: **JDK 21** (project compiles with a 21 toolchain, bytecode
targets 17), Android SDK with `platforms;android-35` and matching build-tools.
Put `sdk.dir=…` in `android/local.properties` (gitignored) or set
`ANDROID_HOME`.

Unit tests run on the `debug` variant only — Compose UI tests pull in
`ui-test-manifest`, which is debug-only. Robolectric handles Compose without
an emulator; `junit-vintage-engine` lets JUnit 4 Compose rules run on the
JUnit 5 platform.

### Relay

```bash
cd relay-server
go build -o invoice-relay ./cmd/relay
RELAY_DATA_DIR=/var/lib/invoice-relay ./invoice-relay
```

| Env | Default | Meaning |
|---|---|---|
| `RELAY_PORT` | `8080` | listen port (cloudflared sits in front) |
| `RELAY_DATA_DIR` | `.` | where `state.json` and `mcp_secret` live |
| `RELAY_PUBLIC_URL` | (empty) | printed inside the pairing-QR payload on first boot |

First boot prints the MCP endpoint and pairing-QR payload to stderr:

```
MCP endpoint: /mcp-<32-char-secret>/
Pairing: open "Pair Relay" in the app and scan this QR (valid 5m):
{"relay_url":"https://relay.yourdomain.tw","pairing_code":"A3F9-K2P7"}
```

For local dev without a domain, point a Cloudflare Quick Tunnel at it:

```bash
cloudflared tunnel --url http://localhost:8080
```

…and paste the resulting `https://<random>.trycloudflare.com/mcp-<secret>/`
into a Claude Custom Connector.

## Tech choices

| Concern | Pick |
|---|---|
| Language (app) | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVI + Clean (feature modules, no cross-feature deps) |
| DI | Hilt 2.52 |
| Persistence | Room 2.6.1 over SQLCipher 4.6.1 |
| Secrets | Jetpack Security `EncryptedSharedPreferences` |
| Camera | CameraX 1.4.1 |
| OCR | ML Kit Text Recognition v2 (Chinese) |
| QR | zxing-cpp 3.0.2 (preferred; ML Kit barcode as fallback) |
| HTTP client | Ktor 2.3.13 |
| HTTP server (in-app) | Ktor Server + Netty |
| Serialization | kotlinx.serialization |
| Concurrency | Coroutines + Flow |
| Push | Firebase Messaging (FCM) |
| Tests | JUnit 5 + MockK + Turbine + Kotest + Robolectric |
| Relay | Go 1.22+, `gorilla/websocket`, state-on-disk JSON |
| Tunnel | Cloudflare Tunnel (`cloudflared`) |

## Security & privacy stance

- **No telemetry**, ever. The app does not call home and does not bundle
  any analytics SDK.
- **No account, no sync.** v1 is single-user, single-device, by design.
- **Invoice rows are encrypted at rest** via SQLCipher; key lives in
  `EncryptedSharedPreferences` (hardware-backed where the OEM exposes it).
- **The relay holds no invoice content** — it routes opaque MCP frames
  between an authed phone WSS and an authed MCP-over-HTTPS client. Its
  state file is the pair record and a per-device session row.
- **Pairing is a one-shot** out-of-band QR exchange (5-minute TTL) that
  hands the phone a `device_secret`. Loss of the phone is the kill switch.
- **MCP secret path** sits at the URL — wrong path returns 404, no
  oracle. Combine with a strong tunnel hostname or, better, a Cloudflare
  Access policy in front of `/mcp-…/`.

Threat model and the bits explicitly deferred (multi-tenant, OAuth DCR,
device sync, telemetry) are written down in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## CI

`.github/workflows/android.yml` runs `./gradlew check assembleDebug` on PRs
and pushes to `main`. There's no relay workflow yet — `go test ./...` runs
locally.

## Status

Pre-release, single developer, used in production by one person (me). API
shape, schema, and module layout may move without a deprecation cycle until
v1.0. Issues and PRs welcome, but expect "I'll consider it after iteration 7"
on anything that pulls the project away from local-first.

## Licence

TBD — likely MIT or Apache-2.0. The Vocabulary-by-Acumotion editorial design
language is © Acumotion and used here under personal-use understanding; if
you fork to ship under your own brand, change the type system and palette.
