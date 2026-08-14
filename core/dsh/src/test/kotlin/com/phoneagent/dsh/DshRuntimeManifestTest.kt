package com.phoneagent.dsh

import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DshRuntimeManifestTest {
    @Test fun decodesPinnedRuntime() {
        val manifest = Json.decodeDshRuntimeManifest(
            """{"schemaVersion":1,"runtimeVersion":"r1","dshVersion":"0.1.0-rc.6","nodeVersion":"24.19.0","sourceCommit":"abc","packageLockSha256":{"arm64-v8a":"def"},"archives":{}}""",
        )
        assertEquals("0.1.0-rc.6", manifest.dshVersion)
        assertEquals("24.19.0", manifest.nodeVersion)
        assertEquals("def", manifest.packageLockSha256["arm64-v8a"])
    }

    @Test fun decodesPowerShellUtf8Bom() {
        val manifest = Json.decodeDshRuntimeManifest(
            "\uFEFF" +
                """{"schemaVersion":1,"runtimeVersion":"r1","dshVersion":"0.1.0-rc.6","nodeVersion":"24.19.0","sourceCommit":"abc","archives":{}}""",
        )
        assertEquals("r1", manifest.runtimeVersion)
    }

    @Test fun restoresBundledNodeExecutePermission() {
        val node = createTempDirectory("sai-dsh-node-").resolve("node").toFile().apply {
            writeText("node")
            setExecutable(false, false)
        }
        assertEquals(true, ensureRuntimeExecutable(node))
        assertEquals(true, node.canExecute())
    }
}
