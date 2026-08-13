package com.phoneagent.dsh

import com.phoneagent.runtime.JobState
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.RunRequest
import com.phoneagent.runtime.RuntimeJob
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

class DshRuntimeSupervisor(
    private val runtime: LinuxRuntime,
    private val provisioner: DshRuntimeProvisioner,
    private val workspace: File,
    private val bridgeEndpoint: () -> DshBridgeEndpoint,
    private val prepareConfiguration: suspend () -> Unit = {},
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()
    private val requested = AtomicBoolean(false)
    private var job: RuntimeJob? = null
    private var webToken: String? = null
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DshRuntimeState> = _state.asStateFlow()

    fun ensureStarted() {
        if (!requested.compareAndSet(false, true)) return
        scope.launch { startLocked() }
    }

    suspend fun awaitReady(timeoutMillis: Long = 30_000): DshRuntimeState = withTimeout(timeoutMillis) {
        state.first { it.phase == DshRuntimePhase.READY || it.phase == DshRuntimePhase.FAILED }.also {
            check(it.phase == DshRuntimePhase.READY) { it.detail }
        }
    }

    suspend fun restart() {
        lifecycle.withLock {
            stopProcess()
            requested.set(true)
            startProcess()
        }
    }

    suspend fun rollback() = lifecycle.withLock {
        stopProcess()
        val version = provisioner.rollback()
        requested.set(true)
        _state.value = DshRuntimeState(
            DshRuntimePhase.STARTING,
            "Starting rolled back DeepSeek Harness",
            runtimeVersion = version,
        )
        startProcess()
    }

    suspend fun restoreBundledRuntime() = lifecycle.withLock {
        stopProcess()
        provisioner.selectBundledRuntime()
        requested.set(true)
        _state.value = DshRuntimeState(DshRuntimePhase.INSTALLING, "Restoring bundled DSH runtime", 0f)
        provisioner.install { progress ->
            _state.value = DshRuntimeState(DshRuntimePhase.INSTALLING, "Restoring bundled DSH runtime", progress)
        }
        startProcess()
    }

    suspend fun stop() = lifecycle.withLock {
        requested.set(false)
        _state.value = _state.value.copy(phase = DshRuntimePhase.STOPPING, detail = "Stopping DSH")
        stopProcess()
        _state.value = initialState()
    }

    private suspend fun startLocked() = lifecycle.withLock {
        runCatching {
            if (!provisioner.isInstalled()) {
                _state.value = DshRuntimeState(DshRuntimePhase.INSTALLING, "Installing offline DSH runtime", 0f)
                provisioner.install { progress ->
                    _state.value = DshRuntimeState(DshRuntimePhase.INSTALLING, "Installing offline DSH runtime", progress)
                }
            }
            provisioner.ensureBundledPresets()
            startProcess()
        }.onFailure { error ->
            requested.set(false)
            _state.value = DshRuntimeState(DshRuntimePhase.FAILED, error.message ?: "DSH failed to start")
        }
    }

    private suspend fun startProcess() {
        prepareConfiguration()
        val manifest = provisioner.manifest
        val node = File(provisioner.current, "node/bin/node").absolutePath.shellQuote()
        val launcher = File(provisioner.current, "app/sai-dsh-launcher.mjs").absolutePath.shellQuote()
        val command = "exec $node $launcher"
        val bridge = bridgeEndpoint()
        val accessToken = ByteArray(32).also(SecureRandom()::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it).also { _ -> it.fill(0) }
        }
        webToken = accessToken
        val activeVersion = provisioner.activeRuntimeVersion ?: manifest.runtimeVersion
        _state.value = DshRuntimeState(DshRuntimePhase.STARTING, "Starting DeepSeek Harness", runtimeVersion = activeVersion)
        job = runtime.startJob(
            RunRequest(
                command = command,
                workingDirectory = "/home/phoneagent",
                workspaceHostPath = workspace.absolutePath,
                environment = mapOf(
                    "DSH_HOME" to provisioner.home.absolutePath,
                    "DSH_TELEMETRY_DISABLED" to "1",
                    "DSH_PERMISSION_MODE" to "workspace-write",
                    "NODE_ENV" to "production",
                ),
                sensitiveEnvironment = mapOf(
                    "SAI_BRIDGE_URL" to bridge.url,
                    "SAI_BRIDGE_TOKEN" to bridge.token,
                    "SAI_WEB_TOKEN" to accessToken,
                    "SAI_DSH_PORT" to manifest.port.toString(),
                ),
                timeoutMillis = Long.MAX_VALUE,
                outputLimitBytes = 512 * 1024,
            ),
        )
        val url = "http://127.0.0.1:${manifest.port}/"
        repeat(100) {
            if (!requested.get()) return
            if (health(url, accessToken)) {
                _state.value = DshRuntimeState(
                    DshRuntimePhase.READY,
                    "DeepSeek Harness ${manifest.dshVersion} ready",
                    1f,
                    url,
                    activeVersion,
                    accessToken,
                )
                monitor(url, accessToken)
                return
            }
            delay(200)
        }
        val snapshot = job?.id?.let { id -> runtime.listJobs().firstOrNull { it.id == id } }
        error(snapshot?.outputPreview?.takeLast(2_000).orEmpty().ifBlank { "DSH did not become ready in 20 seconds" })
    }

    private fun monitor(url: String, accessToken: String) = scope.launch {
        var failures = 0
        while (requested.get()) {
            delay(5_000)
            failures = if (health(url, accessToken)) 0 else failures + 1
            if (failures >= 3) {
                _state.value = _state.value.copy(phase = DshRuntimePhase.FAILED, detail = "DSH stopped responding")
                requested.set(false)
                break
            }
        }
    }

    private fun health(url: String, accessToken: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(url.trimEnd('/') + "/__sai_health")
            .header("Authorization", "Bearer $accessToken").head().build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private suspend fun stopProcess() {
        job?.let { runtime.stopJob(it.id) }
        job = null
        webToken = null
    }

    private fun initialState() = if (provisioner.isInstalled()) {
        DshRuntimeState(DshRuntimePhase.NOT_INSTALLED, "DSH is installed and stopped", runtimeVersion = provisioner.activeRuntimeVersion)
    } else DshRuntimeState()

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"
}
