package tw.invoicewallet.datasource.mcpserver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import tw.invoicewallet.datasource.authz.AuthDecision

/**
 * Stateless MCP/JSON-RPC 2.0 message handler. Works on raw request/response strings so
 * it can be unit-tested without an HTTP engine; the Ktor route is a thin adapter.
 *
 * Supports `initialize`, `tools/list`, `tools/call`, and ignores `notifications/` messages.
 * Every `tools/call` is authorized and audited via [authorize] / [onResult].
 */
class McpDispatcher(
    tools: List<McpTool>,
    private val authorize: suspend (toolName: String) -> AuthDecision,
    private val onResult: suspend (toolName: String, resultCount: Int) -> Unit = { _, _ -> },
    private val serverName: String = "invoice-wallet",
    private val serverVersion: String = "1.0.0",
    private val protocolVersion: String = "2024-11-05",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val toolsByName = tools.associateBy { it.name }

    /** Handles one request body, returning the response body (empty string for notifications). */
    suspend fun handle(requestBody: String): String {
        val root = try {
            json.parseToJsonElement(requestBody)
        } catch (_: Exception) {
            return errorResponse(JsonNull, PARSE_ERROR, "Parse error")
        }
        val obj = root as? JsonObject ?: return errorResponse(JsonNull, INVALID_REQUEST, "Invalid Request")
        val id = obj["id"] ?: JsonNull
        val method = obj.stringOrNull("method")
            ?: return errorResponse(id, INVALID_REQUEST, "Missing method")

        if (method.startsWith("notifications/")) return ""

        return when (method) {
            "initialize" -> successResponse(id, initializeResult())
            "tools/list" -> successResponse(id, toolsListResult())
            "tools/call" -> handleToolCall(id, obj["params"] as? JsonObject)
            else -> errorResponse(id, METHOD_NOT_FOUND, "Method not found: $method")
        }
    }

    private suspend fun handleToolCall(id: JsonElement, params: JsonObject?): String {
        val name = params?.stringOrNull("name")
            ?: return errorResponse(id, INVALID_PARAMS, "Missing tool name")
        val tool = toolsByName[name]
            ?: return errorResponse(id, INVALID_PARAMS, "Unknown tool: $name")
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())

        return when (val decision = authorize(name)) {
            is AuthDecision.Denied -> errorResponse(id, UNAUTHORIZED, "Denied: ${decision.reason.name}")
            is AuthDecision.Allowed -> {
                val result = tool.call(arguments, decision.constraints)
                onResult(name, result.resultCount())
                successResponse(id, toolCallResult(result))
            }
        }
    }

    private fun initializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", protocolVersion)
        putJsonObject("capabilities") { putJsonObject("tools") {} }
        putJsonObject("serverInfo") {
            put("name", serverName)
            put("version", serverVersion)
        }
    }

    private fun toolsListResult(): JsonObject = buildJsonObject {
        put(
            "tools",
            buildJsonArray {
                toolsByName.values.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchema", tool.inputSchema)
                        },
                    )
                }
            },
        )
    }

    /** Wraps a tool's structured result as an MCP text-content block (interoperable form). */
    private fun toolCallResult(result: JsonElement): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", json.encodeToString(JsonElement.serializer(), result))
                    },
                )
            },
        )
    }

    private fun successResponse(id: JsonElement, result: JsonElement): String = encode(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        },
    )

    private fun errorResponse(id: JsonElement, code: Int, message: String): String = encode(
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        },
    )

    private fun encode(obj: JsonObject): String = json.encodeToString(JsonElement.serializer(), obj)

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    /** A tool result shaped like `{items:[...]}` reports its row count; otherwise 1 (0 if null). */
    private fun JsonElement.resultCount(): Int {
        val items = (this as? JsonObject)?.get("items")
        return when {
            items is JsonArray -> items.size
            this is JsonNull -> 0
            else -> 1
        }
    }

    private companion object {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val UNAUTHORIZED = -32001
    }
}
