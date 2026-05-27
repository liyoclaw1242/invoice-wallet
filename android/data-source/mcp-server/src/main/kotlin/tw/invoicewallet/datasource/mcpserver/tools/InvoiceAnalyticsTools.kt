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
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.datasource.authz.QueryConstraints
import tw.invoicewallet.datasource.mcpserver.McpTool

/** `get_spending_summary` — totals grouped by category / merchant / month / weekday. */
class GetSpendingSummaryTool(private val invoices: InvoiceRepository) : McpTool {
    override val name = "get_spending_summary"
    override val description = "彙總花費，可依 category / merchant / month / weekday 分組。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            stringProp("date_from", "起始日期 (ISO，含)")
            stringProp("date_to", "結束日期 (ISO，含)")
            stringProp("group_by", "分組維度：category | merchant | month | weekday (預設 category)")
        }
    }

    override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement {
        val from = arguments.date("date_from")
        val to = arguments.date("date_to")
        val groupBy = arguments.string("group_by") ?: "category"

        val base = invoices.observeAll().first().applyConstraints(constraints)
            .filter { (from == null || it.issueDate >= from) && (to == null || it.issueDate <= to) }

        val keyOf: (Invoice) -> String = when (groupBy) {
            "merchant" -> { inv -> inv.merchantName.ifBlank { "(未填商店)" } }
            "month" -> { inv -> inv.issueDate.toString().take(7) }
            "weekday" -> { inv -> inv.issueDate.dayOfWeek.name }
            else -> { inv -> inv.category ?: "(未分類)" }
        }

        val groups = base.groupBy(keyOf)
            .map { (key, list) -> Triple(key, list.sumOf { it.totalAmount }, list.size) }
            .sortedByDescending { it.second }

        return buildJsonObject {
            put("group_by", if (groupBy in setOf("merchant", "month", "weekday")) groupBy else "category")
            put("invoice_count", base.size)
            put("total_amount", base.sumOf { it.totalAmount })
            put(
                "groups",
                buildJsonArray {
                    groups.forEach { (key, total, count) ->
                        add(
                            buildJsonObject {
                                put("key", key)
                                put("total_amount", total)
                                put("count", count)
                            },
                        )
                    }
                },
            )
        }
    }
}

/** `get_lottery_status` — lottery standing across invoices, optionally for one period. */
class GetLotteryStatusTool(private val invoices: InvoiceRepository) : McpTool {
    override val name = "get_lottery_status"
    override val description = "回報發票對獎狀態彙總與中獎清單，可指定期別 (yyymm)。"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            stringProp("period", "期別，如 11502 (選填)")
        }
    }

    override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement {
        val period = arguments.string("period")
        val base = invoices.observeAll().first().applyConstraints(constraints)
            .filter { period == null || it.issuePeriod == period }

        val statusCounts = base.groupingBy { it.lotteryStatus.name }.eachCount()
        val winners = base.filter { (it.lotteryPrize ?: 0) > 0 }

        return buildJsonObject {
            period?.let { put("period", it) }
            put("invoice_count", base.size)
            putJsonObject("status_counts") {
                LotteryStatus.entries.forEach { put(it.name, statusCounts[it.name] ?: 0) }
            }
            put("total_winnings", winners.sumOf { it.lotteryPrize ?: 0 })
            put(
                "winners",
                buildJsonArray {
                    winners.forEach {
                        add(
                            buildJsonObject {
                                put("invoice_number", it.invoiceNumber)
                                put("prize", it.lotteryPrize ?: 0)
                                put("status", it.lotteryStatus.name)
                            },
                        )
                    }
                },
            )
        }
    }
}
