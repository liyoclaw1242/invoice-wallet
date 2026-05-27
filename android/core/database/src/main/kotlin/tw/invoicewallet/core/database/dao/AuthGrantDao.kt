package tw.invoicewallet.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import tw.invoicewallet.core.database.entity.AuthGrantEntity

@Dao
interface AuthGrantDao {
    @Upsert
    suspend fun upsert(grant: AuthGrantEntity)

    @Query("SELECT * FROM auth_grants WHERE id = :id")
    suspend fun getById(id: String): AuthGrantEntity?

    /** Grants that have not been revoked, newest first. */
    @Query("SELECT * FROM auth_grants WHERE revoked_at IS NULL ORDER BY granted_at DESC")
    fun observeActive(): Flow<List<AuthGrantEntity>>

    @Query("UPDATE auth_grants SET revoked_at = :revokedAt WHERE id = :id")
    suspend fun revoke(id: String, revokedAt: Instant)
}
