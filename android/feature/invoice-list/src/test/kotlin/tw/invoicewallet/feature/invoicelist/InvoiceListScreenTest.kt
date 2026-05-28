package tw.invoicewallet.feature.invoicelist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pixel-class viewport so the bottom scan pill stays clickable in tests.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InvoiceListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_invoices_and_a_row_tap_reports_the_id() {
        var clicked: String? = null
        composeRule.setContent {
            InvoiceListScreen(
                uiState = InvoiceListUiState(invoices = listOf(invoice("a", merchant = "全聯福利中心"))),
                onQueryChange = {},
                onScanClick = {},
                onInvoiceClick = { clicked = it },
            )
        }

        composeRule.onNodeWithText("全聯福利中心").assertIsDisplayed()
        composeRule.onNodeWithText("全聯福利中心").performClick()
        clicked shouldBe "a"
    }

    @Test
    fun shows_empty_state_with_its_scan_cta_when_there_are_no_invoices() {
        var scanned = false
        composeRule.setContent {
            InvoiceListScreen(
                uiState = InvoiceListUiState(),
                onQueryChange = {},
                onScanClick = { scanned = true },
                onInvoiceClick = {},
            )
        }

        composeRule.onNodeWithTag("empty-state").assertIsDisplayed()
        composeRule.onNodeWithText("開始掃描").performClick()
        scanned shouldBe true
    }

    @Test
    fun scan_pill_triggers_scan_when_invoices_exist() {
        var scanned = false
        composeRule.setContent {
            InvoiceListScreen(
                uiState = InvoiceListUiState(invoices = listOf(invoice("a", "全聯"))),
                onQueryChange = {},
                onScanClick = { scanned = true },
                onInvoiceClick = {},
            )
        }

        // The floating pill keeps the legacy "scan-fab" testTag so calling code stays unchanged.
        composeRule.onNodeWithTag("scan-fab").performClick()
        scanned shouldBe true
    }

    @Test
    fun lottery_pill_routes_to_lottery() {
        var lottery = false
        composeRule.setContent {
            InvoiceListScreen(
                uiState = InvoiceListUiState(
                    invoices = listOf(invoice("a", "全聯")),
                    summary = InvoiceSummary(year = 2026, month = 5, monthCount = 1, totalCount = 1),
                ),
                onQueryChange = {},
                onScanClick = {},
                onInvoiceClick = {},
                onLotteryClick = { lottery = true },
            )
        }

        composeRule.onNodeWithTag("lottery-action").performClick()
        lottery shouldBe true
    }

    private fun invoice(id: String, merchant: String) = Invoice(
        id = id,
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = merchant,
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 100,
        taxAmount = 5,
        randomCode = "1234",
        category = null,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        deletedAt = null,
    )
}
