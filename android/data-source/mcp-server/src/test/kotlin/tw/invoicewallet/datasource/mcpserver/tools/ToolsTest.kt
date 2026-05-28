package tw.invoicewallet.datasource.mcpserver.tools

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceItem
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.datasource.authz.QueryConstraints

class ToolsTest {

    private val repo = FakeInvoiceRepository(
        listOf(
            inv("a", LocalDate(2026, 1, 5), "全聯福利中心", 300, "食品", number = "AA00000001"),
            inv("b", LocalDate(2026, 2, 10), "全聯福利中心", 150, "食品", number = "AA00000002"),
            inv("c", LocalDate(2026, 2, 20), "屈臣氏", 500, "日用", number = "BB00000003", note = "洗髮精"),
            inv(
                "d",
                LocalDate(2026, 3, 1),
                "馬偕醫院",
                1200,
                "醫療",
                number = "CC00000004",
                status = LotteryStatus.CHECKED_WON,
                prize = 1000,
            ),
        ),
        itemsByInvoice = mapOf(
            "c" to listOf(
                InvoiceItem("c-1", "c", "洗髮精", 1.0, 320, 320, null, 0),
                InvoiceItem("c-2", "c", "牙膏", 2.0, 90, 180, null, 1),
            ),
        ),
    )

    @Test
    fun `list_invoices returns everything by default`() = runTest {
        val result = ListInvoicesTool(repo).call(JsonObject(emptyMap()), QueryConstraints())
        result.jsonObject["total"]!!.jsonPrimitive.int shouldBe 4
        result.jsonObject["items"]!!.jsonArray.size shouldBe 4
    }

    @Test
    fun `list_invoices filters by category and amount`() = runTest {
        val args = buildJsonObject {
            put("category", "食品")
            put("min_amount", 200)
        }
        val result = ListInvoicesTool(repo).call(args, QueryConstraints())
        result.jsonObject["total"]!!.jsonPrimitive.int shouldBe 1
    }

    @Test
    fun `list_invoices honours pagination`() = runTest {
        val args = buildJsonObject {
            put("limit", 2)
            put("offset", 1)
        }
        val result = ListInvoicesTool(repo).call(args, QueryConstraints())
        result.jsonObject["total"]!!.jsonPrimitive.int shouldBe 4
        result.jsonObject["items"]!!.jsonArray.size shouldBe 2
    }

    @Test
    fun `constraints hide excluded categories and older invoices`() = runTest {
        val constraints = QueryConstraints(notBefore = LocalDate(2026, 2, 1), excludedCategories = listOf("醫療"))
        val result = ListInvoicesTool(repo).call(JsonObject(emptyMap()), constraints)
        // b (2/10 食品) + c (2/20 日用); a is too old, d is excluded category
        result.jsonObject["total"]!!.jsonPrimitive.int shouldBe 2
    }

    @Test
    fun `get_invoice_detail returns the invoice or null`() = runTest {
        val tool = GetInvoiceDetailTool(repo)
        tool.call(buildJsonObject { put("invoice_id", "c") }, QueryConstraints())
            .jsonObject["merchant_name"]!!.jsonPrimitive.content shouldBe "屈臣氏"
        tool.call(buildJsonObject { put("invoice_id", "missing") }, QueryConstraints()) shouldBe JsonNull
    }

    @Test
    fun `get_invoice_detail embeds the invoice's line items`() = runTest {
        val result = GetInvoiceDetailTool(repo)
            .call(buildJsonObject { put("invoice_id", "c") }, QueryConstraints())
            .jsonObject

        val items = result["items"]!!.jsonArray
        items.size shouldBe 2
        items.first().jsonObject["name"]!!.jsonPrimitive.content shouldBe "洗髮精"
        items.first().jsonObject["amount"]!!.jsonPrimitive.int shouldBe 320
    }

    @Test
    fun `get_invoice_detail returns an empty items array when there are none`() = runTest {
        val result = GetInvoiceDetailTool(repo)
            .call(buildJsonObject { put("invoice_id", "a") }, QueryConstraints())
            .jsonObject

        result["items"]!!.jsonArray.size shouldBe 0
    }

    @Test
    fun `get_invoice_detail respects the grant's category exclusion`() = runTest {
        val constraints = QueryConstraints(excludedCategories = listOf("醫療"))
        GetInvoiceDetailTool(repo).call(buildJsonObject { put("invoice_id", "d") }, constraints) shouldBe JsonNull
    }

    @Test
    fun `search_invoices matches keyword and returns empty when none`() = runTest {
        val tool = SearchInvoicesTool(repo)
        tool.call(buildJsonObject { put("keyword", "屈臣氏") }, QueryConstraints())
            .jsonObject["items"]!!.jsonArray.size shouldBe 1
        tool.call(buildJsonObject { put("keyword", "nothere") }, QueryConstraints())
            .jsonObject["items"]!!.jsonArray.size shouldBe 0
    }

