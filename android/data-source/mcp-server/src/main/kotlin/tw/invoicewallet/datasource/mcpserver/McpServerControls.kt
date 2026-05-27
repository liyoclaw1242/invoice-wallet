package tw.invoicewallet.datasource.mcpserver

/** The slice of [McpServerManager] the settings UI needs (token display + regenerate). */
interface McpServerControls {
    val isRunning: Boolean

    fun token(): String

    fun regenerateToken(): String
}
