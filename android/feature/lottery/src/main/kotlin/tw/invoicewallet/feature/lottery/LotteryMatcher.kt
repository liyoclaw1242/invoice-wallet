package tw.invoicewallet.feature.lottery

import tw.invoicewallet.core.model.Invoice
import tw.invoicewallet.core.model.LotteryNumber

/** Taiwan uniform-invoice lottery prizes and their amounts (NT$). */
enum class LotteryPrize(val amountTwd: Int) {
    SPECIAL(10_000_000), // 特別獎
    GRAND(2_000_000), // 特獎
    FIRST(200_000), // 頭獎
    SECOND(40_000), // 二獎
    THIRD(10_000), // 三獎
    FOURTH(4_000), // 四獎
    FIFTH(1_000), // 五獎
    SIXTH(200), // 六獎 / 增開六獎
}

sealed interface LotteryResult {
    data class Won(val prize: LotteryPrize) : LotteryResult
    data object NoPrize : LotteryResult

    /** Period mismatch or the invoice number isn't a usable 8-digit value. */
    data object NotApplicable : LotteryResult
}

/**
 * Matches an invoice's 8-digit number against a period's winning numbers, returning the
 * single highest prize. Rules: 特別獎/特獎 = full 8 digits; 頭獎 = full 8 of any 頭獎;
 * 二~六獎 = matching last 7/6/5/4/3 digits of any 頭獎; 增開六獎 = last 3 digits.
 */
object LotteryMatcher {

    fun match(invoice: Invoice, numbers: LotteryNumber): LotteryResult {
        if (invoice.issuePeriod != numbers.period) return LotteryResult.NotApplicable
        val digits = invoice.lotteryDigits() ?: return LotteryResult.NotApplicable

        val matches = buildList {
            if (digits == numbers.specialPrize) add(LotteryPrize.SPECIAL)
            if (digits == numbers.grandPrize) add(LotteryPrize.GRAND)
            numbers.firstPrize.forEach { first -> matchFirstPrize(digits, first)?.let(::add) }
            if (numbers.additionalSixth.any { it == digits.takeLast(3) }) add(LotteryPrize.SIXTH)
        }

        return matches.maxByOrNull { it.amountTwd }
            ?.let { LotteryResult.Won(it) }
            ?: LotteryResult.NoPrize
    }

    private fun matchFirstPrize(digits: String, firstPrize: String): LotteryPrize? = when {
        digits == firstPrize -> LotteryPrize.FIRST
        digits.takeLast(7) == firstPrize.takeLast(7) -> LotteryPrize.SECOND
        digits.takeLast(6) == firstPrize.takeLast(6) -> LotteryPrize.THIRD
        digits.takeLast(5) == firstPrize.takeLast(5) -> LotteryPrize.FOURTH
        digits.takeLast(4) == firstPrize.takeLast(4) -> LotteryPrize.FIFTH
        digits.takeLast(3) == firstPrize.takeLast(3) -> LotteryPrize.SIXTH
        else -> null
    }

    /** The 8-digit numeric part of the invoice number, or null if not usable. */
    private fun Invoice.lotteryDigits(): String? =
        invoiceNumber.takeLast(8).takeIf { it.length == 8 && it.all(Char::isDigit) }
}
