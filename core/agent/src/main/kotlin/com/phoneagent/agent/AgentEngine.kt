package com.phoneagent.agent

import com.phoneagent.provider.MessageRole
import com.phoneagent.provider.ModelEvent
import com.phoneagent.provider.ModelMessage
import com.phoneagent.provider.ModelRequest
import com.phoneagent.provider.ModelToolCall
import com.phoneagent.provider.ModelContentPart
import com.phoneagent.provider.ProviderAdapter
import com.phoneagent.provider.ProviderCredential
import com.phoneagent.provider.ModelReasoningPolicy
import com.phoneagent.provider.ReasoningMode
import com.phoneagent.provider.ReasoningParameterFormat
import com.phoneagent.provider.ReasoningSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.util.UUID

class AgentEngine(
    private val provider: ProviderAdapter,
    private val credential: ProviderCredential,
    private val tools: ToolRegistry,
    private val approvalGate: ApprovalGate,
    private val approvalPolicy: ApprovalPolicy = ApprovalPolicy(),
    private val contextManager: ContextManager = ContextManager(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val checkpointSink: suspend (AgentCheckpoint) -> Unit = {},
    private val steering: AgentSteering = AgentSteering(),
) {
    private val writeMutex = Mutex()
    private val secretRedactor = SecretRedactor(listOf(credential.apiKey))

    fun run(
        config: AgentRunConfig,
        userPrompt: String,
        restoredMessages: List<ModelMessage> = emptyList(),
        userContentParts: List<ModelContentPart> = emptyList(),
    ): Flow<AgentEvent> = flow {
        val workspace = File(config.workspacePath).canonicalFile
        require(workspace.isDirectory || workspace.mkdirs()) { "Cannot create workspace ${workspace.path}" }
        emit(AgentEvent.RunStarted(config.sessionId, config.mode))
        emit(AgentEvent.UserMessage(userPrompt))
        emit(AgentEvent.StateChanged(AgentRunState.RUNNING))

        var messages = if (restoredMessages.isEmpty()) {
            listOf(ModelMessage(MessageRole.SYSTEM, systemPrompt(config, workspace)))
        } else restoredMessages
        messages = messages + ModelMessage(
            role = MessageRole.USER,
            content = userPrompt,
            contentParts = if (userContentParts.isEmpty()) emptyList() else listOf(ModelContentPart.Text(userPrompt)) + userContentParts,
        )
        val loopGuard = ArrayDeque<String>()
        val sessionApprovedTools = mutableSetOf<String>()
        var emptyVisibleRounds = 0
        var requiredToolRepairAttempts = 0
        var requiredToolSatisfied = config.requiredToolNameAfterVisibleResponse == null
        var forceRequiredTool = false
        val runtimeInstructions = linkedSetOf<String>().apply {
            config.additionalSystemInstruction?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        }

        for (round in 0 until config.maxRounds) {
            steering.drain().forEach { steer ->
                steer.additionalSystemInstruction?.let(runtimeInstructions::add)
                messages += ModelMessage(MessageRole.USER, "User changed direction while the task was running:\n${steer.text}")
                emit(AgentEvent.SteerApplied(steer.text))
            }
            val compacted = contextManager.compact(messages, config.contextWindow, config.maxOutputTokens)
            messages = compacted.messages
            compacted.summary?.let { emit(AgentEvent.ContextCompacted(compacted.removedCount, it)) }

            val assistantText = StringBuilder()
            val assistantReasoning = StringBuilder()
            val messageId = UUID.randomUUID().toString()
            val reasoningId = UUID.randomUUID().toString()
            var messageStarted = false
            var reasoningStarted = false
            val toolCalls = mutableListOf<ModelEvent.ToolCall>()
            var terminalError: ModelEvent.Error? = null
            var finishReason: String? = null
            val requestedReasoning = if (
                forceRequiredTool &&
                ModelReasoningPolicy.capabilities(provider.profile.copy(defaultModel = config.model))?.parameterFormat == ReasoningParameterFormat.DEEPSEEK_THINKING
            ) {
                // DeepSeek rejects object/required tool_choice while thinking is enabled.
                // This hidden repair turn only emits speech, so disabling reasoning here
                // preserves the user's reasoning setting for every actual work round.
                ReasoningSelection(mode = ReasoningMode.DISABLED)
            } else config.reasoningSelection
            provider.stream(
                ModelRequest(
                    model = config.model,
                    messages = messages.withRuntimeInstructions(runtimeInstructions),
                    tools = tools.definitions(),
                    requiredToolName = config.requiredToolNameAfterVisibleResponse.takeIf { forceRequiredTool },
                    maxOutputTokens = config.maxOutputTokens,
                    reasoningEffort = config.reasoningEffort,
                    reasoningSelection = requestedReasoning,
                ),
                credential,
            ).collect { event ->
                when (event) {
                    is ModelEvent.TextDelta -> {
                        if (!messageStarted) { emit(AgentEvent.AssistantMessageStarted(messageId)); messageStarted = true }
                        assistantText.append(event.text); emit(AgentEvent.AssistantDelta(event.text))
                    }
                    is ModelEvent.ReasoningDelta -> {
                        if (!reasoningStarted) { emit(AgentEvent.ReasoningStarted(reasoningId)); reasoningStarted = true }
                        assistantReasoning.append(event.text)
                        emit(AgentEvent.ReasoningDelta(event.text))
                    }
                    is ModelEvent.ToolCall -> toolCalls += event
                    is ModelEvent.Usage -> emit(event.toAgentEvent())
                    is ModelEvent.Error -> {
                        terminalError = event
                        emit(AgentEvent.Error(secretRedactor.redact(event.message), event.retryable))
                    }
                    is ModelEvent.Completed -> if (!event.finishReason.isNullOrBlank()) finishReason = event.finishReason
                    else -> Unit
                }
            }
            if (reasoningStarted) emit(AgentEvent.ReasoningCompleted(reasoningId))
            if (messageStarted) emit(AgentEvent.AssistantMessageCompleted(messageId))
            terminalError?.let {
                emit(AgentEvent.StateChanged(AgentRunState.FAILED, secretRedactor.redact(it.message)))
                return@flow
            }
            if (assistantText.isNotEmpty() || assistantReasoning.isNotEmpty() || toolCalls.isNotEmpty()) {
                messages += ModelMessage(
                    role = MessageRole.ASSISTANT,
                    content = assistantText.toString(),
                    toolCalls = toolCalls.map { ModelToolCall(it.id, it.name, it.arguments) },
                    reasoningContent = assistantReasoning.toString().takeIf(String::isNotEmpty),
                )
            }
            val requiredToolName = config.requiredToolNameAfterVisibleResponse
            val hasRequiredTool = requiredToolName != null && toolCalls.any { it.name == requiredToolName }
            val hasWorkAfterSpeech = requiredToolName != null && toolCalls.any { it.name != requiredToolName }
            if (hasWorkAfterSpeech) requiredToolSatisfied = false
            if (hasRequiredTool && !hasWorkAfterSpeech) {
                requiredToolSatisfied = true
                forceRequiredTool = false
            }
            checkpointSink(AgentCheckpoint(config.sessionId, round, "MODEL_RESPONSE", messages))
            if (toolCalls.isEmpty()) {
                if (!requiredToolSatisfied) {
                    if (requiredToolRepairAttempts < 2) {
                        requiredToolRepairAttempts++
                        forceRequiredTool = true
                        emit(AgentEvent.TaskProgress("语音播报工具未调用，正在请求模型补充播报"))
                        messages += ModelMessage(
                            MessageRole.USER,
                            "Protocol correction: do not repeat the visible answer. Call the `${config.requiredToolNameAfterVisibleResponse}` tool now with a concise spoken summary. Return only that tool call and no visible text.",
                        )
                        continue
                    }
                    val message = "模型未按语音通话协议调用 ${config.requiredToolNameAfterVisibleResponse} 工具"
                    emit(AgentEvent.Error(message, retryable = true))
                    emit(AgentEvent.StateChanged(AgentRunState.FAILED, message))
                    return@flow
                }
                if (finishReason.equals("length", ignoreCase = true)) {
                    emit(AgentEvent.TaskProgress("模型输出达到长度上限，正在继续生成可见结论"))
                    messages += ModelMessage(
                        MessageRole.USER,
                        "Continue from the previous response. Do not repeat prior text. Finish the task and provide a concise visible conclusion.",
                    )
                    continue
                }
                if (assistantText.isBlank()) {
                    emptyVisibleRounds++
                    if (emptyVisibleRounds <= 1) {
                        emit(AgentEvent.TaskProgress("模型只返回了思考内容，正在请求可见结论"))
                        messages += ModelMessage(
                            MessageRole.USER,
                            "You returned no visible assistant answer. Provide the user-facing result now. If work is incomplete, use tools; otherwise summarize what was done and how it was verified.",
                        )
                        continue
                    }
                    val message = "模型连续两次未返回可见答复，任务未被标记为完成"
                    emit(AgentEvent.Error(message, retryable = true))
                    emit(AgentEvent.StateChanged(AgentRunState.FAILED, message))
                    return@flow
                }
                emit(AgentEvent.StateChanged(AgentRunState.COMPLETED))
                return@flow
            }
            emptyVisibleRounds = 0

            for (call in toolCalls) {
                emit(AgentEvent.ToolRequested(call.id, call.name, call.arguments))
                emit(AgentEvent.ToolProgress(call.id, call.name, "正在执行"))
                val signature = "${call.name}:${call.arguments}"
                loopGuard += signature
                while (loopGuard.size > 6) loopGuard.removeFirst()
                if (loopGuard.count { it == signature } >= 3) {
                    val result = ToolResult(false, "Stopped repeated identical tool call")
                    emit(AgentEvent.ToolFinished(call.id, call.name, result))
                    messages += ModelMessage(MessageRole.TOOL, result.output, call.id, call.name)
                    continue
                }
                val tool = tools.get(call.name)
                if (tool == null) {
                    val result = ToolResult(false, "Unknown tool: ${call.name}")
                    emit(AgentEvent.ToolFinished(call.id, call.name, result))
                    messages += ModelMessage(MessageRole.TOOL, result.output, call.id, call.name)
                    continue
                }
                val arguments = repairArguments(call.arguments)
                val context = ToolExecutionContext(workspace, config.mode, config.sessionId)
                val authorization = approvalPolicy.authorize(
                    config.mode,
                    tool.capabilities,
                    call.arguments,
                    config.sessionWorkspaceWriteAllowed,
                    config.sessionNormalShellAllowed,
                )
                if (!authorization.allowed) {
                    val result = ToolResult(false, authorization.explanation)
                    emit(AgentEvent.ToolFinished(call.id, call.name, result))
                    messages += ModelMessage(MessageRole.TOOL, result.output, call.id, call.name)
                    continue
                }
                if (authorization.confirmationRequired && call.name !in sessionApprovedTools) {
                    val approval = ApprovalRequest(
                        id = UUID.randomUUID().toString(),
                        toolName = call.name,
                        argumentsJson = call.arguments,
                        capabilities = tool.capabilities,
                        riskExplanation = authorization.explanation,
                        preview = tool.preview(arguments, context),
                    )
                    emit(AgentEvent.StateChanged(AgentRunState.WAITING_APPROVAL))
                    emit(AgentEvent.ApprovalRequested(approval))
                    val decision = approvalGate.request(approval)
                    emit(AgentEvent.ApprovalResolved(approval.id, decision))
                    emit(AgentEvent.StateChanged(AgentRunState.RUNNING))
                    if (decision == ApprovalDecision.DENY) {
                        val result = ToolResult(false, "User denied this action")
                        emit(AgentEvent.ToolFinished(call.id, call.name, result))
                        messages += ModelMessage(MessageRole.TOOL, result.output, call.id, call.name)
                        continue
                    }
                    if (decision == ApprovalDecision.ALLOW_SESSION) sessionApprovedTools += call.name
                }
                val result = secretRedactor.redact(runCatching {
                    if (ToolCapability.WORKSPACE_WRITE in tool.capabilities || ToolCapability.DELETE in tool.capabilities) {
                        writeMutex.withLock { tool.execute(arguments, context) }
                    } else tool.execute(arguments, context)
                }.getOrElse { ToolResult(false, it.message ?: it::class.java.simpleName) })
                emit(AgentEvent.ToolFinished(call.id, call.name, result))
                messages += ModelMessage(MessageRole.TOOL, result.output.take(100_000), call.id, call.name)
                checkpointSink(AgentCheckpoint(config.sessionId, round, "TOOL_FINISHED", messages))
            }
        }
        emit(AgentEvent.StateChanged(AgentRunState.PAUSED, "Reached ${config.maxRounds} round safety limit"))
    }

    private fun repairArguments(raw: String): JsonObject {
        val trimmed = raw.trim().ifBlank { "{}" }
        runCatching { return json.parseToJsonElement(trimmed) as JsonObject }
        val extracted = trimmed.substringAfter('{', "").substringBeforeLast('}', "")
        if (extracted.isNotEmpty()) runCatching { return json.parseToJsonElement("{$extracted}") as JsonObject }
        return buildJsonObject { }
    }

    private fun List<ModelMessage>.withRuntimeInstructions(instructions: Set<String>): List<ModelMessage> =
        if (instructions.isEmpty()) this else buildList {
            val systemEnd = this@withRuntimeInstructions.indexOfLast { it.role == MessageRole.SYSTEM }
            if (systemEnd < 0) {
                add(ModelMessage(MessageRole.SYSTEM, instructions.joinToString("\n\n")))
                addAll(this@withRuntimeInstructions)
            } else {
                addAll(this@withRuntimeInstructions.take(systemEnd + 1))
                add(ModelMessage(MessageRole.SYSTEM, instructions.joinToString("\n\n")))
                addAll(this@withRuntimeInstructions.drop(systemEnd + 1))
            }
        }

    private fun systemPrompt(config: AgentRunConfig, workspace: File): String = """
        You are sai, a mobile coding agent. The name evokes both AI and a sail carrying work forward on a phone.
        Use the available tools when they help you complete the user's request.
        Keep changes focused and responses concise.

        Workspace: ${workspace.path}
        Mode: ${config.mode}
        Inspect evidence before changing files. Keep all paths inside the workspace.
        In Plan mode, do not request write or shell tools. Never expose API keys or hidden application data.
        PRoot is a compatibility environment, not a security sandbox. Avoid destructive commands.
        When a consequential choice has no safe reversible default, use the user-question capability; otherwise proceed with the safest reversible option.
        Reply in the language of the user's latest message. Keep code, identifiers, paths, commands, and technical terms unchanged.
        You may return useful workspace files and web URLs. Format URLs as standard Markdown links. Format generated files as relative Markdown links such as [report.pdf](report.pdf), so sai can render them as actionable file cards. Never fabricate a file or link; verify that a referenced file exists.
        Android WebView and the PRoot environment share the device network. For a local development server, bind it to 127.0.0.1 or 0.0.0.0 and give browser tools http://127.0.0.1:<port>. Do not use a desktop-only hostname.
        Finish with a concise result and verification status.
        User directives beginning with / select a built-in capability. /read-url activates safe URL or repository inspection with http_fetch; /memory uses the explicit project memory file. A token like ${'$'}skill-name invokes the matching enabled SKILL.md instructions supplied by the runtime.
        ${if (config.requiredToolNameAfterVisibleResponse != null) "Voice-call protocol: every completed response MUST invoke `${config.requiredToolNameAfterVisibleResponse}` exactly once. Visible text is never spoken automatically." else ""}
    """.trimIndent()
}
