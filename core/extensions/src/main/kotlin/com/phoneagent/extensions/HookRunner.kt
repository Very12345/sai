package com.phoneagent.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class HookInput(
    val phase: String,
    val toolName: String,
    val argumentsJson: String,
    val workspace: String,
)

@Serializable
data class HookOutput(
    val decision: HookDecision = HookDecision.ALLOW,
    val message: String? = null,
    val replacementArgumentsJson: String? = null,
)

@Serializable
enum class HookDecision { ALLOW, WARN, BLOCK }

data class HookDefinition(
    val id: String,
    val command: List<String>,
    val workingDirectory: File,
    val timeoutMillis: Long = 10_000,
)

class HookRunner(private val json: Json = Json { ignoreUnknownKeys = true }) {
    suspend fun run(hook: HookDefinition, input: HookInput): HookOutput = withContext(Dispatchers.IO) {
        require(hook.command.isNotEmpty()) { "Hook command is empty" }
        val process = ProcessBuilder(hook.command)
            .directory(hook.workingDirectory)
            .redirectErrorStream(false)
            .start()
        process.outputStream.bufferedWriter().use { it.write(json.encodeToString(input)) }
        if (!process.waitFor(hook.timeoutMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return@withContext HookOutput(HookDecision.BLOCK, "Hook ${hook.id} timed out")
        }
        val stderr = process.errorStream.bufferedReader().readText().take(2_000)
        if (process.exitValue() != 0) return@withContext HookOutput(HookDecision.BLOCK, "Hook ${hook.id} failed: $stderr")
        runCatching { json.decodeFromString<HookOutput>(process.inputStream.bufferedReader().readText()) }
            .getOrElse { HookOutput(HookDecision.BLOCK, "Invalid hook output: ${it.message}") }
    }
}

