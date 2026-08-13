package com.phoneagent.app

import android.content.ContentResolver
import android.net.Uri
import com.phoneagent.extensions.ExtensionInstallPlan
import com.phoneagent.extensions.ExtensionKind
import com.phoneagent.extensions.ExtensionPermission
import com.phoneagent.extensions.StagedExtensionFile
import java.security.MessageDigest
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream

class ExtensionZipImporter(private val resolver: ContentResolver) {
    fun inspect(uri: Uri, displayName: String): ExtensionInstallPlan {
        val files = mutableListOf<StagedExtensionFile>()
        val warnings = mutableListOf<String>()
        val permissions = mutableSetOf(ExtensionPermission.WORKSPACE_READ)
        var total = 0L
        resolver.openInputStream(uri).use { source ->
            requireNotNull(source) { "无法读取扩展 ZIP" }
            ZipArchiveInputStream(source.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextZipEntry ?: break
                    if (entry.isDirectory) continue
                    val path = normalize(entry.name)
                    require(!entry.isUnixSymlink) { "扩展 ZIP 不允许符号链接：$path" }
                    val bytes = zip.readBytesLimited(MAX_FILE_BYTES)
                    total += bytes.size
                    require(total <= MAX_TOTAL_BYTES) { "扩展 ZIP 展开后超过 16 MB" }
                    require(files.size < MAX_FILES) { "扩展 ZIP 文件数量超过 512" }
                    val text = bytes.decodeToString(throwOnInvalidSequence = true)
                    scan(path, text, permissions, warnings)
                    files += StagedExtensionFile(path, text, sha256(bytes))
                }
            }
        }
        val rootPrefix = commonRoot(files.map(StagedExtensionFile::path))
        val normalizedFiles = if (rootPrefix == null) files else files.map { it.copy(path = it.path.removePrefix("$rootPrefix/")) }
        val kind = when {
            normalizedFiles.any { it.path == "SKILL.md" } -> ExtensionKind.SKILL
            normalizedFiles.any { it.path in MANIFESTS } -> ExtensionKind.PLUGIN
            normalizedFiles.any { it.path == ".mcp.json" } -> ExtensionKind.MCP
            else -> error("ZIP 中未找到 SKILL.md 或受支持的插件清单")
        }
        val digest = sha256(normalizedFiles.sortedBy { it.path }
            .joinToString("\n") { "${it.path}:${it.digest}" }.encodeToByteArray())
        val id = displayName.removeSuffix(".zip").replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { digest.take(12) }
        return ExtensionInstallPlan(
            id = id,
            name = displayName.removeSuffix(".zip"),
            version = digest.take(12),
            source = "SAF ZIP:$displayName",
            kind = kind,
            sourceDigest = digest,
            files = normalizedFiles,
            permissions = permissions,
            warnings = warnings.distinct(),
            safeToStage = warnings.none { it.startsWith("阻止") },
        )
    }

    private fun normalize(raw: String): String {
        val value = raw.replace('\\', '/').trimStart('/')
        require(value.isNotBlank() && value.split('/').none { it.isBlank() || it == ".." }) { "扩展 ZIP 路径逃逸：$raw" }
        require(!value.startsWith(".git/")) { "扩展 ZIP 不允许携带 Git 元数据" }
        return value
    }

    private fun scan(path: String, text: String, permissions: MutableSet<ExtensionPermission>, warnings: MutableList<String>) {
        if (Regex("(?i)https?://|\\b(curl|wget|fetch)\\b").containsMatchIn(text)) permissions += ExtensionPermission.NETWORK
        if (Regex("(?i)\\b(bash|shell|powershell|subprocess|exec)\\b").containsMatchIn(text)) permissions += ExtensionPermission.SHELL
        if (Regex("(?i)(api[_ -]?key|authorization|credential|secret|token)").containsMatchIn(text)) permissions += ExtensionPermission.SECRETS
        if (Regex("(?i)\\b(write|patch|delete|remove|rename|move)\\b").containsMatchIn(text)) permissions += ExtensionPermission.WORKSPACE_WRITE
        if (path.endsWith(".sh") || path.endsWith(".ps1")) warnings += "$path 是脚本；sai 不会自动执行安装脚本"
    }

    private fun commonRoot(paths: List<String>): String? {
        val segments = paths.map { it.split('/') }
        val first = segments.firstOrNull()?.firstOrNull() ?: return null
        return first.takeIf { root -> segments.all { it.size > 1 && it.first() == root } }
    }

    private fun ZipArchiveInputStream.readBytesLimited(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "扩展单文件超过 2 MB" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_FILES = 512
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 16L * 1024 * 1024
        private val MANIFESTS = setOf(
            ".codex-plugin/plugin.json",
            ".claude-plugin/plugin.json",
            ".claude-plugin/marketplace.json",
            "reasonix-plugin.json",
            "plugin.json",
        )
    }
}
