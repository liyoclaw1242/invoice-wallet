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
            mcpEnabled = prefs[Keys.MCP_ENABLED] ?: false,
            mcpLanMode = prefs[Keys.MCP_LAN] ?: false,
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

    override suspend fun setMcpEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MCP_ENABLED] = enabled }
    }

    override suspend fun setMcpLanMode(lanMode: Boolean) {
        dataStore.edit { it[Keys.MCP_LAN] = lanMode }
    }

    private fun String?.toThemeMode(): ThemeMode =
        this?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM

    private fun String?.toScanMode(): DefaultScanMode =
        this?.let { runCatching { DefaultScanMode.valueOf(it) }.getOrNull() } ?: DefaultScanMode.CAMERA

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val SCAN = stringPreferencesKey("default_scan_mode")
        val ONBOARDED = booleanPreferencesKey("onboarding_completed")
        val MCP_ENABLED = booleanPreferencesKey("mcp_enabled")
        val MCP_LAN = booleanPreferencesKey("mcp_lan_mode")
    }
}
