package tw.invoicewallet.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A single Taiwanese e-invoice. Domain model — pure Kotlin, no persistence
 * concerns. The Room entity + mappers live in :core:database (ARCHITECTURE §5.1).
 */
@Serializable
data class Invoice(
    val id: String,
    val invoiceNumber: String,
    val issueDate: LocalDate,
    val issuePeriod: String,
    val merchantName: String,
    val merchantTaxId: String? = null,
    val buyerTaxId: String? = null,
    val carrierIdEncrypted: String? = null,
    val totalAmount: Int,
    val taxAmount: Int,
    val currency: String = "TWD",
    val randomCode: String,
    val category: String? = null,
    val source: InvoiceSource,
    val ocrConfidence: Float? = null,
    val lotteryStatus: LotteryStatus = LotteryStatus.PENDING,
    val lotteryPrize: Int? = null,
    val imagePath: String? = null,
    val userNote: String? = null,
    val userTags: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

/** Display form of the invoice number: `"AB12345678"` -> `"AB-12345678"`. */
fun Invoice.formattedNumber(): String = if (invoiceNumber.length == 10) {
    "${invoiceNumber.substring(0, 2)}-${invoiceNumber.substring(2)}"
} else {
    invoiceNumber
}
