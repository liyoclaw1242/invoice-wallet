package tw.invoicewallet.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Room persistence shape for an invoice (ARCHITECTURE §5.1). Enums stored as their name. */
@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["invoice_number"], unique = true),
        Index(value = ["issue_date"]),
        Index(value = ["merchant_tax_id"]),
    ],
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String,
    @ColumnInfo(name = "issue_date") val issueDate: LocalDate,
    @ColumnInfo(name = "issue_period") val issuePeriod: String,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "merchant_tax_id") val merchantTaxId: String?,
    @ColumnInfo(name = "buyer_tax_id") val buyerTaxId: String?,
    @ColumnInfo(name = "carrier_id_encrypted") val carrierIdEncrypted: String?,
    @ColumnInfo(name = "total_amount") val totalAmount: Int,
    @ColumnInfo(name = "tax_amount") val taxAmount: Int,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "random_code") val randomCode: String,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "ocr_confidence") val ocrConfidence: Float?,
    @ColumnInfo(name = "lottery_status") val lotteryStatus: String,
    @ColumnInfo(name = "lottery_prize") val lotteryPrize: Int?,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "user_note") val userNote: String?,
    @ColumnInfo(name = "user_tags") val userTags: List<String>,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
)
