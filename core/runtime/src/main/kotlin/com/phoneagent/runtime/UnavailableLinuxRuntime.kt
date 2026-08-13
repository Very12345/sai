package com.phoneagent.runtime

class UnavailableLinuxRuntime(private val reason: String) : LinuxRuntime {
    override suspend fun probe() = RuntimeCapability(
        available = false,
        architecture = System.getProperty("os.arch") ?: "unknown",
        rootfsReady = false,
        prootReady = false,
        detail = reason,
    )

    override suspend fun run(request: RunRequest) = RunResult(-1, "", reason)

    override suspend fun startJob(request: RunRequest): RuntimeJob =
        error(reason)

    override suspend fun listJobs(): List<RuntimeJob> = emptyList()

    override suspend fun stopJob(id: String): Boolean = false

    override suspend fun openPty(
        workingDirectory: String,
        environment: Map<String, String>,
        workspaceHostPath: String?,
    ): PtySession =
        error(reason)
}
