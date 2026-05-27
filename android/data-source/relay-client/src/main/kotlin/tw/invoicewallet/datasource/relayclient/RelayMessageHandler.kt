package tw.invoicewallet.datasource.relayclient

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tw.invoicewallet.datasource.mcpserver.McpDispatcher

/**
 * Turns a relay WebSocket envelope into a response envelope. An `mcp_request` carries a
 * raw JSON-RPC payload; we run it through the same [McpDispatcher] the local server uses
 * (so remote and local answers are identical) and wrap the result as `mcp_response`.
 * Non-request frames return null (nothing to send back).
 */
class RelayMessageHandler(private val dispatcher: McpDispatcher) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(incoming: String): String? {
        val obj = runCatching { json.parseToJsonElement(incoming) as? JsonObject }.getOrNull() ?: return null
        if (obj.string("type") != TYPE_REQUEST) return null
        val requestId = obj.string("request_id") ?: return null

        val payload = obj["payload"]?.toString() ?: "{}"
        val responseElement = json.parseToJsonElement(dispatcher.handle(payload))

        return json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("type", TYPE_RESPONSE)
                put("request_id", requestId)
                put("status", "ok")
                put("payload", responseElement)
            },
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private companion object {
        const val TYPE_REQUEST = "mcp_request"
        const val TYPE_RESPONSE = "mcp_response"
    }
}
