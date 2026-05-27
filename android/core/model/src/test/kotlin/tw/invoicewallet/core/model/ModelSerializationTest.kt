package tw.invoicewallet.core.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ModelSerializationTest {

    @Test
    fun `LotteryNumber roundtrips with its prize lists intact`() {
        val number = LotteryNumber(
            period = "11502",
            specialPrize = "12345678",
            grandPrize = "87654321",
            firstPrize = listOf("11223344", "55667788"),
            additionalSixth = listOf("123", "456"),
            fetchedAt = Instant.parse("2026-03-25T00:00:00Z"),
        )

        val encoded = Json.encodeToString(LotteryNumber.serializer(), number)
        Json.decodeFromString(LotteryNumber.serializer(), encoded) shouldBe number
    }

    @Test
    fun `AuthGrant roundtrips with nullable fields preserved`() {
        val grant = AuthGrant(
            id = "grant-1",
            clientName = "Claude Mobile",
            channel = AuthChannel.RELAY,
            scopes = listOf("list_invoices", "get_spending_summary"),
            excludedCategories = null,
            dateRangeDays = 90,
            grantedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = null,
            revokedAt = null,
        )

        val encoded = Json.encodeToString(AuthGrant.serializer(), grant)
        Json.decodeFromString(AuthGrant.serializer(), encoded) shouldBe grant
    }
}
