package com.phoneagent.runtime

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

@Serializable
data class RootfsManifest(
    val version: String,
    val architecture: String,
    val url: String,
    val sha256: String,
    val compressedBytes: Long,
    val installedBytes: Long,
    val sourceUrl: String,
    val embeddedAsset: String? = null,
)

sealed interface RootfsInstallState {
    data object NotInstalled : RootfsInstallState
    data class CopyingEmbedded(val copied: Long, val total: Long) : RootfsInstallState
    data class Downloading(val downloaded: Long, val total: Long) : RootfsInstallState
    data object Verifying : RootfsInstallState
    data object Extracting : RootfsInstallState
    data class Provisioning(val stage: String) : RootfsInstallState
    data class Ready(val version: String) : RootfsInstallState
    data class Failed(val message: String) : RootfsInstallState
}

fun interface RootfsExtractor {
    suspend fun extract(archive: File, destination: File)
}

class RootfsInstaller(
    context: Context,
    private val client: OkHttpClient,
    private val extractor: RootfsExtractor,
) {
    private val context = context.applicationContext
    private val runtimeDir = File(context.filesDir, "runtime")
    val rootfsDir: File = File(runtimeDir, "debian")
    private val archive = File(runtimeDir, "rootfs.part")

    suspend fun install(manifest: RootfsManifest, onState: (RootfsInstallState) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                runtimeDir.mkdirs()
                require(runtimeDir.usableSpace > manifest.installedBytes + manifest.compressedBytes) {
                    "Insufficient storage for Debian runtime"
                }
                if (manifest.embeddedAsset != null) copyEmbedded(manifest, onState)
                else download(manifest, onState)
                onState(RootfsInstallState.Verifying)
                require(sha256(archive).equals(manifest.sha256, ignoreCase = true)) { "Rootfs checksum mismatch" }
                onState(RootfsInstallState.Extracting)
                val staging = File(runtimeDir, "debian.staging")
                if (staging.exists()) staging.deleteRecursively()
                staging.mkdirs()
                extractor.extract(archive, staging)
                check(File(staging, "bin/bash").exists()) { "Rootfs is missing /bin/bash" }
                configureNetworkFiles(staging)
                repairRuntimeLayout(staging)
                if (rootfsDir.exists()) rootfsDir.deleteRecursively()
                check(staging.renameTo(rootfsDir)) { "Unable to activate rootfs" }
                archive.delete()
                File(rootfsDir, ".phoneagent-version").writeText(manifest.version)
                onState(RootfsInstallState.Ready(manifest.version))
            }.onFailure { onState(RootfsInstallState.Failed(it.message ?: "Installation failed")) }
        }

    /** Repairs host-bound PRoot link2symlink entries from old/offline build archives. */
    fun repairRuntimeLayout(root: File = rootfsDir): Int {
        if (!root.isDirectory) return 0
        var repaired = 0
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(link: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isSymbolicLink) return FileVisitResult.CONTINUE
                val target = runCatching { Files.readSymbolicLink(link).toString() }.getOrNull()
                    ?: return FileVisitResult.CONTINUE
                val marker = "/files/runtime/debian/"
                if (marker !in target) return FileVisitResult.CONTINUE
                val relativeTarget = target.substringAfter(marker).trimStart('/')
                val portable = link.parent.relativize(root.resolve(relativeTarget).toPath())
                runCatching {
                    Files.delete(link)
                    Files.createSymbolicLink(link, portable)
                    repaired++
                }
                return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult = FileVisitResult.CONTINUE
        })

        // A failed dpkg hard-link backup can leave only status-new. Recover the
        // package database before the next dpkg --configure -a attempt.
        runCatching {
            val dpkg = root.resolve("var/lib/dpkg")
            val status = dpkg.resolve("status")
            if (!status.isFile) {
                val recovery = dpkg.resolve("status-new").takeIf(File::isFile)
                    ?: dpkg.listFiles().orEmpty()
                        .filter { it.isFile && it.name.startsWith(".l2s.status") }
                        .maxByOrNull(File::lastModified)
                recovery?.copyTo(status, overwrite = true)?.also { repaired++ }
            }
            dpkg.resolve("status-old").takeIf { Files.isSymbolicLink(it.toPath()) }?.let { old ->
                Files.deleteIfExists(old.toPath())
                if (status.isFile) status.copyTo(old, overwrite = true)
                repaired++
            }
            dpkg.listFiles().orEmpty().filter { it.name.startsWith(".l2s.status") }.forEach { artifact ->
                runCatching { Files.deleteIfExists(artifact.toPath()) }.onSuccess { repaired++ }
            }
        }
        return repaired
    }

    suspend fun importArchive(file: File, manifest: RootfsManifest, onState: (RootfsInstallState) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runtimeDir.mkdirs()
            file.copyTo(archive, overwrite = true)
            install(manifest.copy(url = "file://${file.absolutePath}"), onState)
        }

    private fun download(manifest: RootfsManifest, onState: (RootfsInstallState) -> Unit) {
        if (manifest.url.startsWith("file://")) return
        if (archive.length() == manifest.compressedBytes &&
            sha256(archive).equals(manifest.sha256, ignoreCase = true)
        ) {
            onState(RootfsInstallState.Downloading(archive.length(), manifest.compressedBytes))
            return
        }
        if (archive.length() >= manifest.compressedBytes) archive.delete()

        var retryWithoutRange = false
        repeat(2) { attempt ->
            val offset = if (retryWithoutRange) 0L else archive.takeIf(File::exists)?.length() ?: 0L
            val request = Request.Builder().url(manifest.url).apply {
                if (offset > 0) header("Range", "bytes=$offset-")
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.code == 416 && attempt == 0) {
                    // A stale or already-complete partial file can make the requested range
                    // start at/after EOF. Restart once without a Range header.
                    archive.delete()
                    retryWithoutRange = true
                    return@use
                }
                check(response.isSuccessful) { "Rootfs download failed: HTTP ${response.code}" }
                val contentRange = response.header("Content-Range")
                val append = offset > 0 && response.code == 206 &&
                    contentRange?.startsWith("bytes $offset-") == true
                RandomAccessFile(archive, "rw").use { output ->
                    if (append) output.seek(offset) else output.setLength(0)
                    var downloaded = if (append) offset else 0L
                    val buffer = ByteArray(128 * 1024)
                    response.body.byteStream().use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            check(downloaded <= manifest.compressedBytes) {
                                "Rootfs download exceeded the signed manifest size"
                            }
                            onState(RootfsInstallState.Downloading(downloaded, manifest.compressedBytes))
                        }
                    }
                    check(downloaded == manifest.compressedBytes) {
                        "Rootfs download incomplete: $downloaded/${manifest.compressedBytes} bytes"
                    }
                }
                return
            }
        }
        error("Rootfs download could not be restarted")
    }

    private fun copyEmbedded(manifest: RootfsManifest, onState: (RootfsInstallState) -> Unit) {
        val assetPath = requireNotNull(manifest.embeddedAsset)
        if (archive.length() == manifest.compressedBytes &&
            sha256(archive).equals(manifest.sha256, ignoreCase = true)
        ) return
        archive.delete()
        context.assets.open(assetPath).use { input ->
            archive.outputStream().buffered().use { output ->
                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    check(copied <= manifest.compressedBytes) { "Embedded runtime exceeds signed manifest size" }
                    onState(RootfsInstallState.CopyingEmbedded(copied, manifest.compressedBytes))
                }
                check(copied == manifest.compressedBytes) {
                    "Embedded runtime is incomplete: $copied/${manifest.compressedBytes} bytes"
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun configureNetworkFiles(rootfs: File) {
        // Build-time proxy configuration must never reach a phone. Keep this defensive
        // cleanup even though release archives are sanitized before packaging so older
        // imported archives are repaired as well.
        rootfs.resolve("etc/apt/apt.conf.d/99-sai-build-proxy").delete()
        rootfs.resolve("etc/apt/apt.conf.d").listFiles().orEmpty().forEach { config ->
            if (config.isFile && runCatching { "127.0.0.1:18080" in config.readText() }.getOrDefault(false)) {
                config.delete()
            }
        }
        rootfs.resolve("etc/apt/sources.list").takeIf(File::isFile)?.let { sources ->
            sources.writeText(
                sources.readText()
                    .replace("http://deb.debian.org", "https://deb.debian.org")
                    .replace("http://security.debian.org", "https://security.debian.org"),
            )
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val dnsServers = connectivity.getLinkProperties(connectivity.activeNetwork)
            ?.dnsServers.orEmpty().mapNotNull { it.hostAddress }.distinct()
            .ifEmpty { listOf("1.1.1.1", "8.8.8.8") }
        val resolvConf = rootfs.resolve("etc/resolv.conf")
        runCatching { java.nio.file.Files.deleteIfExists(resolvConf.toPath()) }.getOrThrow()
        resolvConf.parentFile?.mkdirs()
        resolvConf.writeText(dnsServers.joinToString(separator = "\n", postfix = "\n") { "nameserver $it" })
        rootfs.resolve("etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
    }
}
