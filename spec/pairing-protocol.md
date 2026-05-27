# Pairing Protocol (QR, 一次性)

單使用者版本不用 OAuth（ARCHITECTURE §7）。

## 流程

1. Relay 首次啟動產生 `pairing_code`（8 碼，分組 `XXXX-XXXX`，5 分鐘過期、用過即焚），
   並印出 QR 內容：
   ```json
   {"relay_url":"https://relay.yourdomain.tw","pairing_code":"A3F9-K2P7"}
   ```
2. App →「配對 Relay」掃 QR。
3. App `POST {relay_url}/pair/claim`：
   ```json
   {"pairing_code":"A3F9-K2P7","fcm_token":"<fcm>","device_name":"Mi MIX 2"}
   ```
4. Relay 驗 code（存在 / 未過期 / 未用過），consume，產生 `device_secret`（32 碼），
   寫入 device 表，回：
   ```json
   {"device_secret":"<32-char>"}
   ```
5. App 將 `device_secret` 存入 EncryptedSharedPreferences。
6. 之後連 `GET {relay_url}/ws` 帶 `Authorization: Bearer <device_secret>`。

## 錯誤回應（/pair/claim）

| 狀況 | HTTP |
|---|---|
| 未知 code | 404 |
| 已過期 | 410 |
| 已使用 | 409 |
| body 非 JSON | 400 |

## FCM token 更新

`POST {relay_url}/device/refresh`，header `Authorization: Bearer <device_secret>`，
body `{"fcm_token":"<new>"}`。secret 不符 → 401。

## WebSocket 訊息（§8.2，本實作以原始 JSON-RPC payload 隧道）

```json
// Relay → Phone
{"type":"mcp_request","request_id":"<id>","payload":<raw JSON-RPC request>}
// Phone → Relay
{"type":"mcp_response","request_id":"<id>","status":"ok","payload":<raw JSON-RPC response>}
```
