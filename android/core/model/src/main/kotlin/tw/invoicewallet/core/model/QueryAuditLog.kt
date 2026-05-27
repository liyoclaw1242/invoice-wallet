package tw.invoicewallet.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** One audited tool invocation against the wallet (ARCHITECTURE §5.1). */
@Serializable
data class QueryAuditLog(
    val id: String,
    val grantId: String,
    val toolName: String,
    val resultCount: Int,
    val executedAt: Instant,
)
