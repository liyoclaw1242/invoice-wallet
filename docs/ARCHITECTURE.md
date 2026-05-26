# Personal Invoice Wallet — Architecture & Implementation Spec

> **版本**：v2 (local-first, single-user)
> **適用對象**：個人或家庭自架使用，不對外提供服務

---

## 1. 專案願景

打造一款「資料屬於使用者」的台灣電子發票管理 App。手機 App 是主要使用介面與資料源，所有發票資料**僅儲存於手機本地**。同時架設一台**完全由使用者自己掌控**的小型 Relay Server（家用 Pi 或筆電），讓 Claude Mobile / Desktop 等 AI 客戶端可在使用者授權下查詢消費資料。

### Core Principles
1. **Local-first**：發票資料只在手機，Relay 只負責訊息中繼，不儲存任何發票內容
2. **You own everything**：使用者自己跑 Relay，連 metadata 都不離家
3. **Single user, single device**：v1 不做多使用者、不做多裝置同步
4. **Simple over flexible**：不做 OAuth provider、不做 multi-tenant
5. **Off-the-shelf infra**：Cloudflare Tunnel + 家用 Pi，零雲端帳單

### Non-goals (v1)
- 不對外開放給其他使用者
- 不做多裝置同步、不做帳號系統
- 不上 Anthropic Connectors Directory
- 不內建 AI 模型或對話功能
- 不蒐集任何遙測

---

## 2. 系統架構

```
┌───────────────┐   ┌──────────────────┐   ┌─────────────────┐
│ Claude Mobile │   │ Claude Desktop   │   │ 其他 Android    │
│ (Anthropic    │   │ (使用者 PC)      │   │ AI App          │
│  雲端)        │   │                  │   │                 │
└───────┬───────┘   └────────┬─────────┘   └────────┬────────┘
        │                    │                      │
        │ HTTPS              │ ADB reverse          │ Android IPC
        │                    │ (USB, 同網段也可)     │
        ▼                    │                      │
┌───────────────────┐        │                      │
│ Cloudflare Tunnel │        │                      │
│ relay.yourdomain  │        │                      │
└───────┬───────────┘        │                      │
        │                    │                      │
        ▼                    │                      │
┌───────────────────┐        │                      │
│ Relay Server      │        │                      │
│ (家用 Pi / 筆電)   │        │                      │
│ - 純訊息中繼       │        │                      │
│ - 不存發票資料     │        │                      │
└───────┬───────────┘        │                      │
        │ FCM wake / WSS     │                      │
        │                    │                      │
        ▼                    ▼                      ▼
┌──────────────────────────────────────────────────────┐
│ Android App (手機本機)                               │
│  ┌─────────┐  ┌──────────┐  ┌──────────────┐         │
│  │Relay    │─▶│MCP Server│  │Content       │         │
│  │Client   │  │ (Ktor)   │  │Provider      │         │
│  └─────────┘  └────┬─────┘  └──────┬───────┘         │
│                    ▼                ▼                │
│              ┌──────────────────────────┐            │
│              │ Room DB (SQLCipher 加密) │            │
│              └────────────┬─────────────┘            │
│                           ▲                          │
│              ┌────────────┴───────────┐              │
│              │ OCR / QR / 載具 / 手動 │              │
│              └────────────────────────┘              │
└──────────────────────────────────────────────────────┘
```

### 三條 AI 取用路徑（與 v1 相同，但實作大幅簡化）

| 路徑 | 客戶端 | 中介 | 進入點 | 認證 |
|---|---|---|---|---|
| A | Claude Mobile/Web | Cloudflare Tunnel → Relay | Relay → 手機 | Secret URL 路徑 |
| B | Claude Desktop | ADB reverse 或同網段 LAN | App 內 MCP Server | 本機 token |
| C | Other Android AI | Android IPC | Content Provider | Android custom permission |

### 與 v1 的關鍵差異

