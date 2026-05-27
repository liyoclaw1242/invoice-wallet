# invoice-relay

家用 MCP relay server（ARCHITECTURE §8/§9）。坐在 Cloudflare Tunnel 後面，於 secret 路徑
接受 MCP-over-HTTPS，將每個呼叫透過 WebSocket 轉發給已配對的手機（手機離線時以 FCM 喚醒）。

純 Go，唯一外部相依 `gorilla/websocket`；狀態存在 JSON 檔（單使用者三張小表）。

## 建置與執行

```bash
cd relay-server
go build -o invoice-relay ./cmd/relay
RELAY_DATA_DIR=/var/lib/invoice-relay ./invoice-relay
```

環境變數：

| 變數 | 預設 | 說明 |
|---|---|---|
| `RELAY_PORT` | `8080` | 監聽埠（cloudflared 在前面） |
| `RELAY_DATA_DIR` | `.` | 狀態檔（`state.json`）與 MCP secret（`mcp_secret`）目錄 |
| `RELAY_PUBLIC_URL` | （空） | 對外網址，僅用於首啟印出的配對 QR 內容 |

首次啟動（尚未配對）會在 stderr 印出 MCP 端點與配對 QR 內容：

```
MCP endpoint: /mcp-<32碼secret>/
配對：在 App 內選「配對 Relay」並掃描以下 QR 內容（5m0s 內有效）：
{"relay_url":"https://relay.yourdomain.tw","pairing_code":"A3F9-K2P7"}
```

## HTTP 介面

| 方法 | 路徑 | 用途 |
|---|---|---|
| GET | `/health` | 健康檢查 |
| POST | `/pair/claim` | 手機認領配對碼，換取 `device_secret` |
| POST | `/device/refresh` | 更新 FCM token（Bearer `device_secret`） |
| GET | `/ws` | 手機 WebSocket（Bearer `device_secret`） |
| POST | `/mcp-<secret>/` | Claude 的 MCP-over-HTTPS 入口（secret 錯→404） |

`device_secret` 是手機 WS 的認證；`/mcp-<secret>/` 的 secret 是 connector 的「password」。

## 開發階段（免 Pi、免網域）

```bash
# 終端機 1：跑 relay
go run ./cmd/relay

# 終端機 2：Cloudflare Quick Tunnel（免設定，網址會變）
cloudflared tunnel --url http://localhost:8080
# → https://random-name.trycloudflare.com
```

把 `https://random-name.trycloudflare.com/mcp-<secret>/` 貼到 Claude Custom Connector。
正式部署見 `deploy/`。

## 測試

```bash
go test ./...
go test -race ./internal/session/ ./internal/relay/
```
