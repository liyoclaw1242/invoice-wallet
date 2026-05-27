package tw.invoicewallet.datasource.mcpserver

import kotlinx.datetime.Clock
import tw.invoicewallet.core.database.repository.AuthGrantRepository
import tw.invoicewallet.core.database.repository.InvoiceRepository
import tw.invoicewallet.core.model.AuthChannel
import tw.invoicewallet.core.model.AuthGrant
import tw.invoicewallet.datasource.authz.AuthorizationEngine
import tw.invoicewallet.datasource.mcpserver.tools.GetInvoiceDetailTool
import tw.invoicewallet.datasource.mcpserver.tools.GetLotteryStatusTool
import tw.invoicewallet.datasource.mcpserver.tools.GetSpendingSummaryTool
import tw.invoicewallet.datasource.mcpserver.tools.ListInvoicesTool
import tw.invoicewallet.datasource.mcpserver.tools.ListMerchantsTool
import tw.invoicewallet.datasource.mcpserver.tools.SearchInvoicesTool
import javax.inject.Inject
import javax.inject.Singleton

/** The six MCP tools the wallet exposes, all reading the same repository. */
fun walletTools(invoices: InvoiceRepository): List<McpTool> = listOf(
    ListInvoicesTool(invoices),
    GetInvoiceDetailTool(invoices),
    SearchInvoicesTool(invoices),
    GetSpendingSummaryTool(invoices),
    GetLotteryStatusTool(invoices),
    ListMerchantsTool(invoices),
)

/**
 * Single entry point for the app: enables/disables the local MCP server, exposes the
 * Bearer token, and seeds the `LOCAL_MCP` grant on first start. Every served call goes
 * through [AuthorizationEngine] (scope/rate-limit/audit).
 */
@Singleton
class McpServerManager @Inject constructor(
    private val authGrantRepository: AuthGrantRepository,
    private val authorizationEngine: AuthorizationEngine,
    private val tokenStore: McpTokenStore,
    private val invoiceRepository: InvoiceRepository,
    private val clock: Clock,
) : McpServerControls {
    private val controller = McpServerController(
        dispatcherFactory = ::buildDispatcher,
        tokenProvider = { tokenStore.getOrCreate() },
    )

    override val isRunning: Boolean get() = controller.isRunning

    override fun token(): String = tokenStore.getOrCreate()

    override fun regenerateToken(): String = tokenStore.regenerate()

    /** Seeds the local grant (idempotent) and starts the server; loopback unless [lanMode]. */
    suspend fun start(lanMode: Boolean = false, port: Int = McpServerController.DEFAULT_PORT) {
        ensureLocalGrant()
        controller.start(port, if (lanMode) McpServerController.ANY_HOST else McpServerController.LOOPBACK)
    }

    fun stop() = controller.stop()

    private fun buildDispatcher() = McpDispatcher(
        tools = walletTools(invoiceRepository),
        authorize = { tool -> authorizationEngine.authorize(LOCAL_GRANT_ID, tool) },
        onResult = { tool, count -> authorizationEngine.recordResult(LOCAL_GRANT_ID, tool, count) },
    )

    private suspend fun ensureLocalGrant() {
        if (authGrantRepository.getById(LOCAL_GRANT_ID) == null) {
            authGrantRepository.grant(
                AuthGrant(
                    id = LOCAL_GRANT_ID,
                    clientName = "Claude Desktop (local MCP)",
                    channel = AuthChannel.LOCAL_MCP,
                    scopes = listOf("*"),
                    grantedAt = clock.now(),
                ),
            )
        }
    }

    companion object {
        const val LOCAL_GRANT_ID = "local-mcp"
    }
}
