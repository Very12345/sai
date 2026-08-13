package com.phoneagent.agent.tools

import com.phoneagent.agent.Tool
import com.phoneagent.agent.ToolCapability
import com.phoneagent.agent.ToolExecutionContext
import com.phoneagent.agent.ToolResult
import com.phoneagent.provider.ToolDefinition
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.RunRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

class ShellTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "run_command", "Run a Bash command in the local Debian environment.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("command", buildJsonObject { put("type", "string") })
                put("timeout_seconds", buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 600) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add("command") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.SHELL)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String = arguments.string("command")

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val result = runtime.run(RunRequest(
            command = arguments.string("command"),
            workingDirectory = "/home/phoneagent",
            timeoutMillis = arguments.int("timeout_seconds", 120).coerceIn(1, 600) * 1_000L,
            workspaceHostPath = context.workspace.absolutePath,
        ))
        return ToolResult(
            success = result.exitCode == 0,
            output = buildString {
                if (result.stdout.isNotBlank()) append(result.stdout)
                if (result.stderr.isNotBlank()) { if (isNotEmpty()) appendLine(); append(result.stderr) }
                if (result.timedOut) appendLine("\nCommand timed out")
                appendLine("\n[exit ${result.exitCode}, ${result.durationMillis} ms]")
            },
            metadata = mapOf("exitCode" to result.exitCode.toString()),
            truncated = result.truncated,
        )
    }
}

class PythonTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "run_python", "Run Python code in the local Debian workspace.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("code", buildJsonObject { put("type", "string") }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add("code") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.SHELL, ToolCapability.WORKSPACE_WRITE)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext) = arguments.string("code")

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val code = arguments.string("code")
        val escaped = code.replace("'", "'\\''")
        val result = runtime.run(RunRequest("python3 -c '$escaped'", "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath))
        return ToolResult(result.exitCode == 0, (result.stdout + result.stderr).take(1_000_000), truncated = result.truncated)
    }
}

class StartJobTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "start_job", "Start a Bash command as a managed background job.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("command", buildJsonObject { put("type", "string") }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add("command") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.SHELL)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext) = arguments.string("command")

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val job = runtime.startJob(RunRequest(arguments.string("command"), "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath))
        return ToolResult(true, "Started job ${job.id}: ${job.command}", metadata = mapOf("jobId" to job.id))
    }
}

class ListJobsTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "list_jobs", "List managed background jobs and their latest output.",
        buildJsonObject { put("type", "object"); put("properties", buildJsonObject { }); put("additionalProperties", false) },
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val jobs = runtime.listJobs()
        val output = jobs.joinToString("\n\n") { job ->
            "${job.id} ${job.state} exit=${job.exitCode ?: "-"}\n${job.command}\n${job.outputPreview}"
        }
        return ToolResult(true, output, truncated = jobs.any { it.outputTruncated })
    }
}

class StopJobTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "stop_job", "Stop one managed background job.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("job_id", buildJsonObject { put("type", "string") }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add("job_id") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.SHELL)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val id = arguments.string("job_id")
        val stopped = runtime.stopJob(id)
        return ToolResult(stopped, if (stopped) "Stopped job $id" else "Unknown job $id")
    }
}

class GitStatusTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "git_status", "Show Git status and diff without modifying the repository.",
        buildJsonObject { put("type", "object"); put("properties", buildJsonObject { }); put("additionalProperties", false) },
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val result = runtime.run(RunRequest("git status --short --branch && git diff --stat && git diff --", "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath))
        return ToolResult(result.exitCode == 0, result.stdout + result.stderr, truncated = result.truncated)
    }
}

class GitCommitTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "git_commit", "Stage selected paths and create a Git commit. Never pushes.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("message", buildJsonObject { put("type", "string") })
                put("paths", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add("message"); add("paths") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_WRITE, ToolCapability.SHELL)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext) = "Create commit: ${arguments.string("message")}" 

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val paths = (arguments["paths"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { it.toString().trim('"') }
        require(paths.isNotEmpty()) { "No paths selected" }
        val safePaths = paths.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
        val message = arguments.string("message").replace("'", "'\\''")
        val result = runtime.run(RunRequest("git add -- $safePaths && git commit -m '$message'", "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath))
        return ToolResult(result.exitCode == 0, result.stdout + result.stderr, truncated = result.truncated)
    }
}

class ApplyPatchTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "apply_patch", "Apply a unified Git patch atomically after validating it.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("patch", buildJsonObject { put("type", "string") }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add("patch") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_WRITE)
    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String = arguments.string("patch").take(20_000)
    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val patch = arguments.string("patch")
        require(patch.length <= 2_000_000) { "Patch exceeds 2 MB" }
        val encoded = Base64.getEncoder().encodeToString(patch.encodeToByteArray())
        val command = "printf '%s' '$encoded' | base64 -d > /tmp/phoneagent.patch && git apply --check /tmp/phoneagent.patch && git apply /tmp/phoneagent.patch"
        val result = runtime.run(RunRequest(command, "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath))
        return ToolResult(result.exitCode == 0, (result.stdout + result.stderr).ifBlank { "Patch applied" }, truncated = result.truncated)
    }
}

class GitInspectTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "git_inspect", "Inspect Git diff, log, show, branches, or status without modifying history.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", kotlinx.serialization.json.buildJsonArray { listOf("status", "diff", "log", "show", "branch").forEach { add(it) } })
                })
                put("revision", buildJsonObject { put("type", "string") })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add("action") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)
    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val revision = arguments.string("revision", "HEAD").replace(Regex("[^A-Za-z0-9_./@{}~^:+-]"), "")
        val command = when (arguments.string("action")) {
            "status" -> "git status --short --branch"
            "diff" -> "git diff --no-ext-diff --"
            "log" -> "git log --oneline --decorate -30"
            "show" -> "git show --stat --oneline '$revision'"
            "branch" -> "git branch --all --verbose --no-abbrev"
            else -> return ToolResult(false, "Unsupported Git action")
        }
        val result = runtime.run(RunRequest(command, "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath))
        return ToolResult(result.exitCode == 0, result.stdout + result.stderr, truncated = result.truncated)
    }
}

class CodeAnalysisTool(private val runtime: LinuxRuntime) : Tool {
    override val definition = ToolDefinition(
        "code_analysis", "Run diagnostics or find symbols using available local language tools. Returns an install hint when a server is missing.",
        buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("action", buildJsonObject { put("type", "string"); put("enum", kotlinx.serialization.json.buildJsonArray { add("diagnostics"); add("symbols"); add("references") }) })
                put("path", buildJsonObject { put("type", "string") })
                put("query", buildJsonObject { put("type", "string") })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add("action") })
            put("additionalProperties", false)
        },
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)
    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val path = arguments.string("path", ".").replace("'", "'\\''")
        val query = arguments.string("query", "").replace("'", "'\\''")
        val command = when (arguments.string("action")) {
            "symbols" -> "rg -n --hidden --glob '!.git/**' '^(class|interface|object|fun|def|function|struct|enum|type) ' '$path' || true"
            "references" -> if (query.isBlank()) "printf 'query is required\\n'; exit 2" else "rg -n --hidden --glob '!.git/**' -- '$query' '$path' || true"
            "diagnostics" -> """
                if find '$path' -name '*.py' -print -quit | grep -q .; then find '$path' -name '*.py' -not -path '*/.git/*' -print0 | xargs -0 -r python3 -m py_compile
                elif [ -f Cargo.toml ] && command -v cargo >/dev/null; then cargo check --message-format=short
                elif [ -f go.mod ] && command -v go >/dev/null; then go test ./...
                elif [ -f package.json ] && command -v npx >/dev/null; then npx --no-install tsc --noEmit
                else printf 'No supported diagnostics tool detected. Install the matching optional toolchain/LSP in Settings.\\n'; fi
            """.trimIndent()
            else -> return ToolResult(false, "Unsupported code analysis action")
        }
        val result = runtime.run(RunRequest(command, "/home/phoneagent", workspaceHostPath = context.workspace.absolutePath, timeoutMillis = 300_000))
        return ToolResult(result.exitCode == 0, result.stdout + result.stderr, truncated = result.truncated)
    }
}
