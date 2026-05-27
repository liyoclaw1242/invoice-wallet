package tw.invoicewallet.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @BeforeEach
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tempDir, "settings.preferences_pb") }
        repo = DataStoreSettingsRepository(dataStore)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `returns defaults when nothing has been stored`() = runTest {
        repo.settings.first() shouldBe AppSettings()
    }

    @Test
    fun `persists theme mode`() = runTest {
        repo.setThemeMode(ThemeMode.DARK)
        repo.settings.first().themeMode shouldBe ThemeMode.DARK
    }

    @Test
    fun `persists default scan mode`() = runTest {
        repo.setDefaultScanMode(DefaultScanMode.GALLERY)
        repo.settings.first().defaultScanMode shouldBe DefaultScanMode.GALLERY
    }

    @Test
    fun `persists onboarding completion`() = runTest {
        repo.settings.first().onboardingCompleted shouldBe false
        repo.setOnboardingCompleted(true)
        repo.settings.first().onboardingCompleted shouldBe true
    }
}
