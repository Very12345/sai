package com.phoneagent.app

import android.content.ContentResolver
import android.net.Uri
import com.phoneagent.extensions.ExtensionInstallPlan
import com.phoneagent.extensions.ExtensionKind
import com.phoneagent.extensions.ExtensionPermission
import com.phoneagent.extensions.StagedExtensionFile
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream

class ExtensionZipImporter(private val resolver: ContentResolver) {
    private val json = Json { ignoreUnknownKeys = true }

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
        val packageFile = normalizedFiles.firstOrNull { it.path == "package.json" }
        val packageManifest = packageFile?.let { runCatching { json.parseToJsonElement(it.contents).jsonObject }.getOrNull() }
        val hasCordisPatch = normalizedFiles.any { it.path in DSH_PATCHES }
        val kind = when {
            normalizedFiles.any { it.path == "SKILL.md" } -> ExtensionKind.SKILL
            normalizedFiles.any { it.path == ".mcp.json" } -> ExtensionKind.MCP
            packageManifest?.containsKey("dsh") == true || hasCordisPatch -> ExtensionKind.PLUGIN
            else -> error("ZIP 中未找到 SKILL.md、.mcp.json 或有效 DSH bundle")
        }
        if (kind == ExtensionKind.PLUGIN) validateDshPlugin(packageManifest, normalizedFiles, hasCordisPatch, warnings)
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
        if (path == ".mcp.json" && Regex("(?i)(authorization|api[_-]?key|secret|token|password)\\s*[\"']?\\s*:").containsMatchIn(text)) {
            warnings += "阻止 MCP ZIP 中的明文凭据；请导入后使用 Keystore 凭据引用"
        }
    }

    private fun validateDshPlugin(
        manifest: JsonObject?,
        files: List<StagedExtensionFile>,
        hasCordisPatch: Boolean,
        warnings: MutableList<String>,
    ) {
        requireNotNull(manifest) { "DSH 插件缺少根 package.json" }
        require(manifest.containsKey("dsh") || hasCordisPatch) { "插件没有 DSH bundle 或 Cordis patch" }
        val license = (manifest["license"] as? JsonPrimitive)?.contentOrNull
        if (license.isNullOrBlank() && files.none { it.path.startsWith("LICENSE", true) }) {
            warnings += "DSH 插件没有可验证的许可证；公开源码不等同于 MIT，请自行确认作者授权"
        }
        val scripts = manifest["scripts"] as? JsonObject
        val forbidden = LIFECYCLE_SCRIPTS.filter { scripts?.containsKey(it) == true }
        require(forbidden.isEmpty()) { "DSH 插件包含禁止的生命周期脚本：${forbidden.joinToString()}" }
        val main = (manifest["main"] as? JsonPrimitive)?.contentOrNull
            ?: (manifest["module"] as? JsonPrimitive)?.contentOrNull
            ?: "index.js"
        require(main.endsWith(".js") || main.endsWith(".mjs") || main.endsWith(".cjs")) {
            "DSH 插件没有预构建 JavaScript 入口"
        }
        require(files.any { it.path == main.removePrefix("./") }) { "DSH 插件入口不存在：$main" }
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
        private val DSH_PATCHES = setOf("cordis.patch.yml", "cordis.patch.yaml", "dsh.bundle.patch.yml")
        private val LIFECYCLE_SCRIPTS = setOf("preinstall", "install", "postinstall", "prepare", "prepublish", "prepublishOnly")
    }
}
