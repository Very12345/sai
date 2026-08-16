package com.phoneagent.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BundledGitHubCliArchiveTest {
    @Test fun readsAaptExpandedRawTar() {
        assertEquals("gh-test", readPayload(tarBytes()))
    }

    @Test fun readsSourceTarGzip() {
        val compressed = ByteArrayOutputStream().also { output ->
            GzipCompressorOutputStream(output).use { it.write(tarBytes()) }
        }.toByteArray()
        assertEquals("gh-test", readPayload(compressed))
    }

    private fun readPayload(bytes: ByteArray): String =
        githubCliTarInputStream(ByteArrayInputStream(bytes)).use { tar ->
            val entry = tar.nextEntry
            require(entry.name.endsWith("/bin/gh"))
            tar.readBytes().toString(Charsets.UTF_8)
        }

    private fun tarBytes(): ByteArray {
        val payload = "gh-test".toByteArray()
        return ByteArrayOutputStream().also { output ->
            TarArchiveOutputStream(output).use { tar ->
                val entry = TarArchiveEntry("gh_2.97.0_linux_arm64/bin/gh").apply { size = payload.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(payload)
                tar.closeArchiveEntry()
                tar.finish()
            }
        }.toByteArray()
    }
}
