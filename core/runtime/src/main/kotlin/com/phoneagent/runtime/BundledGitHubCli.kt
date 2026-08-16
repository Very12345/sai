package com.phoneagent.runtime

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class GitHubCliInstallResult(val version: String, val binary: File, val alreadyInstalled: Boolean)

/** Installs the signed-in-APK GitHub CLI into the private Debian rootfs without network access. */
class BundledGitHubCli(
    context: Context,
    private val rootfs: File,
) {
    private val assets = context.applicationContext.assets

    suspend fun install(): Result<GitHubCliInstallResult> = withContext(Dispatchers.IO) {
        runCatching {
            check(rootfs.resolve("bin/bash").isFile) { "请先初始化 Debian 环境" }
            val bundle = bundleForDevice()
            val target = rootfs.resolve("usr/local/bin/gh")
            val marker = rootfs.resolve(".sai-gh-${bundle.version}")
            if (target.isFile && marker.isFile && sha256(target) == bundle.binarySha256) {
                return@runCatching GitHubCliInstallResult(bundle.version, target, true)
            }

            target.parentFile?.mkdirs()
            val staging = File(target.parentFile, ".gh-${bundle.version}.tmp")
            staging.delete()
            openBundleAsset(bundle).use { raw ->
                githubCliTarInputStream(raw).use { tar ->
                    var found = false
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.endsWith("/bin/gh")) {
                            staging.outputStream().buffered().use { output -> tar.copyTo(output, 256 * 1024) }
                            found = true
                            break
                        }
                    }
                    check(found) { "内置 gh 资源缺少可执行文件" }
                }
            }
            check(staging.length() == bundle.binaryBytes) { "内置 gh 文件大小校验失败" }
            check(sha256(staging) == bundle.binarySha256) { "内置 gh SHA-256 校验失败" }
            check(staging.setExecutable(true, false)) { "无法设置 gh 执行权限" }
            if (target.exists()) check(target.delete()) { "无法更新旧版 gh" }
            check(staging.renameTo(target)) { "无法启用 gh" }
            rootfs.listFiles().orEmpty().filter { it.name.startsWith(".sai-gh-") }.forEach(File::delete)
            marker.writeText("${bundle.version}\n${bundle.binarySha256}\n")
            GitHubCliInstallResult(bundle.version, target, false)
        }
    }

    private fun bundleForDevice(): Bundle = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> ARM64
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> X86_64
        else -> error("当前 ABI 不支持内置 GitHub CLI")
    }

    /**
     * AAPT transparently expands assets ending in `.gz` and stores them in the APK without the
     * suffix. Local source trees still contain the original `.tar.gz`, while installed APKs expose
     * `.tar`. Accept both forms so GitHub login does not depend on the Android packaging detail.
     */
    private fun openBundleAsset(bundle: Bundle): InputStream {
        val candidates = listOf(bundle.asset, "${bundle.asset}.gz")
        candidates.forEach { candidate ->
            runCatching { return assets.open(candidate).buffered() }
        }
        error("内置 gh 资源缺失：${candidates.joinToString(" 或 ")}")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Bundle(
        val version: String,
        val asset: String,
        val binarySha256: String,
        val binaryBytes: Long,
    )

    companion object {
        private val ARM64 = Bundle(
            "2.97.0", "runtime/gh_2.97.0_linux_arm64.tar",
            "ccbb0f14178faefac1cb0f336a853071fa63a1d0df23ef5ab7a304fe3859e082", 38_076_578,
        )
        private val X86_64 = Bundle(
            "2.97.0", "runtime/gh_2.97.0_linux_amd64.tar",
            "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409", 40_992_930,
        )
    }
}

/** Opens either the raw TAR exposed by AAPT or the original TAR.GZ used by source builds. */
internal fun githubCliTarInputStream(input: InputStream): TarArchiveInputStream {
    val buffered = input.buffered().apply { mark(2) }
    val first = buffered.read()
    val second = buffered.read()
    buffered.reset()
    val archive = if (first == 0x1f && second == 0x8b) GzipCompressorInputStream(buffered) else buffered
    return TarArchiveInputStream(archive)
}
