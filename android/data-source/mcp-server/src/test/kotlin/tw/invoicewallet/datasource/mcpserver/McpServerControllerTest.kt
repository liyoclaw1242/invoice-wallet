package tw.invoicewallet.datasource.mcpserver

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tw.invoicewallet.datasource.authz.AuthDecision
import tw.invoicewallet.datasource.authz.QueryConstraints
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

class McpServerControllerTest {

    @Test
    fun `serves with the right token, rejects a wrong one, and stops`() {
        val port = freePort()
        val controller = McpServerController(
            dispatcherFactory = {
                McpDispatcher(
                    tools = emptyList(),
                    authorize = { AuthDecision.Allowed("local-mcp", QueryConstraints()) },
                )
            },
            tokenProvider = { "tok" },
        )

        controller.start(port)
        try {
            awaitReady(port)
            statusOf(port, token = "tok") shouldBe 200
            statusOf(port, token = "wrong") shouldBe 401
        } finally {
            controller.stop()
        }

        controller.isRunning shouldBe false
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun awaitReady(port: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            runCatching { Socket("127.0.0.1", port).close() }.onSuccess { return }
            Thread.sleep(50)
        }
        error("server did not bind to port $port in time")
    }

    private fun statusOf(port: Int, token: String): Int {
        val connection = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        return connection.run {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write("""{"jsonrpc":"2.0","method":"initialize","id":1}""".toByteArray()) }
            val code = responseCode
            disconnect()
            code
        }
    }
}
