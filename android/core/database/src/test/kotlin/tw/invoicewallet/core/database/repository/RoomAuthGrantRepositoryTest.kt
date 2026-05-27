package tw.invoicewallet.core.database.repository

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import tw.invoicewallet.core.model.QueryAuditLog

class RoomAuthGrantRepositoryTest {

    private val now = Instant.parse("2026-03-01T00:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }
    private val grantDao = FakeAuthGrantDao()
    private val auditDao = FakeQueryAuditLogDao()
    private val repository = RoomAuthGrantRepository(grantDao, auditDao, fixedClock)

    @Test
    fun `revoke stamps the clock's time and drops it from observeActive`() = runTest {
        repository.grant(authGrant(id = "grant-1"))

        repository.revoke("grant-1")

        repository.getById("grant-1")?.revokedAt shouldBe now
        repository.observeActive().first() shouldBe emptyList()
    }

    @Test
    fun `auditCountSince counts only entries at or after the threshold`() = runTest {
        repository.recordAudit(audit("old", Instant.parse("2026-01-01T00:00:00Z")))
        repository.recordAudit(audit("new", Instant.parse("2026-01-10T00:00:00Z")))

        repository.auditCountSince("grant-1", Instant.parse("2026-01-05T00:00:00Z")) shouldBe 1
    }

    private fun audit(id: String, executedAt: Instant) = QueryAuditLog(
        id = id,
        grantId = "grant-1",
        toolName = "list_invoices",
        resultCount = 3,
        executedAt = executedAt,
    )
}