| 項目 | v1 (multi-tenant) | v2 (local-first 單使用者) |
|---|---|---|
| Relay 部署 | Cloud Run / VPS | 家用 Raspberry Pi 或筆電 |
| HTTPS | 自架反向代理或雲端 LB | Cloudflare Tunnel |
| 認證 | OAuth 2.1 + DCR | 隨機 secret URL 路徑 |
| 多使用者支援 | 是 | 否 (only you) |
| 帳號系統 | 是 | 無 |
| Relay 資料庫 | PostgreSQL + Redis | SQLite 一個檔案 |
| 配對方式 | OAuth 登入頁 | QR Code 一次性配對 |
| 月成本 | $5~$20 雲端費 | ≈ 0 (家裡電費) |

---

## 3. 技術選型

### Android App（與 v1 相同）

| 領域 | 選用 |
|---|---|
| 語言 | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| 架構 | MVI + Clean Architecture |
| DI | Hilt |
| 本機 DB | Room + SQLCipher |
| 加密儲存 | Jetpack Security (EncryptedSharedPreferences) |
| 相機 | CameraX |
| OCR | ML Kit Text Recognition v2 (Chinese) |
| QR Code | ML Kit Barcode Scanning |
| HTTP Client | Ktor Client |
| HTTP Server (in-app) | Ktor Server + Netty |
| 序列化 | kotlinx.serialization |
| Async | Coroutines + Flow |
| FCM | Firebase Messaging |

### Relay Server（大幅簡化）

| 領域 | 選用 | 備註 |
|---|---|---|
| 語言 | Go 1.22+ | 一個 binary，部署簡單 |
| HTTP framework | net/http + chi router | 不需要重武器 |
| WebSocket | nhooyr/websocket | |
| Push | Firebase Admin SDK (Go) | 喚醒手機 |
| 儲存 | SQLite (modernc.org/sqlite) | 配對資訊、log，無發票 |
| HTTPS 暴露 | Cloudflare Tunnel (cloudflared) | 免費、零設定 |
| 部署 | systemd service on Pi/Linux | |

---

## 4. Repo 結構

```
invoice-wallet/
├── android/                          # Android App (與 v1 大致相同)
│   ├── app/
│   ├── core/
│   │   ├── database/                 # Room DB, entities, DAOs
│   │   ├── crypto/                   # SQLCipher, key management
│   │   ├── model/                    # Domain models
│   │   └── network/                  # HTTP client, FCM
│   ├── feature/
│   │   ├── scan/                     # CameraX + OCR + QR
│   │   ├── invoice-list/
│   │   ├── invoice-detail/
│   │   ├── lottery/                  # 對獎
│   │   ├── carrier/                  # 財政部載具 API
│   │   ├── pairing/                  # 與 Relay 配對
│   │   └── settings/
│   ├── data-source/
│   │   ├── mcp-server/               # In-app Ktor MCP server (Claude Desktop 用)
│   │   ├── content-provider/
│   │   ├── relay-client/             # WebSocket / FCM 連到家用 Relay
│   │   └── authz/                    # 簡化的授權引擎
│   └── build-logic/
│
├── relay-server/                     # Go relay (跑在 Pi 或筆電)
│   ├── cmd/relay/
│   │   └── main.go
│   ├── internal/
│   │   ├── mcp/                      # MCP protocol handler
│   │   ├── pairing/                  # QR Code 配對流程
│   │   ├── session/                  # WebSocket session 管理
│   │   ├── transport/                # FCM wake + WS
│   │   └── storage/                  # SQLite (只存配對與 audit log)
│   ├── deploy/
│   │   ├── systemd/                  # invoice-relay.service
│   │   ├── cloudflared/              # tunnel config 範本
│   │   └── README.md                 # Pi 部署教學
│   └── go.mod
│
├── spec/
│   ├── mcp-tools.md
│   ├── content-provider.md
│   ├── data-schema.md
│   └── pairing-protocol.md           # QR Code 配對協議
│
├── docs/
│   ├── ARCHITECTURE.md               # 本文件
│   ├── DEPLOYMENT.md                 # Pi / 筆電 / Cloudflare Tunnel 步驟
│   ├── SECURITY.md
│   └── ROADMAP.md
│
└── README.md
```

---

