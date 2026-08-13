package com.phoneagent.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RuntimePackageAction { INSTALL, REMOVE }

data class RuntimePackageGroup(
    val id: String,
    val title: String,
    val description: String,
    val sizeHint: String,
    val packages: List<String>,
    val verifyCommand: String,
)

data class RuntimePackageStatus(
    val group: RuntimePackageGroup,
    val installed: Boolean,
    val version: String? = null,
)

data class RuntimePackagePlan(
    val group: RuntimePackageGroup,
    val action: RuntimePackageAction,
    val aptSummary: String,
    val downloadBytes: Long? = null,
    val installedBytes: Long? = null,
    val availableBytes: Long? = null,
    val allowed: Boolean = true,
    val reason: String? = null,
)

data class RuntimePackageProgress(
    val stage: String,
    val percent: Int? = null,
    val detail: String = "",
    val logTail: String = "",
)

object RuntimePackageCatalog {
    val groups = listOf(
        RuntimePackageGroup(
            id = "python",
            title = "Python 工具链",
            description = "Python 3、pip、venv 与 pytest",
            sizeHint = "约 80–180 MB",
            packages = listOf("python3", "python3-pip", "python3-venv", "python3-pytest"),
            verifyCommand = "python3 --version",
        ),
        RuntimePackageGroup(
            id = "ssh",
            title = "Git 远程访问",
            description = "Git 已作为基础组件内置；此项补充 OpenSSH 客户端",
            sizeHint = "约 10–40 MB",
            packages = listOf("openssh-client", "ca-certificates"),
            verifyCommand = "ssh -V 2>&1",
        ),
        RuntimePackageGroup(
            id = "nodejs",
            title = "Node.js 工具链",
            description = "Debian 稳定版 Node.js 与 npm",
            sizeHint = "约 120–300 MB",
            packages = listOf("nodejs", "npm"),
            verifyCommand = "node --version && npm --version",
        ),
        RuntimePackageGroup(
            id = "latex",
            title = "LaTeX 中文套件",
            description = "pdfLaTeX、XeLaTeX、latexmk 与中文语言包",
            sizeHint = "约 1–2.5 GB",
            packages = listOf("texlive-latex-base", "texlive-latex-recommended", "texlive-xetex", "texlive-lang-chinese", "latexmk"),
            verifyCommand = "xelatex --version | head -n1 && latexmk --version | head -n1",
        ),
        RuntimePackageGroup(
            id = "rust",
            title = "Rust 工具链",
            description = "rustc、Cargo、rustfmt、Clippy 与 pkg-config",
            sizeHint = "实际体积以安装预检为准",
            packages = listOf("rustc", "cargo", "rustfmt", "clippy", "pkg-config"),
            verifyCommand = "rustc --version && cargo --version",
        ),
        RuntimePackageGroup(
            id = "go",
            title = "Go 工具链",
            description = "Go、gopls 语言服务与 Delve 调试器",
            sizeHint = "实际体积以安装预检为准",
            packages = listOf("golang-go", "gopls", "delve"),
            verifyCommand = "go version && gopls version",
        ),
        RuntimePackageGroup(
            id = "java",
            title = "Java 工具链",
            description = "OpenJDK 21、Maven 与 Gradle",
            sizeHint = "体积较大，安装前会检查可用空间",
            packages = listOf("openjdk-21-jdk-headless", "maven", "gradle"),
            verifyCommand = "java -version 2>&1 | head -n1 && mvn --version | head -n1",
        ),
        RuntimePackageGroup(
            id = "cpp",
            title = "C/C++ 工具链",
            description = "GCC/G++、Clang、LLD、GDB、CMake、Ninja 与 Make",
            sizeHint = "体积较大，安装前会检查可用空间",
            packages = listOf("build-essential", "clang", "lld", "gdb", "cmake", "ninja-build", "pkg-config"),
            verifyCommand = "gcc --version | head -n1 && clang --version | head -n1 && cmake --version | head -n1",
        ),
        RuntimePackageGroup(
            id = "database",
            title = "数据库 CLI",
            description = "SQLite、PostgreSQL 与 MySQL/MariaDB 客户端",
            sizeHint = "实际体积以安装预检为准",
            packages = listOf("sqlite3", "postgresql-client", "default-mysql-client"),
            verifyCommand = "sqlite3 --version && psql --version && mysql --version",
        ),
        RuntimePackageGroup(
            id = "network",
            title = "网络与 API 工具",
            description = "curl、HTTPie、jq 与 OpenAPI 规范验证器",
            sizeHint = "实际体积以安装预检为准",
            packages = listOf("curl", "httpie", "jq", "python3-openapi-spec-validator"),
            verifyCommand = "curl --version | head -n1 && http --version && jq --version && openapi-spec-validator --version",
        ),
    )
}

