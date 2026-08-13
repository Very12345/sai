package com.phoneagent.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

class OpenAiResponsesAdapter(
    profile: ProviderProfile,
    client: OkHttpClient = defaultClient(),
) : HttpProviderAdapter(profile, client) {
    private data class PendingCall(var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder())
    private val calls = ConcurrentHashMap<Int, PendingCall>()

    override fun resetStreamState() = calls.clear()

    public override fun buildStreamingRequest(request: ModelRequest, credential: ProviderCredential): Request {
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            request.previousResponseId?.let { put("previous_response_id", it) }
            request.maxOutputTokens?.let { put("max_output_tokens", it) }
            request.effectiveReasoningEffort().wireValue()?.let { effort ->
                put("reasoning", buildJsonObject { put("effort", effort) })
            }
            put("input", buildJsonArray {
                request.messages.forEach { message ->
                    when (message.role) {
                        MessageRole.TOOL -> add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", requireNotNull(message.toolCallId) { "Tool output is missing call id" })
                            put("output", message.textContent())
                        })
                        else -> {
                            if (message.normalizedParts().isNotEmpty()) add(buildJsonObject {
                                put("role", message.role.wireRole())
                                put("content", message.openAiResponsesContent())
                            })
                            message.toolCalls.forEach { call -> add(buildJsonObject {
                                put("type", "function_call")
                                put("call_id", call.id)
                                put("name", call.name)
                                put("arguments", call.arguments)
                            }) }
                        }
                    }
                }
            })
            if (request.tools.isNotEmpty()) put("tools", openAiResponseTools(request.tools))
            request.requiredToolName?.let { name ->
                put("tool_choice", buildJsonObject { put("type", "function"); put("name", name) })
            }
            request.temperature?.let { put("temperature", it) }
            if (request.metadata.isNotEmpty()) put("metadata", JsonObject(request.metadata.mapValues { JsonPrimitive(it.value) }))
        }
        return postJson(profile.baseUrl.trimEnd('/') + profile.requestPath, credential, body)
    }

    public override fun parseSse(event: SseEvent): List<ModelEvent> {
        if (event.data == "[DONE]") return listOf(ModelEvent.Completed())
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrElse {
            return listOf(ModelEvent.Error("Invalid OpenAI event: ${it.message}"))
        }
        val type = root["type"].stringOrNull() ?: event.event.orEmpty()
        return when (type) {
            "response.created", "response.in_progress" -> listOf(ModelEvent.Started(root["response"]?.jsonObject?.get("id").stringOrNull()))
            "response.output_text.delta" -> listOf(ModelEvent.TextDelta(root["delta"].stringOrNull().orEmpty()))
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                listOf(ModelEvent.ReasoningDelta(root["delta"].stringOrNull().orEmpty()))
            "response.output_item.added" -> {
                val index = root["output_index"]?.jsonPrimitive?.intOrNull ?: 0
                val item = root["item"]?.jsonObject ?: return emptyList()
                if (item["type"].stringOrNull() != "function_call") return emptyList()
                val call = calls.computeIfAbsent(index) { PendingCall() }
                call.id = item["call_id"].stringOrNull() ?: item["id"].stringOrNull().orEmpty()
                call.name = item["name"].stringOrNull().orEmpty()
                listOf(ModelEvent.ToolCallDelta(index, call.id, call.name))
            }
            "response.function_call_arguments.delta" -> {
                val index = root["output_index"]?.jsonPrimitive?.intOrNull ?: 0
                val delta = root["delta"].stringOrNull().orEmpty()
                calls.computeIfAbsent(index) { PendingCall() }.arguments.append(delta)
                listOf(ModelEvent.ToolCallDelta(index, argumentsDelta = delta))
            }
            "response.function_call_arguments.done" -> {
                val index = root["output_index"]?.jsonPrimitive?.intOrNull ?: 0
                val call = calls.remove(index) ?: PendingCall(
                    id = root["call_id"].stringOrNull().orEmpty(),
                    name = root["name"].stringOrNull().orEmpty(),
                )
                val arguments = root["arguments"].stringOrNull() ?: call.arguments.toString()
                listOf(ModelEvent.ToolCall(call.id.ifBlank { "call-$index" }, call.name, arguments))
            }
            "response.completed" -> {
                val response = root["response"]?.jsonObject
                val usage = response?.get("usage") as? JsonObject
                buildList {
                    usage?.let {
                        add(ModelEvent.Usage(
                            inputTokens = it["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                            outputTokens = it["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                            cachedInputTokens = it.cachedInputTokens(),
                        ))
                    }
                    add(ModelEvent.Completed(response?.get("id").stringOrNull(), "completed"))
                }
            }
            "error", "response.failed" -> listOf(ModelEvent.Error(root["message"].stringOrNull() ?: event.data.take(500)))
            else -> emptyList()
        }
    }
}

