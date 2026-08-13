package com.phoneagent.app

import com.phoneagent.data.SecretStore
import com.phoneagent.runtime.BundledGitHubCli
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.RunRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicReference

data class GitHubCliStatus(
    val installed: Boolean,
    val version: String? = null,
    val login: String? = null,
    val detail: String = "",
)

/** Keystore-backed GitHub CLI facade. Tokens are injected only into the child process. */
class GitHubCliManager(
    private val installer: BundledGitHubCli,
    private val runtime: LinuxRuntime,
    private val secrets: SecretStore,
    private val workspace: File,
) {
    private val mutex = Mutex()

    suspend fun installAndStatus(): GitHubCliStatus = mutex.withLock {
        installer.install().fold(
            onSuccess = { statusLocked(it.version) },
            onFailure = { GitHubCliStatus(false, detail = it.message ?: "gh 安装失败") },
        )
    }

    suspend fun loginWithToken(token: CharArray): Result<GitHubCliStatus> = mutex.withLock {
        val value = token.concatToString()
        token.fill('\u0000')
        try {
            val result = runGh(listOf("api", "user", "--jq", ".login"), value)
            check(result.exitCode == 0) { sanitize(result.stderr.ifBlank { result.stdout }) }
            secrets.put(TOKEN_ALIAS, value.toCharArray())
            Result.success(statusLocked())
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * Uses gh's OAuth device flow in an isolated temporary config directory. The resulting token is
     * copied directly into Android Keystore-backed storage and the gh config is deleted by a shell
     * trap. Only the one-time user code is exposed to the UI callback.
     */
    suspend fun loginWithDeviceFlow(onCode: (String) -> Unit): Result<GitHubCliStatus> = mutex.withLock {
        val observedCode = AtomicReference<String?>(null)
        val loginOutput = StringBuilder()
        val command = """
            gh_config=${'$'}(mktemp -d)
            trap 'rm -rf "${'$'}gh_config"' EXIT
            export GH_CONFIG_DIR="${'$'}gh_config"
            export BROWSER=echo
            gh auth login --hostname github.com --git-protocol https --web --skip-ssh-key
            printf '\nSAI_GH_TOKEN='
            gh auth token --hostname github.com
        """.trimIndent()
        runCatching {
            val result = runtime.runStreaming(
                RunRequest(
                    command = command,
                    workingDirectory = "/home/phoneagent",
                    workspaceHostPath = workspace.absolutePath,
                    timeoutMillis = 10 * 60_000L,
                    outputLimitBytes = 200_000,
                    environment = mapOf("GH_PAGER" to "cat"),
                ),
            ) { output ->
                val searchable = synchronized(loginOutput) {
                    loginOutput.append(output.text)
                    if (loginOutput.length > 8_192) loginOutput.delete(0, loginOutput.length - 8_192)
                    loginOutput.toString()
                }
                DEVICE_CODE.find(searchable)?.value?.let { code ->
                    if (observedCode.getAndSet(code) != code) onCode(code)
                }
            }
            check(result.exitCode == 0) { sanitize(result.stderr.ifBlank { result.stdout }) }
            val token = TOKEN_RESULT.find(result.stdout)?.groupValues?.getOrNull(1)
                ?: error("GitHub 设备登录完成，但未能取得临时凭据")
            secrets.put(TOKEN_ALIAS, token.toCharArray())
            statusLocked()
        }
    }

    suspend fun logout(): GitHubCliStatus = mutex.withLock {
        secrets.remove(TOKEN_ALIAS)
        statusLocked()
    }

    suspend fun run(arguments: List<String>, timeoutMillis: Long = 120_000): Result<String> = mutex.withLock {
        val chars = secrets.get(TOKEN_ALIAS) ?: return@withLock Result.failure(IllegalStateException("请先登录 GitHub"))
        val token = chars.concatToString()
        chars.fill('\u0000')
        runCatching {
            val result = runGh(arguments, token, timeoutMillis)
            check(result.exitCode == 0) { sanitize(result.stderr.ifBlank { result.stdout }) }
            sanitize(result.stdout)
        }
    }

    private suspend fun statusLocked(knownVersion: String? = null): GitHubCliStatus {
        val versionResult = runtime.run(RunRequest("gh --version | head -n1", "/home/phoneagent", timeoutMillis = 15_000, workspaceHostPath = workspace.absolutePath))
        if (versionResult.exitCode != 0) return GitHubCliStatus(false, detail = versionResult.stderr.ifBlank { "gh 不可用" })
        val version = knownVersion ?: Regex("gh version ([^ ]+)").find(versionResult.stdout)?.groupValues?.getOrNull(1)
        val chars = secrets.get(TOKEN_ALIAS) ?: return GitHubCliStatus(true, version, detail = "未登录")
        val token = chars.concatToString()
        chars.fill('\u0000')
        val account = runGh(listOf("api", "user", "--jq", ".login"), token, 30_000)
        return if (account.exitCode == 0) {
            GitHubCliStatus(true, version, sanitize(account.stdout).trim(), "已登录")
        } else {
            GitHubCliStatus(true, version, detail = "凭据已保存，但当前无法验证：${sanitize(account.stderr).take(160)}")
        }
    }

    private suspend fun runGh(arguments: List<String>, token: String, timeoutMillis: Long = 30_000) = runtime.run(
        RunRequest(
            command = "gh ${arguments.joinToString(" ", transform = ::shellQuote)}",
            workingDirectory = "/home/phoneagent",
            workspaceHostPath = workspace.absolutePath,
            timeoutMillis = timeoutMillis,
            outputLimitBytes = 500_000,
            environment = mapOf("GH_PROMPT_DISABLED" to "1"),
            sensitiveEnvironment = mapOf("GH_TOKEN" to token),
        ),
    )

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(authorization|token|bearer|ghp_|github_pat_)[^\\s]{4,}"), "[REDACTED]")
        .trim()

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    companion object {
        private const val TOKEN_ALIAS = "github:github.com:token"
        private val DEVICE_CODE = Regex("(?<![A-Z0-9])[A-Z0-9]{4}-[A-Z0-9]{4}(?![A-Z0-9])")
        private val TOKEN_RESULT = Regex("SAI_GH_TOKEN=([^\\s]+)")
    }
}
