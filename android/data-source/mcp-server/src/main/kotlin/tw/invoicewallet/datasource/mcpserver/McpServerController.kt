package tw.invoicewallet.datasource.mcpserver

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer

/**
 * Owns the lifecycle of the embedded CIO MCP server. Binds loopback (`127.0.0.1`) by
 * default — reachable from a PC only via `adb reverse tcp:7777 tcp:7777` — or `0.0.0.0`
 * in LAN mode. The dispatcher and token are read lazily so they stay current across
 * restarts and token regeneration.
 */
class McpServerController(
    private val dispatcherFactory: () -> McpDispatcher,
    private val tokenProvider: () -> String,
) {
    private var engine: ApplicationEngine? = null

    val isRunning: Boolean get() = engine != null

    fun start(port: Int = DEFAULT_PORT, host: String = LOOPBACK) {
        if (engine != null) return
        engine = embeddedServer(CIO, port = port, host = host) {
            mcpModule(dispatcherFactory(), tokenProvider)
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        engine = null
    }

    companion object {
        const val DEFAULT_PORT = 7777
        const val LOOPBACK = "127.0.0.1"
        const val ANY_HOST = "0.0.0.0"
    }
}
