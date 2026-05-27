package tw.invoicewallet.feature.settings

import kotlinx.coroutines.flow.Flow

/** Reads/writes the non-sensitive app settings as a reactive stream. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDefaultScanMode(mode: DefaultScanMode)

    suspend fun setOnboardingCompleted(completed: Boolean)
}
