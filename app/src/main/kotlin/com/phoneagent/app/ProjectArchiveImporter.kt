package com.phoneagent.app

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream

data class ProjectArchiveReport(
    val fileCount: Int,
    val expandedBytes: Long,
    val containsGit: Boolean,
    val strippedTopDirectory: String?,
    val digest: String,
)

/** Streams a SAF ZIP into a private staging directory with mobile-safe limits. */
class ProjectArchiveImporter(
    private val resolver: ContentResolver,
    private val stagingRoot: File,
) {
    fun extract(uri: Uri, target: File): ProjectArchiveReport {
        stagingRoot.mkdirs()
        val staging = File(stagingRoot, "project-${System.currentTimeMillis()}-${target.name.hashCode().toUInt()}")
        check(staging.mkdirs()) { "无法创建项目导入暂存目录" }
        try {
            val entries = scan(uri)
            val declaredBytes = entries.sumOf { (_, size) -> size.coerceAtLeast(0) }
            require(declaredBytes <= MAX_EXPANDED_BYTES) { "ZIP 声明的展开大小超过 2 GB" }
            require(stagingRoot.usableSpace >= declaredBytes + REQUIRED_HEADROOM_BYTES) {
                "存储空间不足：导入需要约 ${(declaredBytes + REQUIRED_HEADROOM_BYTES) / 1_048_576} MB"
            }
            val top = commonTopDirectory(entries.map { it.first })
            var count = 0
            var expanded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri).use { source ->
                requireNotNull(source) { "无法读取 ZIP" }
                ZipArchiveInputStream(source.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextZipEntry ?: break
                        val safePath = normalizedPath(entry, top) ?: continue
                        validateEntry(entry, safePath)
                        val output = File(staging, safePath).canonicalFile
                        require(output.path.startsWith(staging.canonicalPath + File.separator)) { "ZIP 路径逃逸：${entry.name}" }
                        if (entry.isDirectory) {
                            output.mkdirs()
                            continue
                        }
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { sink ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                expanded += read
                                require(entryBytes <= MAX_ENTRY_BYTES) { "ZIP 单个文件超过 256 MB：$safePath" }
                                require(expanded <= MAX_EXPANDED_BYTES) { "ZIP 展开后超过 2 GB 安全上限" }
                                sink.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                        count++
                        require(count <= MAX_FILES) { "ZIP 文件数量超过 100000" }
                    }
                }
            }
            require(count > 0) { "ZIP 中没有可导入文件" }
            require(!target.exists()) { "项目目录已存在：${target.name}" }
            target.parentFile?.mkdirs()
            check(staging.renameTo(target)) { "无法把暂存项目移动到工作区" }
            return ProjectArchiveReport(
                fileCount = count,
                expandedBytes = expanded,
                containsGit = File(target, ".git").exists(),
                strippedTopDirectory = top,
                digest = digest.digest().joinToString("") { "%02x".format(it) },
            )
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun scan(uri: Uri): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        resolver.openInputStream(uri).use { source ->
            requireNotNull(source) { "无法读取 ZIP" }
            ZipArchiveInputStream(source.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextZipEntry ?: break
                    validateEntry(entry, entry.name)
                    result += entry.name.replace('\\', '/') to entry.size
                    require(result.size <= MAX_FILES) { "ZIP 条目数量超过 100000" }
                }
            }
        }
        return result
    }

    private fun validateEntry(entry: ZipArchiveEntry, path: String) {
        val normalized = path.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/')) { "ZIP 包含绝对路径" }
        require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "ZIP 包含 Windows 绝对路径" }
        require(normalized.split('/').none { it == ".." }) { "ZIP 路径逃逸：${entry.name}" }
        require(!entry.isUnixSymlink) { "ZIP 中的符号链接被阻止：${entry.name}" }
        require(entry.size <= MAX_ENTRY_BYTES || entry.size < 0) { "ZIP 单个文件超过 256 MB：${entry.name}" }
    }

    private fun normalizedPath(entry: ZipArchiveEntry, top: String?): String? {
        var value = entry.name.replace('\\', '/').trimStart('/')
        if (top != null) value = value.removePrefix("$top/")
        return value.trimEnd('/').takeIf(String::isNotBlank)
    }

    private fun commonTopDirectory(paths: List<String>): String? {
        val meaningful = paths.map { it.trim('/').split('/') }.filter { it.isNotEmpty() }
        val candidate = meaningful.firstOrNull()?.firstOrNull() ?: return null
        return candidate.takeIf { top -> meaningful.all { it.size > 1 && it.first() == top } }
    }

    companion object {
        private const val MAX_FILES = 100_000
        private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024
        private const val REQUIRED_HEADROOM_BYTES = 256L * 1024 * 1024
    }
}
