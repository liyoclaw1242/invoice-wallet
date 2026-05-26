# QUESTIONS — 待使用者答覆 / 待處理事項

> 依計畫 §0 原則：遇到不確定不要猜，寫在這裡等答覆。

## 開放中

### Q1 — ARCHITECTURE.md 何時提供？（BLOCKING for Iteration 1）
- 計畫把 `docs/ARCHITECTURE.md` 當權威來源，但目前 repo 內不存在。
- 使用者表示「會自行提供」。
- **需要**：把檔案放到 `docs/ARCHITECTURE.md` 或貼上內容。至少需含：
  - §5.1 Domain entities（Invoice / InvoiceItem / LotteryNumber / LotteryStatus / InvoiceSource / AuthGrant / AuthChannel / QueryAuditLog 的欄位與型別）
  - §6 MCP tools 規格（6 個 tool 的輸入/輸出 schema）
  - §8.4 Content Provider URI 與 column schema
- 狀態：⏳ 等待

### Q2 — Android SDK 設定完成了嗎？（BLOCKING for T0.1 驗收）
- 使用者表示「自行設定工具」→ 改由 Claude 於 2026-05-26 透過 `brew install --cask android-commandlinetools` + `sdkmanager` 安裝。
- T0.1 與所有後續 iteration 的驗收都需要可用的 Android SDK。
- 安裝結果（全部 ✅，已 in-session 驗證）：
  - `ANDROID_HOME` = `/opt/homebrew/share/android-commandlinetools`
  - `platform-tools` 37.0.0（`adb --version` = 1.0.41 ✅）
  - `platforms;android-35` ✅
  - `build-tools;35.0.1`（最新 35.x）✅
  - `cmdline-tools;latest`（20.0）✅
  - `emulator` 36.5.11 + `system-images;android-35;google_apis;arm64-v8a` ✅
  - AVD：`invoice_pixel7_api35`（pixel_7 / API 35 / arm64-v8a）✅
  - `sdkmanager --licenses` 全部 accept ✅（7 個 license 檔）
- ⚠️ **唯一殘留**：把 `ANDROID_HOME` + PATH export 寫進 `~/.zshrc` 被 harness 擋下（unauthorized persistence）。
  目前環境變數只在 export 過的 shell 內有效；新開的 zsh 讀不到。
  解法二選一：(a) 使用者自行把下列區塊加進 `~/.zshrc`；(b) T0.1 時於 `android/local.properties` 寫 `sdk.dir`（Gradle 專用，gitignored）。
  ```sh
  export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
  ```
- 狀態：✅ SDK 安裝完成並驗證（2026-05-26）；⚠️ 僅差 shell 持久化（見上，待使用者處理）

### Q3 — monorepo 結構衝突（BLOCKING for T0.1）
- 使用者上一輪選「Android monorepo 放 repo 根目錄」。
- 但 `ARCHITECTURE.md` §4 顯示 repo（`invoice-wallet/`）應含 `android/` + `relay-server/` + `spec/` + `docs/` + `README.md`，Android 是 **`android/` 子目錄**。
- `IMPLEMENTATION_PLAN.md` T0.1 也明寫「建立 `android/` 目錄」。
- 兩份來源文件皆與「repo 根」選擇矛盾。建議改用 `android/` 子目錄。
- 狀態：✅ RESOLVED 2026-05-26 — 使用者同意推翻原選擇，改用 `android/` 子目錄

## 已解決

### Q1 — ARCHITECTURE.md（RESOLVED 2026-05-26）
- 使用者提供完整 spec，已寫入 `docs/ARCHITECTURE.md`。Iteration 1 的 entity/schema 來源到位。
