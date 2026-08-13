package com.phoneagent.provider

import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolRequestTest {
    private val toolCall = ModelToolCall("call-42", "read_file", "{\"path\":\"README.md\"}")
    private val messages = listOf(
        ModelMessage(MessageRole.USER, "Inspect the project"),
        ModelMessage(MessageRole.ASSISTANT, "", toolCalls = listOf(toolCall), reasoningContent = "tool reasoning"),
        ModelMessage(MessageRole.TOOL, "1: # Project", toolCallId = toolCall.id, name = toolCall.name),
    )
    private val credential = ProviderCredential("test-secret")

    @Test
    fun responsesPreservesFunctionCallAndOutputIds() {
        val profile = ProviderPresets.all.first { it.id == "openai" }.copy(baseUrl = "https://example.test")
        val body = body(OpenAiResponsesAdapter(profile).buildStreamingRequest(request(profile), credential))
        assertTrue(body.contains("\"type\":\"function_call\""))
        assertTrue(body.contains("\"type\":\"function_call_output\""))
        assertTrue(body.contains("\"call_id\":\"call-42\""))
    }

    @Test
    fun chatPreservesAssistantToolCallAndToolResult() {
        val profile = ProviderPresets.all.first { it.id == "deepseek" }.copy(baseUrl = "https://example.test")
        val body = body(OpenAiChatAdapter(profile).buildStreamingRequest(request(profile, ReasoningEffort.HIGH), credential))
        assertTrue(body.contains("\"tool_calls\""))
        assertTrue(body.contains("\"tool_call_id\":\"call-42\""))
        assertTrue(body.contains("\"model\":\"deepseek-v4-flash\""))
        assertTrue(body.contains("\"reasoning_content\":\"tool reasoning\""))
        assertTrue(body.contains("\"reasoning_effort\":\"high\""))
        assertTrue(body.contains("\"thinking\":{\"type\":\"enabled\"}"))
    }

    @Test
    fun anthropicUsesToolUseAndToolResultBlocks() {
        val profile = ProviderPresets.all.first { it.id == "anthropic" }.copy(baseUrl = "https://example.test")
        val body = body(AnthropicAdapter(profile).buildStreamingRequest(request(profile, ReasoningEffort.MEDIUM), credential))
        assertTrue(body.contains("\"type\":\"tool_use\""))
        assertTrue(body.contains("\"type\":\"tool_result\""))
        assertTrue(body.contains("\"tool_use_id\":\"call-42\""))
        assertTrue(body.contains("\"output_config\":{\"effort\":\"medium\"}"))
    }

    @Test
    fun geminiUsesFunctionCallAndResponseParts() {
        val profile = ProviderPresets.all.first { it.id == "gemini" }.copy(baseUrl = "https://example.test")
        val body = body(GeminiAdapter(profile).buildStreamingRequest(request(profile), credential))
        assertTrue(body.contains("\"functionCall\""))
        assertTrue(body.contains("\"functionResponse\""))
        assertTrue(body.contains("\"id\":\"call-42\""))
    }

    @Test
    fun responsesSendsReasoningEffort() {
        val profile = ProviderPresets.all.first { it.id == "openai" }.copy(baseUrl = "https://example.test")
        val body = body(OpenAiResponsesAdapter(profile).buildStreamingRequest(request(profile, ReasoningEffort.XHIGH), credential))
        assertTrue(body.contains("\"reasoning\":{\"effort\":\"xhigh\"}"))
    }

    @Test
    fun gemini25MapsEffortToThinkingBudget() {
        val profile = ProviderPresets.all.first { it.id == "gemini" }.copy(baseUrl = "https://example.test")
        val request = request(profile, ReasoningEffort.LOW).copy(model = "gemini-2.5-flash")
        val body = body(GeminiAdapter(profile).buildStreamingRequest(request, credential))
        assertTrue(body.contains("\"thinkingBudget\":1024"))
        assertTrue(body.contains("\"includeThoughts\":true"))
    }

    @Test
    fun protocolRepairForcesTheSpeakToolAcrossProviders() {
        val openAi = ProviderPresets.all.first { it.id == "openai" }.copy(baseUrl = "https://example.test")
        val chat = ProviderPresets.all.first { it.id == "deepseek" }.copy(baseUrl = "https://example.test")
        val anthropic = ProviderPresets.all.first { it.id == "anthropic" }.copy(baseUrl = "https://example.test")
        val gemini = ProviderPresets.all.first { it.id == "gemini" }.copy(baseUrl = "https://example.test")

        assertTrue(body(OpenAiResponsesAdapter(openAi).buildStreamingRequest(request(openAi).copy(requiredToolName = "speak"), credential)).contains("\"tool_choice\":{\"type\":\"function\",\"name\":\"speak\"}"))
        assertTrue(body(OpenAiChatAdapter(chat).buildStreamingRequest(request(chat).copy(requiredToolName = "speak"), credential)).contains("\"function\":{\"name\":\"speak\"}"))
        assertTrue(body(AnthropicAdapter(anthropic).buildStreamingRequest(request(anthropic).copy(requiredToolName = "speak"), credential)).contains("\"tool_choice\":{\"type\":\"tool\",\"name\":\"speak\"}"))
        assertTrue(body(GeminiAdapter(gemini).buildStreamingRequest(request(gemini).copy(requiredToolName = "speak"), credential)).contains("\"allowedFunctionNames\":[\"speak\"]"))
    }

    private fun request(profile: ProviderProfile, effort: ReasoningEffort = ReasoningEffort.AUTO) =
        ModelRequest(profile.defaultModel, messages, reasoningEffort = effort)

    private fun body(request: okhttp3.Request): String = Buffer().also {
        requireNotNull(request.body).writeTo(it)
    }.readUtf8()
}
