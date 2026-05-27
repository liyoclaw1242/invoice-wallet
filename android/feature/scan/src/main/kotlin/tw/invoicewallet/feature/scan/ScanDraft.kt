package tw.invoicewallet.feature.scan

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryStatus
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

/** ROC tax period for a date: 民國年(3) + 期別(2, 雙月一期, Jan-Feb=01 … Nov-Dec=06). */
private fun LocalDate.toInvoicePeriod(): String {
    val rocYear = year - 1911
    val period = (monthNumber + 1) / 2
    return "%03d%02d".format(rocYear, period)
}
