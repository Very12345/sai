package com.phoneagent.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-only compatibility vocabulary for Room v1-v4 records and the retired
 * native conversation renderer. New tasks are never executed from this package;
 * DeepSeek Harness owns prompting, tools, approval, compaction and persistence.
 */
@Serializable
enum class AgentMode { PLAN, AGENT, GOAL }

@Serializable
enum class AgentRunState { IDLE, RUNNING, WAITING_APPROVAL, PAUSED, COMPLETED, FAILED, CANCELLED }

@Serializable
enum class TaskQueueState { READY, QUEUED, STARTING, ACTIVE, WAITING_RESOURCE, PAUSED, FINISHED }

@Serializable
enum class ToolCapability {
    WORKSPACE_READ, WORKSPACE_WRITE, DELETE, SHELL, NETWORK, PACKAGE_INSTALL,
    EXTERNAL_STORAGE, GIT_HISTORY, DEVICE_CONTROL, BROWSER_CONTROL,
}

@Serializable
enum class ApprovalDecision { ALLOW_ONCE, ALLOW_SESSION, DENY }

@Serializable
data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val argumentsJson: String,
    val capabilities: Set<ToolCapability>,
    val riskExplanation: String,
    val preview: String? = null,
)

@Serializable
data class ToolResult(
    val success: Boolean,
    val output: String,
    val metadata: Map<String, String> = emptyMap(),
    val truncated: Boolean = false,
)

@Serializable
sealed interface AgentEvent {
    @Serializable @SerialName("run_started") data class RunStarted(val sessionId: String, val mode: AgentMode) : AgentEvent
    @Serializable @SerialName("user_message") data class UserMessage(val text: String) : AgentEvent
    @Serializable @SerialName("assistant_started") data class AssistantMessageStarted(val messageId: String) : AgentEvent
    @Serializable @SerialName("assistant_delta") data class AssistantDelta(val text: String) : AgentEvent
    @Serializable @SerialName("assistant_completed") data class AssistantMessageCompleted(val messageId: String) : AgentEvent
    @Serializable @SerialName("reasoning_started") data class ReasoningStarted(val blockId: String) : AgentEvent
    @Serializable @SerialName("reasoning_delta") data class ReasoningDelta(val text: String) : AgentEvent
    @Serializable @SerialName("reasoning_completed") data class ReasoningCompleted(val blockId: String) : AgentEvent
    @Serializable @SerialName("tool_requested") data class ToolRequested(val callId: String, val name: String, val argumentsJson: String) : AgentEvent
    @Serializable @SerialName("approval_requested") data class ApprovalRequested(val request: ApprovalRequest) : AgentEvent
    @Serializable @SerialName("approval_resolved") data class ApprovalResolved(val requestId: String, val decision: ApprovalDecision) : AgentEvent
    @Serializable @SerialName("tool_finished") data class ToolFinished(val callId: String, val name: String, val result: ToolResult) : AgentEvent
    @Serializable @SerialName("tool_progress") data class ToolProgress(val callId: String, val name: String, val detail: String) : AgentEvent
    @Serializable @SerialName("diff") data class DiffProduced(val path: String, val unifiedDiff: String) : AgentEvent
    @Serializable @SerialName("attachment") data class AttachmentProduced(val id: String, val displayName: String, val mimeType: String, val localPath: String) : AgentEvent
    @Serializable @SerialName("task_queued") data class TaskQueued(val position: Int, val reason: String) : AgentEvent
    @Serializable @SerialName("task_progress") data class TaskProgress(val label: String, val fraction: Float? = null) : AgentEvent
    @Serializable @SerialName("steer") data class SteerApplied(val text: String) : AgentEvent
    @Serializable @SerialName("speech_requested") data class SpeechRequested(val id: String, val text: String, val voiceTurnId: String? = null) : AgentEvent
    @Serializable @SerialName("browser_observation") data class BrowserObservation(val url: String, val title: String, val summary: String) : AgentEvent
    @Serializable @SerialName("device_action") data class DeviceAction(val action: String, val target: String, val success: Boolean, val detail: String = "") : AgentEvent
    @Serializable @SerialName("usage") data class Usage(val inputTokens: Long, val outputTokens: Long, val cachedInputTokens: Long) : AgentEvent
    @Serializable @SerialName("context_compacted") data class ContextCompacted(val removedMessages: Int, val summary: String) : AgentEvent
    @Serializable @SerialName("state") data class StateChanged(val state: AgentRunState, val detail: String? = null) : AgentEvent
    @Serializable @SerialName("error") data class Error(val message: String, val retryable: Boolean = false) : AgentEvent
}

object ConversationProjection {
    fun latestAssistantText(events: List<AgentEvent>): String {
        val lastUser = events.indexOfLast { it is AgentEvent.UserMessage }
        if (lastUser < 0) return ""
        val currentTurn = events.drop(lastUser + 1)
        val lastStart = currentTurn.indexOfLast { it is AgentEvent.AssistantMessageStarted }
        if (lastStart < 0) return ""
        return currentTurn.drop(lastStart + 1)
            .takeWhile { it !is AgentEvent.AssistantMessageCompleted }
            .filterIsInstance<AgentEvent.AssistantDelta>()
            .joinToString("") { it.text }
    }
}
