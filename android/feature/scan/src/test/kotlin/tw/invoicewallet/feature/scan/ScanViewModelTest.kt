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
    private val lotteryRepository = FakeLotteryRepository()
    private val recognizer = FakeInvoiceRecognizer()
    private val merchantDirectory = FakeMerchantDirectory(mapOf("90650686" to "瑪可希維"))
    private val viewModel = ScanViewModel(
        invoiceRepository = repository,
        lotteryRepository = lotteryRepository,
        recognizer = recognizer,
        merchantDirectory = merchantDirectory,
        clock = object : Clock {
            override fun now(): Instant = fixedNow
        },
    )

    @Test
    fun `onQrDetected auto-saves a new invoice, emits Saved, and increments the session count`() = runTest {
        viewModel.events.test {
            viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)

            val saved = awaitItem().shouldBeInstanceOf<ScanEvent.Saved>()
            // The merchant tax id resolves via the directory → 瑪可希維; banner reads naturally.
            saved.label shouldBe "瑪可希維 · NT$232"
        }
        viewModel.session.value.savedCount shouldBe 1
        val stored = repository.getByInvoiceNumber("ZP46105854")!!
        stored.merchantName shouldBe "瑪可希維"
        stored.totalAmount shouldBe 232
        stored.source shouldBe InvoiceSource.QR_CODE
    }

    @Test
    fun `onQrDetected persists the line items the QR carried`() = runTest {
        viewModel.onQrDetected(LEFT_QR_WITH_ITEMS, rightBytes = null)

        val stored = repository.getByInvoiceNumber("ZP46105854")!!
        repository.getItems(stored.id).map { it.name } shouldBe listOf("漢堡", "飲料")
    }

    @Test
    fun `onQrDetected dedups the same invoice number within the rapid-frame window`() = runTest {
        viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)
        viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null) // same code still in frame

        viewModel.session.value.savedCount shouldBe 1
    }

    @Test
    fun `onQrDetected on an already-stored invoice emits Duplicate and does not overwrite`() = runTest {
        // User had already scanned this invoice and added a note; re-scanning must not clobber it.
        repository.upsert(sampleInvoice(id = "old").copy(userNote = "上次的午餐"))

        viewModel.events.test {
            viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)
            awaitItem().shouldBeInstanceOf<ScanEvent.Duplicate>()
        }
        repository.getById("old")!!.userNote shouldBe "上次的午餐"
        viewModel.session.value.savedCount shouldBe 0 // duplicates don't count toward this session
    }

    @Test
    fun `onQrDetected with valid left and unreadable right still saves the invoice`() = runTest {
        // ** prefix + an invalid UTF-8 byte → strict right-code decode fails; the
        // invoice must still be recognised from the left code alone.
        val badRight = byteArrayOf(0x2A, 0x2A, 0xFF.toByte())

        viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = badRight)

        val stored = repository.getByInvoiceNumber("ZP46105854")
        stored?.invoiceNumber shouldBe "ZP46105854"
    }

    @Test
    fun `onQrDetected with a malformed QR emits Failed and does not save`() = runTest {
        viewModel.events.test {
            viewModel.onQrDetected("too-short", rightBytes = null)
            awaitItem().shouldBeInstanceOf<ScanEvent.Failed>()
        }
        viewModel.session.value.savedCount shouldBe 0
    }

    @Test
    fun `onQrDetected runs the cached-numbers lottery match inline and reports the prize`() = runTest {
        // VALID_LEFT_QR carries invoice number ZP46105854 → 8-digit suffix 46105854.
        // Seed winning numbers for the same issuePeriod (11502 = ROC 115, period 02 / Mar–Apr)
        // whose 特別獎 matches the full 8 digits → top prize.
        lotteryRepository.seed(
            tw.invoicewallet.core.model.LotteryNumber(
                period = "11502",
                specialPrize = "46105854",
                grandPrize = "00000000",
                firstPrize = emptyList(),
                additionalSixth = emptyList(),
                fetchedAt = fixedNow,
            ),
        )

        viewModel.events.test {
            viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)

            val saved = awaitItem().shouldBeInstanceOf<ScanEvent.Saved>()
            saved.lotteryPrize shouldBe 10_000_000 // 特別獎
        }
        val stored = repository.getByInvoiceNumber("ZP46105854")!!
        stored.lotteryStatus shouldBe LotteryStatus.CHECKED_WON
        stored.lotteryPrize shouldBe 10_000_000
    }

    @Test
    fun `onQrDetected leaves lotteryPrize null when no cached numbers exist`() = runTest {
        viewModel.events.test {
            viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)
            awaitItem().shouldBeInstanceOf<ScanEvent.Saved>().lotteryPrize shouldBe null
        }
        repository.getByInvoiceNumber("ZP46105854")!!.lotteryStatus shouldBe LotteryStatus.PENDING
    }

    @Test
    fun `undoSavedInvoice soft-deletes the invoice and decrements savedCount`() = runTest {
        viewModel.onQrDetected(VALID_LEFT_QR, rightBytes = null)
        val saved = repository.getByInvoiceNumber("ZP46105854")!!

        viewModel.undoSavedInvoice(saved.id)

        repository.getById(saved.id) shouldBe null
        viewModel.session.value.savedCount shouldBe 0
    }

    @Test
    fun `onImageSelected with QR carrying items keeps the existing Detected-then-confirm gallery flow`() = runTest {
        recognizer.result = RecognitionResult(qrLeft = LEFT_QR_WITH_ITEMS)

        viewModel.onImageSelected(mockk())
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
    fun `onCancel returns the gallery flow to Idle`() = runTest {
        recognizer.result = RecognitionResult(ocr = RecognizedText.of("謝謝光臨")) // nothing recognised
        viewModel.onImageSelected(mockk())
        viewModel.state.value.shouldBeInstanceOf<ScanState.Error>()

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

    /** Minimal LotteryRepository — only the methods ScanViewModel.checkLotteryInline uses. */
    private class FakeLotteryRepository : tw.invoicewallet.core.database.repository.LotteryRepository {
        private val byPeriod = mutableMapOf<String, tw.invoicewallet.core.model.LotteryNumber>()
        fun seed(number: tw.invoicewallet.core.model.LotteryNumber) {
            byPeriod[number.period] = number
        }
        override suspend fun upsert(number: tw.invoicewallet.core.model.LotteryNumber) {
            byPeriod[number.period] = number
        }
        override suspend fun getByPeriod(period: String) = byPeriod[period]
        override fun observeAll() =
            kotlinx.coroutines.flow.flowOf(byPeriod.values.toList().sortedByDescending { it.period })
        override suspend fun latest() = byPeriod.values.maxByOrNull { it.period }
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
