package tw.invoicewallet.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Cached winning numbers for one lottery period (ARCHITECTURE §5.1). */
@Serializable
data class LotteryNumber(
    val period: String,
    val specialPrize: String,
    val grandPrize: String,
    val firstPrize: List<String>,
    val additionalSixth: List<String> = emptyList(),
    val fetchedAt: Instant,
)
