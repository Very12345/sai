package com.phoneagent.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class ProcessLinuxRuntime(
    private val commandBuilder: ProotCommandBuilder,
    private val rootfs: File,
) : LinuxRuntime {
    private data class ManagedJob(val request: RunRequest, val process: Process, @Volatile var model: RuntimeJob)
    private val jobs = ConcurrentHashMap<String, ManagedJob>()
    private val jobMutex = Mutex()

    override suspend fun probe(): RuntimeCapability {
        if (!rootfs.resolve("bin/bash").exists()) {
            return RuntimeCapability(false, System.getProperty("os.arch") ?: "unknown", false, false, detail = "Debian rootfs is not installed")
        }
        val result = runCatching {
            run(RunRequest("printf '%s\\n' \"$(python3 --version 2>&1)\" \"$(git --version 2>&1)\"", "/home/phoneagent", timeoutMillis = 15_000))
        }.getOrElse { error ->
            return RuntimeCapability(
                available = false,
                architecture = System.getProperty("os.arch") ?: "unknown",
                rootfsReady = true,
                prootReady = false,
                detail = error.message ?: "Local Debian runtime probe failed",
            )
        }
        val lines = result.stdout.lines()
        return RuntimeCapability(
            available = result.exitCode == 0,
            architecture = System.getProperty("os.arch") ?: "unknown",
            rootfsReady = true,
            prootReady = result.exitCode == 0,
            pythonVersion = lines.firstOrNull { it.startsWith("Python") },
            gitVersion = lines.firstOrNull { it.startsWith("git version") }
                ?.removePrefix("git version "),
            detail = if (result.exitCode == 0) "Local Debian runtime ready" else result.stderr,
        )
    }

    override suspend fun run(request: RunRequest): RunResult = runInternal(request, null)

    override suspend fun runStreaming(
        request: RunRequest,
        onOutput: (RuntimeOutput) -> Unit,
    ): RunResult = runInternal(request, onOutput)

    private suspend fun runInternal(
        request: RunRequest,
        onOutput: ((RuntimeOutput) -> Unit)?,
    ): RunResult = withContext(Dispatchers.IO) {
        lateinit var result: RunResult
        val duration = measureTimeMillis {
            val process = process(request).start()
            result = coroutineScope {
                val stdout = async { readLimited(process.inputStream, request.outputLimitBytes, false, onOutput) }
                val stderr = async { readLimited(process.errorStream, request.outputLimitBytes, true, onOutput) }
                var completed = false
                try {
                    completed = withTimeoutOrNull(request.timeoutMillis) {
                        while (process.isAlive) delay(100)
                        true
                    } ?: false
                } finally {
                    if (!completed && process.isAlive) terminateProcessGroup(process, force = false)
                }
                if (!completed && process.isAlive) terminateProcessGroup(process, force = true)
                val out = stdout.await()
                val err = stderr.await()
                RunResult(
                    exitCode = if (completed) process.exitValue() else -1,
                    stdout = out.first,
                    stderr = err.first,
                    timedOut = !completed,
                    truncated = out.second || err.second,
                )
            }
        }
        result.copy(durationMillis = duration)
    }

    override suspend fun startJob(request: RunRequest): RuntimeJob = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val process = process(request).redirectErrorStream(true).start()
        val model = RuntimeJob(id, request.command, JobState.RUNNING, System.currentTimeMillis())
        jobs[id] = ManagedJob(request, process, model)
        Thread {
            val managed = jobs[id] ?: return@Thread
            val output = runCatching { readJobOutput(managed, request.outputLimitBytes) }
                .getOrDefault("" to false)
            val exit = runCatching { process.waitFor() }.getOrDefault(-1)
            jobs[id]?.let { managed ->
                managed.model = managed.model.copy(
                    state = when {
                        managed.model.state == JobState.CANCELLED -> JobState.CANCELLED
                        exit == 0 -> JobState.COMPLETED
                        else -> JobState.FAILED
                    },
                    exitCode = exit,
                    outputPreview = output.first,
                    outputTruncated = output.second,
                )
            }
        }.start()
        model
    }

    override suspend fun listJobs(): List<RuntimeJob> = jobMutex.withLock {
        jobs.values.map { it.model }.sortedByDescending { it.startedAtEpochMillis }
    }

    override suspend fun stopJob(id: String): Boolean = jobMutex.withLock {
        val job = jobs[id] ?: return@withLock false
        job.model = job.model.copy(state = JobState.CANCELLED)

        // Long-running PRoot jobs usually have Node, shell and helper descendants. Destroying
        // only the Java Process leaves those descendants alive, so the next DSH instance races
        // the old listener and fails with EADDRINUSE. Jobs are launched through setsid when the
        // device supports it; terminate the whole process group and wait for the port-owning
        // descendants to actually exit before reporting success.
        terminateProcessGroup(job.process, force = false)
        val stopped = withContext(Dispatchers.IO) {
            runCatching { job.process.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)
        }
        if (!stopped && job.process.isAlive) {
            terminateProcessGroup(job.process, force = true)
            withContext(Dispatchers.IO) {
                runCatching { job.process.waitFor(2, TimeUnit.SECONDS) }
            }
        }
        !job.process.isAlive
    }

    override suspend fun openPty(
        workingDirectory: String,
        environment: Map<String, String>,
        workspaceHostPath: String?,
    ): PtySession =
        withContext(Dispatchers.IO) {
            val completeEnvironment = commandBuilder.hostEnvironment().toMutableMap()
            val guestEnvironment = environment.filterKeys { it !in SECRET_ENV_NAMES }
            NativePtySession.start(
                commandBuilder.interactive(workingDirectory, workspaceHostPath, guestEnvironment),
                completeEnvironment,
            )
        }

    private fun process(request: RunRequest): ProcessBuilder {
        val guestEnvironment = request.environment.filterKeys { it !in SECRET_ENV_NAMES } +
            request.sensitiveEnvironment.filterKeys { it in SECRET_ENV_NAMES }
        val command = commandBuilder.shell(
            request.command,
            request.workingDirectory,
            request.workspaceHostPath,
            guestEnvironment,
            request.trustedBinds,
        )
        val isolated = if (File("/system/bin/setsid").canExecute()) listOf("/system/bin/setsid") + command else command
        return ProcessBuilder(isolated).apply {
            environment().clear()
            environment().putAll(commandBuilder.hostEnvironment())
        }
    }

    private fun terminateProcessGroup(process: Process, force: Boolean) {
        // Android's java.lang.Process API does not expose pid() on every supported API level.
        // Reflection keeps process-group cancellation available while retaining the parent
        // process fallback below when an OEM implementation hides both members.
        val pid = runCatching {
            (process.javaClass.getMethod("pid").invoke(process) as Number).toLong()
        }.recoverCatching {
            var type: Class<*>? = process.javaClass
            var field: java.lang.reflect.Field? = null
            while (type != null && field == null) {
                field = runCatching { type.getDeclaredField("pid") }.getOrNull()
                type = type.superclass
            }
            requireNotNull(field).apply { isAccessible = true }.getLong(process)
        }.getOrNull()
        if (pid != null && File("/system/bin/kill").canExecute()) {
            val signal = if (force) "-KILL" else "-TERM"
            runCatching { ProcessBuilder("/system/bin/kill", signal, "-$pid").start().waitFor() }
        }
        if (process.isAlive) {
            if (force) process.destroyForcibly() else process.destroy()
        }
    }

    private fun readLimited(
        stream: java.io.InputStream,
        limit: Int,
        isError: Boolean,
        onOutput: ((RuntimeOutput) -> Unit)?,
    ): Pair<String, Boolean> {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        var truncated = false
        stream.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (count > 0) onOutput?.invoke(RuntimeOutput(String(buffer, 0, count, Charsets.UTF_8), isError))
                val writable = minOf(count, (limit - total).coerceAtLeast(0))
                if (writable > 0) output.write(buffer, 0, writable)
                total += writable
                if (writable < count) truncated = true
            }
        }
        return output.toString(Charsets.UTF_8.name()) to truncated
    }

    private fun readJobOutput(job: ManagedJob, limit: Int): Pair<String, Boolean> {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        var truncated = false
        job.process.inputStream.use { stream ->
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                val writable = minOf(count, (limit - total).coerceAtLeast(0))
                if (writable > 0) output.write(buffer, 0, writable)
                total += writable
                if (writable < count) truncated = true
                job.model = job.model.copy(
                    outputPreview = output.toString(Charsets.UTF_8.name()),
                    outputTruncated = truncated,
                )
            }
        }
        return output.toString(Charsets.UTF_8.name()) to truncated
    }

    companion object {
        private val SECRET_ENV_NAMES = setOf(
            "OPENAI_API_KEY", "ANTHROPIC_API_KEY", "GEMINI_API_KEY", "DEEPSEEK_API_KEY",
            "GH_TOKEN", "GITHUB_TOKEN",
        "SAI_BRIDGE_URL", "SAI_BRIDGE_TOKEN", "SAI_WEB_TOKEN",
        )
    }
}
