package com.phoneagent.runtime

import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class CommonsCompressRootfsExtractorTest {
    @Test
    fun stripsSingleDistributionDirectory() = runBlocking {
        val temporary = createTempDirectory("phoneagent-rootfs-test-").toFile()
        val archive = temporary.resolve("rootfs.tar.xz")
        writeArchive(archive, "debian-trixie-aarch64/bin/bash" to "#!/bin/bash\n")
        val destination = temporary.resolve("rootfs").apply { mkdirs() }

        CommonsCompressRootfsExtractor().extract(archive, destination)

        assertEquals("#!/bin/bash\n", destination.resolve("bin/bash").readText())
        assertFalse(destination.resolve("debian-trixie-aarch64").exists())
    }

    private fun writeArchive(archive: File, vararg files: Pair<String, String>) {
        TarArchiveOutputStream(XZCompressorOutputStream(archive.outputStream())).use { tar ->
            files.forEach { (name, contents) ->
                val bytes = contents.toByteArray()
                val entry = TarArchiveEntry(name).apply {
                    size = bytes.size.toLong()
                    mode = 0x1ED
                }
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
    }
}
