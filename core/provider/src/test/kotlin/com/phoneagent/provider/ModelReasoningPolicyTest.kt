package com.phoneagent.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelReasoningPolicyTest {
    @Test
    fun glmFiveTwoExposesCollapsedOfficialLevels() {
        val profile = ProviderPresets.all.first().copy(
            defaultModel = "glm-5.2",
            capabilities = ProviderCapabilities(reasoning = true),
        )
        assertEquals(
            listOf("Auto", "Disabled", "High", "Max"),
            ModelReasoningPolicy.options(profile).map(ReasoningOption::label).sorted(),
        )
        assertEquals(ReasoningEffort.HIGH, ModelReasoningPolicy.normalize(profile.copy(
            reasoningEffort = ReasoningEffort.HIGH,
            reasoningSelection = ReasoningSelection(ReasoningMode.ENABLED, ReasoningEffort.HIGH),
        )).reasoningEffort)
    }

    @Test
    fun anthropicExposesMaxLabel() {
        val profile = ProviderProfile(
            id = "anthropic",
            displayName = "Anthropic",
            protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://example.test",
            requestPath = "/v1/messages",
            defaultModel = "claude-opus",
            capabilities = ProviderCapabilities(reasoning = true),
        )
        assertTrue(ModelReasoningPolicy.options(profile).any { it.label == "Max" })
    }

    @Test
    fun deepSeekV4FlashUsesModelSpecificFourLevelsEvenBehindCustomEndpoint() {
        val profile = ProviderProfile(
            id = "custom-router",
            displayName = "Custom",
            protocol = ProviderProtocol.OPENAI_CHAT,
            baseUrl = "https://example.test",
            requestPath = "/v1/chat/completions",
            defaultModel = "vendor/deepseek-v4-flash",
            capabilities = ProviderCapabilities(reasoning = true),
        )

        assertEquals(
            listOf("Auto", "High", "Max", "Disabled"),
            ModelReasoningPolicy.options(profile).map(ReasoningOption::label),
        )
        assertTrue(ModelReasoningPolicy.usesDeepSeekThinkingWire(profile))
    }
}
