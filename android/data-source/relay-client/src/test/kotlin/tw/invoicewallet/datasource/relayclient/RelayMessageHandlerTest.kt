package tw.invoicewallet.datasource.relayclient

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import tw.invoicewallet.datasource.authz.AuthDecision
import tw.invoicewallet.datasource.authz.QueryConstraints
import tw.invoicewallet.datasource.mcpserver.McpDispatcher
import tw.invoicewallet.datasource.mcpserver.McpTool

class RelayMessageHandlerTest {

    private val json = Json

    private fun handler() = RelayMessageHandler(
        McpDispatcher(
            tools = listOf(StubTool),
            authorize = { AuthDecision.Allowed("relay", QueryConstraints()) },
        ),
    )

    @Test
    fun `wraps a tools-call request as an mcp_response envelope`() = runTest {
        val incoming = buildJsonObject {
            put("type", "mcp_request")
            put("request_id", "req-1")
            put(
                "payload",
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", "tools/call")
                    put("id", 1)
                    put("params", buildJsonObject { put("name", "stub_tool") })
                },
            )
        }.toString()

        val out = handler().handle(incoming)!!
        val obj = json.parseToJsonElement(out).jsonObject
        obj["type"]!!.jsonPrimitive.content shouldBe "mcp_response"
        obj["request_id"]!!.jsonPrimitive.content shouldBe "req-1"
        // payload is the dispatcher's JSON-RPC response, wrapping the tool's text content
        out shouldContain "stub-ok"
    }

    @Test
    fun `ignores non-request envelopes`() = runTest {
        val incoming = buildJsonObject {
            put("type", "mcp_response")
            put("request_id", "x")
        }.toString()
        handler().handle(incoming) shouldBe null
    }

    private object StubTool : McpTool {
        override val name = "stub_tool"
        override val description = "stub"
        override val inputSchema: JsonObject = buildJsonObject { put("type", "object") }
        override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement =
            buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("id", "stub-ok") }) }) }
    }
}
