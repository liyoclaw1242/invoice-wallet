package tw.invoicewallet.feature.scan.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.kotest.matchers.nulls.shouldNotBeNull
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
import tw.invoicewallet.feature.scan.ScanState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScanScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detected_state_shows_fields_and_confirm_returns_the_edited_invoice() {
        var confirmed: Invoice? = null
        composeRule.setContent {
            MaterialTheme {
                ScanScreen(
                    state = ScanState.Detected(draft),
                    onPickImage = {},
                    onConfirm = { confirmed = it },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("確認發票資料").assertIsDisplayed()
        composeRule.onNodeWithTag("field-merchant").performTextReplacement("八方雲集")
        composeRule.onNodeWithTag("confirm-button").performScrollTo().performClick()

        confirmed.shouldNotBeNull()
        confirmed!!.merchantName shouldBe "八方雲集"
        confirmed!!.invoiceNumber shouldBe "ZP46105854"
    }

    @Test
    fun saved_state_shows_the_success_message() {
        composeRule.setContent {
            MaterialTheme { ScanScreen(ScanState.Saved("inv-1"), {}, {}, {}) }
        }

        composeRule.onNodeWithTag("scan-saved").assertIsDisplayed()
    }

    @Test
    fun error_state_shows_the_error_message() {
        composeRule.setContent {
            MaterialTheme { ScanScreen(ScanState.Error("無法解析發票 QR code"), {}, {}, {}) }
        }

        composeRule.onNodeWithText("無法解析發票 QR code").assertIsDisplayed()
    }

    private val draft = Invoice(
        id = "draft-1",
        invoiceNumber = "ZP46105854",
        issueDate = LocalDate(2026, 4, 21),
        issuePeriod = "11502",
        merchantName = "",
        merchantTaxId = "90650686",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 232,
        taxAmount = 11,
        randomCode = "9684",
        category = null,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-05-27T00:00:00Z"),
        updatedAt = Instant.parse("2026-05-27T00:00:00Z"),
        deletedAt = null,
    )
}
