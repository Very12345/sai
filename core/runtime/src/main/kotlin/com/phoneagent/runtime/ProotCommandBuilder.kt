package com.phoneagent.runtime

import java.io.File

data class ProotConfig(
    val executable: File,
    val rootfs: File,
    val home: File,
    val tmp: File,
    val shell: String = "/bin/bash",
    val systemLinker: File? = null,
    val loader: File? = null,
    val nativeLibraryDirectory: File? = null,
)

class ProotCommandBuilder(private val config: ProotConfig) {
    fun hostEnvironment(): Map<String, String> = buildMap {
        config.loader?.takeIf(File::isFile)?.let { put("PROOT_LOADER", it.absolutePath) }
        config.nativeLibraryDirectory?.takeIf(File::isDirectory)?.let {
            put("LD_LIBRARY_PATH", it.absolutePath)
        }
        put("PROOT_TMP_DIR", config.tmp.absolutePath)
    }

    fun shell(
        command: String,
        workingDirectory: String,
        workspaceHostPath: String? = null,
        guestEnvironment: Map<String, String> = emptyMap(),
    ): List<String> {
        val workspace = workspaceHostPath?.let(::File)?.canonicalFile ?: config.home.canonicalFile
        require(workspace.isDirectory || workspace.mkdirs()) { "Workspace is unavailable: ${workspace.path}" }
        // link2symlink represents Git hard links as absolute links under Android's
        // /data/data/<package> alias. Bind only this app's private data root back to the
        // same guest paths so Git can read its objects; Android UID isolation remains intact.
        val appDataRoot = config.rootfs.parentFile?.parentFile?.parentFile?.canonicalFile
            ?.takeIf { it.name.isNotBlank() && File(it, "files").isDirectory }
        val prootInvocation = mutableListOf<String>()
        config.systemLinker?.takeIf(File::exists)?.let { prootInvocation += it.absolutePath }
        prootInvocation += config.executable.absolutePath
        prootInvocation += listOf(
            "--root-id",
            "--link2symlink",
            "--kill-on-exit",
            "--sysvipc",
            "--rootfs=${config.rootfs.absolutePath}",
            "--bind=${workspace.absolutePath}:/home/phoneagent",
            "--bind=${config.tmp.absolutePath}:/tmp",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=/system",
            "--bind=/apex",
        )
        appDataRoot?.let { data ->
            prootInvocation += "--bind=${data.absolutePath}:${data.absolutePath}"
            prootInvocation += "--bind=${data.absolutePath}:/data/data/${data.name}"
        }
        prootInvocation += listOf(
            "--cwd=$workingDirectory",
            "/usr/bin/env",
            "-i",
            "HOME=/home/phoneagent",
            "USER=phoneagent",
            "LANG=C.UTF-8",
            "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        )
        guestEnvironment.toSortedMap().forEach { (name, value) ->
            require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid environment name" }
            require('\u0000' !in value) { "Environment value contains NUL" }
            prootInvocation += "$name=$value"
        }
        prootInvocation += listOf(config.shell, "-lc", command)
        return prootInvocation
    }

    fun interactive(
        workingDirectory: String,
        workspaceHostPath: String? = null,
        guestEnvironment: Map<String, String> = emptyMap(),
    ): List<String> = shell("exec ${config.shell} -l", workingDirectory, workspaceHostPath, guestEnvironment)
}
