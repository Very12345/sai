package com.phoneagent.app

import com.phoneagent.dsh.DshRuntimeProvisioner
import com.phoneagent.harness.HarnessKind
import com.phoneagent.provider.ProviderProtocol
import com.phoneagent.provider.ProviderProfile
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.RunRequest
import com.phoneagent.runtime.RuntimeJob
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HarnessWebPhase { STOPPED, PREPARING, STARTING, READY, FAILED }

data class HarnessWebRuntimeState(
    val phase: HarnessWebPhase = HarnessWebPhase.STOPPED,
    val detail: String = "",
    val url: String? = null,
) {
    val ready: Boolean get() = phase == HarnessWebPhase.READY && url != null
}

/**
 * Starts the audited mobile Web GUIs that sit on top of the real Codex and
 * Claude Code runtimes. The GUI never implements an agent loop: Codex is
 * driven through app-server and Claude through its official CLI protocol.
 */
class HarnessWebRuntimeSupervisor(
    private val runtime: LinuxRuntime,
    private val provisioner: DshRuntimeProvisioner,
    private val workspaceRoot: File,
    private val providerSnapshot: () -> Pair<ProviderProfile, CharArray?>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = ConcurrentHashMap<HarnessKind, Mutex>()
    private val jobs = ConcurrentHashMap<HarnessKind, RuntimeJob>()
    private val monitors = ConcurrentHashMap<HarnessKind, Job>()
    private val _states = MutableStateFlow(defaultStates())
    val states: StateFlow<Map<HarnessKind, HarnessWebRuntimeState>> = _states.asStateFlow()

    fun ensureStarted(kind: HarnessKind) {
        require(kind in SUPPORTED)
        if (_states.value[kind]?.phase in setOf(HarnessWebPhase.PREPARING, HarnessWebPhase.STARTING, HarnessWebPhase.READY)) return
        scope.launch { start(kind) }
    }

    suspend fun restart(kind: HarnessKind) = mutex(kind).withLock {
        stopProcess(kind)
        startProcess(kind)
    }

    suspend fun stop(kind: HarnessKind) = mutex(kind).withLock {
        stopProcess(kind)
        update(kind, HarnessWebRuntimeState())
    }

    private suspend fun start(kind: HarnessKind) = mutex(kind).withLock {
        runCatching {
            update(kind, HarnessWebRuntimeState(HarnessWebPhase.PREPARING, "正在准备 ${label(kind)} GUI"))
            provisioner.install()
            startProcess(kind)
        }.onFailure { error ->
            update(kind, HarnessWebRuntimeState(HarnessWebPhase.FAILED, error.message ?: "${label(kind)} GUI 启动失败"))
        }
    }

    private suspend fun startProcess(kind: HarnessKind) {
        val spec = spec(kind)
        val guiEntry = File(provisioner.current, spec.hostEntry)
        check(guiEntry.isFile) {
            "离线运行时缺少 ${label(kind)} GUI；请重新生成 sai Harness 运行时"
        }
        update(kind, HarnessWebRuntimeState(HarnessWebPhase.STARTING, "正在启动 ${label(kind)} GUI"))
        val (profile, credential) = providerSnapshot()
        val sensitive = linkedMapOf<String, String>()
        try {
            if (credential != null) {
                when (kind) {
                    HarnessKind.CODEX -> if (profile.protocol in setOf(ProviderProtocol.OPENAI_RESPONSES, ProviderProtocol.OPENAI_CHAT)) {
                        sensitive["OPENAI_API_KEY"] = credential.concatToString()
                    }
                    HarnessKind.CLAUDE_CODE -> if (profile.protocol == ProviderProtocol.ANTHROPIC_MESSAGES) {
                        sensitive["ANTHROPIC_API_KEY"] = credential.concatToString()
                    }
                    else -> Unit
                }
            }
        } finally {
            credential?.fill('\u0000')
        }
        val environment = linkedMapOf(
            "HOME" to "/var/lib/sai-dsh",
            "PATH" to "/opt/sai-dsh/app/node_modules/.bin:/opt/sai-dsh/node/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "NODE_ENV" to "production",
        )
        when (kind) {
            HarnessKind.CODEX -> {
                environment["CODEX_HOME"] = "/var/lib/sai-dsh/.codex"
                val codexCommand = File(provisioner.current, "app/node_modules/.bin/codex")
                val codexFiles = File(provisioner.current, "app/node_modules/@openai")
                    .walkTopDown().filter(File::isFile).toList()
                val codexNative = codexFiles.firstOrNull { it.parentFile?.name == "bin" && it.name == "codex" }
                    ?: error("离线运行时缺少 Codex 原生二进制")
                codexFiles.filter { it.name in CODEX_EXECUTABLES }.forEach { binary ->
                    check(binary.canExecute() || binary.setExecutable(true, true)) { "Codex 组件不可执行：${binary.name}" }
                }
                check(codexCommand.isFile && (codexCommand.canExecute() || codexCommand.setExecutable(true, true)) &&
                    (codexNative.canExecute() || codexNative.setExecutable(true, true))) {
                    "Codex 命令不可执行"
                }
                environment["CODEXUI_CODEX_COMMAND"] = "/opt/sai-dsh/app/node_modules/.bin/codex"
                if (profile.protocol in setOf(ProviderProtocol.OPENAI_RESPONSES, ProviderProtocol.OPENAI_CHAT)) {
                    environment["OPENAI_BASE_URL"] = profile.baseUrl.trimEnd('/')
                }
            }
            HarnessKind.CLAUDE_CODE -> {
                environment["CLAUDE_CONFIG_DIR"] = "/var/lib/sai-dsh/.claude"
                if (profile.protocol == ProviderProtocol.ANTHROPIC_MESSAGES) {
                    environment["ANTHROPIC_BASE_URL"] = profile.baseUrl.trimEnd('/')
                }
            }
            else -> Unit
        }
        val command = if (kind == HarnessKind.CLAUDE_CODE) {
            val nativePackage = File(provisioner.current, "app/node_modules/@anthropic-ai")
                .listFiles().orEmpty()
                .firstOrNull { it.isDirectory && it.name.startsWith("claude-code-linux-") }
                ?: error("离线运行时缺少 Claude Code 原生二进制")
            val nativeBinary = File(nativePackage, "claude")
            check(nativeBinary.isFile && (nativeBinary.canExecute() || nativeBinary.setExecutable(true, true))) {
                "Claude Code 原生二进制不可执行"
            }
            "exec /opt/sai-dsh/node/bin/node /opt/sai-dsh/app/gui/claude/dist/cli/node.js " +
                "--port 3091 --host 127.0.0.1 --claude-path /opt/sai-dsh/app/node_modules/@anthropic-ai/${nativePackage.name}/claude"
        } else spec.command
        jobs[kind] = runtime.startJob(
            RunRequest(
                command = command,
                workingDirectory = "/workspace",
                workspaceHostPath = workspaceRoot.absolutePath,
                environment = environment,
                sensitiveEnvironment = sensitive,
                timeoutMillis = Long.MAX_VALUE,
                outputLimitBytes = 512 * 1024,
                trustedBinds = mapOf(
                    provisioner.current.absolutePath to "/opt/sai-dsh",
                    provisioner.home.absolutePath to "/var/lib/sai-dsh",
                ),
            ),
        )
        sensitive.values.forEach { /* The runtime receives copied strings; do not retain another map. */ }
        repeat(150) {
            if (portOpen(spec.port)) {
                val state = HarnessWebRuntimeState(HarnessWebPhase.READY, "${label(kind)} GUI 已就绪", "http://127.0.0.1:${spec.port}/")
                update(kind, state)
                monitors[kind] = monitor(kind, spec.port)
                return
            }
            delay(200)
        }
        val snapshot = jobs[kind]?.id?.let { id -> runtime.listJobs().firstOrNull { it.id == id } }
        error(snapshot?.outputPreview?.takeLast(2_000).orEmpty().ifBlank { "${label(kind)} GUI 未在 30 秒内就绪" })
    }

    private fun monitor(kind: HarnessKind, port: Int) = scope.launch {
        var failures = 0
        while (true) {
            delay(5_000)
            failures = if (portOpen(port)) 0 else failures + 1
            if (failures >= 3) {
                update(kind, HarnessWebRuntimeState(HarnessWebPhase.FAILED, "${label(kind)} GUI 已停止响应"))
                return@launch
            }
        }
    }

    private suspend fun stopProcess(kind: HarnessKind) {
        monitors.remove(kind)?.cancel()
        jobs.remove(kind)?.let { runtime.stopJob(it.id) }
        repeat(50) {
            if (!portOpen(spec(kind).port)) return
            delay(100)
        }
    }

    private fun portOpen(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 160) }
        true
    }.getOrDefault(false)

    private fun update(kind: HarnessKind, value: HarnessWebRuntimeState) = _states.update { it + (kind to value) }
    private fun mutex(kind: HarnessKind) = lifecycle.computeIfAbsent(kind) { Mutex() }

    private data class Spec(val port: Int, val hostEntry: String, val command: String)

    private fun spec(kind: HarnessKind) = when (kind) {
        HarnessKind.CODEX -> Spec(
            3090,
            "app/gui/codex/dist-cli/index.js",
            "exec /opt/sai-dsh/node/bin/node /opt/sai-dsh/app/gui/codex/dist-cli/index.js --port 3090 --no-password --no-tunnel --no-open --no-login",
        )
        HarnessKind.CLAUDE_CODE -> Spec(
            3091,
            "app/gui/claude/dist/cli/node.js",
            "",
        )
        else -> error("Unsupported Web harness: $kind")
    }

    private fun label(kind: HarnessKind) = if (kind == HarnessKind.CODEX) "Codex" else "Claude Code"

    companion object {
        private val SUPPORTED = setOf(HarnessKind.CODEX, HarnessKind.CLAUDE_CODE)
        private val CODEX_EXECUTABLES = setOf("codex", "codex-code-mode-host", "rg", "bwrap", "zsh")
        private fun defaultStates() = SUPPORTED.associateWith { HarnessWebRuntimeState() }
    }
}
