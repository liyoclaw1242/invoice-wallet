package tw.invoicewallet.datasource.relayclient

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.AuthGrantRepository
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.AuthChannel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.datasource.authz.AuthorizationEngine
import tw.invoicewallet.datasource.mcpserver.McpDispatcher
import tw.invoicewallet.datasource.mcpserver.walletTools
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the remote (RELAY) path: keeps an outbound WebSocket to the relay open while
 * enabled, answering forwarded MCP calls with the same dispatcher + tools as the local
 * server but under a RELAY grant (seeded on first start). Auto-reconnects on drop.
 */
@Singleton
class RelayManager @Inject constructor(
    private val authGrantRepository: AuthGrantRepository,
    private val authorizationEngine: AuthorizationEngine,
    private val invoiceRepository: InvoiceRepository,
    private val deviceStore: RelayDeviceStore,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: HttpClient by lazy { HttpClient(OkHttp) { install(WebSockets) } }
    private var job: Job? = null

    val isPaired: Boolean get() = deviceStore.isPaired
    val isRunning: Boolean get() = job?.isActive == true

    /** Connects (and keeps reconnecting) if the device is paired. No-op otherwise. */
    fun start() {
        if (isRunning || !deviceStore.isPaired) return
        val url = deviceStore.relayUrl() ?: return
        val secret = deviceStore.deviceSecret() ?: return

        job = scope.launch {
            ensureRelayGrant()
            val connection = RelayConnection(client, RelayMessageHandler(buildDispatcher()))
            while (isActive) {
                runCatching { connection.run(url, secret) }
                if (!isActive) break
                delay(RECONNECT_DELAY_MS) // socket dropped → back off then retry
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun buildDispatcher() = McpDispatcher(
        tools = walletTools(invoiceRepository),
        authorize = { tool -> authorizationEngine.authorize(RELAY_GRANT_ID, tool) },
        onResult = { tool, count -> authorizationEngine.recordResult(RELAY_GRANT_ID, tool, count) },
    )

    private suspend fun ensureRelayGrant() {
        if (authGrantRepository.getById(RELAY_GRANT_ID) == null) {
            authGrantRepository.grant(
                AuthGrant(
                    id = RELAY_GRANT_ID,
                    clientName = "Relay (remote AI)",
                    channel = AuthChannel.RELAY,
                    scopes = listOf("*"),
                    grantedAt = clock.now(),
                ),
            )
        }
    }

    companion object {
        const val RELAY_GRANT_ID = "relay"
        private const val RECONNECT_DELAY_MS = 5_000L
    }
}
