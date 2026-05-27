package tw.invoicewallet.datasource.mcpserver.tools

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.datasource.authz.QueryConstraints

// --- argument parsing (tolerant: wrong/absent types read as null) ---

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

internal fun JsonObject.date(key: String): LocalDate? =
    string(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// --- shared projection / filtering ---

/** Applies the authorization window + category exclusions every tool must honour. */
internal fun List<Invoice>.applyConstraints(c: QueryConstraints): List<Invoice> {
    val notBefore = c.notBefore
    val excluded = c.excludedCategories
    return filter { invoice ->
        val category = invoice.category
        (notBefore == null || invoice.issueDate >= notBefore) &&
            (category == null || category !in excluded)
    }
}

/** Privacy-aware projection of an invoice for AI clients — omits the encrypted carrier id. */
internal fun Invoice.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("invoice_number", invoiceNumber)
    put("issue_date", issueDate.toString())
    put("issue_period", issuePeriod)
    put("merchant_name", merchantName)
    merchantTaxId?.let { put("merchant_tax_id", it) }
    put("total_amount", totalAmount)
    put("tax_amount", taxAmount)
    category?.let { put("category", it) }
    put("lottery_status", lotteryStatus.name)
    lotteryPrize?.let { put("lottery_prize", it) }
    userNote?.let { put("note", it) }
    if (userTags.isNotEmpty()) put("tags", buildJsonArray { userTags.forEach { add(it) } })
}

internal fun JsonObjectBuilder.stringProp(name: String, description: String) = putJsonObject(name) {
    put("type", "string")
    put("description", description)
}

internal fun JsonObjectBuilder.intProp(name: String, description: String) = putJsonObject(name) {
    put("type", "integer")
    put("description", description)
}
