package tw.invoicewallet.datasource.relayclient

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

/**
 * Maintains one outbound WebSocket to the relay's `/ws`, authenticated by the
 * device_secret. Each incoming `mcp_request` is answered via [RelayMessageHandler].
 * [run] blocks until the socket closes; the manager handles reconnection.
 */
class RelayConnection(private val client: HttpClient, private val handler: RelayMessageHandler) {

    suspend fun run(relayUrl: String, deviceSecret: String) {
        client.webSocket(
            urlString = toWebSocketUrl(relayUrl),
            request = { header(HttpHeaders.Authorization, "Bearer $deviceSecret") },
        ) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    handler.handle(frame.readText())?.let { send(Frame.Text(it)) }
                }
            }
        }
    }

    private fun toWebSocketUrl(relayUrl: String): String {
        val base = relayUrl.trimEnd('/')
        val ws = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> base
        }
        return "$ws/ws"
    }
}
