package com.phoneagent.dsh

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

class DshRuntimeProvisioner(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    val root = File(context.filesDir, "dsh")
    val current = File(root, "runtime/current")
    private val previous = File(root, "runtime/previous")
    private val rollbackPin = File(root, "runtime/.rollback-pinned")
    val home = File(root, "home")
    val manifest: DshRuntimeManifest by lazy {
        context.assets.open(MANIFEST_ASSET).bufferedReader().use {
            json.decodeDshRuntimeManifest(it.readText())
        }
    }

    val activeRuntimeVersion: String?
        get() = File(current, ".installed").readTextOrNull()

    fun isInstalled(): Boolean {
        val installed = activeRuntimeVersion
        val accepted = installed == manifest.runtimeVersion || rollbackPin.readTextOrNull() == installed
        val node = File(current, "node/bin/node")
        return accepted &&
            ensureRuntimeExecutable(node) &&
            File(current, "app/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile
    }

    fun canRollback(): Boolean =
        File(previous, ".installed").isFile &&
            File(previous, "node/bin/node").isFile &&
            File(previous, "app/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile

    suspend fun install(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (isInstalled()) {
            ensureBundledPresets()
            return@withContext
        }
        val abi = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64-v8a"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
            else -> error("DSH supports only arm64-v8a and x86_64")
        }
        val archive = manifest.archives[abi]
            ?: error("Offline DSH archive for $abi is absent. Run scripts/prepare-dsh-runtime.ps1 before building.")
        val staging = File(root, "runtime/staging-${System.nanoTime()}")
        staging.mkdirs()
        try {
            // Verify the complete compressed asset before extraction. Digesting
            // underneath XZCompressorInputStream is subtly wrong: the tar reader
            // may stop at the tar EOF before the XZ footer has been consumed,
            // producing a hash of only a prefix of the APK asset.
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(archive.asset).buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var count = input.read(buffer)
                while (count >= 0) {
                    if (count > 0) digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(archive.sha256, ignoreCase = true)) { "DSH archive SHA-256 mismatch" }

            var copied = 0L
            val input = context.assets.open(archive.asset)
            TarArchiveInputStream(XZCompressorInputStream(input, true)).use { tar ->
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    extractEntry(staging, entry, tar)
                    copied += entry.size.coerceAtLeast(0)
                    onProgress((copied.toDouble() / archive.bytes.coerceAtLeast(1)).toFloat().coerceIn(0f, .99f))
                    entry = tar.nextEntry
                }
            }
            manifest.packageLockSha256[abi]?.let { expected ->
                val lock = File(staging, "app/package-lock.json")
                check(lock.isFile) { "DSH runtime is missing package-lock.json" }
                val lockHash = MessageDigest.getInstance("SHA-256").digest(lock.readBytes())
                    .joinToString("") { "%02x".format(it) }
                check(lockHash.equals(expected, ignoreCase = true)) { "DSH dependency lock SHA-256 mismatch" }
            }
            check(ensureRuntimeExecutable(File(staging, "node/bin/node"))) {
                "DSH Node executable permission could not be restored"
            }
            File(staging, ".installed").writeText(manifest.runtimeVersion)
            home.mkdirs()
            if (previous.exists()) previous.deleteRecursively()
            if (current.exists() && !current.renameTo(previous)) error("Cannot preserve previous DSH runtime")
            if (!staging.renameTo(current)) {
                if (previous.exists()) previous.renameTo(current)
                error("Cannot activate DSH runtime")
            }
            rollbackPin.delete()
            ensureBundledPresets()
            onProgress(1f)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    /** Swaps current and previous without deleting either runtime. Call only while DSH is stopped. */
    fun rollback(): String {
        check(canRollback()) { "No verified previous DSH runtime is available" }
        val swap = File(root, "runtime/swap-${System.nanoTime()}")
        check(current.renameTo(swap)) { "Cannot stage the current DSH runtime" }
        try {
            check(previous.renameTo(current)) { "Cannot activate the previous DSH runtime" }
            check(swap.renameTo(previous)) { "Cannot preserve the replaced DSH runtime" }
        } catch (error: Throwable) {
            if (!current.exists() && previous.exists()) previous.renameTo(current)
            if (swap.exists() && !previous.exists()) swap.renameTo(previous)
            throw error
        }
        val version = activeRuntimeVersion ?: error("Rolled back runtime has no version marker")
        rollbackPin.parentFile?.mkdirs()
        rollbackPin.writeText(version)
        ensureBundledPresets()
        return version
    }

    /** Allows the bundled runtime to replace a user-selected rollback on next install. */
    fun selectBundledRuntime() {
        rollbackPin.delete()
    }

    fun ensureBundledPresets() {
        val standard = File(current, "app/node_modules/@deepseek-ai/dsh/config/agent-presets/standard")
        if (!standard.isDirectory) return
        val target = File(home, ".agent-presets/sai-voice")
        val marker = File(target, ".sai-version")
        if (marker.readTextOrNull() == manifest.runtimeVersion) return
        if (target.exists()) target.deleteRecursively()
        check(standard.copyRecursively(target, overwrite = true)) { "Unable to provision sai voice preset" }
        File(target, "agent.cordis.yml").appendText(
            "\n- id: sai-voice-policy\n" +
                "  name: '@sai/dsh-voice'\n" +
                "  inject: [systemPrompt]\n" +
                "  config: { promptOnly: true }\n",
            Charsets.UTF_8,
        )
        File(target, "preset.yml").writeText(
            "name: sai 语音通话\ndescription: 连续倾听、主动 speak 与插话转向。\n",
            Charsets.UTF_8,
        )
        marker.writeText(manifest.runtimeVersion)
    }

    private fun extractEntry(root: File, entry: TarArchiveEntry, tar: TarArchiveInputStream) {
        val name = entry.name.removePrefix("./").trimStart('/')
        if (name.isBlank()) return
        val output = File(root, name).canonicalFile
        require(output.path.startsWith(root.canonicalPath + File.separator)) { "DSH archive entry escapes destination: $name" }
        when {
            entry.isDirectory -> output.mkdirs()
            entry.isSymbolicLink -> {
                require(!File(entry.linkName).isAbsolute && !entry.linkName.split('/').contains("..")) {
                    "Unsafe DSH symlink: ${entry.linkName}"
                }
                output.parentFile?.mkdirs()
                runCatching { output.delete() }
                Os.symlink(entry.linkName, output.absolutePath)
            }
            entry.isFile -> {
                output.parentFile?.mkdirs()
                output.outputStream().buffered().use { tar.copyTo(it, 128 * 1024) }
                Os.chmod(output.absolutePath, entry.mode and 0x1FF)
            }
        }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

    companion object { const val MANIFEST_ASSET = "dsh-runtime/manifest.json" }
}

/** PowerShell 5 writes UTF-8 with a BOM by default; tolerate it in generated runtime manifests. */
internal fun Json.decodeDshRuntimeManifest(content: String): DshRuntimeManifest =
    decodeFromString(content.removePrefix("\uFEFF"))

/** ZIP/tar creation on Windows can erase Unix execute bits from the bundled Node binary. */
internal fun ensureRuntimeExecutable(file: File): Boolean =
    file.isFile && (file.canExecute() || file.setExecutable(true, true)) && file.canExecute()
