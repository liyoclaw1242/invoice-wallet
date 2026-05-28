package tw.invoicewallet.feature.settings.carrier

import kotlinx.datetime.LocalDate

/**
 * Parses 財政部「手機條碼載具」CSV exports (the file you download from
 * `einvoice.nat.gov.tw`'s carrier query page). Pure Kotlin — no Android types — so the
 * shape lives in unit tests.
 *
 * Format (UTF-8 with BOM):
 *   載具自訂名稱,發票日期(YYYYMMDD),發票號碼,發票金額,發票狀態,折讓,賣方統編,賣方名稱,賣方地址,
 *   買方統編,數量,單價,金額,品名
 *
 * One row per item; multi-item invoices repeat the header columns with the same
 * invoice number. The file ends with two free-text notice lines (without commas)
 * which we recognise by their column count and skip silently.
 */
object CarrierCsvParser {

    private const val EXPECTED_COLUMNS = 14

    fun parse(text: String): CarrierCsvParseResult {
        // Spelled \uFEFF (not the literal char) so source-level reformatters won't strip it.
        val cleaned = text.removePrefix("\uFEFF")
        val rows = readCsv(cleaned)
        if (rows.isEmpty()) return CarrierCsvParseResult(emptyList(), 0)

        val errors = mutableListOf<String>()
        var skipped = 0
        val byInvoice = LinkedHashMap<String, Builder>()

        // Row 0 is the header — we don't validate column names (財政部 has tweaked them
        // over the years); we trust the column order, which has been stable.
        rows.drop(1).forEachIndexed { idx, row ->
            // Trailing 兩行說明文字 don't have commas → treat short rows as non-data.
            if (row.size < EXPECTED_COLUMNS) {
                skipped++
                return@forEachIndexed
            }

            val invoiceNumber = row[2].trim()
            val status = row[4].trim()
            // Voided invoices show up too; the user shouldn't see them in their wallet.
            if (invoiceNumber.isEmpty() || status.contains("作廢")) {
                skipped++
                return@forEachIndexed
            }

            val date = parseYmd(row[1].trim())
            val total = row[3].trim().toIntOrNull()
            if (date == null || total == null) {
                errors += "第 ${idx + 2} 列：日期或金額無法解析"
                skipped++
                return@forEachIndexed
            }

            val builder = byInvoice.getOrPut(invoiceNumber) {
                Builder(
                    invoiceNumber = invoiceNumber,
                    issueDate = date,
                    sellerTaxId = row[6].trim(),
                    sellerName = row[7].trim(),
                    sellerAddress = row[8].trim(),
                    buyerTaxId = row[9].trim().takeIf { it.isNotBlank() },
                    totalAmount = total,
                    status = status,
                )
            }
            // Item is optional per row — some MOF exports list header-only rows when the
            // seller didn't itemise. Skip silently when name/price is missing.
            val name = row[13].trim()
            val qty = row[10].trim().toDoubleOrNull()
            val unitPrice = row[11].trim().toIntOrNull()
            val amount = row[12].trim().toIntOrNull()
            if (name.isNotEmpty() && qty != null && unitPrice != null) {
                builder.items += CarrierItemRow(name, qty, unitPrice, amount ?: (qty * unitPrice).toInt())
            }
        }

        return CarrierCsvParseResult(
            invoices = byInvoice.values.map { it.build() },
            skippedRows = skipped,
            errors = errors,
        )
    }

    private fun parseYmd(s: String): LocalDate? {
        if (s.length != 8) return null
        val y = s.substring(0, 4).toIntOrNull() ?: return null
        val m = s.substring(4, 6).toIntOrNull() ?: return null
        val d = s.substring(6, 8).toIntOrNull() ?: return null
        return runCatching { LocalDate(y, m, d) }.getOrNull()
    }

    private class Builder(
        val invoiceNumber: String,
        val issueDate: LocalDate,
        val sellerTaxId: String,
        val sellerName: String,
        val sellerAddress: String,
        val buyerTaxId: String?,
        val totalAmount: Int,
        val status: String,
        val items: MutableList<CarrierItemRow> = mutableListOf(),
    ) {
        fun build() = CarrierInvoiceRow(
            invoiceNumber = invoiceNumber,
            issueDate = issueDate,
            sellerTaxId = sellerTaxId,
            sellerName = sellerName,
            sellerAddress = sellerAddress,
            buyerTaxId = buyerTaxId,
            totalAmount = totalAmount,
            status = status,
            items = items.toList(),
        )
    }
}

/** One invoice as it came out of the MOF carrier CSV. */
data class CarrierInvoiceRow(
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val sellerTaxId: String,
    val sellerName: String,
    val sellerAddress: String,
    val buyerTaxId: String?,
    val totalAmount: Int,
    val status: String,
    val items: List<CarrierItemRow>,
)

/** One line item carried by a CSV row. */
data class CarrierItemRow(val name: String, val quantity: Double, val unitPrice: Int, val amount: Int)

/** Whole-file parse outcome. [skippedRows] counts rows we deliberately ignored
 *  (trailing notes, voided invoices); [errors] lists rows we couldn't parse. */
data class CarrierCsvParseResult(
    val invoices: List<CarrierInvoiceRow>,
    val skippedRows: Int,
    val errors: List<String> = emptyList(),
)

/** RFC 4180 reader: handles addresses or names that happen to contain commas. */
internal fun readCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            inQuotes -> when {
                ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                ch == '"' -> inQuotes = false
                else -> field.append(ch)
            }
            ch == '"' -> inQuotes = true
            ch == ',' -> {
                row.add(field.toString())
                field.clear()
            }
            ch == '\r' -> Unit
            ch == '\n' -> {
                row.add(field.toString())
                field.clear()
                rows.add(row)
                row = mutableListOf()
            }
            else -> field.append(ch)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row)
    }
    return rows
}
