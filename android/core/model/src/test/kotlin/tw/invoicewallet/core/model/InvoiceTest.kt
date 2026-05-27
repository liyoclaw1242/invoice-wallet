package tw.invoicewallet.core.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class InvoiceTest {

    private val sample = Invoice(
        id = "019589f0-0000-7000-8000-000000000001",
        invoiceNumber = "AB12345678",
        issueDate = LocalDate(2026, 1, 15),
        issuePeriod = "11502",
        merchantName = "全聯福利中心",
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 105,
        taxAmount = 5,
        randomCode = "1234",
        category = null,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = listOf("食物"),
        createdAt = Instant.parse("2026-01-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-01-15T10:00:00Z"),
        deletedAt = null,
    )

    @Test
    fun `formattedNumber inserts a hyphen after the two-letter track`() {
        sample.formattedNumber() shouldBe "AB-12345678"
    }

    @Test
    fun `copy changes only the targeted field and preserves value equality`() {
        val edited = sample.copy(userNote = "午餐")

        edited.userNote shouldBe "午餐"
        edited.copy(userNote = null) shouldBe sample
    }

    @Test
    fun `serializes and deserializes back to an equal value`() {
        val encoded = Json.encodeToString(Invoice.serializer(), sample)
        val decoded = Json.decodeFromString(Invoice.serializer(), encoded)

        decoded shouldBe sample
    }
}
