package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.QueryAuditLog

/** Access to AI-client authorization grants and their query audit log. */
interface AuthGrantRepository {
    suspend fun grant(grant: AuthGrant)

    suspend fun getById(id: String): AuthGrant?

    fun observeActive(): Flow<List<AuthGrant>>

    suspend fun revoke(id: String)

    suspend fun recordAudit(log: QueryAuditLog)

    suspend fun auditCountSince(grantId: String, since: Instant): Int
}
