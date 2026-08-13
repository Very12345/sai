package com.phoneagent.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

class GeminiAdapter(
    profile: ProviderProfile,
    client: OkHttpClient = defaultClient(),
) : HttpProviderAdapter(profile, client) {
    override fun parseModels(body: String): List<ModelInfo> {
        val models = json.parseToJsonElement(body).jsonObject["models"] as? JsonArray ?: return emptyList()
        return models.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val methods = model["supportedGenerationMethods"] as? JsonArray
            if (methods != null && methods.none { it.stringOrNull() == "generateContent" }) return@mapNotNull null
            val id = model["baseModelId"].stringOrNull()
                ?: model["name"].stringOrNull()?.substringAfterLast('/')
                ?: return@mapNotNull null
            ModelInfo(
                id = id,
                displayName = model["displayName"].stringOrNull() ?: id,
                contextWindow = model["inputTokenLimit"]?.jsonPrimitive?.intOrNull,
            )
        }
    }

    override suspend fun listModels(credential: ProviderCredential): List<ModelInfo> {
        val request = Request.Builder()
            .url(profile.baseUrl.trimEnd('/') + (profile.modelsPath ?: "/v1beta/models"))
            .header("x-goog-api-key", credential.apiKey)
            .apply { profile.customHeaders.forEach(::header) }
            .get().build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw ProviderHttpError(response.code, body)
                parseModels(body)
            }
        }
    }

    public override fun buildStreamingRequest(request: ModelRequest, credential: ProviderCredential): Request {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n") { it.textContent() }
        val body = buildJsonObject {
            if (system.isNotBlank()) put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", buildJsonArray {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", if (message.role == MessageRole.ASSISTANT) "model" else "user")
                        put("parts", buildJsonArray {
                            if (message.role == MessageRole.TOOL) {
                                add(buildJsonObject { put("functionResponse", buildJsonObject {
                                    message.toolCallId?.let { put("id", it) }
                                    put("name", requireNotNull(message.name) { "Tool result is missing tool name" })
                                    put("response", buildJsonObject { put("output", message.textContent()) })
                                }) })
                            } else {
                                message.normalizedParts().forEach { part ->
                                    add(ModelMessage(message.role, contentParts = listOf(part)).geminiParts().let {
                                        (it as kotlinx.serialization.json.JsonArray).first()
                                    })
                                }
                                message.toolCalls.forEach { call -> add(buildJsonObject { put("functionCall", buildJsonObject {
                                    put("id", call.id)
                                    put("name", call.name)
                                    put("args", runCatching { json.parseToJsonElement(call.arguments) }
                                        .getOrElse { buildJsonObject { } })
                                }) }) }
                            }
                        })
                    })
                }
            })
            put("generationConfig", buildJsonObject {
                request.temperature?.let { put("temperature", it) }
                request.maxOutputTokens?.let { put("maxOutputTokens", it) }
                val selection = request.effectiveReasoningSelection()
                val effort = request.effectiveReasoningEffort()
                if (selection.mode != ReasoningMode.AUTO) {
                    put("thinkingConfig", buildJsonObject {
                        put("includeThoughts", selection.mode != ReasoningMode.DISABLED)
                        if (request.model.contains("2.5")) {
                            put("thinkingBudget", selection.budgetTokens ?: when (effort) {
                                ReasoningEffort.NONE -> 0
                                ReasoningEffort.MINIMAL -> 128
                                ReasoningEffort.LOW -> 1_024
                                ReasoningEffort.MEDIUM -> 8_192
                                ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX -> 24_576
                                ReasoningEffort.AUTO -> -1
                            })
                        } else if (selection.mode != ReasoningMode.DISABLED) {
                            put("thinkingLevel", when (effort) {
                                ReasoningEffort.MINIMAL, ReasoningEffort.LOW -> "low"
                                ReasoningEffort.MEDIUM -> "medium"
                                ReasoningEffort.HIGH, ReasoningEffort.XHIGH, ReasoningEffort.MAX -> "high"
                                else -> error("unreachable")
                            })
                        }
                    })
                }
            })
            if (request.tools.isNotEmpty()) put("tools", buildJsonArray { add(buildJsonObject {
                put("functionDeclarations", buildJsonArray {
                    request.tools.forEach { tool -> add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }) }
                })
            }) })
            request.requiredToolName?.let { name ->
                put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject {
                        put("mode", "ANY")
                        put("allowedFunctionNames", buildJsonArray { add(JsonPrimitive(name)) })
                    })
                })
            }
        }
        val path = profile.requestPath.replace("{model}", request.model)
        return Request.Builder()
            .url(profile.baseUrl.trimEnd('/') + path + "?alt=sse")
            .header("x-goog-api-key", credential.apiKey)
            .header("Accept", "text/event-stream")
            .apply { profile.customHeaders.forEach(::header) }
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    override fun parseSse(event: SseEvent): List<ModelEvent> {
        val root = runCatching { json.parseToJsonElement(event.data).jsonObject }.getOrElse {
            return listOf(ModelEvent.Error("Invalid Gemini event: ${it.message}"))
        }
        root["error"]?.jsonObject?.let { return listOf(ModelEvent.Error(it["message"].stringOrNull() ?: "Gemini error")) }
        val result = mutableListOf<ModelEvent>()
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        val parts = candidate?.get("content")?.jsonObject?.get("parts") as? JsonArray
        parts?.forEach { partElement ->
            val part = partElement.jsonObject
            part["text"].stringOrNull()?.let { text ->
                if (part["thought"]?.jsonPrimitive?.content == "true") result += ModelEvent.ReasoningDelta(text)
                else result += ModelEvent.TextDelta(text)
            }
            part["functionCall"]?.jsonObject?.let { call ->
                val name = call["name"].stringOrNull().orEmpty()
                val args = call["args"]?.toString() ?: "{}"
                val id = call["id"].stringOrNull() ?: "gemini-${name}-${args.hashCode()}"
                result += ModelEvent.ToolCall(id, name, args)
            }
        }
        candidate?.get("finishReason").stringOrNull()?.let { result += ModelEvent.Completed(finishReason = it) }
        (root["usageMetadata"] as? JsonObject)?.let { usage ->
            result += ModelEvent.Usage(
                inputTokens = usage["promptTokenCount"]?.jsonPrimitive?.longOrNull ?: 0,
                outputTokens = usage["candidatesTokenCount"]?.jsonPrimitive?.longOrNull ?: 0,
                cachedInputTokens = usage["cachedContentTokenCount"]?.jsonPrimitive?.longOrNull ?: 0,
            )
        }
        return result
    }
}