## 5. 資料模型

### 5.1 Android App — Room Entities

#### Invoice (主表)

```kotlin
@Entity(tableName = "invoices",
    indices = [
        Index(value = ["invoice_number"], unique = true),
        Index(value = ["issue_date"]),
        Index(value = ["merchant_tax_id"])
    ])
data class Invoice(
    @PrimaryKey val id: String,                                   // UUID v7
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String, // AB12345678
    @ColumnInfo(name = "issue_date") val issueDate: LocalDate,
    @ColumnInfo(name = "issue_period") val issuePeriod: String,     // "11302"
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "merchant_tax_id") val merchantTaxId: String?,
    @ColumnInfo(name = "buyer_tax_id") val buyerTaxId: String?,
    @ColumnInfo(name = "carrier_id_encrypted") val carrierIdEncrypted: String?,
    @ColumnInfo(name = "total_amount") val totalAmount: Int,        // 含稅總額 (元)
    @ColumnInfo(name = "tax_amount") val taxAmount: Int,
    @ColumnInfo(name = "currency") val currency: String = "TWD",
    @ColumnInfo(name = "random_code") val randomCode: String,       // 對獎需要
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "source") val source: InvoiceSource,
    @ColumnInfo(name = "ocr_confidence") val ocrConfidence: Float?,
    @ColumnInfo(name = "lottery_status") val lotteryStatus: LotteryStatus,
    @ColumnInfo(name = "lottery_prize") val lotteryPrize: Int?,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "user_note") val userNote: String?,
    @ColumnInfo(name = "user_tags") val userTags: List<String>,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?
)

enum class InvoiceSource { OCR, QR_CODE, CARRIER_API, MANUAL }
enum class LotteryStatus { PENDING, CHECKED_NO_PRIZE, CHECKED_WON, CLAIMED }
```

#### InvoiceItem

```kotlin
@Entity(tableName = "invoice_items", ...)
data class InvoiceItem(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val name: String,
    val quantity: Double,
    val unitPrice: Int,
    val amount: Int,
    val category: String?,
    val sequence: Int
)
```

#### LotteryNumber (中獎號碼快取)

```kotlin
@Entity(tableName = "lottery_numbers", primaryKeys = ["period"])
data class LotteryNumber(
    val period: String,
    val specialPrize: String,
    val grandPrize: String,
    val firstPrize: List<String>,
    val additionalSixth: List<String>,
    val fetchedAt: Instant
)
```

#### AuthGrant (對外 AI 客戶端的授權記錄)

```kotlin
@Entity(tableName = "auth_grants")
data class AuthGrant(
    @PrimaryKey val id: String,
    val clientName: String,           // "Claude Mobile" / "Gemini" / Desktop app package
    val channel: AuthChannel,         // RELAY / LOCAL_MCP / CONTENT_PROVIDER
    val scopes: List<String>,
    val excludedCategories: List<String>?,
    val dateRangeDays: Int?,
    val grantedAt: Instant,
    val expiresAt: Instant?,
    val revokedAt: Instant?
)

enum class AuthChannel { RELAY, LOCAL_MCP, CONTENT_PROVIDER }
```

#### QueryAuditLog

```kotlin
@Entity(tableName = "query_audit_log")
data class QueryAuditLog(
    @PrimaryKey val id: String,
    val grantId: String,
    val toolName: String,
    val resultCount: Int,
    val executedAt: Instant
)
```

### 5.2 Relay Server — SQLite Schema（極簡）

```sql
-- 唯一的一台手機（單使用者）
CREATE TABLE device (
    id INTEGER PRIMARY KEY,           -- 永遠是 1
    device_name TEXT,
    fcm_token TEXT,
    public_key TEXT,                  -- E2E 加密用（Phase 5）
    paired_at INTEGER NOT NULL,
    last_seen INTEGER
);

-- 配對用的一次性 code
CREATE TABLE pairing_request (
    code TEXT PRIMARY KEY,            -- 8 碼隨機
    expires_at INTEGER NOT NULL,
    consumed_at INTEGER
);

-- Audit log (僅 metadata，無 payload)
CREATE TABLE relay_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    occurred_at INTEGER NOT NULL,
    event_type TEXT,                  -- WAKE / FORWARD / ERROR
    tool_name TEXT,
    success INTEGER,
    error_message TEXT
);
```

