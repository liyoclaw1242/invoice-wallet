package tw.invoicewallet.datasource.mcpserver.tools

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.datasource.authz.QueryConstraints
import tw.invoicewallet.datasource.mcpserver.McpTool

/** `search_invoices` — keyword search across merchant / number / note. */
class SearchInvoicesTool(private val invoices: InvoiceRepository) : McpTool {
    override val name = "search_invoices"
    override val description = "以關鍵字搜尋發票（商店名／發票號碼／備註）。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            stringProp("keyword", "搜尋關鍵字")
            intProp("limit", "回傳上限 (預設 50，最多 500)")
        }
        put("required", buildJsonArray { add("keyword") })
    }

    override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement {
        val keyword = arguments.string("keyword")
            ?: return buildJsonObject { put("items", buildJsonArray {}) }
        val limit = (arguments.int("limit") ?: 50).coerceIn(1, 500)
        val results = invoices.search(keyword, limit).applyConstraints(constraints)
        return buildJsonObject {
            put("items", buildJsonArray { results.forEach { add(it.toJson()) } })
        }
    }
}

/** `list_merchants` — distinct merchants with visit counts and total spend. */
class ListMerchantsTool(private val invoices: InvoiceRepository) : McpTool {
    override val name = "list_merchants"
    override val description = "列出去過的商店，附造訪次數與累計金額。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            intProp("min_visits", "最少造訪次數 (預設 1)")
            stringProp("date_from", "起始日期 (ISO，含)")
            intProp("limit", "回傳上限 (預設 50)")
        }
    }

    override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement {
        val minVisits = (arguments.int("min_visits") ?: 1).coerceAtLeast(1)
        val from = arguments.date("date_from")
        val limit = (arguments.int("limit") ?: 50).coerceIn(1, 500)

        val base = invoices.observeAll().first().applyConstraints(constraints)
            .filter { it.merchantName.isNotBlank() && (from == null || it.issueDate >= from) }

        val items = base.groupBy { it.merchantName }
            .map { (merchant, list) ->
                Triple(merchant, list, list.sumOf { it.totalAmount })
            }
            .filter { it.second.size >= minVisits }
            .sortedByDescending { it.second.size }
            .take(limit)

        return buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    items.forEach { (merchant, list, total) ->
                        add(
                            buildJsonObject {
                                put("merchant_name", merchant)
                                list.firstNotNullOfOrNull { it.merchantTaxId }?.let { put("merchant_tax_id", it) }
                                put("visit_count", list.size)
                                put("total_amount", total)
                            },
                        )
                    }
                },
            )
        }
    }
}
