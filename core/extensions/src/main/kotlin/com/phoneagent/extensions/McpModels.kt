package com.phoneagent.extensions

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class McpTransport { STDIO, STREAMABLE_HTTP, SSE }

@Serializable
data class McpServerConfig(
    val id: String,
    val displayName: String,
    val transport: McpTransport,
    val command: List<String> = emptyList(),
    val url: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = false,
    val startupTimeoutMillis: Long = 30_000,
    val callTimeoutMillis: Long = 300_000,
)

@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject,
)

@Serializable
data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false,
)

@Serializable
data class McpContent(
    val type: String,
    val text: String? = null,
    val mimeType: String? = null,
    val data: String? = null,
)

interface McpClient : AutoCloseable {
    suspend fun initialize(): String
    suspend fun listTools(): List<McpTool>
    suspend fun callTool(name: String, arguments: JsonObject): McpToolResult
}

