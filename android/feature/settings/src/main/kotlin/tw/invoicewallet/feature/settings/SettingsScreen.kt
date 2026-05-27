package tw.invoicewallet.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.invoicewallet.feature.export.ExportFormat

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
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
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section("外觀主題") {
                ChipRow(
                    options = ThemeMode.entries,
                    selected = uiState.themeMode,
                    label = { it.displayName() },
                    tag = { "theme-${it.name}" },
                    onSelect = onThemeModeChange,
                )
            }

            Section("預設掃描方式") {
                ChipRow(
                    options = DefaultScanMode.entries,
                    selected = uiState.defaultScanMode,
                    label = { it.displayName() },
                    tag = { "scan-${it.name}" },
                    onSelect = onDefaultScanModeChange,
                )
            }

            Section("手機條碼載具") {
                var draft by remember(uiState.carrierCode) { mutableStateOf(uiState.carrierCode) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("載具條碼（加密儲存於本機）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("carrier-field"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCarrierCodeSave(draft) },
                        modifier = Modifier.testTag("carrier-save"),
                    ) { Text("儲存") }
                    OutlinedButton(
                        onClick = {
                            draft = ""
                            onCarrierCodeClear()
                        },
                        modifier = Modifier.testTag("carrier-clear"),
                    ) { Text("清除") }
                }
            }

            Section("資料匯出") {
                Text(
                    "資料屬於你 — 隨時可帶走整個發票錢包。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onExport(ExportFormat.JSON) },
                        modifier = Modifier.testTag("export-JSON"),
                    ) { Text("匯出 JSON") }
                    OutlinedButton(
                        onClick = { onExport(ExportFormat.CSV) },
                        modifier = Modifier.testTag("export-CSV"),
                    ) { Text("匯出 CSV") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
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
