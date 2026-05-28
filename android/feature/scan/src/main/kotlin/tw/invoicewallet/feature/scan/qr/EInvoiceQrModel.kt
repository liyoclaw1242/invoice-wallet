package tw.invoicewallet.feature.scan.qr

import kotlinx.datetime.LocalDate

/** Text encoding declared by the left QR's flag (0 = Big5, 1 = UTF-8). */
enum class InvoiceTextEncoding { UTF8, BIG5 }

/** One line item decoded from a QR code. Quantity is [Double] because gas-station
 *  invoices sell by litres (e.g. 30.32 L × NT$33.9). Unit price stays Int — receipt
 *  items round to the nearest dollar, the < NT$1 precision loss is acceptable. */
data class ParsedItem(val name: String, val quantity: Double, val unitPrice: Int)

/** The fixed + delimited fields decoded from the LEFT QR code. */
data class LeftCode(
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val randomCode: String,
    val untaxedAmount: Int,
    val totalAmount: Int,
    val buyerTaxId: String?,
    val sellerTaxId: String,
    val encoding: InvoiceTextEncoding,
    /** Items physically carried in the left code (often a subset of the invoice). */
    val items: List<ParsedItem>,
)

/** A fully parsed invoice: left-code header + all items (left + right merged). */
data class ParsedInvoice(
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val randomCode: String,
    val untaxedAmount: Int,
    val totalAmount: Int,
    val buyerTaxId: String?,
    val sellerTaxId: String,
    val encoding: InvoiceTextEncoding,
    val items: List<ParsedItem>,
)

/** Raised when a QR payload does not conform to the MOF e-invoice format. */
sealed class EInvoiceQrException(message: String) : Exception(message) {
    class MalformedLeftCode(message: String) : EInvoiceQrException(message)
    class MalformedRightCode(message: String) : EInvoiceQrException(message)
}
