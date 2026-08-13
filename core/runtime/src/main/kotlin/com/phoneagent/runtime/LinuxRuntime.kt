package com.phoneagent.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.io.Closeable

@Serializable
data class RunRequest(
    val command: String,
    val workingDirectory: String,
    val environment: Map<String, String> = emptyMap(),
    val timeoutMillis: Long = 120_000,
    val outputLimitBytes: Int = 1_000_000,
    /** Host-side Android directory mounted at /home/phoneagent for this run. */
    val workspaceHostPath: String? = null,
    /** Never serialized or surfaced to the Agent event log. Only trusted app services may set it. */
    @kotlinx.serialization.Transient
    val sensitiveEnvironment: Map<String, String> = emptyMap(),
)

@Serializable
data class RunResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
    val durationMillis: Long = 0,
)

@Serializable
data class RuntimeJob(
    val id: String,
    val command: String,
    val state: JobState,
    val startedAtEpochMillis: Long,
    val exitCode: Int? = null,
    val outputPreview: String = "",
    val outputTruncated: Boolean = false,
)

@Serializable
enum class JobState { RUNNING, COMPLETED, FAILED, CANCELLED }

sealed interface TerminalEvent {
    data class Output(val bytes: ByteArray) : TerminalEvent
    data class Closed(val exitCode: Int) : TerminalEvent
    data class Failure(val message: String) : TerminalEvent
}

interface PtySession : Closeable {
    val events: Flow<TerminalEvent>
    suspend fun write(bytes: ByteArray)
    suspend fun resize(columns: Int, rows: Int)
}

interface LinuxRuntime {
    suspend fun probe(): RuntimeCapability
    suspend fun run(request: RunRequest): RunResult
    /** Runs a command while forwarding stdout/stderr chunks as soon as they arrive. */
    suspend fun runStreaming(request: RunRequest, onOutput: (RuntimeOutput) -> Unit): RunResult {
        val result = run(request)
        if (result.stdout.isNotEmpty()) onOutput(RuntimeOutput(result.stdout, isError = false))
        if (result.stderr.isNotEmpty()) onOutput(RuntimeOutput(result.stderr, isError = true))
        return result
    }
    suspend fun startJob(request: RunRequest): RuntimeJob
    suspend fun listJobs(): List<RuntimeJob>
    suspend fun stopJob(id: String): Boolean
    suspend fun openPty(
        workingDirectory: String,
        environment: Map<String, String> = emptyMap(),
        workspaceHostPath: String? = null,
    ): PtySession
}

data class RuntimeOutput(
    val text: String,
    val isError: Boolean,
)

@Serializable
data class RuntimeCapability(
    val available: Boolean,
    val architecture: String,
    val rootfsReady: Boolean,
    val prootReady: Boolean,
    val pythonVersion: String? = null,
    val gitVersion: String? = null,
    val detail: String,
)
