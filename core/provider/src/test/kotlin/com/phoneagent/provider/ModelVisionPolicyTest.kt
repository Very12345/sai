package com.phoneagent.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelVisionPolicyTest {
    @Test
    fun `text model gets vision fallback while multimodal model does not`() {
        val deepSeek = ProviderPresets.all.first { it.id == "deepseek" }
        val qwen = ProviderPresets.all.first { it.id == "qwen" }

        assertFalse(ModelVisionPolicy.supportsImageInput(deepSeek.copy(defaultModel = "deepseek-v4-flash")))
        assertFalse(ModelVisionPolicy.supportsImageInput(qwen.copy(defaultModel = "qwen-max")))
        assertTrue(ModelVisionPolicy.supportsImageInput(qwen.copy(defaultModel = "qwen2.5-vl-72b-instruct")))
        assertTrue(ModelVisionPolicy.isVisionCandidate(deepSeek, "deepseek-vl2"))
    }
}
