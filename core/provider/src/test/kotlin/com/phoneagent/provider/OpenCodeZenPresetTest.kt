package com.phoneagent.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeZenPresetTest {
    @Test
    fun `zen free tier is explicit and does not require a user secret`() {
        val zen = ProviderPresets.all.first { it.id == "opencode-zen" }

        assertEquals("https://opencode.ai/zen", zen.baseUrl)
        assertEquals("/v1/chat/completions", zen.requestPath)
        assertEquals("/v1/models", zen.modelsPath)
        assertEquals("deepseek-v4-flash-free", zen.defaultModel)
        assertEquals("public", zen.anonymousApiKey)
        assertTrue(zen.capabilities.modelDiscovery)
    }
}
