package tw.invoicewallet.datasource.authz

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import tw.invoicewallet.core.database.repository.AuthGrantRepository
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.core.model.QueryAuditLog
import java.util.UUID

/** Filters derived from a grant that every tool must honour before returning data. */
data class QueryConstraints(val notBefore: LocalDate? = null, val excludedCategories: List<String> = emptyList())

enum class DenyReason { UNKNOWN_GRANT, REVOKED, EXPIRED, OUT_OF_SCOPE, RATE_LIMITED }

sealed interface AuthDecision {
    data class Allowed(val grantId: String, val constraints: QueryConstraints) : AuthDecision

    data class Denied(val reason: DenyReason) : AuthDecision
}

/**
 * Turns an [AuthGrant] into an allow/deny decision for a single tool call: checks the
 * grant is live and in scope, enforces a per-grant rate limit, and derives the query
 * constraints (date window + excluded categories). Every served call is audited.
 *
 * Scopes are tool names; `"*"` grants every tool. ARCHITECTURE §6 / §11.
 */
class AuthorizationEngine(
    private val repository: AuthGrantRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val rateLimitPerMinute: Int = 60,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun authorize(grantId: String, toolName: String): AuthDecision {
        val grant = repository.getById(grantId) ?: return AuthDecision.Denied(DenyReason.UNKNOWN_GRANT)
        val now = clock.now()
        if (grant.revokedAt != null) return AuthDecision.Denied(DenyReason.REVOKED)
        grant.expiresAt?.let { if (it <= now) return AuthDecision.Denied(DenyReason.EXPIRED) }
        if (!grant.allows(toolName)) return AuthDecision.Denied(DenyReason.OUT_OF_SCOPE)

        val windowStart = now.minus(1, DateTimeUnit.MINUTE)
        if (repository.auditCountSince(grant.id, windowStart) >= rateLimitPerMinute) {
            return AuthDecision.Denied(DenyReason.RATE_LIMITED)
        }

        val notBefore = grant.dateRangeDays?.let {
            now.minus(it, DateTimeUnit.DAY, timeZone).toLocalDateTime(timeZone).date
        }
        return AuthDecision.Allowed(grant.id, QueryConstraints(notBefore, grant.excludedCategories.orEmpty()))
    }

    /** Records that [toolName] ran for [grantId] returning [resultCount] rows. */
    suspend fun recordResult(grantId: String, toolName: String, resultCount: Int) {
        repository.recordAudit(
            QueryAuditLog(
                id = idGenerator(),
                grantId = grantId,
                toolName = toolName,
                resultCount = resultCount,
                executedAt = clock.now(),
            ),
        )
    }

    private fun AuthGrant.allows(tool: String): Boolean = scopes.contains("*") || scopes.contains(tool)
}
