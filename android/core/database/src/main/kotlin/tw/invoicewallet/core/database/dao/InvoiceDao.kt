package tw.invoicewallet.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.database.entity.InvoiceEntity

@Dao
interface InvoiceDao {
    /** Strict insert — aborts (throws) on a duplicate `invoice_number`. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(invoice: InvoiceEntity)

    /** Insert-or-update by primary key. */
    @Upsert
    suspend fun upsert(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE invoice_number = :invoiceNumber")
    suspend fun findByInvoiceNumber(invoiceNumber: String): InvoiceEntity?

    /** Live list of non-deleted invoices, newest issue date first. */
    @Query("SELECT * FROM invoices WHERE deleted_at IS NULL ORDER BY issue_date DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query(
        "SELECT * FROM invoices " +
            "WHERE deleted_at IS NULL AND issue_date BETWEEN :from AND :to " +
            "ORDER BY issue_date DESC",
    )
    fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<InvoiceEntity>>

    @Query(
        "SELECT * FROM invoices " +
            "WHERE deleted_at IS NULL AND merchant_tax_id = :merchantTaxId " +
            "ORDER BY issue_date DESC",
    )
    suspend fun findByMerchant(merchantTaxId: String): List<InvoiceEntity>

    @Query(
        "SELECT * FROM invoices " +
            "WHERE deleted_at IS NULL AND (" +
            "merchant_name LIKE '%' || :keyword || '%' OR " +
            "user_note LIKE '%' || :keyword || '%') " +
            "ORDER BY issue_date DESC LIMIT :limit",
    )
    suspend fun search(keyword: String, limit: Int): List<InvoiceEntity>

    /** Soft delete: stamp `deleted_at` so default queries exclude the row. */
    @Query("UPDATE invoices SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Instant)
}