class OpenAiChatAdapter(
    profile: ProviderProfile,
    client: OkHttpClient = defaultClient(),
) : HttpProviderAdapter(profile, client) {
    private data class PendingCall(var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder())
    private val calls = ConcurrentHashMap<Int, PendingCall>()

    override fun resetStreamState() = calls.clear()

    public override fun buildStreamingRequest(request: ModelRequest, credential: ProviderCredential): Request {
        val body = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("stream_options", buildJsonObject { put("include_usage", true) })
            request.maxOutputTokens?.let { put("max_tokens", it) }
            request.temperature?.let { put("temperature", it) }
            val selection = request.effectiveReasoningSelection()
            val effort = request.effectiveReasoningEffort()
            val requestProfile = profile.copy(defaultModel = request.model)
            when (ModelReasoningPolicy.capabilities(requestProfile)?.parameterFormat) {
                ReasoningParameterFormat.DEEPSEEK_THINKING -> when (effort) {
                    ReasoningEffort.AUTO -> Unit
                    ReasoningEffort.NONE -> put("thinking", buildJsonObject { put("type", "disabled") })
                    else -> {
                        put("thinking", buildJsonObject { put("type", "enabled") })
                        put("reasoning_effort", if (effort in setOf(ReasoningEffort.XHIGH, ReasoningEffort.MAX)) "max" else "high")
                    }
                }
                ReasoningParameterFormat.QWEN_BUDGET -> {
                    if (selection.mode == ReasoningMode.DISABLED) put("enable_thinking", false)
                    else if (selection.mode != ReasoningMode.AUTO) {
                        put("enable_thinking", true)
                        selection.budgetTokens?.let { put("thinking_budget", it) }
                    }
                }
                ReasoningParameterFormat.BINARY_THINKING -> when (selection.mode) {
                    ReasoningMode.DISABLED -> put("thinking", buildJsonObject { put("type", "disabled") })
                    ReasoningMode.ENABLED, ReasoningMode.ADAPTIVE -> put("thinking", buildJsonObject { put("type", "enabled") })
                    ReasoningMode.AUTO -> Unit
                }
                else -> effort.wireValue()?.let { put("reasoning_effort", it) }
            }
            put("messages", buildJsonArray {
                request.messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role.wireRole())
                        put("content", message.openAiChatContent())
                        message.toolCallId?.let { put("tool_call_id", it) }
                        message.name?.let { put("name", it) }
                        message.reasoningContent?.let { put("reasoning_content", it) }
                        if (message.toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray {
                            message.toolCalls.forEach { call -> add(buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", call.name)
                                    put("arguments", call.arguments)
                                })
                            }) }
                        })
                    })
                }
            })
            if (request.tools.isNotEmpty()) put("tools", openAiChatTools(request.tools))
            request.requiredToolName?.let { name ->
                put("tool_choice", buildJsonObject {
                    put("type", "function")
                    put("function", buildJsonObject { put("name", name) })
                })
            }
        }
        return postJson(profile.baseUrl.trimEnd('/') + profile.requestPath, credential, body)
    }

    public override fun parseSse(event: SseEvent): List<ModelEvent> {
        if (event.data == "[DONE]") return listOf(ModelEvent.Completed())
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrElse {
            return listOf(ModelEvent.Error("Invalid OpenAI-compatible event: ${it.message}"))
        }
        root["error"]?.jsonObject?.let { error ->
            return listOf(ModelEvent.Error(error["message"].stringOrNull() ?: "Provider error"))
        }
        val result = mutableListOf<ModelEvent>()
        val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject
        val delta = choice?.get("delta") as? JsonObject
        delta?.get("content").stringOrNull()?.takeIf(String::isNotEmpty)?.let { result += ModelEvent.TextDelta(it) }
        (delta?.get("reasoning_content").stringOrNull() ?: delta?.get("reasoning").stringOrNull())
            ?.takeIf(String::isNotEmpty)?.let { result += ModelEvent.ReasoningDelta(it) }
        (delta?.get("tool_calls") as? JsonArray)?.forEach { element ->
            val tool = element.jsonObject
            val index = tool["index"]?.jsonPrimitive?.intOrNull ?: 0
            val function = tool["function"] as? JsonObject
            val call = calls.computeIfAbsent(index) { PendingCall() }
            // OpenAI-compatible providers commonly send the id/name only in the
            // first tool-call chunk and then send empty strings while streaming
            // arguments. Never let those continuation chunks erase metadata we
            // already collected. Some gateways also flatten `name` onto the
            // tool-call object, so accept that shape as a compatibility fallback.
            tool["id"].stringOrNull()?.takeIf(String::isNotBlank)?.let { call.id = it }
            (
                function?.get("name").stringOrNull()
                    ?: tool["name"].stringOrNull()
                )?.takeIf(String::isNotBlank)?.let { call.name = it }
            val args = function?.get("arguments").stringOrNull().orEmpty()
            call.arguments.append(args)
            result += ModelEvent.ToolCallDelta(index, call.id.ifBlank { null }, call.name.ifBlank { null }, args)
        }
        choice?.get("finish_reason").stringOrNull()?.let { reason ->
            if (reason == "tool_calls") {
                calls.toSortedMap().forEach { (index, call) ->
                    if (call.name.isBlank()) {
                        result += ModelEvent.Error(
                            "Provider returned a tool call without a function name",
                            retryable = true,
                        )
                    } else {
                        result += ModelEvent.ToolCall(
                            call.id.ifBlank { "call-$index" },
                            call.name,
                            call.arguments.toString(),
                        )
                    }
                }
                calls.clear()
            }
            result += ModelEvent.Completed(root["id"].stringOrNull(), reason)
        }
        (root["usage"] as? JsonObject)?.let { usage ->
            result += ModelEvent.Usage(
                inputTokens = usage["prompt_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                outputTokens = usage["completion_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                cachedInputTokens = usage.cachedInputTokens(),
            )
        }
        return result
    }
}

