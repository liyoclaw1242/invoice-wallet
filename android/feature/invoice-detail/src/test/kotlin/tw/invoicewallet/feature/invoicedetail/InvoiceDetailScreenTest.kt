package tw.invoicewallet.feature.invoicedetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
@Config(sdk = [34])
class InvoiceDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_fields_and_save_reports_edited_note() {
        var savedNote: String? = null
        composeRule.setContent {
            MaterialTheme {
                InvoiceDetailScreen(
                    state = InvoiceDetailState.Loaded(invoice()),
                    onSave = { note, _ -> savedNote = note },
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("全聯福利中心").assertIsDisplayed()
        composeRule.onNodeWithTag("note-field").performTextReplacement("週末採買")
        composeRule.onNodeWithTag("save-button").performScrollTo().performClick()

        savedNote shouldBe "週末採買"
    }

    @Test
    fun delete_requires_confirmation_then_reports() {
        var deleted = false
        composeRule.setContent {
            MaterialTheme {
                InvoiceDetailScreen(
                    state = InvoiceDetailState.Loaded(invoice()),
                    onSave = { _, _ -> },
                    onDelete = { deleted = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("delete-button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm-delete").performClick()

        deleted shouldBe true
    }

    @Test
    fun shows_not_found_state() {
        composeRule.setContent {
            MaterialTheme {
                InvoiceDetailScreen(
                    state = InvoiceDetailState.NotFound,
                    onSave = { _, _ -> },
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("detail-not-found").assertIsDisplayed()
    }

    private fun invoice() = Invoice(
        id = "inv-1",
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = "全聯福利中心",
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 105,
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
