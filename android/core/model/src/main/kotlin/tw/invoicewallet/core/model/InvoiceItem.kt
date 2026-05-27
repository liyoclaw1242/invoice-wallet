package tw.invoicewallet.core.model

import kotlinx.serialization.Serializable

/** A line item belonging to an [Invoice]. */
@Serializable
data class InvoiceItem(
    val id: String,
    val invoiceId: String,
    val name: String,
    val quantity: Double,
    val unitPrice: Int,
    val amount: Int,
    val category: String? = null,
    val sequence: Int,
)
