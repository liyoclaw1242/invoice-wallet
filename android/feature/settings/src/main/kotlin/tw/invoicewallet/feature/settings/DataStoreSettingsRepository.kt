package tw.invoicewallet.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SettingsRepository] backed by a Preferences DataStore. Unknown values fall back to defaults. */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME].toThemeMode(),
            defaultScanMode = prefs[Keys.SCAN].toScanMode(),
            onboardingCompleted = prefs[Keys.ONBOARDED] ?: false,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }

    override suspend fun setDefaultScanMode(mode: DefaultScanMode) {
        dataStore.edit { it[Keys.SCAN] = mode.name }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDED] = completed }
    }

    private fun String?.toThemeMode(): ThemeMode =
        this?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    private fun String?.toScanMode(): DefaultScanMode =
        this?.let { runCatching { DefaultScanMode.valueOf(it) }.getOrNull() } ?: DefaultScanMode.CAMERA

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val SCAN = stringPreferencesKey("default_scan_mode")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
    }
}