class RuntimePackageManager(private val runtime: LinuxRuntime) {
    suspend fun simulate(
        group: RuntimePackageGroup,
        action: RuntimePackageAction,
        onStage: (String) -> Unit = {},
    ): Result<RuntimePackagePlan> = runCatching {
        require(RuntimePackageCatalog.groups.any { it.id == group.id && it.packages == group.packages }) { "未知工具链" }
        onStage("正在清理软件源配置…")
        prepareApt()
        if (action == RuntimePackageAction.INSTALL) {
            onStage("正在更新 Debian 软件索引…")
            val update = runtime.run(
                RunRequest(
                    command = "$APT_ENV; apt-get -o Acquire::Retries=2 update",
                    workingDirectory = "/home/phoneagent",
                    timeoutMillis = 5 * 60_000L,
                    outputLimitBytes = 600_000,
                ),
            )
            check(update.exitCode == 0) { friendlyAptError(update.stderr.ifBlank { update.stdout }) }
        }
        onStage("正在计算下载量与占用空间…")
        val packages = group.packages.joinToString(" ") { shellQuote(it) }
        val verb = if (action == RuntimePackageAction.INSTALL) "install" else "purge"
        val result = runtime.run(
            RunRequest(
                command = "export LANG=C; apt-get -s -o Debug::NoLocking=1 $verb --no-install-recommends $packages; printf '\\nPHONEAGENT_FREE='; df -PB1 / | awk 'NR==2 {print $4}'",
                workingDirectory = "/home/phoneagent",
                timeoutMillis = 120_000,
                outputLimitBytes = 600_000,
            ),
        )
        check(result.exitCode == 0) { friendlyAptError(result.stderr.ifBlank { result.stdout }) }
        val combined = result.stdout + "\n" + result.stderr
        val download = Regex("Need to get ([0-9.,]+) ([kMGT]?B)").find(combined)?.let { sizeToBytes(it.groupValues[1], it.groupValues[2]) }
        val installed = Regex("After this operation, ([0-9.,]+) ([kMGT]?B)").find(combined)?.let { sizeToBytes(it.groupValues[1], it.groupValues[2]) }
        val free = Regex("PHONEAGENT_FREE=(\\d+)").find(combined)?.groupValues?.get(1)?.toLongOrNull()
        val required = installed?.coerceAtLeast(0L)?.plus(download ?: 0L)
        val allowed = action != RuntimePackageAction.INSTALL || required == null || free == null || required + 256L * 1024 * 1024 <= free
        RuntimePackagePlan(
            group = group,
            action = action,
            aptSummary = combined.substringBefore("PHONEAGENT_FREE=").trim().takeLast(30_000),
            downloadBytes = download,
            installedBytes = installed,
            availableBytes = free,
            allowed = allowed,
            reason = if (allowed) null else "空间不足：安装后至少保留 256 MB 安全余量",
        )
    }

    suspend fun query(): List<RuntimePackageStatus> {
        prepareApt()
        return RuntimePackageCatalog.groups.map { group ->
        val command = group.packages.joinToString(" ") { shellQuote(it) }
        val result = runtime.run(
            RunRequest(
                command = "dpkg-query -W -f='\${Status}\n' $command 2>/dev/null | grep -c '^install ok installed$' || true",
                workingDirectory = "/home/phoneagent",
                timeoutMillis = 30_000,
            ),
        )
        val count = result.stdout.trim().toIntOrNull() ?: 0
        val installed = result.exitCode == 0 && count == group.packages.size
        val version = if (installed) {
            runtime.run(
                RunRequest(group.verifyCommand, "/home/phoneagent", timeoutMillis = 30_000, outputLimitBytes = 20_000),
            ).stdout.lineSequence().firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
        } else null
            RuntimePackageStatus(group, installed, version)
        }
    }

