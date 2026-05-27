package tw.invoicewallet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.invoicewallet.app.ui.theme.InvoiceWalletTheme

/**
 * Instrumented smoke test — runs on a real device/emulator via AndroidJUnitRunner.
 * Renders [HomeScreen] directly (no Activity launch) so it stays independent of the
 * Hilt instrumented test runner introduced in T0.4.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_screen_shows_app_title() {
        composeRule.setContent {
            InvoiceWalletTheme {
                HomeScreen()
            }
        }

        composeRule.onNodeWithText("Invoice Wallet").assertIsDisplayed()
    }
}