    @Test
    fun `list_merchants counts visits and filters by min_visits`() = runTest {
        val all = ListMerchantsTool(repo).call(JsonObject(emptyMap()), QueryConstraints())
        all.jsonObject["items"]!!.jsonArray.size shouldBe 3 // 全聯, 屈臣氏, 馬偕

        val repeat = ListMerchantsTool(repo).call(buildJsonObject { put("min_visits", 2) }, QueryConstraints())
        val items = repeat.jsonObject["items"]!!.jsonArray
        items.size shouldBe 1
        items.first().jsonObject["merchant_name"]!!.jsonPrimitive.content shouldBe "全聯福利中心"
        items.first().jsonObject["visit_count"]!!.jsonPrimitive.int shouldBe 2
        items.first().jsonObject["total_amount"]!!.jsonPrimitive.int shouldBe 450
    }

    @Test
    fun `get_spending_summary groups by category`() = runTest {
        val result = GetSpendingSummaryTool(repo).call(
            buildJsonObject { put("group_by", "category") },
            QueryConstraints(),
        )
        result.jsonObject["total_amount"]!!.jsonPrimitive.int shouldBe 2150
        val food = result.jsonObject["groups"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["key"]!!.jsonPrimitive.content == "食品" }
        food["total_amount"]!!.jsonPrimitive.int shouldBe 450
        food["count"]!!.jsonPrimitive.int shouldBe 2
    }

    @Test
    fun `get_spending_summary groups by month`() = runTest {
        val result = GetSpendingSummaryTool(repo).call(
            buildJsonObject { put("group_by", "month") },
            QueryConstraints(),
        )
        result.jsonObject["group_by"]!!.jsonPrimitive.content shouldBe "month"
        result.jsonObject["groups"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content }
            .toSet() shouldBe setOf("2026-01", "2026-02", "2026-03")
    }

    @Test
    fun `get_lottery_status summarises winners and statuses`() = runTest {
        val result = GetLotteryStatusTool(repo).call(JsonObject(emptyMap()), QueryConstraints())
        result.jsonObject["total_winnings"]!!.jsonPrimitive.int shouldBe 1000
        result.jsonObject["winners"]!!.jsonArray.size shouldBe 1
        result.jsonObject["status_counts"]!!.jsonObject["CHECKED_WON"]!!.jsonPrimitive.int shouldBe 1
        result.jsonObject["status_counts"]!!.jsonObject["PENDING"]!!.jsonPrimitive.int shouldBe 3
    }

    @Test
    fun `every tool advertises an object input schema`() {
        listOf(
            ListInvoicesTool(repo),
            GetInvoiceDetailTool(repo),
            SearchInvoicesTool(repo),
            ListMerchantsTool(repo),
            GetSpendingSummaryTool(repo),
            GetLotteryStatusTool(repo),
        ).forEach { tool ->
            tool.inputSchema["type"]!!.jsonPrimitive.content shouldBe "object"
            tool.name.isNotBlank() shouldBe true
            tool.inputSchema.shouldBeInstanceOf<JsonObject>()
        }
    }

    private fun inv(
        id: String,
        date: LocalDate,
        merchant: String,
        amount: Int,
        category: String?,
        number: String = "AB12345678",
        note: String? = null,
        status: LotteryStatus = LotteryStatus.PENDING,
        prize: Int? = null,
    ) = Invoice(
        id = id,
        invoiceNumber = number,
        issueDate = date,
        issuePeriod = "%s%02d".format(date.year - 1911, (date.monthNumber + 1) / 2 * 2),
        merchantName = merchant,
        merchantTaxId = "1234567$id".take(8),
        buyerTaxId = null,
        carrierIdEncrypted = "enc:secret",
        totalAmount = amount,
        taxAmount = amount / 20,
        randomCode = "0000",
        category = category,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = status,
        lotteryPrize = prize,
        imagePath = null,
        userNote = note,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        deletedAt = null,
    )
}

private class FakeInvoiceRepository(
    private val data: List<Invoice>,
    private val itemsByInvoice: Map<String, List<InvoiceItem>> = emptyMap(),
) : InvoiceRepository {
    override suspend fun upsert(invoice: Invoice): Invoice = invoice
    override suspend fun getItems(invoiceId: String): List<InvoiceItem> = itemsByInvoice[invoiceId].orEmpty()
    override suspend fun getById(id: String): Invoice? = data.firstOrNull { it.id == id }
    override fun observeAll(): Flow<List<Invoice>> = flowOf(data)
    override fun queryByDateRange(from: LocalDate, to: LocalDate): Flow<List<Invoice>> =
        flowOf(data.filter { it.issueDate >= from && it.issueDate <= to })
    override suspend fun softDelete(id: String) = Unit
    override suspend fun search(keyword: String, limit: Int): List<Invoice> = data.filter {
        it.merchantName.contains(keyword, ignoreCase = true) ||
            it.invoiceNumber.contains(keyword, ignoreCase = true) ||
            (it.userNote?.contains(keyword, ignoreCase = true) == true)
    }.take(limit)
}
