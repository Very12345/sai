package com.phoneagent.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull

@Serializable
enum class ProviderProtocol {
    OPENAI_RESPONSES,
    OPENAI_CHAT,
    ANTHROPIC_MESSAGES,
    GEMINI_NATIVE,
}

@Serializable
enum class ReasoningEffort {
    AUTO,
    NONE,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX,
}

@Serializable
enum class ReasoningMode { AUTO, ENABLED, DISABLED, ADAPTIVE }

@Serializable
data class ReasoningSelection(
    val mode: ReasoningMode = ReasoningMode.AUTO,
    val effort: ReasoningEffort? = null,
    val budgetTokens: Int? = null,
) {
    fun legacyEffort(): ReasoningEffort = when (mode) {
        ReasoningMode.DISABLED -> ReasoningEffort.NONE
        ReasoningMode.AUTO, ReasoningMode.ADAPTIVE -> effort ?: ReasoningEffort.AUTO
        ReasoningMode.ENABLED -> effort ?: ReasoningEffort.HIGH
    }

    companion object {
        fun fromLegacy(effort: ReasoningEffort): ReasoningSelection = when (effort) {
            ReasoningEffort.AUTO -> ReasoningSelection()
            ReasoningEffort.NONE -> ReasoningSelection(ReasoningMode.DISABLED)
            else -> ReasoningSelection(ReasoningMode.ENABLED, effort)
        }
    }
}

@Serializable
enum class ReasoningCapabilitySource { USER_OVERRIDE, PROVIDER_METADATA, OFFICIAL_RULE, UNKNOWN }

@Serializable
enum class ReasoningParameterFormat {
    OPENAI_EFFORT,
    DEEPSEEK_THINKING,
    ANTHROPIC_EFFORT,
    GEMINI_BUDGET,
    GEMINI_LEVEL,
    QWEN_BUDGET,
    BINARY_THINKING,
}

@Serializable
data class ModelReasoningCapabilities(
    val supportedModes: Set<ReasoningMode> = emptySet(),
    val supportedEfforts: List<ReasoningEffort> = emptyList(),
    val defaultSelection: ReasoningSelection = ReasoningSelection(),
    val mandatory: Boolean = false,
    val minBudgetTokens: Int? = null,
    val maxBudgetTokens: Int? = null,
    val parameterFormat: ReasoningParameterFormat? = null,
    val source: ReasoningCapabilitySource = ReasoningCapabilitySource.UNKNOWN,
    val aliases: Map<ReasoningEffort, ReasoningEffort> = emptyMap(),
)

@Serializable
data class ProviderCapabilities(
    val streaming: Boolean = true,
    val tools: Boolean = true,
    val reasoning: Boolean = false,
    val imageInput: Boolean = false,
    val audioInput: Boolean = false,
    val modelDiscovery: Boolean = false,
)

@Serializable
data class ProviderProfile(
    val id: String,
    val displayName: String,
    val protocol: ProviderProtocol,
    val baseUrl: String,
    val requestPath: String,
    val modelsPath: String? = null,
    val defaultModel: String,
    val customHeaders: Map<String, String> = emptyMap(),
    val contextWindow: Int = 128_000,
    val maxOutputTokens: Int = 16_384,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
    val reasoningSelection: ReasoningSelection = ReasoningSelection.fromLegacy(reasoningEffort),
    val modelReasoningCapabilities: Map<String, ModelReasoningCapabilities> = emptyMap(),
    val capabilities: ProviderCapabilities = ProviderCapabilities(),
    val modelPricing: Map<String, ModelPricing> = emptyMap(),
)

@Serializable
data class ModelPricing(
    @SerialName("cachedInputPerMillionUsd") val cachedInputPerMillion: Double,
    @SerialName("uncachedInputPerMillionUsd") val uncachedInputPerMillion: Double,
    @SerialName("outputPerMillionUsd") val outputPerMillion: Double,
    val sourceLabel: String = "服务商公开价格",
    val currency: String = "USD",
)

