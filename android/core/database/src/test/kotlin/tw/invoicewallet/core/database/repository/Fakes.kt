package tw.invoicewallet.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import tw.invoicewallet.core.database.dao.AuthGrantDao
import tw.invoicewallet.core.database.dao.InvoiceDao
import tw.invoicewallet.core.database.dao.InvoiceItemDao
import tw.invoicewallet.core.database.dao.LotteryNumberDao
import tw.invoicewallet.core.database.dao.QueryAuditLogDao
import tw.invoicewallet.core.database.entity.AuthGrantEntity
import tw.invoicewallet.core.database.entity.InvoiceEntity
import tw.invoicewallet.core.database.entity.InvoiceItemEntity
import tw.invoicewallet.core.database.entity.LotteryNumberEntity
import tw.invoicewallet.core.database.entity.QueryAuditLogEntity

/** Hand-rolled in-memory fakes so repository logic can be unit-tested off-device. */

class FakeInvoiceDao : InvoiceDao {
    private val state = MutableStateFlow<List<InvoiceEntity>>(emptyList())

    override suspend fun insert(invoice: InvoiceEntity) {
        require(state.value.none { it.invoiceNumber == invoice.invoiceNumber }) {
            "UNIQUE constraint failed: invoices.invoice_number"
        }
        state.value = state.value + invoice
    }

    override suspend fun upsert(invoice: InvoiceEntity) {
        state.value = state.value.filterNot { it.id == invoice.id } + invoice
    }

    override suspend fun getById(id: String): InvoiceEntity? = state.value.find { it.id == id }

    override suspend fun findByInvoiceNumber(invoiceNumber: String): InvoiceEntity? =
        state.value.find { it.invoiceNumber == invoiceNumber }

    override fun observeAll(): Flow<List<InvoiceEntity>> =
        state.map { rows -> rows.filter { it.deletedAt == null }.sortedByDescending { it.issueDate } }

    override fun observeByDateRange(from: LocalDate, to: LocalDate): Flow<List<InvoiceEntity>> = state.map { rows ->
        rows.filter { it.deletedAt == null && it.issueDate >= from && it.issueDate <= to }
            .sortedByDescending { it.issueDate }
    }

    override suspend fun findByMerchant(merchantTaxId: String): List<InvoiceEntity> =
        state.value.filter { it.deletedAt == null && it.merchantTaxId == merchantTaxId }
            .sortedByDescending { it.issueDate }

    override suspend fun search(keyword: String, limit: Int): List<InvoiceEntity> = state.value
        .filter {
            it.deletedAt == null &&
                (it.merchantName.contains(keyword) || it.userNote?.contains(keyword) == true)
        }
        .take(limit)

    override suspend fun softDelete(id: String, deletedAt: Instant) {
        state.value = state.value.map {
            if (it.id == id) it.copy(deletedAt = deletedAt, updatedAt = deletedAt) else it
        }
    }
}

class FakeInvoiceItemDao : InvoiceItemDao {
    private val state = MutableStateFlow<List<InvoiceItemEntity>>(emptyList())

    override suspend fun insertAll(items: List<InvoiceItemEntity>) {
        state.value = state.value + items
    }

    override suspend fun getByInvoiceId(invoiceId: String): List<InvoiceItemEntity> =
        state.value.filter { it.invoiceId == invoiceId }.sortedBy { it.sequence }

    override fun observeByInvoiceId(invoiceId: String): Flow<List<InvoiceItemEntity>> =
        state.map { rows -> rows.filter { it.invoiceId == invoiceId }.sortedBy { it.sequence } }

    override suspend fun deleteByInvoiceId(invoiceId: String) {
        state.value = state.value.filterNot { it.invoiceId == invoiceId }
    }
}

class FakeLotteryNumberDao : LotteryNumberDao {
    private val state = MutableStateFlow<List<LotteryNumberEntity>>(emptyList())

    override suspend fun upsert(lotteryNumber: LotteryNumberEntity) {
        state.value = state.value.filterNot { it.period == lotteryNumber.period } + lotteryNumber
    }

    override suspend fun getByPeriod(period: String): LotteryNumberEntity? = state.value.find { it.period == period }

    override fun observeAll(): Flow<List<LotteryNumberEntity>> =
        state.map { rows -> rows.sortedByDescending { it.period } }

    override suspend fun getLatest(): LotteryNumberEntity? = state.value.maxByOrNull { it.period }
}

class FakeAuthGrantDao : AuthGrantDao {
    private val state = MutableStateFlow<List<AuthGrantEntity>>(emptyList())

    override suspend fun upsert(grant: AuthGrantEntity) {
        state.value = state.value.filterNot { it.id == grant.id } + grant
    }

    override suspend fun getById(id: String): AuthGrantEntity? = state.value.find { it.id == id }

    override fun observeActive(): Flow<List<AuthGrantEntity>> =
        state.map { rows -> rows.filter { it.revokedAt == null }.sortedByDescending { it.grantedAt } }

    override suspend fun revoke(id: String, revokedAt: Instant) {
        state.value = state.value.map { if (it.id == id) it.copy(revokedAt = revokedAt) else it }
    }
}

class FakeQueryAuditLogDao : QueryAuditLogDao {
    private val rows = mutableListOf<QueryAuditLogEntity>()

    override suspend fun insert(log: QueryAuditLogEntity) {
        rows += log
    }

    override suspend fun getByGrant(grantId: String): List<QueryAuditLogEntity> =
        rows.filter { it.grantId == grantId }.sortedByDescending { it.executedAt }

    override suspend fun countSince(grantId: String, since: Instant): Int =
        rows.count { it.grantId == grantId && it.executedAt >= since }
}