整個 Relay DB 就這三張表。沒有 user、沒有 OAuth、沒有 session（session 在記憶體裡）。

---

## 6. MCP Tool 定義

對外暴露的 MCP tools（與 v1 相同，介面穩定）：

- `list_invoices(date_from, date_to, merchant_name, category, min_amount, max_amount, limit, offset)`
- `get_invoice_detail(invoice_id)`
- `search_invoices(keyword, limit)`
- `get_spending_summary(date_from, date_to, group_by: category|merchant|month|weekday)`
- `get_lottery_status(period?)`
- `list_merchants(min_visits?, date_from?, limit?)`

完整 JSON Schema 見 `spec/mcp-tools.md`（獨立文件）。

---

## 7. 配對協議 (Pairing)

單使用者版本不用 OAuth，改用 QR Code 一次性配對。流程：

1. 使用者在 Pi 上啟動 Relay 服務（首次啟動）
2. Relay CLI 顯示：
   ```
   訪問 https://relay.yourdomain.tw/pair 完成配對
   或在手機 App 內選「配對 Relay」掃描以下 QR Code:
   [QR Code]
   QR 內容: {"relay_url":"https://relay.yourdomain.tw","pairing_code":"A3F9-K2P7"}
   ```
3. 使用者打開手機 App → 設定 → 配對 Relay → 掃 QR
4. App 發 POST 到 `https://relay.yourdomain.tw/pair/claim` 帶 pairing_code + FCM token + device name
5. Relay 驗 code，寫入 device 表，回傳一個 device_secret 給 App
6. App 儲存 device_secret 到 EncryptedSharedPreferences
7. 之後 App 連 Relay 的 WebSocket 都帶這個 secret

pairing_code 5 分鐘後過期，用過即焚。

### Secret URL 設計

Relay 的 MCP 端點不放在 `/mcp`，而是放在一個 secret 路徑：

```
https://relay.yourdomain.tw/mcp-<32-char-random>/
```

這 32 碼 random suffix 由 Relay 首次啟動時產生，寫入設定檔。它就是這個 connector 的「password」——任何人拿到完整 URL 就能呼叫，所以**不要分享**。

把這個 URL 拿去 Claude Custom Connector 設定中貼上，免 OAuth。

---

## 8. 通訊協議

### 8.1 Claude → Relay (MCP over HTTPS)

```
POST https://relay.yourdomain.tw/mcp-<secret>/
Content-Type: application/json

{"jsonrpc":"2.0","method":"tools/call","params":{"name":"list_invoices","arguments":{...}},"id":1}
```

Relay 處理流程：
1. 驗證 URL 路徑中的 secret（不對就 404）
2. 解析 MCP 請求
3. 查 `device` 表拿 FCM token
4. 若 WebSocket session 已開啟 → 直接轉發
5. 若無 → 發 FCM data message 喚醒手機
6. 等手機連 WebSocket 過來，轉發請求
7. 收到手機回應 → HTTPS response 給 Claude

### 8.2 手機 ↔ Relay (WebSocket)

App 開「允許 AI 遠端查詢」switch 時：
- 註冊 FCM token 到 Relay (`POST /device/refresh`)
- 進入 foreground 時主動連 WebSocket
- background 時靠 FCM 喚醒

WebSocket 訊息格式：

```json
// Relay → Phone
{
  "type": "mcp_request",
  "request_id": "uuid",
  "tool_name": "list_invoices",
  "arguments": {...}
}

// Phone → Relay
{
  "type": "mcp_response",
  "request_id": "uuid",
  "status": "ok",
  "result": {...}
}
```

WebSocket 認證：連線時 header 帶 `Authorization: Bearer <device_secret>`。

### 8.3 Claude Desktop ↔ Phone (ADB / LAN)

