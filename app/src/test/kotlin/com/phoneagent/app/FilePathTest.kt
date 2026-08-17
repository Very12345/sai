package com.phoneagent.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePathTest {
    @Test
    fun `runtime directory is represented relative to sai root`() {
        val root = Files.createTempDirectory("sai-root").toFile()
        val runtime = File(root, "dsh/runtime").apply { mkdirs() }

        assertEquals("dsh/runtime", relativeFilePath(root, runtime))
    }

    @Test
    fun `path outside sai root is rejected`() {
        val parent = Files.createTempDirectory("sai-parent").toFile()
        val root = File(parent, "files").apply { mkdirs() }
        val outside = File(parent, "outside").apply { mkdirs() }

        assertNull(relativeFilePath(root, outside))
    }
}
