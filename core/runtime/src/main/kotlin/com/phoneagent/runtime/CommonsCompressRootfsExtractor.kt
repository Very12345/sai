package com.phoneagent.runtime

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

class CommonsCompressRootfsExtractor : RootfsExtractor {
    override suspend fun extract(archive: File, destination: File) = withContext(Dispatchers.IO) {
        val root = destination.canonicalFile
        val commonPrefix = detectSingleTopLevelDirectory(archive)
        val pendingHardLinks = mutableListOf<Pair<File, String>>()
        TarArchiveInputStream(XZCompressorInputStream(FileInputStream(archive), true)).use { tar ->
            var entry: TarArchiveEntry? = tar.nextEntry
            while (entry != null) {
                val relativeName = stripPrefix(entry.name, commonPrefix)
                if (relativeName.isBlank()) {
                    entry = tar.nextEntry
                    continue
                }
                val output = safeOutput(root, relativeName)
                when {
                    entry.isDirectory -> output.mkdirs()
                    entry.isSymbolicLink -> {
                        output.parentFile?.mkdirs()
                        runCatching { Files.deleteIfExists(output.toPath()) }
                        Os.symlink(entry.linkName, output.absolutePath)
                    }
                    entry.isLink -> pendingHardLinks += output to stripPrefix(entry.linkName, commonPrefix)
                    entry.isFile -> {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { sink -> tar.copyTo(sink, 128 * 1024) }
                        runCatching { Os.chmod(output.absolutePath, entry.mode and 0x1FF) }
                    }
                }
                entry = tar.nextEntry
            }
        }
        pendingHardLinks.forEach { (output, linkName) ->
            val target = safeOutput(root, linkName)
            output.parentFile?.mkdirs()
            runCatching { Os.link(target.absolutePath, output.absolutePath) }
                .recoverCatching { target.copyTo(output, overwrite = true) }
                .getOrThrow()
        }
    }

    private fun detectSingleTopLevelDirectory(archive: File): String? {
        val components = linkedSetOf<String>()
        var hasTopLevelFile = false
        TarArchiveInputStream(XZCompressorInputStream(FileInputStream(archive), true)).use { tar ->
            var entry: TarArchiveEntry? = tar.nextEntry
            while (entry != null) {
                val normalized = entry.name.removePrefix("./").trimStart('/')
                if (normalized.isNotBlank()) {
                    val separator = normalized.indexOf('/')
                    if (separator < 0) {
                        if (!entry.isDirectory) hasTopLevelFile = true
                        components += normalized
                    } else {
                        components += normalized.substring(0, separator)
                    }
                }
                if (components.size > 1 || hasTopLevelFile) return null
                entry = tar.nextEntry
            }
        }
        return components.singleOrNull()
    }

    private fun stripPrefix(rawName: String, prefix: String?): String {
        val normalized = rawName.removePrefix("./").trimStart('/')
        if (prefix == null) return normalized
        return when {
            normalized == prefix -> ""
            normalized.startsWith("$prefix/") -> normalized.removePrefix("$prefix/")
            else -> normalized
        }
    }

    private fun safeOutput(root: File, rawName: String): File {
        val normalized = rawName.removePrefix("./").trimStart('/')
        require(normalized.isNotBlank()) { "Invalid empty tar entry" }
        val output = File(root, normalized).canonicalFile
        require(output.path.startsWith(root.path + File.separator)) { "Tar entry escapes destination: $rawName" }
        return output
    }
}
