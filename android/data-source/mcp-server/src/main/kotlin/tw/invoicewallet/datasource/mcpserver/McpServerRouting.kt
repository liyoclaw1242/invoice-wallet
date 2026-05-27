package tw.invoicewallet.datasource.mcpserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

/**
 * The MCP HTTP transport: a single `POST /mcp` endpoint guarded by a Bearer token.
 * The body is a JSON-RPC message handed to [dispatcher]; notifications (empty response)
 * answer 204. [tokenProvider] is read per request so the token can be regenerated live.
 */
fun Application.mcpModule(dispatcher: McpDispatcher, tokenProvider: () -> String) {
    routing {
        post("/mcp") {
            val provided = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            if (provided == null || !constantTimeEquals(provided, tokenProvider())) {
                call.respondText(
                    """{"error":"unauthorized"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized,
                )
                return@post
            }
            val response = dispatcher.handle(call.receiveText())
            if (response.isEmpty()) {
                call.respondText("", status = HttpStatusCode.NoContent)
            } else {
                call.respondText(response, ContentType.Application.Json)
            }
        }
    }
}

/** Length-independent constant-time comparison to avoid leaking the token via timing. */
private fun constantTimeEquals(a: String, b: String): Boolean {
    val x = a.encodeToByteArray()
    val y = b.encodeToByteArray()
    var diff = x.size xor y.size
    for (i in x.indices) {
        diff = diff or (x[i].toInt() xor y[getOrZeroIndex(y, i)].toInt())
    }
    return diff == 0
}

private fun getOrZeroIndex(arr: ByteArray, i: Int): Int = if (i < arr.size) i else 0
