package tw.invoicewallet.feature.lottery

import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.InvoiceSource
import tw.invoicewallet.core.model.LotteryNumber
import tw.invoicewallet.core.model.LotteryStatus

class LotteryMatcherTest {

    private val numbers = LotteryNumber(
        period = PERIOD,
        specialPrize = "12345678",
        grandPrize = "87654321",
        firstPrize = listOf("11223344", "55667788", "99001122"),
        additionalSixth = listOf("789", "456"),
        fetchedAt = Instant.parse("2026-05-25T00:00:00Z"),
    )

    private fun result(eightDigits: String, period: String = PERIOD) =
        LotteryMatcher.match(invoice("XX$eightDigits", period), numbers)

    @Test fun `full 8 digits matching the special prize wins 特別獎`() {
        result("12345678") shouldBe LotteryResult.Won(LotteryPrize.SPECIAL)
    }

    @Test fun `full 8 digits matching the grand prize wins 特獎`() {
        result("87654321") shouldBe LotteryResult.Won(LotteryPrize.GRAND)
    }

    @Test fun `full 8 digits matching a first prize wins 頭獎`() {
        result("11223344") shouldBe LotteryResult.Won(LotteryPrize.FIRST)
    }

    @Test fun `matching any of several first prizes wins 頭獎`() {
        result("55667788") shouldBe LotteryResult.Won(LotteryPrize.FIRST)
        result("99001122") shouldBe LotteryResult.Won(LotteryPrize.FIRST)
    }

    @Test fun `matching the last 7 digits of a first prize wins 二獎`() {
        result("21223344") shouldBe LotteryResult.Won(LotteryPrize.SECOND)
    }

    @Test fun `matching the last 6 digits wins 三獎`() {
        result("00223344") shouldBe LotteryResult.Won(LotteryPrize.THIRD)
    }

    @Test fun `matching the last 5 digits wins 四獎`() {
        result("00023344") shouldBe LotteryResult.Won(LotteryPrize.FOURTH)
    }

    @Test fun `matching the last 4 digits wins 五獎`() {
        result("00003344") shouldBe LotteryResult.Won(LotteryPrize.FIFTH)
    }

    @Test fun `matching the last 3 digits of a first prize wins 六獎`() {
        result("00000344") shouldBe LotteryResult.Won(LotteryPrize.SIXTH)
    }

    @Test fun `matching an additional sixth prize (last 3) wins 六獎`() {
        result("00000789") shouldBe LotteryResult.Won(LotteryPrize.SIXTH)
    }

    @Test fun `the highest matching prize is returned`() {
        // ends in 344 (六獎) but is a full first-prize match → 頭獎 wins
        result("11223344") shouldBe LotteryResult.Won(LotteryPrize.FIRST)
    }

    @Test fun `no matching digits yields NoPrize`() {
        result("00000000") shouldBe LotteryResult.NoPrize
    }

    @Test fun `a different period is NotApplicable`() {
        result("12345678", period = "11402") shouldBe LotteryResult.NotApplicable
    }

    @Test fun `a non 8-digit invoice number is NotApplicable`() {
        LotteryMatcher.match(invoice("AB123", PERIOD), numbers) shouldBe LotteryResult.NotApplicable
    }

    @Test fun `prize amounts follow the official schedule`() {
        LotteryPrize.SPECIAL.amountTwd shouldBe 10_000_000
        LotteryPrize.GRAND.amountTwd shouldBe 2_000_000
        LotteryPrize.FIRST.amountTwd shouldBe 200_000
        LotteryPrize.SIXTH.amountTwd shouldBe 200
    }

    private fun invoice(number: String, period: String) = Invoice(
        id = number,
        invoiceNumber = number,
        issueDate = LocalDate(2026, 4, 15),
        issuePeriod = period,
        merchantName = "商店",
        merchantTaxId = "12345678",
        buyerTaxId = null,
        carrierIdEncrypted = null,
        totalAmount = 100,
        taxAmount = 5,
        randomCode = "1234",
        category = null,
        source = InvoiceSource.QR_CODE,
        ocrConfidence = null,
        lotteryStatus = LotteryStatus.PENDING,
        lotteryPrize = null,
        imagePath = null,
        userNote = null,
        userTags = emptyList(),
        createdAt = Instant.parse("2026-04-15T10:00:00Z"),
        updatedAt = Instant.parse("2026-04-15T10:00:00Z"),
        deletedAt = null,
    )

    private companion object {
        const val PERIOD = "11502"
    }
}