@Serializable
data class ProviderCredential(
    val apiKey: String,
    val organization: String? = null,
)

@Serializable
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class ModelMessage(
    val role: MessageRole,
    val content: String = "",
    val toolCallId: String? = null,
    val name: String? = null,
    val toolCalls: List<ModelToolCall> = emptyList(),
    val reasoningContent: String? = null,
    val contentParts: List<ModelContentPart> = emptyList(),
)

@Serializable
sealed interface ModelContentPart {
    @Serializable @SerialName("text")
    data class Text(val text: String) : ModelContentPart

    @Serializable @SerialName("image")
    data class Image(
        val mimeType: String,
        val base64Data: String? = null,
        val remoteUrl: String? = null,
        val detail: String = "auto",
    ) : ModelContentPart

    @Serializable @SerialName("audio")
    data class Audio(
        val mimeType: String,
        val format: String,
        val base64Data: String,
    ) : ModelContentPart

    @Serializable @SerialName("file")
    data class FileAttachment(
        val fileName: String,
        val mimeType: String,
        val base64Data: String,
    ) : ModelContentPart
}

fun ModelMessage.normalizedParts(): List<ModelContentPart> = when {
    contentParts.isNotEmpty() -> contentParts
    content.isNotEmpty() -> listOf(ModelContentPart.Text(content))
    else -> emptyList()
}

fun ModelMessage.textContent(): String = normalizedParts()
    .filterIsInstance<ModelContentPart.Text>()
    .joinToString("") { it.text }

@Serializable
data class ModelToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val strict: Boolean = true,
)

@Serializable
data class ModelRequest(
    val model: String,
    val messages: List<ModelMessage>,
    val tools: List<ToolDefinition> = emptyList(),
    /** Force one named tool for a protocol-repair turn; null keeps provider auto selection. */
    val requiredToolName: String? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val previousResponseId: String? = null,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
    val reasoningSelection: ReasoningSelection = ReasoningSelection.fromLegacy(reasoningEffort),
    val metadata: Map<String, String> = emptyMap(),
)

fun ModelRequest.effectiveReasoningSelection(): ReasoningSelection =
    if (reasoningSelection == ReasoningSelection() && reasoningEffort != ReasoningEffort.AUTO) {
        ReasoningSelection.fromLegacy(reasoningEffort)
    } else reasoningSelection

fun ModelRequest.effectiveReasoningEffort(): ReasoningEffort = effectiveReasoningSelection().legacyEffort()

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val contextWindow: Int? = null,
    val reasoningCapabilities: ModelReasoningCapabilities? = null,
)

@Serializable
sealed interface ModelEvent {
    @Serializable
    @SerialName("started")
    data class Started(val responseId: String? = null) : ModelEvent

    @Serializable
    @SerialName("text_delta")
    data class TextDelta(val text: String) : ModelEvent

    @Serializable
    @SerialName("reasoning_delta")
    data class ReasoningDelta(val text: String) : ModelEvent

    @Serializable
    @SerialName("tool_call_delta")
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val argumentsDelta: String = "",
    ) : ModelEvent

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: String,
    ) : ModelEvent

    @Serializable
    @SerialName("usage")
    data class Usage(
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val cachedInputTokens: Long = 0,
    ) : ModelEvent

    @Serializable
    @SerialName("completed")
    data class Completed(val responseId: String? = null, val finishReason: String? = null) : ModelEvent

    @Serializable
    @SerialName("error")
    data class Error(val message: String, val retryable: Boolean = false, val statusCode: Int? = null) : ModelEvent
}

data class ProviderProbe(
    val reachable: Boolean,
    val latencyMs: Long,
    val detail: String,
)

data class ProviderHttpError(
    val statusCode: Int,
    val responseBody: String,
) : RuntimeException("Provider returned HTTP $statusCode: ${responseBody.take(500)}")

internal fun JsonElement?.stringOrNull(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
