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
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.datasource.mcpserver.McpServerControls
import tw.invoicewallet.feature.export.ExportFormat
import tw.invoicewallet.feature.export.InvoiceExporter
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultScanMode: DefaultScanMode = DefaultScanMode.CAMERA,
    val carrierCode: String = "",
    val mcpEnabled: Boolean = false,
    val mcpLanMode: Boolean = false,
    val mcpToken: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val carrierCodeStore: CarrierCodeStore,
    private val invoiceRepository: InvoiceRepository,
    private val mcpServer: McpServerControls,
) : ViewModel() {

    // The carrier code is read on demand (encrypted store, not a Flow), so we mirror it
    // into a StateFlow and keep it in sync on writes.
    private val carrierCode = MutableStateFlow(carrierCodeStore.get().orEmpty())
    private val mcpToken = MutableStateFlow(mcpServer.token())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, carrierCode, mcpToken) { settings, carrier, token ->
            SettingsUiState(
                themeMode = settings.themeMode,
                defaultScanMode = settings.defaultScanMode,
                carrierCode = carrier,
                mcpEnabled = settings.mcpEnabled,
                mcpLanMode = settings.mcpLanMode,
                mcpToken = token,
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

    fun regenerateMcpToken() {
        mcpToken.value = mcpServer.regenerateToken()
    }

    /** Serialises every invoice in the wallet to [format]; the caller writes it to a chosen file. */
    suspend fun buildExport(format: ExportFormat): String =
        InvoiceExporter.export(invoiceRepository.observeAll().first(), format)
}
