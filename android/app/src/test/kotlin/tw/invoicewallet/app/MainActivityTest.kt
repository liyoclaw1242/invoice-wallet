package tw.invoicewallet.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tw.invoicewallet.app.ui.theme.InvoiceWalletTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_screen_shows_app_title() {
        composeRule.setContent {
            InvoiceWalletTheme {
                InvoiceWalletScaffold {}
            }
        }

        composeRule.onNodeWithText("Invoice Wallet").assertIsDisplayed()
    }
}
