package com.phoneagent.extensions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class StdioMcpClient(
    private val config: McpServerConfig,
    workingDirectory: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : McpClient {
    private val process = ProcessBuilder(config.command).directory(workingDirectory).apply {
        environment().putAll(config.environment)
        redirectErrorStream(false)
    }.start()
    private val writer = process.outputStream.bufferedWriter()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val nextId = AtomicLong(1)

    init {
        Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()?.let { message ->
                    message["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { id -> pending.remove(id)?.complete(message) }
                }
            }
            pending.values.forEach { it.completeExceptionally(IllegalStateException("MCP process exited")) }
            pending.clear()
        }.start()
    }

    override suspend fun initialize(): String {
        val response = request("initialize", buildJsonObject {
            put("protocolVersion", "2025-06-18")
            put("capabilities", buildJsonObject { })
            put("clientInfo", buildJsonObject { put("name", "sai"); put("version", "1.1.2") })
        }, config.startupTimeoutMillis)
        notify("notifications/initialized", buildJsonObject { })
        return response["result"]?.jsonObject?.get("protocolVersion")?.jsonPrimitive?.content ?: "unknown"
    }

    override suspend fun listTools(): List<McpTool> {
        val response = request("tools/list", buildJsonObject { }, config.callTimeoutMillis)
        val tools = response["result"]?.jsonObject?.get("tools") as? JsonArray ?: return emptyList()
        return tools.map { item ->
            val obj = item.jsonObject
            McpTool(
                name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                inputSchema = obj["inputSchema"]?.jsonObject ?: buildJsonObject { put("type", "object") },
            )
        }
    }

    override suspend fun callTool(name: String, arguments: JsonObject): McpToolResult {
        val response = request("tools/call", buildJsonObject { put("name", name); put("arguments", arguments) }, config.callTimeoutMillis)
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

    private suspend fun request(method: String, params: JsonObject, timeout: Long): JsonObject {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        write(buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params) })
        return withTimeout(timeout) { deferred.await() }
    }

    private suspend fun notify(method: String, params: JsonObject) {
        write(buildJsonObject { put("jsonrpc", "2.0"); put("method", method); put("params", params) })
    }

    private suspend fun write(message: JsonObject) = withContext(Dispatchers.IO) {
        synchronized(writer) {
            writer.write(message.toString())
            writer.newLine()
            writer.flush()
        }
    }

    override fun close() {
        process.destroy()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}
