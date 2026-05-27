package tw.invoicewallet.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tw.invoicewallet.core.database.entity.InvoiceItemEntity

@Dao
interface InvoiceItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InvoiceItemEntity>)

    @Query("SELECT * FROM invoice_items WHERE invoice_id = :invoiceId ORDER BY sequence ASC")
    suspend fun getByInvoiceId(invoiceId: String): List<InvoiceItemEntity>

    @Query("SELECT * FROM invoice_items WHERE invoice_id = :invoiceId ORDER BY sequence ASC")
    fun observeByInvoiceId(invoiceId: String): Flow<List<InvoiceItemEntity>>

    @Query("DELETE FROM invoice_items WHERE invoice_id = :invoiceId")
    suspend fun deleteByInvoiceId(invoiceId: String)
}
