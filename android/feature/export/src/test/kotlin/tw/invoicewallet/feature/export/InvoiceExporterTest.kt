package tw.invoicewallet.feature.export

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus

class InvoiceExporterTest {

    @Test
    fun `json round-trips losslessly`() {
        val invoices = sample()
        val text = InvoiceExporter.export(invoices, ExportFormat.JSON)
        InvoiceExporter.import(text, ExportFormat.JSON) shouldBe invoices
    }

    @Test
    fun `csv round-trips losslessly`() {
        val invoices = sample()
        val text = InvoiceExporter.export(invoices, ExportFormat.CSV)
        InvoiceExporter.import(text, ExportFormat.CSV) shouldBe invoices
    }

    @Test
    fun `csv survives commas, quotes and newlines inside fields`() {
        val nasty = base("x").copy(
            merchantName = "全聯, \"福利\" 中心",
            userNote = "買了\n兩瓶,還有\"折扣\"",
            userTags = listOf("飲料, 零食", "報帳\"用\""),
        )
        val text = InvoiceExporter.export(listOf(nasty), ExportFormat.CSV)
        InvoiceExporter.import(text, ExportFormat.CSV) shouldBe listOf(nasty)
    }

    @Test
    fun `csv has a header row and one data row per invoice`() {
        val text = InvoiceExporter.export(sample(), ExportFormat.CSV)
        text.lineSequence().first() shouldContain "invoiceNumber"
        InvoiceExporter.import(text, ExportFormat.CSV) shouldHaveSize sample().size
    }

    @Test
    fun `json is human-readable and names its fields`() {
        val text = InvoiceExporter.export(sample(), ExportFormat.JSON)
        text shouldContain "\"merchantName\""
        text shouldContain "\n" // pretty-printed
    }

    @Test
    fun `empty list round-trips in both formats`() {
        InvoiceExporter.import(InvoiceExporter.export(emptyList(), ExportFormat.JSON), ExportFormat.JSON) shouldBe
            emptyList()
        InvoiceExporter.import(InvoiceExporter.export(emptyList(), ExportFormat.CSV), ExportFormat.CSV) shouldBe
            emptyList()
    }

    @Test
    fun `exports and re-imports ten thousand invoices within a few seconds`() {
        val invoices = (1..10_000).map { base("id-$it").copy(totalAmount = it, userTags = listOf("t$it")) }
        val elapsed = kotlin.system.measureTimeMillis {
            val csv = InvoiceExporter.export(invoices, ExportFormat.CSV)
            InvoiceExporter.import(csv, ExportFormat.CSV) shouldBe invoices
            val json = InvoiceExporter.export(invoices, ExportFormat.JSON)
            InvoiceExporter.import(json, ExportFormat.JSON) shouldBe invoices
        }
        assert(elapsed < 5_000) { "export/import of 10k invoices took ${elapsed}ms" }
    }

    private fun sample(): List<Invoice> = listOf(
        base("a").copy(
            merchantName = "全聯福利中心",
            userNote = "週末採買",
            userTags = listOf("食品", "日用"),
            lotteryStatus = LotteryStatus.CHECKED_WON,
            lotteryPrize = 1_000,
        ),
        // all-nullable-null row to prove null vs value survives
        base("b").copy(
            merchantName = "",
            merchantTaxId = null,
            buyerTaxId = null,
            carrierIdEncrypted = null,
            category = null,
            ocrConfidence = null,
            lotteryPrize = null,
            imagePath = null,
            userNote = null,
            userTags = emptyList(),
            source = InvoiceSource.MANUAL,
        ),
        base("c").copy(
            ocrConfidence = 0.87f,
            source = InvoiceSource.OCR,
            deletedAt = Instant.parse("2026-02-01T00:00:00Z"),
        ),
    )

    private fun base(id: String) = Invoice(
        id = id,
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = "商店",
        merchantTaxId = "12345678",
        buyerTaxId = "87654321",
        carrierIdEncrypted = "enc:abc",
        totalAmount = 100,
        taxAmount = 5,
        currency = "TWD",
        randomCode = "1234",
        category = "餐飲",
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = "note",
        userTags = listOf("a"),
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        deletedAt = null,
    )
}
