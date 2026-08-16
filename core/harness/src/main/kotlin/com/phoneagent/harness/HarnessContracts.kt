package com.phoneagent.harness

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class HarnessKind { DSH, CODEX, CLAUDE_CODE, MANAGER }

@Serializable
data class HarnessCapabilities(
    val attachments: Boolean = false,
    val images: Boolean = false,
    val approvals: Boolean = true,
    val steering: Boolean = false,
    val resume: Boolean = true,
    val extensions: Boolean = false,
)

@Serializable
enum class HarnessHealthState { NOT_INSTALLED, STARTING, READY, DEGRADED, FAILED }

@Serializable
data class HarnessHealth(
    val state: HarnessHealthState,
    val version: String? = null,
    val message: String = "",
)

@Serializable
data class HarnessConfigSnapshot(
    val revision: Long,
    val providersJson: String,
    val defaultsJson: String,
    val extensionsJson: String = "[]",
)

@Serializable
data class HarnessSessionSpec(
    val saiSessionId: String,
    val workspaceId: String,
    val workingDirectory: String,
    val providerId: String,
    val modelId: String,
    val reasoningConfigJson: String,
    val preset: String? = null,
)

@Serializable
data class HarnessHandle(
    val kind: HarnessKind,
    val saiSessionId: String,
    val externalSessionId: String,
    val workspaceId: String,
)

@Serializable
sealed interface HarnessInputPart {
    @Serializable @SerialName("text")
    data class Text(val text: String) : HarnessInputPart

    @Serializable @SerialName("file")
    data class File(
        val displayName: String,
        val mimeType: String,
        val localPath: String,
        val sizeBytes: Long,
    ) : HarnessInputPart

    @Serializable @SerialName("image")
    data class Image(
        val displayName: String,
        val mimeType: String,
        val localPath: String,
    ) : HarnessInputPart

    @Serializable @SerialName("audio")
    data class Audio(
        val displayName: String,
        val mimeType: String,
        val localPath: String,
    ) : HarnessInputPart
}

@Serializable
data class HarnessInput(val parts: List<HarnessInputPart>) {
    companion object {
        fun text(value: String) = HarnessInput(listOf(HarnessInputPart.Text(value)))
    }
}

@Serializable
enum class ApprovalDecision { APPROVE, DENY }

@Serializable
enum class ApprovalComplexity { SIMPLE, COMPLEX }

@Serializable
sealed interface HarnessEvent {
    val sessionId: String
    val occurredAt: Long

    @Serializable @SerialName("status")
    data class Status(
        override val sessionId: String,
        val state: String,
        val summary: String = "",
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent

    @Serializable @SerialName("message_delta")
    data class MessageDelta(
        override val sessionId: String,
        val messageId: String,
        val text: String,
        val reasoning: Boolean = false,
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent

    @Serializable @SerialName("tool")
    data class Tool(
        override val sessionId: String,
        val callId: String,
        val name: String,
        val phase: String,
        val summary: String = "",
        val payload: JsonObject? = null,
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent

    @Serializable @SerialName("approval")
    data class Approval(
        override val sessionId: String,
        val requestId: String,
        val title: String,
        val summary: String,
        val complexity: ApprovalComplexity,
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent

    @Serializable @SerialName("usage")
    data class Usage(
        override val sessionId: String,
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val cachedInputTokens: Long = 0,
        val cost: Double = 0.0,
        val currency: String = "CNY",
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent

    @Serializable @SerialName("completed")
    data class Completed(
        override val sessionId: String,
        val summary: String = "",
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent

    @Serializable @SerialName("failed")
    data class Failed(
        override val sessionId: String,
        val message: String,
        val retryable: Boolean = false,
        override val occurredAt: Long = System.currentTimeMillis(),
    ) : HarnessEvent
}

interface HarnessAdapter {
    val kind: HarnessKind
    val capabilities: HarnessCapabilities

    suspend fun prepare(): HarnessHealth
    suspend fun syncConfiguration(snapshot: HarnessConfigSnapshot)
    suspend fun createSession(spec: HarnessSessionSpec): HarnessHandle
    suspend fun listSessions(workspaceId: String): List<HarnessHandle>
    suspend fun send(handle: HarnessHandle, input: HarnessInput)
    suspend fun steer(handle: HarnessHandle, input: HarnessInput)
    suspend fun cancel(handle: HarnessHandle)
    suspend fun respondApproval(requestId: String, decision: ApprovalDecision)
    fun observe(handle: HarnessHandle): Flow<HarnessEvent>
}

class HarnessRegistry(adapters: Collection<HarnessAdapter> = emptyList()) {
    private val entries = linkedMapOf<HarnessKind, HarnessAdapter>()

    init { adapters.forEach(::register) }

    @Synchronized fun register(adapter: HarnessAdapter) { entries[adapter.kind] = adapter }
    @Synchronized fun get(kind: HarnessKind): HarnessAdapter? = entries[kind]
    @Synchronized fun require(kind: HarnessKind): HarnessAdapter =
        requireNotNull(entries[kind]) { "$kind runtime is not installed" }
    @Synchronized fun all(): List<HarnessAdapter> = entries.values.toList()
}
