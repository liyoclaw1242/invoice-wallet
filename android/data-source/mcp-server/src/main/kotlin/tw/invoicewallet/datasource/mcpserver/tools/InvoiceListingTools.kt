package tw.invoicewallet.datasource.mcpserver.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.datasource.authz.QueryConstraints
import tw.invoicewallet.datasource.mcpserver.McpTool

/** `list_invoices` — filtered, paginated listing. */
class ListInvoicesTool(private val invoices: InvoiceRepository) : McpTool {
    override val name = "list_invoices"
    override val description = "列出發票，可依日期區間、商店名、類別、金額範圍篩選並分頁。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            stringProp("date_from", "起始日期 (ISO，含)")
            stringProp("date_to", "結束日期 (ISO，含)")
            stringProp("merchant_name", "商店名稱子字串比對")
            stringProp("category", "類別精確比對")
            intProp("min_amount", "最低總金額")
            intProp("max_amount", "最高總金額")
            intProp("limit", "回傳上限 (預設 50，最多 500)")
            intProp("offset", "略過筆數 (預設 0)")
        }
    }

    override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement {
        val from = arguments.date("date_from")
        val to = arguments.date("date_to")
        val merchant = arguments.string("merchant_name")
        val category = arguments.string("category")
        val min = arguments.int("min_amount")
        val max = arguments.int("max_amount")

        val filtered = invoices.observeAll().first().applyConstraints(constraints).filter {
            (from == null || it.issueDate >= from) &&
                (to == null || it.issueDate <= to) &&
                (merchant == null || it.merchantName.contains(merchant, ignoreCase = true)) &&
                (category == null || it.category == category) &&
                (min == null || it.totalAmount >= min) &&
                (max == null || it.totalAmount <= max)
        }
        val limit = (arguments.int("limit") ?: 50).coerceIn(1, 500)
        val offset = (arguments.int("offset") ?: 0).coerceAtLeast(0)
        val page = filtered.drop(offset).take(limit)

        return buildJsonObject {
            put("total", filtered.size)
            put("items", buildJsonArray { page.forEach { add(it.toJson()) } })
        }
    }
}

/** `get_invoice_detail` — one invoice by id (with its line items), or null if missing / outside the grant. */
class GetInvoiceDetailTool(private val invoices: InvoiceRepository) : McpTool {
    override val name = "get_invoice_detail"
    override val description = "依發票 id 取得單張發票明細，含逐項品名／數量／金額。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { stringProp("invoice_id", "發票 id") }
        put("required", buildJsonArray { add("invoice_id") })
    }

    override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement {
        val id = arguments.string("invoice_id") ?: return JsonNull
        val invoice = invoices.getById(id) ?: return JsonNull
        if (listOf(invoice).applyConstraints(constraints).isEmpty()) return JsonNull
        val items = invoices.getItems(id)
        return buildJsonObject {
            invoice.toJson().forEach { (key, value) -> put(key, value) }
            put("items", buildJsonArray { items.forEach { add(it.toJson()) } })
        }
    }
}
