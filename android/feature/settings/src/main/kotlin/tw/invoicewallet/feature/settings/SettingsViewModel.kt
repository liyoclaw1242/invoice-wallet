package tw.invoicewallet.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.datasource.mcpserver.McpServerControls
import tw.invoicewallet.datasource.relayclient.RelayDeviceStore
import tw.invoicewallet.feature.export.ExportFormat
import tw.invoicewallet.feature.export.InvoiceExporter
import tw.invoicewallet.feature.settings.carrier.CarrierCsvImporter
import tw.invoicewallet.feature.settings.carrier.CarrierCsvParser
import tw.invoicewallet.feature.settings.carrier.ImportResult
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultScanMode: DefaultScanMode = DefaultScanMode.CAMERA,
    val carrierCode: String = "",
    val mcpEnabled: Boolean = false,
    val mcpLanMode: Boolean = false,
    val mcpToken: String = "",
    val remoteEnabled: Boolean = false,
    val relayPaired: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val carrierCodeStore: CarrierCodeStore,
    private val invoiceRepository: InvoiceRepository,
    private val mcpServer: McpServerControls,
    private val relayDeviceStore: RelayDeviceStore,
) : ViewModel() {

    // The carrier code is read on demand (encrypted store, not a Flow), so we mirror it
    // into a StateFlow and keep it in sync on writes.
    private val carrierCode = MutableStateFlow(carrierCodeStore.get().orEmpty())
    private val mcpToken = MutableStateFlow(mcpServer.token())

    // Importer is stateless; constructed inline so its dependency isn't a Hilt ctor param
    // for the VM (keeps existing tests' ctor calls unchanged).
    private val carrierCsvImporter = CarrierCsvImporter(invoiceRepository, Clock.System)

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, carrierCode, mcpToken) { settings, carrier, token ->
            SettingsUiState(
                themeMode = settings.themeMode,
                defaultScanMode = settings.defaultScanMode,
                carrierCode = carrier,
                mcpEnabled = settings.mcpEnabled,
                mcpLanMode = settings.mcpLanMode,
                mcpToken = token,
                remoteEnabled = settings.remoteEnabled,
                relayPaired = relayDeviceStore.isPaired,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setDefaultScanMode(mode: DefaultScanMode) = viewModelScope.launch {
        settingsRepository.setDefaultScanMode(mode)
    }

    fun setCarrierCode(code: String) {
        carrierCodeStore.set(code)
        carrierCode.value = code
    }

    fun clearCarrierCode() {
        carrierCodeStore.clear()
        carrierCode.value = ""
    }

    fun setMcpEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setMcpEnabled(enabled) }

    fun setMcpLanMode(lanMode: Boolean) = viewModelScope.launch { settingsRepository.setMcpLanMode(lanMode) }

    fun setRemoteEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setRemoteEnabled(enabled) }

    fun regenerateMcpToken() {
        mcpToken.value = mcpServer.regenerateToken()
    }

    /** Serialises every invoice in the wallet to [format]; the caller writes it to a chosen file. */
    suspend fun buildExport(format: ExportFormat): String =
        InvoiceExporter.export(invoiceRepository.observeAll().first(), format)

    /**
     * Parses a 財政部 carrier CSV export and persists it. CSV is authoritative for the
     * header fields it carries; user-edited fields (note/tags/lottery state) survive.
     */
    suspend fun importCarrierCsv(text: String): CarrierImportSummary {
        val parsed = CarrierCsvParser.parse(text)
        val result: ImportResult = carrierCsvImporter.import(parsed.invoices)
        return CarrierImportSummary(
            added = result.added,
            updated = result.updated,
            skippedRows = parsed.skippedRows,
            errors = parsed.errors,
        )
    }
}

/** UI-facing summary of an import; rolls the parser's skips into the result. */
data class CarrierImportSummary(val added: Int, val updated: Int, val skippedRows: Int, val errors: List<String>)
