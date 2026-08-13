package com.phoneagent.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

class HttpMcpClient(
    private val config: McpServerConfig,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : McpClient {
    private val nextId = AtomicLong(1)
    @Volatile private var sessionId: String? = null

    override suspend fun initialize(): String {
        val response = request("initialize", buildJsonObject {
            put("protocolVersion", "2025-06-18")
            put("capabilities", buildJsonObject { })
            put("clientInfo", buildJsonObject { put("name", "sai"); put("version", "1.1.2") })
        }, captureSession = true)
        notify("notifications/initialized", buildJsonObject { })
        return response["result"]?.jsonObject?.get("protocolVersion")?.jsonPrimitive?.content ?: "unknown"
    }

    override suspend fun listTools(): List<McpTool> {
        val response = request("tools/list", buildJsonObject { })
        val tools = response["result"]?.jsonObject?.get("tools") as? JsonArray ?: return emptyList()
        return tools.map { item ->
            val obj = item.jsonObject
            McpTool(
                obj["name"]?.jsonPrimitive?.content.orEmpty(),
                obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                obj["inputSchema"]?.jsonObject ?: buildJsonObject { put("type", "object") },
            )
        }
    }

    override suspend fun callTool(name: String, arguments: JsonObject): McpToolResult {
        val response = request("tools/call", buildJsonObject { put("name", name); put("arguments", arguments) })
        val result = response["result"]?.jsonObject ?: error(response["error"].toString())
        val content = (result["content"] as? JsonArray).orEmpty().map { item ->
            val obj = item.jsonObject
            McpContent(
                type = obj["type"]?.jsonPrimitive?.content.orEmpty(),
                text = obj["text"]?.jsonPrimitive?.contentOrNull,
                mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull,
                data = obj["data"]?.jsonPrimitive?.contentOrNull,
            )
        }
        return McpToolResult(content, result["isError"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    private suspend fun request(method: String, params: JsonObject, captureSession: Boolean = false): JsonObject =
        withContext(Dispatchers.IO) {
            val id = nextId.getAndIncrement()
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            }
            val request = requestBuilder().post(payload.toString().toRequestBody(JSON)).build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                check(response.isSuccessful) { "MCP HTTP ${response.code}: ${body.take(500)}" }
                if (captureSession) sessionId = response.header("Mcp-Session-Id")
                json.parseToJsonElement(body).jsonObject
            }
        }

    private suspend fun notify(method: String, params: JsonObject) = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("jsonrpc", "2.0"); put("method", method); put("params", params) }
        client.newCall(requestBuilder().post(payload.toString().toRequestBody(JSON)).build()).execute().close()
    }

    private fun requestBuilder(): Request.Builder {
        val url = requireNotNull(config.url) { "MCP URL is missing" }
        return Request.Builder().url(url)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .apply {
                config.headers.forEach(::header)
                sessionId?.let { header("Mcp-Session-Id", it) }
            }
    }

    override fun close() = Unit

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}

object McpClientFactory {
    fun create(config: McpServerConfig, workingDirectory: java.io.File): McpClient = when (config.transport) {
        McpTransport.STDIO -> StdioMcpClient(config, workingDirectory)
        McpTransport.STREAMABLE_HTTP, McpTransport.SSE -> HttpMcpClient(config)
    }
}
