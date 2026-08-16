package com.phoneagent.app

import com.phoneagent.dsh.DshRuntimeProvisioner
import com.phoneagent.harness.HarnessKind
import com.phoneagent.provider.ProviderProfile
import com.phoneagent.provider.ProviderProtocol
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.RunRequest
import com.phoneagent.runtime.RuntimeOutput
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable

@Serializable
enum class CliPermissionMode { READ_ONLY, WORKSPACE_WRITE, FULL_ACCESS }

@Serializable
data class CliHarnessMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val error: Boolean = false,
)

@Serializable
data class CliHarnessThread(
    val id: String,
    val title: String,
    val messages: List<CliHarnessMessage>,
    val externalSessionId: String? = null,
    val workspacePath: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class CliHarnessUiState(
    val messages: List<CliHarnessMessage> = emptyList(),
    val draft: String = "",
    val running: Boolean = false,
    val status: String = "就绪",
    val externalSessionId: String? = null,
    val activeThreadId: String = UUID.randomUUID().toString(),
    val threads: List<CliHarnessThread> = emptyList(),
    val workspacePath: String = "",
    val permissionMode: CliPermissionMode = CliPermissionMode.WORKSPACE_WRITE,
)

/**
 * Runs the two bundled upstream CLIs inside sai's Debian/PRoot environment.
 * This is intentionally a real process adapter: output is parsed from Codex
 * JSONL and Claude stream-json rather than being simulated by the Android UI.
 */
class BundledCliHarnessController(
    private val runtime: LinuxRuntime,
    private val provisioner: DshRuntimeProvisioner,
    private val providerSettings: ProviderSettingsRepository,
    private val historyRoot: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val jobs = mutableMapOf<HarnessKind, Job>()
    private val _states = MutableStateFlow(mapOf(
        HarnessKind.CODEX to load(HarnessKind.CODEX),
        HarnessKind.CLAUDE_CODE to load(HarnessKind.CLAUDE_CODE),
    ))
    val states: StateFlow<Map<HarnessKind, CliHarnessUiState>> = _states.asStateFlow()

    fun setDraft(kind: HarnessKind, value: String) = mutate(kind) { it.copy(draft = value) }

    fun setPermissionMode(kind: HarnessKind, mode: CliPermissionMode) = mutate(kind) { it.copy(permissionMode = mode) }

    fun newThread(kind: HarnessKind, workspace: File? = null) {
        jobs.remove(kind)?.cancel()
        mutate(kind) { state ->
            state.copy(
                messages = emptyList(), draft = "", running = false, status = "就绪",
                externalSessionId = null, activeThreadId = UUID.randomUUID().toString(),
                workspacePath = workspace?.absolutePath ?: state.workspacePath,
            )
        }
    }

    fun selectThread(kind: HarnessKind, threadId: String) {
        if (jobs[kind]?.isActive == true) return
        mutate(kind) { state ->
            val thread = state.threads.firstOrNull { it.id == threadId } ?: return@mutate state
            state.copy(
                activeThreadId = thread.id, messages = thread.messages, draft = "", running = false,
                status = "历史会话", externalSessionId = thread.externalSessionId,
                workspacePath = thread.workspacePath,
            )
        }
    }

    fun deleteThread(kind: HarnessKind, threadId: String) {
        if (jobs[kind]?.isActive == true && _states.value[kind]?.activeThreadId == threadId) return
        mutate(kind) { state ->
            val remaining = state.threads.filterNot { it.id == threadId }
            if (state.activeThreadId != threadId) state.copy(threads = remaining)
            else {
                val next = remaining.maxByOrNull { it.updatedAt }
                if (next == null) CliHarnessUiState(permissionMode = state.permissionMode)
                else state.copy(
                    threads = remaining, activeThreadId = next.id, messages = next.messages,
                    externalSessionId = next.externalSessionId, workspacePath = next.workspacePath,
                    draft = "", running = false, status = "历史会话",
                )
            }
        }
    }

    fun clear(kind: HarnessKind) {
        newThread(kind)
    }

    fun cancel(kind: HarnessKind) {
        jobs.remove(kind)?.cancel()
        mutate(kind) { it.copy(running = false, status = "已停止") }
    }

    fun send(kind: HarnessKind, workspace: File, profile: ProviderProfile) {
        require(kind == HarnessKind.CODEX || kind == HarnessKind.CLAUDE_CODE)
        val prompt = _states.value[kind]?.draft?.trim().orEmpty()
        if (prompt.isBlank() || jobs[kind]?.isActive == true) return
        val credential = providerSettings.credentialFor(profile.id)
        if (credential == null) {
            mutate(kind) { it.copy(status = "当前提供商尚未保存 API Key") }
            return
        }
        if (kind == HarnessKind.CODEX && profile.protocol !in setOf(ProviderProtocol.OPENAI_RESPONSES, ProviderProtocol.OPENAI_CHAT)) {
            mutate(kind) { it.copy(status = "Codex 需要 OpenAI/兼容协议提供商") }
            return
        }
        if (kind == HarnessKind.CLAUDE_CODE && profile.protocol != ProviderProtocol.ANTHROPIC_MESSAGES) {
            mutate(kind) { it.copy(status = "Claude Code 需要 Anthropic/兼容协议提供商") }
            return
        }
        workspace.mkdirs()
        val previous = _states.value[kind]
        mutate(kind) {
            it.copy(
                draft = "",
                running = true,
                status = "正在启动 ${label(kind)}…",
                workspacePath = workspace.absolutePath,
                messages = it.messages + CliHarnessMessage(role = "user", text = prompt),
            )
        }
        jobs[kind] = scope.launch {
            val pending = StringBuilder()
            val assistant = StringBuilder()
            var sessionId = previous?.externalSessionId
            fun accept(output: RuntimeOutput) {
                pending.append(output.text)
                while (true) {
                    val end = pending.indexOf("\n")
                    if (end < 0) break
                    val line = pending.substring(0, end).trim()
                    pending.delete(0, end + 1)
                    if (line.isEmpty()) continue
                    if (output.isError) {
                        mutate(kind) { it.copy(status = line.takeLast(180)) }
                    } else {
                        val parsed = parseLine(kind, line)
                        parsed.sessionId?.let { sessionId = it }
                        if (!parsed.text.isNullOrBlank()) {
                            assistant.append(parsed.text)
                            upsertAssistant(kind, assistant.toString())
                        }
                        parsed.status?.let { status -> mutate(kind) { it.copy(status = status) } }
                    }
                }
            }
            val result = runCatching {
                runtime.runStreaming(
                    RunRequest(
                        command = command(kind, prompt, profile, sessionId, previous?.permissionMode ?: CliPermissionMode.WORKSPACE_WRITE),
                        workingDirectory = "/home/phoneagent",
                        environment = environment(kind, profile),
                        sensitiveEnvironment = mapOf(secretName(kind) to credential.apiKey),
                        workspaceHostPath = workspace.absolutePath,
                        trustedBinds = mapOf(provisioner.current.absolutePath to "/opt/sai-dsh"),
                        timeoutMillis = 30 * 60_000L,
                        outputLimitBytes = 8_000_000,
                    ),
                    ::accept,
                )
            }
            val tail = pending.toString().trim()
            if (tail.isNotEmpty()) accept(RuntimeOutput("$tail\n", false))
            result.fold(
                onSuccess = { process ->
                    val ok = process.exitCode == 0 && !process.timedOut
                    if (!ok && assistant.isEmpty()) {
                        val error = (process.stderr.ifBlank { process.stdout }).trim().takeLast(1600)
                        append(kind, CliHarnessMessage(role = "system", text = error.ifBlank { "进程退出码 ${process.exitCode}" }, error = true))
                    }
                    mutate(kind) { it.copy(running = false, status = if (ok) "已完成" else "运行失败", externalSessionId = sessionId) }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@fold
                    append(kind, CliHarnessMessage(role = "system", text = error.message ?: "运行失败", error = true))
                    mutate(kind) { it.copy(running = false, status = "运行失败") }
                },
            )
            jobs.remove(kind)
        }
    }

    private data class Parsed(val text: String? = null, val status: String? = null, val sessionId: String? = null)

    private fun parseLine(kind: HarnessKind, line: String): Parsed {
        val root = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return Parsed(status = line.takeLast(160))
        return if (kind == HarnessKind.CODEX) parseCodex(root) else parseClaude(root)
    }

    private fun parseCodex(root: JsonObject): Parsed {
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (type == "thread.started") return Parsed(sessionId = root["thread_id"]?.jsonPrimitive?.contentOrNull, status = "会话已建立")
        val item = root["item"] as? JsonObject
        val itemType = item?.get("type")?.jsonPrimitive?.contentOrNull
        val text = when (itemType) {
            "agent_message" -> item["text"]?.jsonPrimitive?.contentOrNull
            "reasoning" -> item["text"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        return Parsed(text = text, status = when {
            type == "turn.started" -> "正在工作"
            type == "turn.completed" -> "正在收尾"
            type.startsWith("item.") && itemType != null -> "${itemType.replace('_', ' ')}"
            else -> null
        })
    }

    private fun parseClaude(root: JsonObject): Parsed {
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sessionId = root["session_id"]?.jsonPrimitive?.contentOrNull
        if (type == "result") return Parsed(root["result"]?.jsonPrimitive?.contentOrNull, "正在收尾", sessionId)
        val message = root["message"] as? JsonObject
        val content = message?.get("content") as? JsonArray
        val text = content?.mapNotNull { part ->
            (part as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                ?.get("text")?.jsonPrimitive?.contentOrNull
        }?.joinToString("")
        return Parsed(text, if (type == "assistant") "正在工作" else null, sessionId)
    }

    private fun command(kind: HarnessKind, prompt: String, profile: ProviderProfile, sessionId: String?, permission: CliPermissionMode): String {
        val promptArg = prompt.shellQuote()
        val model = profile.defaultModel.shellQuote()
        return if (kind == HarnessKind.CODEX) {
            val executable = "/opt/sai-dsh/app/node_modules/@openai/codex/bin/codex.js"
            val resume = sessionId?.let { "resume ${it.shellQuote()} " }.orEmpty()
            val sandbox = when (permission) {
                CliPermissionMode.READ_ONLY -> "read-only"
                CliPermissionMode.WORKSPACE_WRITE -> "workspace-write"
                CliPermissionMode.FULL_ACCESS -> "danger-full-access"
            }
            "exec /opt/sai-dsh/node/bin/node $executable exec $resume--json --skip-git-repo-check --sandbox $sandbox --model $model $promptArg"
        } else {
            val executable = "/opt/sai-dsh/app/node_modules/@anthropic-ai/claude-code/cli-wrapper.cjs"
            val resume = sessionId?.let { "--resume ${it.shellQuote()} " }.orEmpty()
            val mode = when (permission) {
                CliPermissionMode.READ_ONLY -> "default"
                CliPermissionMode.WORKSPACE_WRITE -> "acceptEdits"
                CliPermissionMode.FULL_ACCESS -> "bypassPermissions"
            }
            "exec /opt/sai-dsh/node/bin/node $executable -p --verbose --output-format stream-json --permission-mode $mode $resume--model $model $promptArg"
        }
    }

    private fun environment(kind: HarnessKind, profile: ProviderProfile): Map<String, String> = if (kind == HarnessKind.CODEX) {
        mapOf("OPENAI_BASE_URL" to profile.baseUrl, "CODEX_HOME" to "/home/phoneagent/.sai/codex")
    } else {
        mapOf("ANTHROPIC_BASE_URL" to profile.baseUrl)
    }

    private fun secretName(kind: HarnessKind) = if (kind == HarnessKind.CODEX) "OPENAI_API_KEY" else "ANTHROPIC_API_KEY"
    private fun label(kind: HarnessKind) = if (kind == HarnessKind.CODEX) "Codex" else "Claude Code"
    private fun String.shellQuote() = "'" + replace("'", "'\\''") + "'"

    private fun mutate(kind: HarnessKind, block: (CliHarnessUiState) -> CliHarnessUiState) {
        _states.update { states ->
            val changed = block(states[kind] ?: CliHarnessUiState())
            val thread = CliHarnessThread(
                id = changed.activeThreadId,
                title = threadTitle(changed.messages),
                messages = changed.messages,
                externalSessionId = changed.externalSessionId,
                workspacePath = changed.workspacePath,
            )
            val threads = if (changed.messages.isEmpty()) changed.threads
            else (changed.threads.filterNot { it.id == thread.id } + thread).sortedByDescending { it.updatedAt }
            val normalized = changed.copy(threads = threads)
            persist(kind, normalized)
            states + (kind to normalized)
        }
    }

    private fun append(kind: HarnessKind, message: CliHarnessMessage) = mutate(kind) { it.copy(messages = it.messages + message) }

    private fun upsertAssistant(kind: HarnessKind, text: String) = mutate(kind) { state ->
        val last = state.messages.lastOrNull()
        if (last?.role == "assistant") state.copy(messages = state.messages.dropLast(1) + last.copy(text = text))
        else state.copy(messages = state.messages + CliHarnessMessage(role = "assistant", text = text))
    }

    private fun threadTitle(messages: List<CliHarnessMessage>): String = messages.firstOrNull { it.role == "user" }
        ?.text?.replace(Regex("\\s+"), " ")?.trim()?.take(32)?.ifBlank { null } ?: "新会话"

    private fun historyFile(kind: HarnessKind) = File(historyRoot, "${kind.name.lowercase()}.json")

    private fun load(kind: HarnessKind): CliHarnessUiState {
        val file = historyFile(kind)
        return runCatching { json.decodeFromString<CliHarnessUiState>(file.readText()) }
            .getOrNull()?.copy(running = false, status = "就绪", draft = "") ?: CliHarnessUiState()
    }

    private fun persist(kind: HarnessKind, state: CliHarnessUiState) {
        runCatching {
            historyRoot.mkdirs()
            val target = historyFile(kind)
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(json.encodeToString(CliHarnessUiState.serializer(), state.copy(running = false, draft = "")))
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }
}
