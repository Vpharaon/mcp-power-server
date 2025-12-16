package com.bazik.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: Capabilities,
    val serverInfo: ServerInfo
)

@Serializable
data class Capabilities(
    val tools: ToolsCapability? = null
)

@Serializable
data class ToolsCapability(
    val supported: Boolean = true
)

@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: InputSchema
)

@Serializable
data class InputSchema(
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList()
)

@Serializable
data class PropertySchema(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

@Serializable
data class ToolsList(
    val tools: List<Tool>
)

@Serializable
data class ToolCallResult(
    val content: List<TextContent>
)

@Serializable
data class TextContent(
    val type: String = "text",
    val text: String
)