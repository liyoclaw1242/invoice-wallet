package tw.invoicewallet.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tw.invoicewallet.feature.export.ExportFormat

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_the_current_settings() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    uiState = SettingsUiState(themeMode = ThemeMode.DARK, carrierCode = "/ABC123"),
                    onThemeModeChange = {},
                    onDefaultScanModeChange = {},
                    onCarrierCodeSave = {},
                    onCarrierCodeClear = {},
                    onExport = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("外觀主題").assertIsDisplayed()
        composeRule.onNodeWithText("資料匯出").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun picking_a_theme_reports_it() {
        var picked: ThemeMode? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    uiState = SettingsUiState(themeMode = ThemeMode.SYSTEM),
                    onThemeModeChange = { picked = it },
                    onDefaultScanModeChange = {},
                    onCarrierCodeSave = {},
                    onCarrierCodeClear = {},
                    onExport = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("theme-DARK").performClick()
        picked shouldBe ThemeMode.DARK
    }

    @Test
    fun saving_carrier_code_reports_the_value() {
        var saved: String? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onThemeModeChange = {},
                    onDefaultScanModeChange = {},
                    onCarrierCodeSave = { saved = it },
                    onCarrierCodeClear = {},
                    onExport = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("carrier-field").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("carrier-field").performTextInput("/XYZ.+9")
        composeRule.onNodeWithTag("carrier-save").performScrollTo().performClick()
        saved shouldBe "/XYZ.+9"
    }

    @Test
    fun tapping_export_json_reports_the_format() {
        var format: ExportFormat? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    uiState = SettingsUiState(),
                    onThemeModeChange = {},
                    onDefaultScanModeChange = {},
                    onCarrierCodeSave = {},
                    onCarrierCodeClear = {},
                    onExport = { format = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("export-JSON").performScrollTo().performClick()
        format shouldBe ExportFormat.JSON
    }
}
