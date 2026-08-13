package com.phoneagent.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class AnthropicAdapter(
    profile: ProviderProfile,
    client: OkHttpClient = defaultClient(),
) : HttpProviderAdapter(profile, client) {
    private data class PendingTool(var id: String = "", var name: String = "", val json: StringBuilder = StringBuilder())
    private val pending = ConcurrentHashMap<Int, PendingTool>()

    override fun resetStreamState() = pending.clear()

    public override fun buildStreamingRequest(request: ModelRequest, credential: ProviderCredential): Request {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n") { it.textContent() }
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("max_tokens", request.maxOutputTokens ?: profile.maxOutputTokens)
            val selection = request.effectiveReasoningSelection()
            val effort = request.effectiveReasoningEffort()
            when (selection.mode) {
                ReasoningMode.ADAPTIVE -> put("thinking", buildJsonObject { put("type", "adaptive") })
                ReasoningMode.DISABLED -> put("thinking", buildJsonObject { put("type", "disabled") })
                ReasoningMode.AUTO -> Unit
                ReasoningMode.ENABLED -> put("output_config", buildJsonObject {
                    put("effort", when (effort) {
                        ReasoningEffort.MINIMAL, ReasoningEffort.LOW -> "low"
                        ReasoningEffort.MEDIUM -> "medium"
                        ReasoningEffort.HIGH -> "high"
                        ReasoningEffort.XHIGH -> "xhigh"
                        ReasoningEffort.MAX -> "max"
                        ReasoningEffort.AUTO, ReasoningEffort.NONE -> "low"
                    })
                })
            }
            if (system.isNotBlank()) put("system", system)
            request.temperature?.let { put("temperature", it) }
            put("messages", buildJsonArray {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == MessageRole.ASSISTANT) "assistant" else "user")
                        when (message.role) {
                            MessageRole.ASSISTANT -> put("content", buildJsonArray {
                                message.normalizedParts().forEach { part ->
                                    add(ModelMessage(message.role, contentParts = listOf(part)).anthropicContent().let {
                                        (it as kotlinx.serialization.json.JsonArray).first()
                                    })
                                }
                                message.toolCalls.forEach { call -> add(buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", call.id)
                                    put("name", call.name)
                                    put("input", runCatching { json.parseToJsonElement(call.arguments) }
                                        .getOrElse { buildJsonObject { } })
                                }) }
                            })
                            MessageRole.TOOL -> put("content", buildJsonArray { add(buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", requireNotNull(message.toolCallId) { "Tool result is missing call id" })
                                put("content", message.textContent())
                            }) })
                            else -> put("content", message.anthropicContent())
                        }
                    })
                }
            })
            if (request.tools.isNotEmpty()) put("tools", buildJsonArray {
                request.tools.forEach { tool -> add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("input_schema", tool.parameters)
                }) }
            })
            request.requiredToolName?.let { name ->
                put("tool_choice", buildJsonObject { put("type", "tool"); put("name", name) })
            }
        }
        return postJson(profile.baseUrl.trimEnd('/') + profile.requestPath, credential, body)
    }

    override fun parseSse(event: SseEvent): List<ModelEvent> {
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrElse {
            return listOf(ModelEvent.Error("Invalid Anthropic event: ${it.message}"))
        }
        val type = root["type"].stringOrNull() ?: event.event.orEmpty()
        return when (type) {
            "message_start" -> {
                val message = root["message"]?.jsonObject
                val usage = message?.get("usage") as? JsonObject
                buildList {
                    add(ModelEvent.Started(message?.get("id").stringOrNull()))
                    usage?.let {
                        add(ModelEvent.Usage(
                            inputTokens = it["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                            cachedInputTokens = it["cache_read_input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                        ))
                    }
                }
            }
            "content_block_start" -> {
                val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                val block = root["content_block"]?.jsonObject ?: return emptyList()
                if (block["type"].stringOrNull() != "tool_use") return emptyList()
                val call = pending.computeIfAbsent(index) { PendingTool() }
                call.id = block["id"].stringOrNull().orEmpty()
                call.name = block["name"].stringOrNull().orEmpty()
                listOf(ModelEvent.ToolCallDelta(index, call.id, call.name))
            }
            "content_block_delta" -> {
                val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                val delta = root["delta"]?.jsonObject ?: return emptyList()
                when (delta["type"].stringOrNull()) {
                    "text_delta" -> listOf(ModelEvent.TextDelta(delta["text"].stringOrNull().orEmpty()))
                    "thinking_delta" -> listOf(ModelEvent.ReasoningDelta(delta["thinking"].stringOrNull().orEmpty()))
                    "input_json_delta" -> {
                        val text = delta["partial_json"].stringOrNull().orEmpty()
                        pending.computeIfAbsent(index) { PendingTool() }.json.append(text)
                        listOf(ModelEvent.ToolCallDelta(index, argumentsDelta = text))
                    }
                    else -> emptyList()
                }
            }
            "content_block_stop" -> {
                val index = root["index"]?.jsonPrimitive?.intOrNull ?: 0
                pending.remove(index)?.let { listOf(ModelEvent.ToolCall(it.id.ifBlank { "tool-$index" }, it.name, it.json.toString())) }
                    ?: emptyList()
            }
            "message_delta" -> {
                val usage = root["usage"] as? JsonObject
                buildList {
                    usage?.let { add(ModelEvent.Usage(outputTokens = it["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0)) }
                    root["delta"]?.jsonObject?.get("stop_reason").stringOrNull()?.let { add(ModelEvent.Completed(finishReason = it)) }
                }
            }
            "message_stop" -> listOf(ModelEvent.Completed())
            "error" -> listOf(ModelEvent.Error(root["error"]?.jsonObject?.get("message").stringOrNull() ?: "Anthropic error"))
            else -> emptyList()
        }
    }
}
