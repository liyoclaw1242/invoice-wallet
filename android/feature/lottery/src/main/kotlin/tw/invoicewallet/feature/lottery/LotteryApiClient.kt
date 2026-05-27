package tw.invoicewallet.feature.lottery

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tw.invoicewallet.core.model.LotteryNumber

/**
 * Downloads the MOF uniform-invoice winning numbers from the public RSS feed
 * (`invoice.etax.nat.gov.tw/invoice.xml`). Each item is one period, e.g.
 * `<title>115年 03~04月</title>` + `<description>特別獎：… 特獎：… 頭獎：…、…、…</description>`.
 */
class LotteryApiClient(
    private val client: HttpClient = HttpClient(OkHttp),
    private val feedUrl: String = "https://invoice.etax.nat.gov.tw/invoice.xml",
) {

    suspend fun fetchRecent(now: Instant = Clock.System.now()): List<LotteryNumber> =
        parse(client.get(feedUrl).bodyAsText(), now)

    fun parse(xml: String, fetchedAt: Instant): List<LotteryNumber> =
        ITEM.findAll(xml).mapNotNull { parseItem(it.groupValues[1], fetchedAt) }.toList()

    private fun parseItem(item: String, fetchedAt: Instant): LotteryNumber? {
        val title = CDATA_TITLE.find(item)?.groupValues?.get(1)?.trim() ?: return null
        val period = periodOf(title) ?: return null
        val description = CDATA_DESC.find(item)?.groupValues?.get(1) ?: return null

        var special: String? = null
        var grand: String? = null
        val firstPrize = mutableListOf<String>()
        val additionalSixth = mutableListOf<String>()

        description.split(P_TAG).map(String::trim).filter(String::isNotEmpty).forEach { segment ->
            val label = segment.substringBefore('：', "").substringBefore(':')
            val numbers = DIGITS.findAll(segment.substringAfter('：').substringAfter(':'))
                .map { it.value }.toList()
            when {
                label.startsWith("特別獎") -> special = numbers.firstOrNull { it.length == 8 }
                label.startsWith("特獎") -> grand = numbers.firstOrNull { it.length == 8 }
                label.startsWith("頭獎") -> firstPrize += numbers.filter { it.length == 8 }
                label.contains("增開") -> additionalSixth += numbers.filter { it.length == 3 }
            }
        }

        val resolvedSpecial = special ?: return null
        val resolvedGrand = grand ?: return null
        if (firstPrize.isEmpty()) return null

        return LotteryNumber(
            period = period,
            specialPrize = resolvedSpecial,
            grandPrize = resolvedGrand,
            firstPrize = firstPrize,
            additionalSixth = additionalSixth,
            fetchedAt = fetchedAt,
        )
    }

    /** "115年 03~04月" -> ROC year + bimonthly period number, e.g. "11502". */
    private fun periodOf(title: String): String? {
        val match = PERIOD_TITLE.find(title) ?: return null
        val rocYear = match.groupValues[1].toIntOrNull() ?: return null
        val startMonth = match.groupValues[2].toIntOrNull() ?: return null
        val periodNumber = (startMonth + 1) / 2
        return "%03d%02d".format(rocYear, periodNumber)
    }

    private companion object {
        val ITEM = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
        val CDATA_TITLE = Regex("""<title><!\[CDATA\[(.*?)]]></title>""", RegexOption.DOT_MATCHES_ALL)
        val CDATA_DESC = Regex("""<description><!\[CDATA\[(.*?)]]></description>""", RegexOption.DOT_MATCHES_ALL)
        val PERIOD_TITLE = Regex("""(\d{2,3})\s*年\s*(\d{1,2})\s*[~～－-]\s*(\d{1,2})\s*月""")
        val P_TAG = Regex("""</?p>""")
        val DIGITS = Regex("""\d+""")
    }
}
