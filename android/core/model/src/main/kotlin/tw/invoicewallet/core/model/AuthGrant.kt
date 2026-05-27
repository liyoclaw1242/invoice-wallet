package tw.invoicewallet.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Record of access granted to an external AI client (ARCHITECTURE §5.1). */
@Serializable
data class AuthGrant(
    val id: String,
    val clientName: String,
    val channel: AuthChannel,
    val scopes: List<String> = emptyList(),
    val excludedCategories: List<String>? = null,
    val dateRangeDays: Int? = null,
    val grantedAt: Instant,
    val expiresAt: Instant? = null,
    val revokedAt: Instant? = null,
)
