package tw.invoicewallet.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tw.invoicewallet.core.database.entity.InvoiceEntity

/**
 * Minimal DAO so the database compiles and T1.2 can verify Room end-to-end.
 * Expanded into the full query surface in T1.3a.
 */
@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: String): InvoiceEntity?
}