```bash
# ADB 方式 (USB 連線)
adb reverse tcp:7777 tcp:7777

# 或同網段 LAN (App 顯示手機 IP)
# Claude Desktop 設 http://<your-phone-ip>:7777/mcp
```

App 內 Ktor server 監聽 `0.0.0.0:7777`（LAN）或 `127.0.0.1:7777`（ADB），僅接受帶有正確 token 的請求。Token 在 App 設定畫面顯示，使用者複製貼到 Claude Desktop 的 connector 設定。

### 8.4 Other Android AI App ↔ Content Provider

```
Authority: tw.example.invoicewallet.provider

URIs:
  content://tw.example.invoicewallet.provider/invoices
  content://tw.example.invoicewallet.provider/invoices/#
  content://tw.example.invoicewallet.provider/items
  content://tw.example.invoicewallet.provider/summary

Custom permission:
  tw.example.invoicewallet.permission.READ_INVOICES
```

使用者在 App 內 whitelist 哪些 package 可以呼叫。

---

## 9. 部署：Cloudflare Tunnel + Raspberry Pi

### 9.1 硬體建議

- Raspberry Pi 4 / 5（推薦 5，4 也夠用）
- 32GB+ SD card 或 USB SSD（建議 SSD，SD 容易壞）
- 穩定電源、家用網路
- 可選：小型 UPS（防停電）

### 9.2 系統與服務

```bash
# Raspberry Pi OS Lite (64-bit)
# 裝 Go (或預編好的 relay binary)
# 裝 cloudflared
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64.deb -o cloudflared.deb
sudo dpkg -i cloudflared.deb

# 登入 Cloudflare 帳號（要事先把網域加到 Cloudflare）
cloudflared tunnel login

# 建立 tunnel
cloudflared tunnel create invoice-relay

# DNS routing
cloudflared tunnel route dns invoice-relay relay.yourdomain.tw
```

### 9.3 cloudflared 設定檔

`/etc/cloudflared/config.yml`:
```yaml
tunnel: <tunnel-id>
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: relay.yourdomain.tw
    service: http://localhost:8080
  - service: http_status:404
```

### 9.4 systemd 服務

`/etc/systemd/system/invoice-relay.service`:
```ini
[Unit]
Description=Invoice Wallet Relay Server
After=network.target

[Service]
Type=simple
User=relay
ExecStart=/usr/local/bin/invoice-relay
Restart=on-failure
RestartSec=5
WorkingDirectory=/var/lib/invoice-relay
Environment="CONFIG_PATH=/etc/invoice-relay/config.toml"

[Install]
WantedBy=multi-user.target
```

`/etc/systemd/system/cloudflared.service`：用 `cloudflared service install` 自動安裝。

### 9.5 開發階段替代方案

不想立刻買 Pi 的話：

```bash
# 開發機跑 relay
go run ./relay-server

# 另一個 terminal
cloudflared tunnel --url http://localhost:8080
# 會印出 https://random-name.trycloudflare.com
```

這是 Quick Tunnel，免設定但網址會變。適合 demo，正式用要綁網域。

---

## 10. 開發階段 (Roadmap)

### Phase 0 — Spike (1 週)
- [ ] ML Kit 對台灣發票（20 張樣本）辨識率測試
- [ ] 電子發票 QR Code 左右碼解析（Base32 左 + Big5 右）
- [ ] 財政部開放資料 API 可用性確認
- [ ] Ktor in-app server + ADB reverse 連通測試

### Phase 1 — Core App (4 週)
- [ ] Room schema + DAO + Repository
- [ ] CameraX + OCR + QR 掃描 → 欄位確認 → 寫入 DB
- [ ] 發票清單、搜尋、篩選
- [ ] 對獎功能
- [ ] 匯出 CSV / JSON

### Phase 2 — Local AI 介接 (3 週)
- [ ] In-app Ktor MCP server，實作 6 個 MCP tools
- [ ] Content Provider 實作 + custom permission
- [ ] 簡化授權引擎 + AuthGrant 管理 UI
- [ ] **Claude Desktop ADB demo 拍片** ← 重要里程碑

