package tw.invoicewallet.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.invoicewallet.core.designsystem.components.OutlinePill
import tw.invoicewallet.core.designsystem.components.PillButton
import tw.invoicewallet.core.designsystem.components.PillChip
import tw.invoicewallet.core.designsystem.components.QuietPill
import tw.invoicewallet.core.designsystem.components.SectionLabel
import tw.invoicewallet.core.designsystem.theme.WalletTheme
import tw.invoicewallet.feature.export.ExportFormat

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onPairRelayClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun writeExport(uri: android.net.Uri, format: ExportFormat) = scope.launch {
        val content = viewModel.buildExport(format)
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
        Toast.makeText(context, "已匯出 ${format.extension.uppercase()}", Toast.LENGTH_SHORT).show()
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.JSON.mimeType),
    ) { uri -> uri?.let { writeExport(it, ExportFormat.JSON) } }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType),
    ) { uri -> uri?.let { writeExport(it, ExportFormat.CSV) } }

    val carrierImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "讀不到 CSV 內容", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val summary = viewModel.importCarrierCsv(text)
            val msg = buildString {
                append("新增 ${summary.added}，更新 ${summary.updated}")
                if (summary.skippedRows > 0) append("，略過 ${summary.skippedRows}")
                if (summary.errors.isNotEmpty()) append("，錯誤 ${summary.errors.size}")
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    SettingsScreen(
        uiState = uiState,
        onThemeModeChange = viewModel::setThemeMode,
        onDefaultScanModeChange = viewModel::setDefaultScanMode,
        onCarrierCodeSave = viewModel::setCarrierCode,
        onCarrierCodeClear = viewModel::clearCarrierCode,
        onExport = { format ->
            when (format) {
                ExportFormat.JSON -> jsonLauncher.launch("invoices.${format.extension}")
                ExportFormat.CSV -> csvLauncher.launch("invoices.${format.extension}")
            }
        },
        onImportCarrierCsv = {
            carrierImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
        },
        onReplayOnboarding = viewModel::replayOnboarding,
        onMcpEnabledChange = viewModel::setMcpEnabled,
        onMcpLanModeChange = viewModel::setMcpLanMode,
        onRegenerateToken = viewModel::regenerateMcpToken,
        onRemoteEnabledChange = viewModel::setRemoteEnabled,
        onPairRelayClick = onPairRelayClick,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDefaultScanModeChange: (DefaultScanMode) -> Unit,
    onCarrierCodeSave: (String) -> Unit,
    onCarrierCodeClear: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onImportCarrierCsv: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onMcpEnabledChange: (Boolean) -> Unit = {},
    onMcpLanModeChange: (Boolean) -> Unit = {},
    onRegenerateToken: () -> Unit = {},
    onRemoteEnabledChange: (Boolean) -> Unit = {},
    onPairRelayClick: () -> Unit = {},
) {
    WalletTheme {
        Scaffold(
            modifier = modifier,
            containerColor = WalletTheme.colors.surfaceBase,
            topBar = {
                TopAppBar(
                    title = { Text("設定", style = WalletTheme.typography.title) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text(
                                "返回",
                                style = WalletTheme.typography.pillLabel,
                                color = WalletTheme.colors.inkSecondary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WalletTheme.colors.surfaceBase,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = WalletTheme.spacing.lg)
                    .padding(bottom = WalletTheme.spacing.xxl),
            ) {
                SectionGroup(label = "外觀") {
                    PillChipRow(
                        options = ThemeMode.entries,
                        selected = uiState.themeMode,
                        label = { it.displayName() },
                        tag = { "theme-${it.name}" },
                        onSelect = onThemeModeChange,
                    )
                }

                SectionGroup(label = "預設掃描方式") {
                    PillChipRow(
                        options = DefaultScanMode.entries,
                        selected = uiState.defaultScanMode,
                        label = { it.displayName() },
                        tag = { "scan-${it.name}" },
                        onSelect = onDefaultScanModeChange,
                    )
                }

                SectionGroup(label = "手機條碼載具") {
                    var draft by remember(uiState.carrierCode) { mutableStateOf(uiState.carrierCode) }
                    PaperField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = "載具條碼（加密儲存於本機）",
                        testTag = "carrier-field",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs)) {
                        PillButton(
                            text = "儲存",
                            onClick = { onCarrierCodeSave(draft) },
                            modifier = Modifier.testTag("carrier-save"),
                        )
                        QuietPill(
                            text = "清除",
                            onClick = {
                                draft = ""
                                onCarrierCodeClear()
                            },
                            modifier = Modifier.testTag("carrier-clear"),
                        )
                    }
                }

                SectionGroup(label = "匯入財政部載具 CSV") {
                    HelperText(
                        "從財政部電子發票平台下載「手機條碼載具」明細 CSV 後匯入。已存在的發票會以 CSV 覆寫" +
                            "（保留你的備註、標籤、對獎狀態）；品項會被替換為 CSV 內容。",
                    )
                    PillButton(
                        text = "選擇 CSV 檔案",
                        onClick = onImportCarrierCsv,
                        modifier = Modifier.testTag("carrier-import"),
                    )
                }

                SectionGroup(label = "資料匯出") {
                    HelperText("資料屬於你 — 隨時可帶走整個發票錢包。")
                    Row(horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs)) {
                        PillButton(
                            text = "匯出 JSON",
                            onClick = { onExport(ExportFormat.JSON) },
                            modifier = Modifier.testTag("export-JSON"),
                        )
                        OutlinePill(
                            text = "匯出 CSV",
                            onClick = { onExport(ExportFormat.CSV) },
                            modifier = Modifier.testTag("export-CSV"),
                        )
                    }
                }

                SectionGroup(label = "AI 連線（本機 MCP）") {
                    SwitchRow(
                        label = "啟用本機 MCP 伺服器",
                        checked = uiState.mcpEnabled,
                        onCheckedChange = onMcpEnabledChange,
                        testTag = "mcp-enable",
                    )
                    if (uiState.mcpEnabled) {
                        SwitchRow(
                            label = "允許區域網路連線 (0.0.0.0)",
                            checked = uiState.mcpLanMode,
                            onCheckedChange = onMcpLanModeChange,
                            testTag = "mcp-lan",
                        )
                        HelperText("Bearer Token（複製到 Claude Desktop connector）")
                        SelectionContainer {
                            Text(
                                uiState.mcpToken,
                                style = WalletTheme.typography.mono,
                                color = WalletTheme.colors.inkPrimary,
                                modifier = Modifier.testTag("mcp-token"),
                            )
                        }
                        OutlinePill(
                            text = "重新產生 Token",
                            onClick = onRegenerateToken,
                            modifier = Modifier.testTag("mcp-regenerate"),
                        )
                        HelperText(
                            "PC 連線：adb reverse tcp:7777 tcp:7777，再將 connector 指向 http://localhost:7777/mcp。" +
                                "發票資料留在手機，僅在你授權的工具呼叫時被讀取。",
                        )
                    }
                }

                SectionGroup(label = "遠端 AI（Relay）") {
                    HelperText(
                        if (uiState.relayPaired) "已配對 Relay。" else "尚未配對。先在 relay 端取得配對碼。",
                    )
                    OutlinePill(
                        text = if (uiState.relayPaired) "重新配對 Relay" else "配對 Relay",
                        onClick = onPairRelayClick,
                        modifier = Modifier.testTag("pair-relay"),
                    )
                    SwitchRow(
                        label = "允許 AI 遠端查詢",
                        checked = uiState.remoteEnabled,
                        onCheckedChange = onRemoteEnabledChange,
                        enabled = uiState.relayPaired,
                        testTag = "remote-enable",
                    )
                    HelperText(
                        "開啟後手機會連到 relay；Claude 經 Cloudflare tunnel→relay→你的手機查詢。" +
                            "發票資料不經過 relay，只在手機上產生回應。",
                    )
                }

                SectionGroup(label = "關於 App") {
                    OutlinePill(
                        text = "重看引導",
                        onClick = onReplayOnboarding,
                        modifier = Modifier.testTag("replay-onboarding"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionGroup(label: String, content: @Composable () -> Unit) {
    SectionLabel(text = label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WalletTheme.spacing.xs, bottom = WalletTheme.spacing.md)
            .background(WalletTheme.colors.surfaceElevated, WalletTheme.shapes.card)
            .padding(WalletTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(WalletTheme.spacing.sm),
    ) {
        content()
    }
}

@Composable
private fun HelperText(text: String) {
    Text(
        text,
        style = WalletTheme.typography.caption,
        color = WalletTheme.colors.inkSecondary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperField(value: String, onValueChange: (String) -> Unit, placeholder: String, testTag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = WalletTheme.typography.bodyMd) },
        singleLine = true,
        shape = WalletTheme.shapes.card,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = WalletTheme.colors.surfaceBase,
            unfocusedContainerColor = WalletTheme.colors.surfaceBase,
            focusedIndicatorColor = WalletTheme.colors.accentTeal,
            unfocusedIndicatorColor = WalletTheme.colors.divider,
            focusedTextColor = WalletTheme.colors.inkPrimary,
            unfocusedTextColor = WalletTheme.colors.inkPrimary,
            focusedPlaceholderColor = WalletTheme.colors.inkTertiary,
            unfocusedPlaceholderColor = WalletTheme.colors.inkTertiary,
        ),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = WalletTheme.typography.bodyLg,
            color = if (enabled) WalletTheme.colors.inkPrimary else WalletTheme.colors.inkTertiary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WalletTheme.colors.surfaceElevated,
                checkedTrackColor = WalletTheme.colors.accentTeal,
                uncheckedThumbColor = WalletTheme.colors.inkTertiary,
                uncheckedTrackColor = WalletTheme.colors.surfaceTinted,
                uncheckedBorderColor = WalletTheme.colors.divider,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun <T> PillChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(WalletTheme.spacing.xs)) {
        options.forEach { option ->
            PillChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag(tag(option)),
            )
        }
    }
}

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "跟隨系統"
    ThemeMode.LIGHT -> "淺色"
    ThemeMode.DARK -> "深色"
}

private fun DefaultScanMode.displayName(): String = when (this) {
    DefaultScanMode.CAMERA -> "相機"
    DefaultScanMode.GALLERY -> "相簿"
}
