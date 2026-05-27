# PROGRESS — Invoice Wallet Android App

> Orchestrator 進度紀錄。每個 iteration 結束更新。

## Baseline（建立時狀態）

- 建立日期：2026-05-26
- Repo：`~/Projects/invoice-app`，全新 git repo，main 分支，**尚無任何 commit**
- 原始骨架：`gradle init` 純 Java 單模組（`src/main/java/org/example/Main.java` + `id("java")`）—— **將於 T0.1 整個替換為 Android monorepo**
- Gradle wrapper：8.14
- 本機工具鏈：JDK 21 (openjdk@21)、Kotlin 2.3.21、Gradle 9.5.1（CLI）、IntelliJ IDEA CE

## 決策紀錄

| 決策 | 選擇 | 日期 |
|------|------|------|
| `docs/ARCHITECTURE.md` 來源 | 使用者提供，已寫入 `docs/ARCHITECTURE.md` | 2026-05-26 |
| Android 工具鏈安裝 | 使用者自行設定（SDK/模擬器/ANDROID_HOME） | 2026-05-26 |
| monorepo 位置 | **`android/` 子目錄**（與 relay-server/、spec/、docs/ 並列，依 ARCHITECTURE §4）；清掉根目錄 Java 範本 | 2026-05-26（推翻原「repo 根」選擇） |
| Compose UI 測試策略 | **Robolectric 為主（JVM/CI）+ 少量 instrumented smoke（androidTest/emulator）** | 2026-05-27 |

## 目標 repo 結構（ARCHITECTURE §4）

```
invoice-app/                 # = invoice-wallet
├── android/                 # Android Gradle monorepo（本計畫 Iter 0–7）
├── relay-server/            # Go relay（之後 Phase 4，另有文件）
├── spec/                    # mcp-tools.md / content-provider.md / data-schema.md ...
├── docs/                    # ARCHITECTURE.md / PROGRESS.md / QUESTIONS.md（已建）
└── README.md
```

## 前置依賴（BLOCKING — 開工前必須備齊）

- [x] **P1 — `docs/ARCHITECTURE.md`**：✅ 已到位（使用者提供完整 v2 spec）。
- [x] **P2 — Android SDK 可用**：✅ 已到位（2026-05-26，Claude 安裝並驗證）。`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`、`adb` 37.0.0 / `sdkmanager` 20.0 / `emulator` 36.5.11 可呼叫、`platforms;android-35` + `build-tools;35.0.1` + `system-images;android-35;google_apis;arm64-v8a` 已裝、AVD `invoice_pixel7_api35` 已建、licenses 全 accept。⚠️ 殘留：`~/.zshrc` 持久化被 harness 擋下，新 shell 需手動 export 或靠 `android/local.properties` 的 `sdk.dir`（詳見 QUESTIONS Q2）。

## Iteration 狀態

| Iteration | 狀態 | 備註 |
|---|---|---|
| 0 Foundation | 🟢 基本完成 | T0.1–T0.5 ✅；待補：T0.3b emulator smoke、CI 需 GitHub remote 才能實跑 |
| 1 Core Data | 🟢 前置已備齊（P1+P2）| 待 Iteration 0 完成 |
| 2 Scan | ⛔ | |
| 3 List/Search | ⛔ | |
| 4 Lottery | ⛔ | |
| 5 Export | ⛔ | |
| 6 MCP Server | ⛔ | |
| 7 Content Provider | ⛔ | |

## 變更日誌

