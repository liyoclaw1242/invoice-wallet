package tw.invoicewallet.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "query_audit_log",
    indices = [Index(value = ["grant_id"])],
)
data class QueryAuditLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "grant_id") val grantId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "result_count") val resultCount: Int,
    @ColumnInfo(name = "executed_at") val executedAt: Instant,
)