### Phase 3 — 載具與分類 (3 週)
- [ ] 財政部電子發票整合服務 API
- [ ] 品項自動分類（規則 + 對照表）
- [ ] 進階統計

### Phase 4 — Relay Server (3 週)
- [ ] Go relay server: chi + WebSocket + FCM
- [ ] SQLite 配對與 audit log
- [ ] QR Code 配對協議實作（手機端 + Relay 端）
- [ ] Secret URL 路徑 + MCP forwarding
- [ ] Pi 部署文件 + systemd / cloudflared 範本

### Phase 5 — Claude Mobile 串接 (1 週)
- [ ] App 內「允許 AI 遠端查詢」總開關 UI
- [ ] FCM 整合 + 喚醒流程
- [ ] App 內 Relay 設定畫面
- [ ] claude.ai 加 custom connector 跑通 end-to-end

### Phase 6 — 可選強化
- [ ] X25519 E2E 加密 payload（Relay 看不到內容）
- [ ] 開放給朋友家人使用：加上極簡 OAuth
- [ ] 上 Connectors Directory（要過審）

---

## 11. 安全模型

### 威脅與防護

| 威脅 | 防護 |
|---|---|
| 手機遺失 | SQLCipher 加密 DB，金鑰於 Android Keystore（硬體支援） |
| Secret URL 外洩 | 提供 App 內「重新產生 URL」功能；Cloudflare WAF 規則限速 |
| Pi 被入侵 | Relay 不存發票，最壞情況外洩 metadata（誰何時查了什麼 tool） |
| 惡意 App 偷讀 | Custom permission + 使用者白名單 package |
| Claude prompt injection 大量匯出 | AuthGrant scope 限制 + 每分鐘 rate limit + audit log |
| 載具碼外洩 | 額外用 Keystore key 加密該欄位 |

### Secret URL 的注意事項

- 路徑 suffix 至少 24 字元 base64url
- HTTPS 加密整個 path，傳輸中安全
- 不要把完整 URL 寫進 git、screenshot、聊天室
- App 設定提供「重新產生」按鈕，原 URL 立即失效
- Cloudflare Access 可以加額外一層（如 email magic link），對個人使用通常過度

### 隱私承諾

- App 不蒐集遙測（無 GA、Firebase Analytics、Sentry）
- Relay 不存發票內容，只存配對資訊與最小 audit metadata
- Crash report 預設關閉
- Google Play Data Safety：勾「不蒐集」

---

## 12. 給 Claude Code 的開發指引

### 開工順序

1. 建立 monorepo 骨架（依第 4 節）
2. **Android app**：Kotlin + Compose + Hilt + Room + SQLCipher 樣板
3. 實作 Room entities (第 5.1 節) + DAOs + Repository 介面
4. Mock OCR → 寫入 DB → 列表查詢的 end-to-end pipeline
5. 接 ML Kit 真 OCR + QR 解析
6. 對獎功能
7. **In-app MCP server**（Ktor）+ 6 個 tools 實作
8. Content Provider + custom permission
9. AuthGrant UI（在 App 設定中）
10. **Relay server**（Go）：HTTP + WS + FCM + SQLite
11. QR Code 配對流程（兩端）
12. Pi 部署腳本 + Cloudflare Tunnel 設定文件
13. App 內「允許 AI 遠端」總開關 + Relay 客戶端
14. End-to-end 測試：claude.ai 加 custom connector → Claude Mobile 提問 → 看見結果

### 風格

- 文件、UI 文案、commit message：繁體中文
- 程式碼註解：英文
- 變數命名：英文
- Material 3 expressive 設計風格
- 主色 `#1F6FEB`，中性灰階為輔
- Onboarding 強調「資料只屬於你」訊息

### 測試

- Android：JUnit5 + Turbine + MockK；UI 用 Compose UI test
- Relay：Go 標準 testing + testify
- 每個 Phase 結尾跑一次 manual end-to-end smoke test

### 進度追蹤

每個 session 結尾寫 `docs/PROGRESS.md`，下次 session 開頭先讀。

—— END OF SPEC ——
