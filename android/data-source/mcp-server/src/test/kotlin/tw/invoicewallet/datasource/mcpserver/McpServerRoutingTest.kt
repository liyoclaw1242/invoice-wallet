package tw.invoicewallet.datasource.mcpserver

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import tw.invoicewallet.datasource.authz.AuthDecision
import tw.invoicewallet.datasource.authz.QueryConstraints

class McpServerRoutingTest {

    private val token = "s3cr3t-token"

    @Test
    fun `rejects a request without the bearer token`() = runTest {
        testApplication {
            application { mcpModule(dispatcher(), tokenProvider = { token }) }
            val response = client.post("/mcp") { setBody(initialize()) }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `rejects a wrong token`() = runTest {
        testApplication {
            application { mcpModule(dispatcher(), tokenProvider = { token }) }
            val response = client.post("/mcp") {
                header("Authorization", "Bearer wrong")
                setBody(initialize())
            }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `serves a valid request with the correct token`() = runTest {
        testApplication {
            application { mcpModule(dispatcher(), tokenProvider = { token }) }
            val response = client.post("/mcp") {
                header("Authorization", "Bearer $token")
                setBody(initialize())
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "invoice-wallet"
        }
    }

    private fun dispatcher() = McpDispatcher(
        tools = emptyList(),
        authorize = { AuthDecision.Allowed("local-mcp", QueryConstraints()) },
    )

    private fun initialize(): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "initialize")
        put("id", 1)
    }.toString()
}
