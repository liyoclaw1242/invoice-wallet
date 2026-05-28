package tw.invoicewallet.feature.scan

import android.net.Uri
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
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
import tw.invoicewallet.feature.scan.merchant.MerchantDirectory
import tw.invoicewallet.feature.scan.ocr.RecognizedText
import tw.invoicewallet.feature.scan.recognition.InvoiceRecognizer
import tw.invoicewallet.feature.scan.recognition.RecognitionResult

class ScanViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val fixedNow = Instant.parse("2026-05-27T00:00:00Z")
    private val repository = FakeInvoiceRepository()
    private val recognizer = FakeInvoiceRecognizer()
    private val merchantDirectory = FakeMerchantDirectory(mapOf("90650686" to "瑪可希維"))
    private val viewModel = ScanViewModel(
        invoiceRepository = repository,
        recognizer = recognizer,
        merchantDirectory = merchantDirectory,
        clock = object : Clock {
            override fun now(): Instant = fixedNow
        },
    )

    @Test
    fun `onQrDetected with a valid QR moves to Detected with a QR-sourced draft`() = runTest {
        viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)

        val detected = viewModel.state.value
        detected.shouldBeInstanceOf<ScanState.Detected>()
        detected.draft.id.isNotBlank() shouldBe true
        detected.draft.invoiceNumber shouldBe "ZP46105854"
        detected.draft.totalAmount shouldBe 232
        detected.draft.merchantTaxId shouldBe "90650686"
        // store name auto-filled from the seller tax ID via the directory
        detected.draft.merchantName shouldBe "瑪可希維"
        detected.draft.source shouldBe InvoiceSource.QR_CODE
    }

    @Test
    fun `onQrDetected surfaces the decoded line items alongside the draft`() = runTest {
        viewModel.onQrDetected(LEFT_QR_WITH_ITEMS, rightBytes = null)

        val detected = viewModel.state.value
        detected.shouldBeInstanceOf<ScanState.Detected>()
        detected.items.map { it.name } shouldBe listOf("漢堡", "飲料")
        detected.items.map { it.unitPrice } shouldBe listOf(85, 147)
        detected.items.first().invoiceId shouldBe detected.draft.id
    }

    @Test
    fun `onUserConfirm persists the detected line items with the invoice`() = runTest {
        viewModel.onQrDetected(LEFT_QR_WITH_ITEMS, rightBytes = null)
        val draft = (viewModel.state.value as ScanState.Detected).draft

        viewModel.onUserConfirm(draft)

        repository.getById(draft.id) shouldBe draft
        repository.getItems(draft.id).map { it.name } shouldBe listOf("漢堡", "飲料")
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
        viewModel.onQrDetected("too-short", rightBytes = null)

        viewModel.state.value.shouldBeInstanceOf<ScanState.Error>()
    }

    @Test
    fun `onQrDetected with a valid left but unreadable right falls back to the left code`() = runTest {
        // ** prefix + an invalid UTF-8 byte → strict right-code decode fails; the
        // invoice must still be recognised from the left code alone.
        val badRight = byteArrayOf(0x2A, 0x2A, 0xFF.toByte())

        viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = badRight)

        val state = viewModel.state.value
        state.shouldBeInstanceOf<ScanState.Detected>()
        state.draft.invoiceNumber shouldBe "ZP46105854"
    }

    @Test
    fun `onCancel returns to Idle`() = runTest {
        viewModel.onQrDetected("too-short", rightBytes = null)

        viewModel.onCancel()

        viewModel.state.value shouldBe ScanState.Idle
    }

    @Test
    fun `onImageSelected with a QR image builds a QR-sourced draft`() = runTest {
        recognizer.result = RecognitionResult(qrLeft = VALID_LEFT_QR)

        viewModel.onImageSelected(mockk())

        val state = viewModel.state.value
        state.shouldBeInstanceOf<ScanState.Detected>()
        state.draft.invoiceNumber shouldBe "ZP46105854"
        state.draft.source shouldBe InvoiceSource.QR_CODE
    }

    @Test
    fun `onImageSelected with an OCR-only image builds an OCR-sourced draft`() = runTest {
        recognizer.result = RecognitionResult(
            ocr = RecognizedText.of("ZF-76011229", "開立日期 115/04/17", "總計 1080"),
        )

        viewModel.onImageSelected(mockk())

        val state = viewModel.state.value
        state.shouldBeInstanceOf<ScanState.Detected>()
        state.draft.source shouldBe InvoiceSource.OCR
        state.draft.totalAmount shouldBe 1080
    }

    @Test
    fun `onImageSelected with nothing recognised moves to Error`() = runTest {
        recognizer.result = RecognitionResult(ocr = RecognizedText.of("謝謝光臨"))

        viewModel.onImageSelected(mockk())

        viewModel.state.value.shouldBeInstanceOf<ScanState.Error>()
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

    private class FakeInvoiceRecognizer(var result: RecognitionResult = RecognitionResult()) : InvoiceRecognizer {
        override suspend fun recognize(image: Uri): RecognitionResult = result
    }

    private class FakeMerchantDirectory(private val names: Map<String, String>) : MerchantDirectory {
        override suspend fun nameFor(taxId: String): String? = names[taxId]
    }

    private companion object {
        // INV2 left QR from the real fixtures (B2C, no right code needed).
        const val VALID_LEFT_QR =
            "ZP4610585411504219684000000dd000000e80000000090650686uVRGyvkXO4arTAAHA722vQ==:**********:1:1:1:"

        // Same header, but the detail section carries two UTF-8 line items (name:qty:price).
        const val LEFT_QR_WITH_ITEMS =
            "ZP4610585411504219684000000dd000000e80000000090650686uVRGyvkXO4arTAAHA722vQ==:**********:2:2:1:漢堡:1:85:飲料:1:147"
    }
}
