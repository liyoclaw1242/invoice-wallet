package tw.invoicewallet.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.invoicewallet.datasource.mcpserver.McpServerManager
import tw.invoicewallet.datasource.relayclient.RelayManager
import tw.invoicewallet.feature.settings.AppSettings
import tw.invoicewallet.feature.settings.SettingsRepository
import javax.inject.Inject

/**
 * App-root state: the persisted settings that decide the theme and whether onboarding
 * still needs to run. `null` means "not loaded yet" so we don't flash the wrong screen.
 * Also drives the local MCP server start/stop from the settings toggle.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mcpServerManager: McpServerManager,
    private val relayManager: RelayManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.mcpEnabled to it.mcpLanMode }
                .distinctUntilChanged()
                .collect { (enabled, lanMode) ->
                    mcpServerManager.stop()
                    if (enabled) mcpServerManager.start(lanMode = lanMode)
                }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.remoteEnabled }
                .distinctUntilChanged()
                .collect { enabled -> if (enabled) relayManager.start() else relayManager.stop() }
        }
    }

    fun completeOnboarding() = viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
}
