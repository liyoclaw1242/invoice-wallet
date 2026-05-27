package tw.invoicewallet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.app.ui.theme.InvoiceWalletTheme
import tw.invoicewallet.feature.invoicelist.InvoiceListScreen
import tw.invoicewallet.feature.invoicelist.InvoiceListUiState

/**
 * Instrumented smoke test — runs on a real device/emulator via AndroidJUnitRunner.
 * Renders the home (invoice list) screen directly, no Activity launch / Hilt needed.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_screen_shows_app_title() {
        composeRule.setContent {
            InvoiceWalletTheme {
                InvoiceListScreen(
                    uiState = InvoiceListUiState(),
                    onQueryChange = {},
                    onScanClick = {},
                    onInvoiceClick = {},
                )
            }
        }

        composeRule.onNodeWithText("發票錢包").assertIsDisplayed()
    }
}