    suspend fun change(
        group: RuntimePackageGroup,
        action: RuntimePackageAction,
        onProgress: (RuntimePackageProgress) -> Unit,
    ): Result<Unit> = operationMutex.withLock { try {
        require(RuntimePackageCatalog.groups.any { it.id == group.id && it.packages == group.packages }) { "Unknown runtime package group" }
        val packages = group.packages.joinToString(" ") { shellQuote(it) }
        val script = when (action) {
            RuntimePackageAction.INSTALL -> """
                set -eu
                $APT_ENV
                $APT_SANITIZE
                printf '__SAI_STAGE__:repair\n'
                dpkg --configure -a
                printf '__SAI_STAGE__:index\n'
                apt-get -o Acquire::Retries=2 -o Dpkg::Use-Pty=0 -o APT::Status-Fd=1 update
                printf '__SAI_STAGE__:install\n'
                apt-get -y --no-install-recommends -o Acquire::Retries=2 -o Dpkg::Use-Pty=0 -o APT::Status-Fd=1 install $packages
                printf '__SAI_STAGE__:cleanup\n'
                apt-get clean
                printf '__SAI_STAGE__:verify\n'
                ${group.verifyCommand}
            """.trimIndent()
            RuntimePackageAction.REMOVE -> """
                set -eu
                $APT_ENV
                $APT_SANITIZE
                printf '__SAI_STAGE__:repair\n'
                dpkg --configure -a
                printf '__SAI_STAGE__:remove\n'
                apt-get purge -y -o Dpkg::Use-Pty=0 -o APT::Status-Fd=1 $packages
                printf '__SAI_STAGE__:cleanup\n'
                apt-get clean
            """.trimIndent()
        }
        val tracker = RuntimeProgressTracker(group.title, action, onProgress)
        tracker.start()
        val result = runtime.runStreaming(
            RunRequest(
                command = script,
                workingDirectory = "/home/phoneagent",
                timeoutMillis = if (group.id == "latex") 60 * 60 * 1_000L else 30 * 60 * 1_000L,
                outputLimitBytes = 4_000_000,
            ),
            tracker::accept,
        )
        tracker.finish(result.exitCode == 0)
        check(!result.timedOut) { "${group.title}操作超时" }
        check(result.exitCode == 0) {
            friendlyAptError(result.stderr.ifBlank { result.stdout }).ifBlank { "${group.title}操作失败" }
        }
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    } }

