package tw.invoicewallet.feature.lottery

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class LotteryApiClientTest {

    private val fetchedAt = Instant.parse("2026-05-27T00:00:00Z")
    private val client = LotteryApiClient(HttpClient(MockEngine { respond("") }))

    @Test
    fun `parses the latest period into a LotteryNumber`() {
        val latest = client.parse(SAMPLE_FEED, fetchedAt).first()

        latest.period shouldBe "11502"
        latest.specialPrize shouldBe "19531471"
        latest.grandPrize shouldBe "85941329"
        latest.firstPrize shouldBe listOf("07225810", "20231230", "83518781")
        latest.additionalSixth shouldBe emptyList()
        latest.fetchedAt shouldBe fetchedAt
    }

    @Test
    fun `parses every period item in the feed`() {
        client.parse(SAMPLE_FEED, fetchedAt).map { it.period } shouldBe listOf("11502", "11501")
    }

    @Test
    fun `parses an additional sixth prize when the feed includes it`() {
        val feed = rss(
            item(
                title = "114年 11~12月",
                description = "<p>特別獎：97023797</p><p>特獎：00507588</p>" +
                    "<p>頭獎：92377231、05232592、78125249</p><p>增開六獎：234、567</p>",
            ),
        )

        client.parse(feed, fetchedAt).first().additionalSixth shouldBe listOf("234", "567")
    }

    private companion object {
        val SAMPLE_FEED = rss(
            item(
                "115年 03~04月",
                "<p>特別獎：19531471</p><p>特獎：85941329</p><p>頭獎：07225810、20231230、83518781</p>",
            ),
            item(
                "115年 01~02月",
                "<p>特別獎：87510041</p><p>特獎：32220522</p><p>頭獎：21677046、44662410、31262513</p>",
            ),
        )

        fun item(title: String, description: String): String =
            """
            <item>
              <title><![CDATA[$title]]></title>
              <description><![CDATA[$description]]></description>
            </item>
            """.trimIndent()

        fun rss(vararg items: String): String = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>
               ${items.joinToString("\n")}
               </channel></rss>
        """.trimIndent()
    }
}
