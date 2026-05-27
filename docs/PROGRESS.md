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
| 0 Foundation | ✅ 完成 | T0.1–T0.5 + T0.3b 全綠；CI 待 GitHub remote 才能實跑 |
| 1 Core Data | ✅ 完成 | T1.1–T1.5 全綠（model→DB→DAO→Repository，加密 + 雙層測試）|
| 2 Scan | ✅ 完成 | T2.1–T2.5 + 即時相機 + 統編→店名；**Mi MIX 2 實機驗證**：掃描→辨識→店名(三家正確)→存加密 DB |
| 3 List/Search | 🔨 進行中 | T3.1 清單 VM、T3.3 清單/搜尋 UI、T3.2 詳情 VM、T3.4 詳情 UI |
| 4 Lottery | ⛔ | |
| 5 Export | ⛔ | |
| 6 MCP Server | ⛔ | |
| 7 Content Provider | ⛔ | |

## 變更日誌

- 2026-05-27：**T0.3b 完成 ✅**。instrumented smoke `HomeScreenSmokeTest` 在 emulator `invoice_pixel7_api35` 跑 `connectedDebugAndroidTest` 通過。修：androidTest APK 打包衝突（JUnit5 jar 重複 META-INF/LICENSE.md，因 :core:testing 以 api 匯出 jupiter 流入 androidTest）→ `configureKotlinAndroid` 加 `packaging.resources.excludes`（LICENSE*/NOTICE*/AL2.0/LGPL2.1）。Iteration 0 完整收尾；instrumented 測試管線端到端驗證（Iteration 1 DB/DAO 測試會用）。
- 2026-05-27：**Iteration 2 收掉 ✅**。店名解析修正（g0v 三種登記版面：公司`data.公司名稱`/商業`data.商業名稱`/稅籍巢狀`data.財政部.營業人名稱`）後，使用者在 Mi MIX 2 實測三家統編（七里香/瑪可希維/士豐）店名皆正確帶入。掃描主線實機全綠。
- 2026-05-27：**T2.3 即時相機已由使用者實機驗證 ✅**（Mi MIX 2：鏡頭對發票 QR → 自動偵測 → 跳確認畫面）。**Phase 2（統編→店名 自動查詢）完成**：使用者選「自動線上查」。`MerchantDirectory`/`CachingMerchantDirectory`（記憶體快取，同統編一 session 查一次，失敗靜默回 null）+ `GovMerchantNameSource`（Ktor + OkHttp 打 g0v `company.g0v.ronny.tw` 公開商業登記，容錯解析 公司名稱/name/營業人名稱）。`ScanViewModel` 注入 `MerchantDirectory`，建好 draft 後若店名空+有賣方統編 → 自動查填；`onQrDetected` 改非同步以容網路查詢。Hilt `ScanModule` 提供 HttpClient(OkHttp, 6s timeout)/source/directory。`INTERNET` 權限（App 首個對外連線；僅送出公開統編）。測試：Ktor MockEngine 驗解析(3)+ cache 行為(2)+ ViewModel 自動填店名。驗收：`check assembleDebug` 綠、APK 裝上 Mi MIX 2 啟動無 crash。⚠️ g0v API 對各統編的實際命中率由使用者實測；查無/離線則店名留白。
- 2026-05-27：**T2.3 設定（程式/建置/安裝驗證；即時掃描實效待裝置實測）**。ML Kit（`com.google.mlkit:barcode-scanning` + `text-recognition-chinese`，**bundled 模型、全本機零連網**）。`InvoiceRecognizer` 介面 + `MlKitInvoiceRecognizer`（相簿影像 → QR 分類[左碼 rawValue / 右碼 rawBytes 0x2a2a] + 中文 OCR → `RecognizedText`）。CameraX 即時掃描：`QrCodeAnalyzer`（ImageAnalysis 即時偵測 e-invoice QR，偵測到左碼即發一次）+ `CameraQrScanner` Composable（Preview + 綁 lifecycle）。`ScanViewModel.onImageSelected`（QR 優先、OCR fallback `ExtractedFields.toDraftInvoice`）；live QR 走既有 `onQrDetected`。Hilt `ScanModule` 提供 recognizer。UI：Idle 加相機預覽插槽（:app 注入）+ 相簿選圖 + 貼上備援 + `Recognizing` 載入態；`:app` 處理 CAMERA 權限 + PickVisualMedia。:feature:scan 補 `testInstrumentationRunner`。驗收：`check assembleDebug` 綠、APK `adb install` 至實機 Mi MIX 2(Android 9) 並啟動無 crash。⚠️ 即時相機對真發票的辨識效果只能由使用者在裝置上實測（模擬器相機無法掃）；ML Kit instrumented smoke 因實機裝測試 APK 逾時未跑成。**統編→店名 自動線上查詢（Phase 2）尚未做**。
- 2026-05-27：**T2.2 完成 ✅**。`InvoiceFieldExtractor`（純邏輯，**不依賴 ML Kit**，吃 `RecognizedText`=辨識文字行；ML Kit `Text`→`RecognizedText` 留待 T2.3）。抽：發票號碼 `[A-Z]{2}[-\s]?\d{8}`（有/無分隔→信心 0.95/0.75）、日期（西元 20xx 優先、民國年月日 CJK、民國分隔 /.- ；自動 +1911）、總額（總計/總金額/應收/合計 優先序，去逗號取最大數）。`ExtractedField<T>` 帶 confidence、`lowConfidenceFields(threshold=0.8)` 供 UI 標示。TDD 9 測試（代表性 fixtures：證明聯/收銀機版面 + 日期/金額變體）。驗收：`:feature:scan:testDebugUnitTest`（共 19）綠 + `check` 綠。註：手機可用需 T2.3 接 ML Kit 影像輸入；真實 OCR 文字之後可擴充 fixtures。
- 2026-05-27：**DI 接線 + :app 整合 + APK ✅**。專案首次 Hilt DI：`:core:database` 加 hilt convention + `DatabaseModule`（提供加密 InvoiceWalletDatabase/InvoiceDao/Clock/InvoiceRepository）。`ScanViewModel` 改 `@HiltViewModel @Inject`（移除 idGenerator，內用 UUID）。`:app` 依賴 `:feature:scan` + `:core:database`（帶 DatabaseModule 上 classpath，經 api 傳遞 :core:model）+ hilt-navigation-compose；`InvoiceWalletApp` = `InvoiceWalletScaffold`(TopAppBar「Invoice Wallet」) + `ScanRoute`(hiltViewModel + ScanScreen)。移除 HomeScreen，:app 2 測試改測 InvoiceWalletScaffold。驗收：`check assembleDebug` 綠、模擬器啟動無 crash（Hilt graph + SQLCipher runtime OK）、APK 32MB → `android/app/build/outputs/apk/debug/app-debug.apk`。
- 2026-05-27：**T2.4 完成 ✅（提前於 T2.2/T2.3，因不卡 OCR 素材/相機）**。`ScanViewModel`（plain ViewModel，constructor 注入 InvoiceRepository + Clock + idGenerator）：`onQrDetected(left,rightBytes)`→`EInvoiceQrParser`→`ParsedInvoice.toDraftInvoice`（source=QR_CODE、issuePeriod 由日期推算雙月期別、tax=total−untaxed、merchantName 空待 OCR/手填）→`Detected(draft)`；`onUserConfirm(invoice)`→`viewModelScope` 內 `repository.upsert`→`Saved(id)`；malformed→`Error`；`onCancel`→`Idle`。OCR 路徑(`onOcrResult`)留待 T2.2。測試：Turbine 觀 StateFlow + FakeInvoiceRepository + MainDispatcherExtension（4 測試）。形成「掃 QR→確認→存進加密 DB」垂直切片。驗收：`:feature:scan:testDebugUnitTest`（含 parser 共 7）綠 + `check` 綠。
- 2026-05-27：**T2.1 完成 ✅（Iteration 2 開始）**。`:feature:scan` android library（純 Kotlin 解析器）。`EInvoiceQrParser`：左碼前 53 字元固定欄（字軌/民國日期→ISO/隨機碼/未稅hex/含稅hex/買方/賣方），AES 變長 → 以 `*` 自用區定位編碼旗標與品項（非固定偏移）；右碼依**編碼旗標**（UTF-8/Big5，不假設）strict-decode bytes 後拆品項；買方 00000000→null、hex 大小寫不敏感、不過度驗證。malformed 拋 `EInvoiceQrException`。測試載入 `src/test/resources/einvoice-qr-fixtures.json`（**3 張真實發票** + 5 malformed）驅動，全綠。⚠️ fixtures 為真實資料，依使用者指示 commit。
- 2026-05-27：**T1.4 + T1.5 完成 ✅ → Iteration 1 收掉**。Repository 介面 + Room 實作放 `:core:database`（計畫允許）。T1.4 介面：`InvoiceRepository`（upsert/getById/observeAll/queryByDateRange/softDelete/search）、`LotteryRepository`、`AuthGrantRepository`（含 audit），回傳 domain model（不漏持久化型別）。T1.5 實作：`RoomInvoiceRepository`/`RoomLotteryRepository`/`RoomAuthGrantRepository`，包 DAO + entity↔domain mapper；softDelete/revoke 注入 `kotlinx.datetime.Clock`（預設 System，測試用固定 clock）。雙層測試：(1) src/test JVM 單元測試用手寫 fake DAO（7 測試：upsert/getById 映射、softDelete 用 clock、日期區間、revoke、audit count）；(2) androidTest 真 Room 整合測試（3 repo round-trip）。驗收：`:core:database:testDebugUnitTest` 7 綠 + `connectedDebugAndroidTest` 25 綠 + `./gradlew check` 綠。決定同 T1.3：orchestrator 序列做（共用模組/模擬器）。
- 2026-05-27：**T1.3 完成 ✅（orchestrator 序列做，非 subagent）**。決定：4 個 DAO 共用 `:core:database` 模組 + DB 類別 + 單一模擬器，平行 subagent 協調成本高於效益（計畫 §2.4 亦警告），改由 orchestrator 序列 TDD。`InvoiceDao`（insert/upsert/getById/findByInvoiceNumber/observeAll(Flow)/observeByDateRange/findByMerchant/search/softDelete）、`InvoiceItemDao`、`LotteryNumberDao`、`AuthGrantDao`、`QueryAuditLogDao`，全 wire 進 DB。androidTest in-memory Room 共 22 測試（含 InvoiceDao 6：unique 衝突拋例外、日期區間降冪、softDelete 排除、Turbine 觀察 Flow）。驗收：`:core:database:connectedDebugAndroidTest` 22 綠（emulator）、`./gradlew check` 綠。
- 2026-05-27：**T1.2 完成 ✅**。`:core:database` android library（room + test + ktlint conventions），依賴 `:core:model`。分 3 phase 在模擬器驗證：
  - **Phase 1**：5 個 Room `@Entity`（對應 §5.1，enum 存 name 字串）+ `Converters`（Instant↔Long、LocalDate↔ISO、List<String>↔JSON）+ entity↔domain `Mappers` + placeholder `InvoiceDao`（T1.3a 擴充）+ `InvoiceWalletDatabase`(v1, exportSchema)。InvoiceWalletDatabaseTest in-memory round-trip 綠。
  - **Phase 2**：SQLCipher（`net.zetetic:sqlcipher-android` + `SupportOpenHelperFactory`，`System.loadLibrary("sqlcipher")` 單次載入）+ `DatabasePassphraseProvider`（EncryptedSharedPreferences / Android Keystore master key，32-byte 隨機 passphrase）+ `buildEncryptedDatabase()`。EncryptionTest：正確金鑰可讀、**錯誤金鑰開不了**。
  - **Phase 3**：DatabaseMigrationTest（MigrationTestHelper 驗 v1 schema，schemas 匯出至 androidTest assets）+ DatabasePassphraseProviderTest（passphrase 穩定 32 bytes）。
  - 驗收：`:core:database:connectedDebugAndroidTest` 5 測試全綠（emulator）；`./gradlew check` 綠。註：DAO 僅 placeholder InvoiceDao，完整 DAO 在 T1.3 平行 subagent。
