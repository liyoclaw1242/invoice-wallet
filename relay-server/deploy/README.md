# 部署：Cloudflare Tunnel + Raspberry Pi

依 ARCHITECTURE §9。單使用者、家用網路。

## 1. 系統與服務（Raspberry Pi OS Lite 64-bit）

```bash
# 安裝 cloudflared
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64.deb -o cloudflared.deb
sudo dpkg -i cloudflared.deb

# 登入 Cloudflare（需先把網域加進 Cloudflare）
cloudflared tunnel login
cloudflared tunnel create invoice-relay
cloudflared tunnel route dns invoice-relay relay.yourdomain.tw
```

## 2. relay binary

```bash
# 在開發機交叉編譯給 Pi（arm64）
GOOS=linux GOARCH=arm64 go build -o invoice-relay ./cmd/relay
scp invoice-relay pi:/usr/local/bin/

# Pi 上
sudo useradd -r -s /usr/sbin/nologin relay || true
sudo mkdir -p /var/lib/invoice-relay && sudo chown relay /var/lib/invoice-relay
```

## 3. systemd

```bash
sudo cp deploy/systemd/invoice-relay.service /etc/systemd/system/
sudo systemctl enable --now invoice-relay
sudo journalctl -u invoice-relay -f   # 看首啟印出的 MCP secret + 配對 QR 內容
```

cloudflared：把 `deploy/cloudflared/config.yml` 放到 `/etc/cloudflared/config.yml`
（填入 tunnel-id），再 `sudo cloudflared service install`。

## 4. 配對

1. `journalctl` 看到的 `{"relay_url":...,"pairing_code":...}` → App「配對 Relay」掃描。
2. App 換到 `device_secret`、連上 `/ws`。
3. Claude Custom Connector 填 `https://relay.yourdomain.tw/mcp-<secret>/`。

## macOS 常駐（launchd，當開發機/家機是 Mac 時）

不跑 Pi、直接讓這台 Mac 當常開 relay host：

```bash
# 1. 編譯 binary（留在 relay-server/ 或複製到穩定路徑）
go build -o invoice-relay ./cmd/relay

# 2. 放兩個 launch agent（範本在 deploy/launchd/，改成你的絕對路徑）
cp deploy/launchd/tw.invoicewallet.*.plist ~/Library/LaunchAgents/

# 3. 載入（開機自動啟動 + 當掉自動重啟 KeepAlive）
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/tw.invoicewallet.relay.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/tw.invoicewallet.tunnel.plist

# 卸載：launchctl bootout gui/$(id -u) ~/Library/LaunchAgents/tw.invoicewallet.relay.plist
# log：relay-server/.data/relay.log、tunnel.log
```

- relay agent 環境變數 `RELAY_DATA_DIR`（state/secret/api_token）、`RELAY_PUBLIC_URL`。
- tunnel agent 跑 `cloudflared tunnel run --url http://localhost:8080 <tunnel-name>`，需 `HOME` 指向使用者家目錄以讀 `~/.cloudflared`。
- 啟動橫幅（relay.log）會印出 `claude mcp add … --header "Authorization: Bearer <api_token>"` 現成指令。

## 安全備註

- `/mcp-<secret>/` 的完整網址即 connector 的密碼——勿外流。
- `device_secret` 認證手機 WS；換手機重新配對即輪替。
- relay 只存配對與 audit metadata（無發票內容）；發票資料永遠只在手機上。
- FCM 喚醒為選用：手機保持 foreground WS 連線時不需要。
