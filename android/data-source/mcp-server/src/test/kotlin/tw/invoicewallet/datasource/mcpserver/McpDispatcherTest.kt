package tw.invoicewallet.datasource.mcpserver

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import tw.invoicewallet.datasource.authz.AuthDecision
import tw.invoicewallet.datasource.authz.DenyReason
import tw.invoicewallet.datasource.authz.QueryConstraints

class McpDispatcherTest {

    private val json = Json

    @Test
    fun `initialize advertises protocol version and server info`() = runTest {
        val response = dispatcher().handle(rpc("initialize"))
        val result = response.result()
        result["protocolVersion"]!!.jsonPrimitive.content shouldBe "2024-11-05"
        result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "invoice-wallet"
    }

    @Test
    fun `tools list returns each tool with its schema`() = runTest {
        val response = dispatcher().handle(rpc("tools/list"))
        val tools = response.result()["tools"]!!.jsonArray
        tools.size shouldBe 1
        tools.first().jsonObject["name"]!!.jsonPrimitive.content shouldBe "stub_tool"
        tools.first().jsonObject["inputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content shouldBe "object"
    }

    @Test
    fun `tools call runs the tool and returns its json as text content`() = runTest {
        val response = dispatcher().handle(
            rpc("tools/call", params = buildJsonObject { put("name", "stub_tool") }),
        )
        val text = response.result()["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        text shouldContain "\"items\""
        text shouldContain "stub-1"
    }

    @Test
    fun `malformed json is a parse error`() = runTest {
        dispatcher().handle("{not json").errorCode() shouldBe -32700
    }

    @Test
    fun `unknown method is method-not-found`() = runTest {
        dispatcher().handle(rpc("does/not/exist")).errorCode() shouldBe -32601
    }

    @Test
    fun `calling an unknown tool is invalid params`() = runTest {
        dispatcher().handle(
            rpc("tools/call", params = buildJsonObject { put("name", "ghost") }),
        ).errorCode() shouldBe -32602
    }

    @Test
    fun `a denied call returns an authorization error`() = runTest {
        val response = dispatcher(decision = AuthDecision.Denied(DenyReason.RATE_LIMITED)).handle(
            rpc("tools/call", params = buildJsonObject { put("name", "stub_tool") }),
        )
        response.errorCode() shouldBe -32001
        response shouldContain "RATE_LIMITED"
    }

    @Test
    fun `notifications produce no response`() = runTest {
        dispatcher().handle(rpc("notifications/initialized")) shouldBe ""
    }

    @Test
    fun `served calls are audited with the result count`() = runTest {
        var audited: Pair<String, Int>? = null
        val dispatcher = dispatcher(onResult = { tool, count -> audited = tool to count })
        dispatcher.handle(rpc("tools/call", params = buildJsonObject { put("name", "stub_tool") }))
        audited shouldBe ("stub_tool" to 1)
    }

    // --- helpers ---

    private fun dispatcher(
        decision: AuthDecision = AuthDecision.Allowed("local-mcp", QueryConstraints()),
        onResult: suspend (String, Int) -> Unit = { _, _ -> },
    ) = McpDispatcher(
        tools = listOf(StubTool),
        authorize = { decision },
        onResult = onResult,
    )

    private fun rpc(method: String, params: JsonObject? = null, id: Int = 1): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", method)
        put("id", id)
        if (params != null) put("params", params)
    }.toString()

    private fun String.result(): JsonObject = json.parseToJsonElement(this).jsonObject["result"]!!.jsonObject

    private fun String.errorCode(): Int =
        json.parseToJsonElement(this).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content.toInt()

    private object StubTool : McpTool {
        override val name = "stub_tool"
        override val description = "A stub tool for tests"
        override val inputSchema: JsonObject = buildJsonObject { put("type", "object") }
        override suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement = buildJsonObject {
            put("items", buildJsonArray { add(buildJsonObject { put("id", "stub-1") }) })
        }
    }
}
