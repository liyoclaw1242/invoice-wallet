package tw.invoicewallet.feature.export

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus

/**
 * Serialises invoices to JSON or CSV and back, losslessly. Pure logic — the caller
 * (a ViewModel) streams the result to a user-chosen location via the Storage Access
 * Framework. JSON is the canonical archive format; CSV targets spreadsheets but is
 * still round-trip-safe for the data this app produces (RFC 4180 quoting).
 *
 * "資料屬於使用者" — the user can take their whole wallet out at any time.
 */
object InvoiceExporter {

    fun export(invoices: List<Invoice>, format: ExportFormat): String = when (format) {
        ExportFormat.JSON -> InvoiceJsonFormat.encode(invoices)
        ExportFormat.CSV -> InvoiceCsvFormat.encode(invoices)
    }

    fun import(text: String, format: ExportFormat): List<Invoice> = when (format) {
        ExportFormat.JSON -> InvoiceJsonFormat.decode(text)
        ExportFormat.CSV -> InvoiceCsvFormat.decode(text)
    }
}

private val invoiceListSerializer = ListSerializer(Invoice.serializer())

internal object InvoiceJsonFormat {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(invoices: List<Invoice>): String = json.encodeToString(invoiceListSerializer, invoices)

    fun decode(text: String): List<Invoice> = json.decodeFromString(invoiceListSerializer, text)
}

internal object InvoiceCsvFormat {
    private val tagsSerializer = ListSerializer(String.serializer())
    private val tagsJson = Json

    private val header = listOf(
        "id", "invoiceNumber", "issueDate", "issuePeriod", "merchantName", "merchantTaxId",
        "buyerTaxId", "carrierIdEncrypted", "totalAmount", "taxAmount", "currency", "randomCode",
        "category", "source", "ocrConfidence", "lotteryStatus", "lotteryPrize", "imagePath",
        "userNote", "userTags", "createdAt", "updatedAt", "deletedAt",
    )

    fun encode(invoices: List<Invoice>): String {
        val rows = ArrayList<String>(invoices.size + 1)
        rows.add(header.joinToString(",") { escape(it) })
        invoices.forEach { rows.add(row(it).joinToString(",") { cell -> escape(cell) }) }
        return rows.joinToString("\n")
    }

    fun decode(text: String): List<Invoice> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        return rows.drop(1).map { invoiceFrom(it) }
    }

    private fun row(i: Invoice): List<String> = listOf(
        i.id,
        i.invoiceNumber,
        i.issueDate.toString(),
        i.issuePeriod,
        i.merchantName,
        i.merchantTaxId.orEmpty(),
        i.buyerTaxId.orEmpty(),
        i.carrierIdEncrypted.orEmpty(),
        i.totalAmount.toString(),
        i.taxAmount.toString(),
        i.currency,
        i.randomCode,
        i.category.orEmpty(),
        i.source.name,
        i.ocrConfidence?.toString().orEmpty(),
        i.lotteryStatus.name,
        i.lotteryPrize?.toString().orEmpty(),
        i.imagePath.orEmpty(),
        i.userNote.orEmpty(),
        tagsJson.encodeToString(tagsSerializer, i.userTags),
        i.createdAt.toString(),
        i.updatedAt.toString(),
        i.deletedAt?.toString().orEmpty(),
    )

    private fun invoiceFrom(c: List<String>): Invoice = Invoice(
        id = c[0],
        invoiceNumber = c[1],
        issueDate = LocalDate.parse(c[2]),
        issuePeriod = c[3],
        merchantName = c[4],
        merchantTaxId = c[5].nullIfEmpty(),
        buyerTaxId = c[6].nullIfEmpty(),
        carrierIdEncrypted = c[7].nullIfEmpty(),
        totalAmount = c[8].toInt(),
        taxAmount = c[9].toInt(),
        currency = c[10],
        randomCode = c[11],
        category = c[12].nullIfEmpty(),
        source = InvoiceSource.valueOf(c[13]),
        ocrConfidence = c[14].nullIfEmpty()?.toFloat(),
        lotteryStatus = LotteryStatus.valueOf(c[15]),
        lotteryPrize = c[16].nullIfEmpty()?.toInt(),
        imagePath = c[17].nullIfEmpty(),
        userNote = c[18].nullIfEmpty(),
        userTags = tagsJson.decodeFromString(tagsSerializer, c[19]),
        createdAt = Instant.parse(c[20]),
        updatedAt = Instant.parse(c[21]),
        deletedAt = c[22].nullIfEmpty()?.let(Instant::parse),
    )

    private fun String.nullIfEmpty(): String? = ifEmpty { null }

    private fun escape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

    /** RFC 4180 reader: handles quoted fields containing commas, quotes and newlines. */
    private fun parse(text: String): List<List<String>> {
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
}
