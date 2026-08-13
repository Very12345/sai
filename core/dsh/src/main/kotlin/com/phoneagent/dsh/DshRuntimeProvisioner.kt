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
    val home = File(root, "home")
    val manifest: DshRuntimeManifest by lazy {
        context.assets.open(MANIFEST_ASSET).bufferedReader().use {
            json.decodeFromString<DshRuntimeManifest>(it.readText())
        }
    }

    fun isInstalled(): Boolean =
        File(current, ".installed").readTextOrNull() == manifest.runtimeVersion &&
            File(current, "node/bin/node").isFile &&
            File(current, "app/node_modules/@deepseek-ai/dsh/lib/bin.js").isFile

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
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val input = context.assets.open(archive.asset)
            val verified = java.security.DigestInputStream(input, digest)
            TarArchiveInputStream(XZCompressorInputStream(verified, true)).use { tar ->
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    extractEntry(staging, entry, tar)
                    copied += entry.size.coerceAtLeast(0)
                    onProgress((copied.toDouble() / archive.bytes.coerceAtLeast(1)).toFloat().coerceIn(0f, .99f))
                    entry = tar.nextEntry
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(archive.sha256, ignoreCase = true)) { "DSH archive SHA-256 mismatch" }
            manifest.packageLockSha256[abi]?.let { expected ->
                val lock = File(staging, "app/package-lock.json")
                check(lock.isFile) { "DSH runtime is missing package-lock.json" }
                val lockHash = MessageDigest.getInstance("SHA-256").digest(lock.readBytes())
                    .joinToString("") { "%02x".format(it) }
                check(lockHash.equals(expected, ignoreCase = true)) { "DSH dependency lock SHA-256 mismatch" }
            }
            File(staging, ".installed").writeText(manifest.runtimeVersion)
            home.mkdirs()
            val previous = File(root, "runtime/previous")
            if (previous.exists()) previous.deleteRecursively()
            if (current.exists() && !current.renameTo(previous)) error("Cannot preserve previous DSH runtime")
            if (!staging.renameTo(current)) error("Cannot activate DSH runtime")
            ensureBundledPresets()
            onProgress(1f)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
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
                "  inject: [saiAndroid]\n" +
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
