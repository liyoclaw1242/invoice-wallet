package tw.invoicewallet.feature.scan

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
import tw.invoicewallet.feature.scan.ocr.ExtractedFields
import tw.invoicewallet.feature.scan.qr.ParsedInvoice

/**
 * Builds an editable draft [Invoice] from a parsed QR code. Fields the QR cannot
 * provide (merchant name, category) are left blank for the user / OCR to fill.
 */
internal fun ParsedInvoice.toDraftInvoice(id: String, now: Instant): Invoice = Invoice(
    id = id,
    invoiceNumber = invoiceNumber,
    issueDate = issueDate,
    issuePeriod = issueDate.toInvoicePeriod(),
    merchantName = "",
    merchantTaxId = sellerTaxId,
    buyerTaxId = buyerTaxId,
    carrierIdEncrypted = null,
    totalAmount = totalAmount,
    taxAmount = (totalAmount - untaxedAmount).coerceAtLeast(0),
    randomCode = randomCode,
    category = null,
    source = InvoiceSource.QR_CODE,
    ocrConfidence = null,
    lotteryStatus = LotteryStatus.PENDING,
    lotteryPrize = null,
    imagePath = null,
    userNote = null,
    userTags = emptyList(),
    createdAt = now,
    updatedAt = now,
    deletedAt = null,
)

/**
 * Builds a draft from OCR-extracted fields (the fallback when no QR is present).
 * Returns null when nothing usable was recognised. Missing fields default to blank /
 * today / 0 for the user to complete; [Invoice.ocrConfidence] carries the weakest hit.
 */
internal fun ExtractedFields.toDraftInvoice(id: String, now: Instant): Invoice? {
    if (invoiceNumber == null && issueDate == null && totalAmount == null) return null
    val date = issueDate?.value ?: now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val confidences = listOfNotNull(invoiceNumber?.confidence, issueDate?.confidence, totalAmount?.confidence)
    return Invoice(
        id = id,
        invoiceNumber = invoiceNumber?.value ?: "",
        issueDate = date,
        issuePeriod = date.toInvoicePeriod(),
        merchantName = "",
        merchantTaxId = null,
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = totalAmount?.value ?: 0,
        taxAmount = 0,
        randomCode = "",
        category = null,
        source = InvoiceSource.OCR,
        ocrConfidence = confidences.minOrNull(),
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )
}

/** ROC tax period for a date: 民國年(3) + 期別(2, 雙月一期, Jan-Feb=01 … Nov-Dec=06). */
private fun LocalDate.toInvoicePeriod(): String {
    val rocYear = year - 1911
    val period = (monthNumber + 1) / 2
    return "%03d%02d".format(rocYear, period)
}
