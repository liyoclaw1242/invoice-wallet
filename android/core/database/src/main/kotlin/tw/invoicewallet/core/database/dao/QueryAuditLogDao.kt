package tw.invoicewallet.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.datetime.Instant
import tw.invoicewallet.core.database.entity.QueryAuditLogEntity

@Dao
interface QueryAuditLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: QueryAuditLogEntity)

    @Query("SELECT * FROM query_audit_log WHERE grant_id = :grantId ORDER BY executed_at DESC")
    suspend fun getByGrant(grantId: String): List<QueryAuditLogEntity>

    /** Count of invocations for a grant since [since] — used for rate limiting (Iter 6). */
    @Query("SELECT COUNT(*) FROM query_audit_log WHERE grant_id = :grantId AND executed_at >= :since")
    suspend fun countSince(grantId: String, since: Instant): Int
}
