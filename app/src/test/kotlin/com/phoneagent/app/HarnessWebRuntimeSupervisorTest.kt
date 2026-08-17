package com.phoneagent.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.phoneagent.provider.ProviderPresets

class HarnessWebRuntimeSupervisorTest {
    @Test
    fun `fresh Claude config registers the mounted sai workspace`() {
        val merged = HarnessWebRuntimeSupervisor.mergeClaudeProjectConfig(null)
        val projects = Json.parseToJsonElement(merged).jsonObject["projects"]?.jsonObject

        assertNotNull(projects)
        assertNotNull(projects!!["/home/phoneagent"])
    }

    @Test
    fun `existing Claude settings and projects are preserved`() {
        val merged = HarnessWebRuntimeSupervisor.mergeClaudeProjectConfig(
            """{"theme":"dark","projects":{"/home/other":{"allowedTools":["Read"]}}}""",
        )
        val root = Json.parseToJsonElement(merged).jsonObject
        val projects = root["projects"]?.jsonObject

        assertEquals("\"dark\"", root["theme"].toString())
        assertNotNull(projects?.get("/home/other"))
        assertNotNull(projects?.get("/home/phoneagent"))
    }

    @Test
    fun `Codex provider base includes the request API prefix exactly once`() {
        val zen = ProviderPresets.all.first { it.id == "opencode-zen" }
        val qwen = ProviderPresets.all.first { it.id == "qwen" }

        assertEquals("https://opencode.ai/zen/v1", HarnessWebRuntimeSupervisor.codexProviderBaseUrl(zen))
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            HarnessWebRuntimeSupervisor.codexProviderBaseUrl(qwen),
        )
    }
}
