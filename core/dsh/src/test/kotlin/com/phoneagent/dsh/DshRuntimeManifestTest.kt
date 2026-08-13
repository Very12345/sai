package com.phoneagent.dsh

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DshRuntimeManifestTest {
    @Test fun decodesPinnedRuntime() {
        val manifest = Json.decodeFromString<DshRuntimeManifest>(
            """{"schemaVersion":1,"runtimeVersion":"r1","dshVersion":"0.1.0-rc.6","nodeVersion":"24.19.0","sourceCommit":"abc","packageLockSha256":{"arm64-v8a":"def"},"archives":{}}""",
        )
        assertEquals("0.1.0-rc.6", manifest.dshVersion)
        assertEquals("24.19.0", manifest.nodeVersion)
        assertEquals("def", manifest.packageLockSha256["arm64-v8a"])
    }
}
