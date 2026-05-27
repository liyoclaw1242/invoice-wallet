package tw.invoicewallet.feature.scan.ocr

import kotlinx.datetime.LocalDate

/**
 * OCR output in a recognizer-neutral form (a list of recognized text lines).
 * The ML Kit `Text` → [RecognizedText] mapping happens in the camera layer (T2.3),
 * keeping the field-extraction logic pure + unit-testable.
 */
data class RecognizedText(val lines: List<String>) {
    val fullText: String get() = lines.joinToString("\n")

    companion object {
        fun of(vararg lines: String): RecognizedText = RecognizedText(lines.toList())
    }
}

/** An extracted value plus how confident the extractor is (0f..1f). */
data class ExtractedField<T>(val value: T, val confidence: Float)

/** Fields recovered from OCR text; any may be null when not found. */
data class ExtractedFields(
    val invoiceNumber: ExtractedField<String>? = null,
    val issueDate: ExtractedField<LocalDate>? = null,
    val totalAmount: ExtractedField<Int>? = null,
) {
    /** Fields whose confidence is below [threshold] — the UI highlights these. */
    fun lowConfidenceFields(threshold: Float = 0.8f): Set<InvoiceField> = buildSet {
        invoiceNumber?.let { if (it.confidence < threshold) add(InvoiceField.INVOICE_NUMBER) }
        issueDate?.let { if (it.confidence < threshold) add(InvoiceField.ISSUE_DATE) }
        totalAmount?.let { if (it.confidence < threshold) add(InvoiceField.TOTAL_AMOUNT) }
    }
}

enum class InvoiceField { INVOICE_NUMBER, ISSUE_DATE, TOTAL_AMOUNT }