- 2026-05-27：**T1.1 完成 ✅（Iteration 1 開始）**。`:core:model` 純 Kotlin JVM module（無 Android 依賴，`kotlin.jvm` + serialization，bytecode target 17 對齊 D8）。Domain models 依 ARCHITECTURE §5.1：`Invoice`(+`formattedNumber()`)、`InvoiceItem`、`LotteryNumber`、`AuthGrant`、`QueryAuditLog` + enums `InvoiceSource`/`LotteryStatus`/`AuthChannel`，全部 `@Serializable`，日期用 kotlinx-datetime（`LocalDate`/`Instant`，公開 API → `api` 依賴）。TDD：InvoiceTest（formattedNumber、copy/value-equality、serialization roundtrip）紅→綠 + ModelSerializationTest（LotteryNumber 清單、AuthGrant nullable roundtrip）。驗收：`:core:model:check` 綠（5 測試 + ktlint）。註：domain models 為純資料類別（不含 Room 註解）；Room @Entity + mapper 在 T1.2。kotlinx-datetime 為 serializable 友善的技術預設。

- 2026-05-26：建立 PROGRESS.md / QUESTIONS.md，記錄 baseline 與三項決策。前置 P1、P2 待解。
- 2026-05-26：安裝並驗證 Android SDK（P2 ✅）。brew cask `android-commandlinetools` + sdkmanager 裝 platform-tools 37.0.0 / android-35 / build-tools 35.0.1 / cmdline-tools latest / emulator 36.5.11 / google_apis arm64-v8a image；建 AVD `invoice_pixel7_api35`；licenses 全 accept。`~/.zshrc` 持久化被 harness 擋下，待使用者處理。Iteration 0 前置就緒。
- 2026-05-26：**T0.1 完成 ✅**。重構為 `android/` 子目錄 monorepo（清掉根 Java 範本）。版本組合 AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28（保守已知良好）。建立 `android/`：`settings.gradle.kts`（pluginManagement + FAIL_ON_PROJECT_REPOS + type-safe accessors）、`gradle/libs.versions.toml`（完整版本目錄）、`gradle.properties`（config cache + build cache + parallel）、`build.gradle.kts`（plugins apply false）、`local.properties`（sdk.dir，gitignored）。`.gitignore` 補 Android + 整個 `.idea/`。驗收：`./gradlew tasks` BUILD SUCCESSFUL、config cache 已存。Commit `bd29f75`。
- 2026-05-27：**T0.5 完成 ✅（CI 待 remote 實跑）**。ktlint convention plugin（`app.convention.ktlint`，jlleitschuh ktlint-gradle 12.1.1 + ktlint engine 1.3.1，`android=true`），套用於 :app + :core:testing。`.editorconfig`（max_line=120、`ktlint_function_naming_ignore_when_annotated_with=Composable` 豁免 Composable PascalCase）。`ktlintFormat` 修正既有格式。`.github/workflows/android.yml`（PR/push→main 跑 `check assembleDebug`，working-dir=android，JDK21，gradle cache）。`README.md`（結構、指令、測試策略）。**修掉 `check` 紅燈**：release 變體 Robolectric Compose 測試因 ui-test-manifest 是 debug-only 而失敗 → 在 app/library conventions 用 `androidComponents.beforeVariants` 停用 release unit test。驗收（本地等價於 CI）：`./gradlew check assembleDebug` BUILD SUCCESSFUL。root build apply false 補上 ktlint。注意：detekt（§12 gate 提及）暫緩，未納入 T0.5。
- 2026-05-27：**T0.4 完成 ✅**。`:core:testing`（android.library + kotlin.jvm.test convention）提供共用測試基建，工具放 main/ 並以 `api` 匯出給 consumer 的 test source set：`MainDispatcherExtension`（JUnit5，`@RegisterExtension`）、`MainDispatcherRule`（JUnit4/Robolectric，`@get:Rule`）、`HiltTestRunner`（FQN 字串引用 HiltTestApplication，避免 compile-time 相依生成類）、`TestClock`（fixture 雛形，domain builders 待 T1.1）。Sanity test（Jupiter）驗證 Dispatchers.Main 被替換。`:app` 加 `testImplementation`/`androidTestImplementation(project(":core:testing"))` 驗證可被消費。驗收：`:core:testing:testDebugUnitTest` + `:app:testDebugUnitTest` BUILD SUCCESSFUL。
- 2026-05-27：**T0.3 完成（差 emulator smoke）🟡**。`:app` module（application+compose+hilt+test conventions）：`@HiltAndroidApp` Application（Hilt 生成驗證）、MainActivity + HomeScreen 顯示「Invoice Wallet」、Material3 主題（主色 #1F6FEB，`ui/theme/Color.kt`+`Theme.kt`）、framework NoActionBar 啟動主題（不引入 material XML 依賴）。TDD：`MainActivityTest`（Robolectric Compose，src/test）紅→綠通過。測試策略決定為「Robolectric 為主 + instrumented smoke」：測試 convention 加 `isIncludeAndroidResources=true` + `junit-vintage-engine`（跑 JUnit4 Compose rule）+ `androidx-test-ext-junit`；compose convention 把 `ui-test-junit4` 加到 testImplementation。`HomeScreenSmokeTest`（androidTest，instrumented）已寫好且 `assembleDebugAndroidTest` 編譯通過，**執行待 emulator 確認後跑 `connectedDebugAndroidTest`**。驗收（非 emulator 部分）：`:app:testDebugUnitTest` 綠 + `:app:assembleDebug`/`assembleDebugAndroidTest` BUILD SUCCESSFUL。
- 2026-05-26：**T0.2 完成 ✅**。`android/build-logic`（composite build，includeBuild）含 6 個 convention plugins：`app.convention.android.application` / `.library` / `.compose` / `.hilt` / `.room` / `app.convention.kotlin.jvm.test`。測試 convention 用 `testOptions.unitTests.all { useJUnitPlatform() }` + JUnit5/MockK/Turbine/Kotest/coroutines-test/Robolectric（不引入 mannodermaus，降風險）。驗收：建兩個 throwaway sample module（alpha=library+test、beta=library+compose+hilt+room），`:sample:alpha:testDebugUnitTest`（JUnit5 綠）+ `:sample:beta:assembleDebug`（compose/hilt-ksp/room 全編譯）BUILD SUCCESSFUL，驗證後移除 samples。注意：root build 的 `apply false` 清單需含 room（與 ksp/hilt 並列）否則 `androidx.room` plugin not found。`android.application` convention 留待 T0.3 實證。
