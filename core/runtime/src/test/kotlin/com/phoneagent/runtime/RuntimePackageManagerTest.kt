package com.phoneagent.runtime

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePackageManagerTest {
    @Test
    fun gitIsNotExposedAsAnOptionalRemovablePackage() {
        assertFalse(RuntimePackageCatalog.groups.any { "git" in it.packages })
        assertTrue(RuntimePackageCatalog.groups.single { it.id == "ssh" }.description.contains("Git 已作为基础组件内置"))
    }

    @Test
    fun installForwardsAptProgressAndSanitizesBuildProxy() = runBlocking {
        val runtime = RecordingRuntime()
        val updates = mutableListOf<RuntimePackageProgress>()
        val group = RuntimePackageCatalog.groups.single { it.id == "ssh" }

        val result = RuntimePackageManager(runtime).change(group, RuntimePackageAction.INSTALL, updates::add)

        assertTrue(result.isSuccess)
        assertTrue(runtime.lastCommand.contains("rm -f /etc/apt/apt.conf.d/99-sai-build-proxy"))
        assertTrue(updates.any { it.stage.contains("更新 Debian 软件索引") })
        assertTrue(updates.any { it.stage.contains("安装 Git 远程访问") && it.percent in 25..93 })
        assertEquals(100, updates.last().percent)
    }

    private class RecordingRuntime : LinuxRuntime {
        var lastCommand: String = ""

        override suspend fun probe() = RuntimeCapability(true, "arm64", true, true, detail = "ready")

        override suspend fun run(request: RunRequest): RunResult {
            lastCommand = request.command
            return RunResult(
                exitCode = 0,
                stdout = """
                    __SAI_STAGE__:repair
                    __SAI_STAGE__:index
                    dlstatus:1:50.0:Downloading package indexes
                    __SAI_STAGE__:install
                    pmstatus:openssh-client:40.0:Unpacking openssh-client
                    __SAI_STAGE__:cleanup
                    __SAI_STAGE__:verify
                """.trimIndent(),
                stderr = "",
            )
        }

        override suspend fun startJob(request: RunRequest) = RuntimeJob("1", request.command, JobState.COMPLETED, 0, 0)
        override suspend fun listJobs() = emptyList<RuntimeJob>()
        override suspend fun stopJob(id: String) = true
        override suspend fun openPty(
            workingDirectory: String,
            environment: Map<String, String>,
            workspaceHostPath: String?,
        ): PtySession = object : PtySession {
            override val events = emptyFlow<TerminalEvent>()
            override suspend fun write(bytes: ByteArray) = Unit
            override suspend fun resize(columns: Int, rows: Int) = Unit
            override fun close() = Unit
        }
    }
}
