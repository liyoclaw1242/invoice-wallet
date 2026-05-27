package tw.invoicewallet.feature.scan

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.core.testing.MainDispatcherExtension

class ScanViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val fixedNow = Instant.parse("2026-05-27T00:00:00Z")
    private val repository = FakeInvoiceRepository()
    private val viewModel = ScanViewModel(
        invoiceRepository = repository,
        clock = object : Clock {
            override fun now(): Instant = fixedNow
        },
        idGenerator = { "fixed-id" },
    )

    @Test
    fun `onQrDetected with a valid QR moves to Detected with a QR-sourced draft`() = runTest {
        viewModel.state.test {
            awaitItem() shouldBe ScanState.Idle

            viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)

            val detected = awaitItem()
            detected.shouldBeInstanceOf<ScanState.Detected>()
            detected.draft.id shouldBe "fixed-id"
            detected.draft.invoiceNumber shouldBe "ZP46105854"
            detected.draft.totalAmount shouldBe 232
            detected.draft.merchantTaxId shouldBe "90650686"
            detected.draft.source shouldBe InvoiceSource.QR_CODE
        }
    }

    @Test
    fun `onUserConfirm persists the invoice and emits Saved`() = runTest {
        val invoice = sampleInvoice("inv-1")

        viewModel.state.test {
            awaitItem() shouldBe ScanState.Idle

            viewModel.onUserConfirm(invoice)

            awaitItem() shouldBe ScanState.Saved("inv-1")
        }
        repository.getById("inv-1") shouldBe invoice
    }

    @Test
    fun `onQrDetected with a malformed QR moves to Error`() = runTest {
        viewModel.state.test {
            awaitItem() shouldBe ScanState.Idle

            viewModel.onQrDetected("too-short", rightBytes = null)

            awaitItem().shouldBeInstanceOf<ScanState.Error>()
        }
    }

    @Test
    fun `onCancel returns to Idle`() = runTest {
        viewModel.onQrDetected("too-short", rightBytes = null)

        viewModel.onCancel()

        viewModel.state.value shouldBe ScanState.Idle
    }

    private fun sampleInvoice(id: String) = Invoice(
        id = id,
        invoiceNumber = "ZP46105854",
        issueDate = LocalDate(2026, 4, 21),
        issuePeriod = "11502",
        merchantName = "瑪可希維",
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
        createdAt = fixedNow,
        updatedAt = fixedNow,
        deletedAt = null,
    )

    private companion object {
        // INV2 left QR from the real fixtures (B2C, no right code needed).
        const val VALID_LEFT_QR =
            "ZP4610585411504219684000000dd000000e80000000090650686uVRGyvkXO4arTAAHA722vQ==:**********:1:1:1:"
    }
}
