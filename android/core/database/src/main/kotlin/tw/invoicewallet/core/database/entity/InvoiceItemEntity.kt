package tw.invoicewallet.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoice_items",
    indices = [Index(value = ["invoice_id"])],
)
data class InvoiceItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "invoice_id") val invoiceId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "unit_price") val unitPrice: Int,
    @ColumnInfo(name = "amount") val amount: Int,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "sequence") val sequence: Int,
)
