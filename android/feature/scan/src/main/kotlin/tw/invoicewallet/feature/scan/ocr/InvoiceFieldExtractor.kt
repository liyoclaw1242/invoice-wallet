package tw.invoicewallet.feature.scan.ocr

import kotlinx.datetime.LocalDate

/**
 * Heuristic extraction of invoice header fields from OCR text. Tolerant by design:
 * Taiwanese receipts vary wildly (傳統收銀機 / 電子發票證明聯 / 三聯式), dates appear
 * in 民國 or 西元 with assorted separators, and amounts sit near a keyword.
 */
class InvoiceFieldExtractor {

    fun extract(recognized: RecognizedText): ExtractedFields = ExtractedFields(
        invoiceNumber = extractInvoiceNumber(recognized.lines),
        issueDate = extractIssueDate(recognized.fullText),
        totalAmount = extractTotalAmount(recognized.lines),
    )

    private fun extractInvoiceNumber(lines: List<String>): ExtractedField<String>? {
        lines.forEach { line ->
            val match = INVOICE_NUMBER.find(line) ?: return@forEach
            val track = match.groupValues[1]
            val number = match.groupValues[2]
            // A clean "AB-12345678" / "AB 12345678" reads more reliably than a fused run.
            val hadSeparator = match.value.length > track.length + number.length
            return ExtractedField("$track$number", if (hadSeparator) 0.95f else 0.75f)
        }
        return null
    }

    private fun extractIssueDate(text: String): ExtractedField<LocalDate>? {
        GREGORIAN.find(text)?.let { m ->
            buildDate(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3])?.let {
                return ExtractedField(it, 0.9f)
            }
        }
        ROC_CJK.find(text)?.let { m ->
            buildDate(m.groupValues[1].toInt() + ROC_OFFSET, m.groupValues[2], m.groupValues[3])?.let {
                return ExtractedField(it, 0.9f)
            }
        }
        ROC_SEPARATED.find(text)?.let { m ->
            buildDate(m.groupValues[1].toInt() + ROC_OFFSET, m.groupValues[2], m.groupValues[3])?.let {
                return ExtractedField(it, 0.85f)
            }
        }
        return null
    }

    private fun extractTotalAmount(lines: List<String>): ExtractedField<Int>? {
        TOTAL_KEYWORDS.forEach { (keyword, confidence) ->
            lines.forEach { line ->
                if (line.contains(keyword)) {
                    largestNumber(line)?.let { return ExtractedField(it, confidence) }
                }
            }
        }
        return null
    }

    private fun buildDate(year: Int, month: String, day: String): LocalDate? =
        runCatching { LocalDate(year, month.toInt(), day.toInt()) }.getOrNull()

    private fun largestNumber(line: String): Int? = NUMBER.findAll(line)
        .mapNotNull { it.value.replace(",", "").toIntOrNull() }
        .maxOrNull()

    private companion object {
        const val ROC_OFFSET = 1911

        val INVOICE_NUMBER = Regex("""([A-Z]{2})[-\s]?(\d{8})""")

        // 西元 first (4-digit year 20xx) so it wins over a 民國 sub-match.
        val GREGORIAN = Regex("""(20\d{2})[/.\-](\d{1,2})[/.\-](\d{1,2})""")
        val ROC_CJK = Regex("""(\d{2,3})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
        val ROC_SEPARATED = Regex("""(\d{2,3})[/.\-](\d{1,2})[/.\-](\d{1,2})""")

        val NUMBER = Regex("""[\d,]+""")

        // Keyword → confidence, in priority order (總計 beats 小計-style 合計).
        val TOTAL_KEYWORDS = listOf(
            "總計" to 0.9f,
            "總金額" to 0.9f,
            "應收" to 0.85f,
            "合計" to 0.78f,
        )
    }
}
