# Invoice Wallet（電子發票錢包）

「資料屬於使用者」的台灣電子發票管理 App。手機 App 是主要介面與資料源，發票資料**僅儲存於手機本地**；可選的自架 Relay 讓 AI 客戶端在授權下查詢消費資料。

完整架構見 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。實作進度見 [`docs/PROGRESS.md`](docs/PROGRESS.md)。

## Repo 結構

```
invoice-app/                # = invoice-wallet monorepo
├── android/                # Android App（Kotlin / Compose / Hilt / Room）
│   ├── app/                # :app — 進入點
│   ├── core/               # :core:* — model / database / testing ...
│   ├── build-logic/        # Gradle convention plugins（集中設定）
│   └── gradle/libs.versions.toml   # 版本目錄（唯一版本來源）
├── relay-server/           # Go relay（之後 Phase 4）
├── spec/                   # API / schema 契約文件
└── docs/                   # 架構、進度、決策
```

## 開發環境需求

- JDK 21（專案以 JDK 21 toolchain 編譯，bytecode target 17）
- Android SDK（`platforms;android-35`、`build-tools;35.x`）。SDK 位置由 `android/local.properties` 的 `sdk.dir` 提供（gitignored），或 `ANDROID_HOME` 環境變數。
- 建議用 Android Studio 開啟 `android/` 目錄。

## 本地常用指令（在 `android/` 下）

```bash
# 完整檢查：Android lint + ktlint + 單元測試
./gradlew check

# 只跑單元測試（debug 變體；含 Robolectric Compose 測試）
./gradlew testDebugUnitTest

# 自動修正程式碼風格
./gradlew ktlintFormat

# 組 debug APK
./gradlew assembleDebug

# Instrumented 測試（需要連接的裝置或模擬器）
./gradlew connectedDebugAndroidTest
```

> 單元測試只跑 debug 變體（release unit test 已停用——Compose UI 測試依賴 debug-only 的 `ui-test-manifest`）。

## 測試策略

- **單元 / Robolectric**：JUnit 5（Jupiter）+ MockK + Turbine + Kotest，跑在 JVM。Compose UI 測試以 Robolectric 為主（免模擬器），透過 `junit-vintage-engine` 在 JUnit Platform 上跑 JUnit4-based 的 Compose rule。
- **Instrumented**：少量 smoke test 放 `src/androidTest`，在模擬器上跑。
- 共用測試工具在 `:core:testing`（`MainDispatcherExtension` / `MainDispatcherRule` / `HiltTestRunner` / `TestClock`）。

## CI

`.github/workflows/android.yml`：PR 與 push 到 `main` 時跑 `./gradlew check assembleDebug`。