private fun JsonObject.cachedInputTokens(): Long =
    this["prompt_cache_hit_tokens"]?.jsonPrimitive?.longOrNull
        ?: this["cache_read_input_tokens"]?.jsonPrimitive?.longOrNull
        ?: this["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.longOrNull
        ?: this["input_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.longOrNull
        ?: 0

private fun ReasoningEffort.wireValue(): String? = when (this) {
    ReasoningEffort.AUTO -> null
    ReasoningEffort.NONE -> "none"
    ReasoningEffort.MINIMAL -> "minimal"
    ReasoningEffort.LOW -> "low"
    ReasoningEffort.MEDIUM -> "medium"
    ReasoningEffort.HIGH -> "high"
    ReasoningEffort.XHIGH -> "xhigh"
    ReasoningEffort.MAX -> "max"
}

internal fun MessageRole.wireRole(): String = when (this) {
    MessageRole.SYSTEM -> "system"
    MessageRole.USER -> "user"
    MessageRole.ASSISTANT -> "assistant"
    MessageRole.TOOL -> "tool"
}

internal fun openAiResponseTools(tools: List<ToolDefinition>) = buildJsonArray {
    tools.forEach { tool ->
        add(buildJsonObject {
            put("type", "function")
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", tool.parameters)
            put("strict", tool.strict)
        })
    }
}

internal fun openAiChatTools(tools: List<ToolDefinition>) = buildJsonArray {
    tools.forEach { tool ->
        add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.parameters)
                put("strict", tool.strict)
            })
        })
    }
}
