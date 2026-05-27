package tw.invoicewallet.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.invoicewallet.feature.settings.AppSettings
import tw.invoicewallet.feature.settings.SettingsRepository
import javax.inject.Inject

/**
 * App-root state: the persisted settings that decide the theme and whether onboarding
 * still needs to run. `null` means "not loaded yet" so we don't flash the wrong screen.
 */
@HiltViewModel
class AppViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() = viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
}