    private fun sizeToBytes(value: String, unit: String): Long {
        val numeric = value.replace(",", ".").toDoubleOrNull() ?: return 0
        val multiplier = when (unit.uppercase()) {
            "KB" -> 1_000L
            "MB" -> 1_000_000L
            "GB" -> 1_000_000_000L
            "TB" -> 1_000_000_000_000L
            else -> 1L
        }
        return (numeric * multiplier).toLong()
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private suspend fun prepareApt() {
        val result = runtime.run(
            RunRequest(
                command = "set -eu\n$APT_SANITIZE",
                workingDirectory = "/home/phoneagent",
                timeoutMillis = 30_000,
                outputLimitBytes = 20_000,
            ),
        )
        check(result.exitCode == 0) { result.stderr.ifBlank { "无法修复 Debian 软件源配置" } }
    }

    private fun friendlyAptError(raw: String): String {
        val tail = raw.lineSequence()
            .filterNot { it.isBlank() }
            .toList()
            .takeLast(40)
            .joinToString("\n")
            .takeLast(8_000)
        return when {
            "127.0.0.1:18080" in tail -> "检测到旧版构建代理残留，已尝试清理；请重试一次。\n$tail"
            "has no installation candidate" in tail -> "软件索引中没有找到该依赖。请检查网络后刷新索引，或稍后重试。\n$tail"
            else -> tail
        }
    }

    companion object {
        private val operationMutex = Mutex()
        private const val APT_ENV = "export LANG=C DEBIAN_FRONTEND=noninteractive"
        private val APT_SANITIZE = """
            rm -f /etc/apt/apt.conf.d/99-sai-build-proxy
            for sai_apt_conf in /etc/apt/apt.conf.d/*; do
                if [ -f "${'$'}sai_apt_conf" ] && grep -q '127\.0\.0\.1:18080' "${'$'}sai_apt_conf"; then rm -f "${'$'}sai_apt_conf"; fi
            done
            sed -i 's|http://deb.debian.org|https://deb.debian.org|g; s|http://security.debian.org|https://security.debian.org|g' /etc/apt/sources.list
        """.trimIndent()
    }
}

private class RuntimeProgressTracker(
    private val title: String,
    private val action: RuntimePackageAction,
    private val emit: (RuntimePackageProgress) -> Unit,
) {
    private val pending = StringBuilder()
    private val log = StringBuilder()
    private var phase = "prepare"
    private var percent = 0
    private var detail = "准备软件包管理器"

    fun start() = publish("正在准备 $title", 0, detail)

    @Synchronized
    fun accept(output: RuntimeOutput) {
        pending.append(output.text.replace('\r', '\n'))
        while (true) {
            val lineEnd = pending.indexOf("\n")
            if (lineEnd < 0) break
            handleLine(pending.substring(0, lineEnd).trim())
            pending.delete(0, lineEnd + 1)
        }
    }

    @Synchronized
    fun finish(success: Boolean) {
        if (pending.isNotBlank()) handleLine(pending.toString().trim())
        publish(if (success) "$title 已完成" else "$title 安装失败", if (success) 100 else percent, detail)
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return
        appendLog(line)
        if (line.startsWith("__SAI_STAGE__:")) {
            phase = line.substringAfter(':')
            val stage = when (phase) {
                "repair" -> Triple("正在修复软件包状态", 2, "检查未完成的安装")
                "index" -> Triple("正在更新 Debian 软件索引", 8, "连接软件源")
                "install" -> Triple("正在安装 $title", 25, "下载并配置依赖")
                "remove" -> Triple("正在卸载 $title", 15, "移除组件")
                "cleanup" -> Triple("正在清理安装缓存", 95, "释放临时空间")
                "verify" -> Triple("正在验证 $title", 98, "检查命令是否可用")
                else -> Triple("正在处理 $title", percent, detail)
            }
            publish(stage.first, stage.second, stage.third)
            return
        }

        val status = Regex("(?:dl|pm)status:[^:]*:([0-9.]+):(.*)").find(line)
        if (status != null) {
            val aptPercent = status.groupValues[1].toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
            val (base, span) = when (phase) {
                "index" -> 8 to 17
                "install" -> 25 to 68
                "remove" -> 15 to 78
                else -> percent to 0
            }
            publish(currentStage(), base + (aptPercent * span / 100.0).toInt(), status.groupValues[2].ifBlank { detail })
            return
        }

        if (line.startsWith("Get:") || line.startsWith("Hit:") ||
            line.startsWith("Preparing to unpack") || line.startsWith("Unpacking ") ||
            line.startsWith("Setting up ") || line.startsWith("Processing triggers")) {
            publish(currentStage(), percent, line.take(180))
        }
    }

    private fun currentStage(): String = when (phase) {
        "index" -> "正在更新 Debian 软件索引"
        "install" -> "正在安装 $title"
        "remove" -> "正在卸载 $title"
        "cleanup" -> "正在清理安装缓存"
        "verify" -> "正在验证 $title"
        else -> "正在准备 $title"
    }

    private fun publish(stage: String, nextPercent: Int, nextDetail: String) {
        percent = nextPercent.coerceIn(percent, 100)
        detail = nextDetail
        emit(RuntimePackageProgress(stage, percent, detail, log.toString()))
    }

    private fun appendLog(line: String) {
        log.appendLine(line)
        if (log.length > 12_000) log.delete(0, log.length - 10_000)
    }
}
