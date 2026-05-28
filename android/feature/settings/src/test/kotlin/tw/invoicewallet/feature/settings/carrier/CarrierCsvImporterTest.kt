package tw.invoicewallet.feature.settings.carrier

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus

class CarrierCsvImporterTest {

    private val now = Instant.parse("2026-05-28T10:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `creates new invoices with source CARRIER_API and persists items`() = runTest {
        val repo = FakeRepo()
        val importer = CarrierCsvImporter(repo, fixedClock)

        val result = importer.import(
            listOf(
                row("YA00000001", date(2026, 3, 26), "新東陽", 308, listOf(item("大心", 1, 308))),
            ),
        )

        result shouldBe ImportResult(added = 1, updated = 0, skipped = 0)
        val saved = repo.allInvoices().single()
        saved.invoiceNumber shouldBe "YA00000001"
        saved.source shouldBe InvoiceSource.CARRIER_API
        saved.merchantName shouldBe "新東陽"
        saved.totalAmount shouldBe 308
        saved.createdAt shouldBe now
        repo.itemsOf(saved.id).map { it.name } shouldBe listOf("大心")
    }

    @Test
    fun `merging onto an existing invoice preserves note, tags, lottery state, and createdAt`() = runTest {
        val original = Invoice(
            id = "id-1",
            invoiceNumber = "YB00000002",
            issueDate = LocalDate(2026, 1, 1),
            issuePeriod = "11501",
            merchantName = "舊店名（使用者改過）",
            merchantTaxId = "00000000",
            buyerTaxId = null,
            carrierIdEncrypted = "enc:carrier",
            totalAmount = 999, // outdated
            taxAmount = 0,
            randomCode = "",
            category = "food",
            source = InvoiceSource.QR_CODE,
            ocrConfidence = null,
            lotteryStatus = LotteryStatus.CHECKED_WON,
            lotteryPrize = 200,
            imagePath = "/photos/1.jpg",
            userNote = "重要：午餐",
            userTags = listOf("food", "lunch"),
            createdAt = Instant.parse("2026-01-15T10:00:00Z"),
            updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
            deletedAt = null,
        )
        val repo = FakeRepo().apply { upsert(original) }
        val importer = CarrierCsvImporter(repo, fixedClock)

        val result = importer.import(
            listOf(row("YB00000002", date(2026, 1, 2), "正確店名", 1234, listOf(item("正確品項", 1, 1234)))),
        )

        result shouldBe ImportResult(added = 0, updated = 1, skipped = 0)
        val merged = repo.getByInvoiceNumber("YB00000002")!!
        // CSV-authoritative fields overwrite:
        merged.merchantName shouldBe "正確店名"
        merged.totalAmount shouldBe 1234
        merged.issueDate shouldBe LocalDate(2026, 1, 2)
        merged.updatedAt shouldBe now
        // user-owned fields survive:
        merged.userNote shouldBe "重要：午餐"
        merged.userTags shouldBe listOf("food", "lunch")
        merged.lotteryStatus shouldBe LotteryStatus.CHECKED_WON
        merged.lotteryPrize shouldBe 200
        merged.imagePath shouldBe "/photos/1.jpg"
        merged.carrierIdEncrypted shouldBe "enc:carrier"
        merged.createdAt shouldBe Instant.parse("2026-01-15T10:00:00Z") // not bumped
        // items are replaced (the CSV is the source of truth for items):
        repo.itemsOf(merged.id).map { it.name } shouldBe listOf("正確品項")
    }

    private fun row(number: String, date: LocalDate, seller: String, total: Int, items: List<CarrierItemRow>) =
        CarrierInvoiceRow(
            invoiceNumber = number,
            issueDate = date,
            sellerTaxId = "12345678",
            sellerName = seller,
            sellerAddress = "—",
            buyerTaxId = null,
            totalAmount = total,
            status = "開立已確認",
            items = items,
        )

    private fun item(name: String, qty: Int, price: Int) =
        CarrierItemRow(name = name, quantity = qty.toDouble(), unitPrice = price, amount = qty * price)

    private fun date(y: Int, m: Int, d: Int) = LocalDate(y, m, d)

    /** Minimal in-memory repository — only the methods the importer touches. */
    private class FakeRepo : InvoiceRepository {
        private val invoices = MutableStateFlow<List<Invoice>>(emptyList())
        private val items = mutableMapOf<String, List<InvoiceItem>>()

        fun allInvoices(): List<Invoice> = invoices.value
        fun itemsOf(id: String): List<InvoiceItem> = items[id].orEmpty()

        override suspend fun upsert(invoice: Invoice): Invoice {
            invoices.value = invoices.value.filterNot { it.id == invoice.id } + invoice
            return invoice
        }

        override suspend fun upsertWithItems(invoice: Invoice, items: List<InvoiceItem>): Invoice {
            this.items[invoice.id] = items
            return upsert(invoice)
        }

        override suspend fun getByInvoiceNumber(invoiceNumber: String): Invoice? =
            invoices.value.firstOrNull { it.invoiceNumber == invoiceNumber }

        override suspend fun getById(id: String): Invoice? = invoices.value.firstOrNull { it.id == id }
        override fun observeAll(): Flow<List<Invoice>> = invoices
        override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> = invoices
        override suspend fun softDelete(id: String) {
            invoices.value = invoices.value.filterNot { it.id == id }
        }
        override suspend fun search(keyword: String, limit: Int): List<Invoice> = emptyList()
    }
}
