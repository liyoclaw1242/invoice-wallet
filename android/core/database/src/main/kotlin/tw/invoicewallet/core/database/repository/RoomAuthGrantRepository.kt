package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tw.invoicewallet.core.database.dao.AuthGrantDao
import tw.invoicewallet.core.database.dao.QueryAuditLogDao
import tw.invoicewallet.core.database.mapper.asEntity
import tw.invoicewallet.core.database.mapper.asExternalModel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.QueryAuditLog

class RoomAuthGrantRepository(
    private val authGrantDao: AuthGrantDao,
    private val queryAuditLogDao: QueryAuditLogDao,
    private val clock: Clock = Clock.System,
) : AuthGrantRepository {

    override suspend fun grant(grant: AuthGrant) = authGrantDao.upsert(grant.asEntity())

    override suspend fun getById(id: String): AuthGrant? = authGrantDao.getById(id)?.asExternalModel()

    override fun observeActive(): Flow<List<AuthGrant>> =
        authGrantDao.observeActive().map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun revoke(id: String) = authGrantDao.revoke(id, clock.now())

    override suspend fun recordAudit(log: QueryAuditLog) = queryAuditLogDao.insert(log.asEntity())

    override suspend fun auditCountSince(grantId: String, since: Instant): Int =
        queryAuditLogDao.countSince(grantId, since)
}
