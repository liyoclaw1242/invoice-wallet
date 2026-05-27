package tw.invoicewallet.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "auth_grants")
data class AuthGrantEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "channel") val channel: String,
    @ColumnInfo(name = "scopes") val scopes: List<String>,
    @ColumnInfo(name = "excluded_categories") val excludedCategories: List<String>?,
    @ColumnInfo(name = "date_range_days") val dateRangeDays: Int?,
    @ColumnInfo(name = "granted_at") val grantedAt: Instant,
    @ColumnInfo(name = "expires_at") val expiresAt: Instant?,
    @ColumnInfo(name = "revoked_at") val revokedAt: Instant?,
)
