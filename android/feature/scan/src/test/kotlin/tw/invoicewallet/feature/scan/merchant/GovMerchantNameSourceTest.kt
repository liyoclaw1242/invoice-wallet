package tw.invoicewallet.feature.scan.merchant

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GovMerchantNameSourceTest {

    private fun source(body: String, status: HttpStatusCode = HttpStatusCode.OK): GovMerchantNameSource {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return GovMerchantNameSource(HttpClient(engine), baseUrl = "https://example.test/api/show")
    }

    @Test
    fun `extracts 公司名稱 from a company-registration response`() = runTest {
        source("""{"data":{"統一編號":"90650686","公司名稱":"瑪可希維科技有限公司"}}""")
            .fetch("90650686") shouldBe "瑪可希維科技有限公司"
    }

    @Test
    fun `extracts 商業名稱 from a sole-proprietor response`() = runTest {
        source("""{"data":{"商業名稱":"士豐商號","組織類型":"獨資"}}""")
            .fetch("39858516") shouldBe "士豐商號"
    }

    @Test
    fun `extracts 營業人名稱 nested under 財政部`() = runTest {
        source("""{"data":{"財政部":{"營業人名稱":"七里香甕仔雞股份有限公司埔里營業所"}}}""")
            .fetch("82653921") shouldBe "七里香甕仔雞股份有限公司埔里營業所"
    }

    @Test
    fun `returns null when the registry has no record`() = runTest {
        source("""{"data":null}""").fetch("00000000") shouldBe null
    }

    @Test
    fun `returns null on an error response or non-JSON body`() = runTest {
        source("Not Found", status = HttpStatusCode.NotFound).fetch("123") shouldBe null
    }
}
