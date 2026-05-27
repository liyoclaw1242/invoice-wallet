package tw.invoicewallet.datasource.authz

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import tw.invoicewallet.core.database.repository.AuthGrantRepository
import tw.invoicewallet.core.model.AuthChannel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.QueryAuditLog

class AuthorizationEngineTest {

    private val now = Instant.parse("2026-02-01T00:00:00Z")

    @Test
    fun `denies an unknown grant`() = runTest {
        val engine = engine(FakeAuthGrantRepository())
        engine.authorize("nope", "list_invoices") shouldBe AuthDecision.Denied(DenyReason.UNKNOWN_GRANT)
    }

    @Test
    fun `denies a revoked grant`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(revokedAt = now)) }
        engine(repo).authorize(GRANT_ID, "list_invoices") shouldBe AuthDecision.Denied(DenyReason.REVOKED)
    }

    @Test
    fun `denies an expired grant`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(expiresAt = now.minusSeconds())) }
        engine(repo).authorize(GRANT_ID, "list_invoices") shouldBe AuthDecision.Denied(DenyReason.EXPIRED)
    }

    @Test
    fun `denies a tool outside the granted scopes`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(scopes = listOf("list_invoices"))) }
        engine(repo).authorize(GRANT_ID, "get_spending_summary") shouldBe AuthDecision.Denied(DenyReason.OUT_OF_SCOPE)
    }

    @Test
    fun `wildcard scope allows any tool`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(scopes = listOf("*"))) }
        engine(repo).authorize(GRANT_ID, "get_lottery_status").shouldBeInstanceOf<AuthDecision.Allowed>()
    }

    @Test
    fun `derives the date window from dateRangeDays`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(scopes = listOf("*"), dateRangeDays = 30)) }
        val decision = engine(repo).authorize(GRANT_ID, "list_invoices")
        decision.shouldBeInstanceOf<AuthDecision.Allowed>()
        decision.constraints.notBefore shouldBe LocalDate(2026, 1, 2)
    }

    @Test
    fun `passes excluded categories through to the constraints`() = runTest {
        val repo = FakeAuthGrantRepository().apply {
            put(grant(scopes = listOf("*"), excludedCategories = listOf("醫療", "成人")))
        }
        val decision = engine(repo).authorize(GRANT_ID, "list_invoices")
        decision.shouldBeInstanceOf<AuthDecision.Allowed>()
        decision.constraints.excludedCategories shouldBe listOf("醫療", "成人")
    }

    @Test
    fun `denies once the per-grant rate limit is hit`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(scopes = listOf("*"))) }
        val engine = engine(repo, rateLimit = 3)
        repeat(3) { engine.recordResult(GRANT_ID, "list_invoices", resultCount = 1) }
        engine.authorize(GRANT_ID, "list_invoices") shouldBe AuthDecision.Denied(DenyReason.RATE_LIMITED)
    }

    @Test
    fun `records an audit entry with the result count`() = runTest {
        val repo = FakeAuthGrantRepository().apply { put(grant(scopes = listOf("*"))) }
        engine(repo).recordResult(GRANT_ID, "search_invoices", resultCount = 7)
        repo.audits.single().let {
            it.toolName shouldBe "search_invoices"
            it.resultCount shouldBe 7
            it.grantId shouldBe GRANT_ID
        }
    }

    private fun engine(repo: AuthGrantRepository, rateLimit: Int = 60) = AuthorizationEngine(
        repository = repo,
        clock = object : Clock {
            override fun now(): Instant = now
        },
        timeZone = TimeZone.UTC,
        rateLimitPerMinute = rateLimit,
        idGenerator = { "audit-id" },
    )

    private fun Instant.minusSeconds(): Instant = Instant.fromEpochSeconds(epochSeconds - 1)

    private fun grant(
        scopes: List<String> = listOf("*"),
        excludedCategories: List<String>? = null,
        dateRangeDays: Int? = null,
        expiresAt: Instant? = null,
        revokedAt: Instant? = null,
    ) = AuthGrant(
        id = GRANT_ID,
        clientName = "Claude Desktop",
        channel = AuthChannel.LOCAL_MCP,
        scopes = scopes,
        excludedCategories = excludedCategories,
        dateRangeDays = dateRangeDays,
        grantedAt = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt = expiresAt,
        revokedAt = revokedAt,
    )

    private companion object {
        const val GRANT_ID = "local-mcp"
    }
}

private class FakeAuthGrantRepository : AuthGrantRepository {
    private val grants = mutableMapOf<String, AuthGrant>()
    val audits = mutableListOf<QueryAuditLog>()

    fun put(grant: AuthGrant) {
        grants[grant.id] = grant
    }

    override suspend fun grant(grant: AuthGrant) = put(grant)
    override suspend fun getById(id: String): AuthGrant? = grants[id]
    override fun observeActive(): Flow<List<AuthGrant>> = flowOf(grants.values.filter { it.revokedAt == null })
    override suspend fun revoke(id: String) {
        grants[id]?.let { grants[id] = it.copy(revokedAt = Instant.parse("2026-02-01T00:00:00Z")) }
    }
    override suspend fun recordAudit(log: QueryAuditLog) {
        audits += log
    }
    override suspend fun auditCountSince(grantId: String, since: Instant): Int =
        audits.count { it.grantId == grantId && it.executedAt >= since }
}
