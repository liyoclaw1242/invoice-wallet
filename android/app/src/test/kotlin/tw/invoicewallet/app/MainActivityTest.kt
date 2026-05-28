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
import tw.invoicewallet.feature.invoicelist.InvoiceListScreen
import tw.invoicewallet.feature.invoicelist.InvoiceListUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_screen_renders_the_empty_state_when_the_wallet_has_no_invoices() {
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

        // The redesigned home has no fixed title; the empty-state copy is what greets users.
        composeRule.onNodeWithText("還沒有發票").assertIsDisplayed()
    }
}
