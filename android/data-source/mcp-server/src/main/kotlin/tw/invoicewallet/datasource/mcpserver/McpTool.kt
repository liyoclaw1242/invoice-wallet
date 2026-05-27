package tw.invoicewallet.datasource.mcpserver

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import tw.invoicewallet.datasource.authz.QueryConstraints

/**
 * One MCP tool the wallet exposes. [inputSchema] is the JSON Schema advertised in
 * `tools/list`; [call] runs the tool, honouring the authorization [QueryConstraints]
 * (date window + excluded categories), and returns its structured JSON result.
 */
interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonObject

    suspend fun call(arguments: JsonObject, constraints: QueryConstraints): JsonElement
}