- 2026-05-26：建立 PROGRESS.md / QUESTIONS.md，記錄 baseline 與三項決策。前置 P1、P2 待解。
- 2026-05-26：安裝並驗證 Android SDK（P2 ✅）。brew cask `android-commandlinetools` + sdkmanager 裝 platform-tools 37.0.0 / android-35 / build-tools 35.0.1 / cmdline-tools latest / emulator 36.5.11 / google_apis arm64-v8a image；建 AVD `invoice_pixel7_api35`；licenses 全 accept。`~/.zshrc` 持久化被 harness 擋下，待使用者處理。Iteration 0 前置就緒。
- 2026-05-26：**T0.1 完成 ✅**。重構為 `android/` 子目錄 monorepo（清掉根 Java 範本）。版本組合 AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28（保守已知良好）。建立 `android/`：`settings.gradle.kts`（pluginManagement + FAIL_ON_PROJECT_REPOS + type-safe accessors）、`gradle/libs.versions.toml`（完整版本目錄）、`gradle.properties`（config cache + build cache + parallel）、`build.gradle.kts`（plugins apply false）、`local.properties`（sdk.dir，gitignored）。`.gitignore` 補 Android + 整個 `.idea/`。驗收：`./gradlew tasks` BUILD SUCCESSFUL、config cache 已存。Commit `bd29f75`。
- 2026-05-27：**T0.5 完成 ✅（CI 待 remote 實跑）**。ktlint convention plugin（`app.convention.ktlint`，jlleitschuh ktlint-gradle 12.1.1 + ktlint engine 1.3.1，`android=true`），套用於 :app + :core:testing。`.editorconfig`（max_line=120、`ktlint_function_naming_ignore_when_annotated_with=Composable` 豁免 Composable PascalCase）。`ktlintFormat` 修正既有格式。`.github/workflows/android.yml`（PR/push→main 跑 `check assembleDebug`，working-dir=android，JDK21，gradle cache）。`README.md`（結構、指令、測試策略）。**修掉 `check` 紅燈**：release 變體 Robolectric Compose 測試因 ui-test-manifest 是 debug-only 而失敗 → 在 app/library conventions 用 `androidComponents.beforeVariants` 停用 release unit test。驗收（本地等價於 CI）：`./gradlew check assembleDebug` BUILD SUCCESSFUL。root build apply false 補上 ktlint。注意：detekt（§12 gate 提及）暫緩，未納入 T0.5。
- 2026-05-27：**T0.4 完成 ✅**。`:core:testing`（android.library + kotlin.jvm.test convention）提供共用測試基建，工具放 main/ 並以 `api` 匯出給 consumer 的 test source set：`MainDispatcherExtension`（JUnit5，`@RegisterExtension`）、`MainDispatcherRule`（JUnit4/Robolectric，`@get:Rule`）、`HiltTestRunner`（FQN 字串引用 HiltTestApplication，避免 compile-time 相依生成類）、`TestClock`（fixture 雛形，domain builders 待 T1.1）。Sanity test（Jupiter）驗證 Dispatchers.Main 被替換。`:app` 加 `testImplementation`/`androidTestImplementation(project(":core:testing"))` 驗證可被消費。驗收：`:core:testing:testDebugUnitTest` + `:app:testDebugUnitTest` BUILD SUCCESSFUL。
- 2026-05-27：**T0.3 完成（差 emulator smoke）🟡**。`:app` module（application+compose+hilt+test conventions）：`@HiltAndroidApp` Application（Hilt 生成驗證）、MainActivity + HomeScreen 顯示「Invoice Wallet」、Material3 主題（主色 #1F6FEB，`ui/theme/Color.kt`+`Theme.kt`）、framework NoActionBar 啟動主題（不引入 material XML 依賴）。TDD：`MainActivityTest`（Robolectric Compose，src/test）紅→綠通過。測試策略決定為「Robolectric 為主 + instrumented smoke」：測試 convention 加 `isIncludeAndroidResources=true` + `junit-vintage-engine`（跑 JUnit4 Compose rule）+ `androidx-test-ext-junit`；compose convention 把 `ui-test-junit4` 加到 testImplementation。`HomeScreenSmokeTest`（androidTest，instrumented）已寫好且 `assembleDebugAndroidTest` 編譯通過，**執行待 emulator 確認後跑 `connectedDebugAndroidTest`**。驗收（非 emulator 部分）：`:app:testDebugUnitTest` 綠 + `:app:assembleDebug`/`assembleDebugAndroidTest` BUILD SUCCESSFUL。
- 2026-05-26：**T0.2 完成 ✅**。`android/build-logic`（composite build，includeBuild）含 6 個 convention plugins：`app.convention.android.application` / `.library` / `.compose` / `.hilt` / `.room` / `app.convention.kotlin.jvm.test`。測試 convention 用 `testOptions.unitTests.all { useJUnitPlatform() }` + JUnit5/MockK/Turbine/Kotest/coroutines-test/Robolectric（不引入 mannodermaus，降風險）。驗收：建兩個 throwaway sample module（alpha=library+test、beta=library+compose+hilt+room），`:sample:alpha:testDebugUnitTest`（JUnit5 綠）+ `:sample:beta:assembleDebug`（compose/hilt-ksp/room 全編譯）BUILD SUCCESSFUL，驗證後移除 samples。注意：root build 的 `apply false` 清單需含 room（與 ksp/hilt 並列）否則 `androidx.room` plugin not found。`android.application` convention 留待 T0.3 實證。
