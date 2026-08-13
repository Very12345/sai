package com.phoneagent.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatToolStreamTest {
    private val profile = ProviderPresets.all.first { it.id == "deepseek" }
        .copy(baseUrl = "https://example.test")

    @Test
    fun emptyContinuationMetadataDoesNotEraseToolNameOrId() {
        val adapter = OpenAiChatAdapter(profile)

        adapter.parseSse(event("""
            {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-real","function":{"name":"list_files","arguments":""}}]},"finish_reason":null}]}
        """))
        adapter.parseSse(event("""
            {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"","function":{"name":"","arguments":"{\"path\":\".\""}}]},"finish_reason":null}]}
        """))
        adapter.parseSse(event("""
            {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":",\"depth\":1}"}}]},"finish_reason":null}]}
        """))
        val events = adapter.parseSse(event("""
            {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
        """))

        val call = events.filterIsInstance<ModelEvent.ToolCall>().single()
        assertEquals("call-real", call.id)
        assertEquals("list_files", call.name)
        assertEquals("{\"path\":\".\",\"depth\":1}", call.arguments)
    }

    @Test
    fun acceptsFlattenedCompatibleToolName() {
        val adapter = OpenAiChatAdapter(profile)
        adapter.parseSse(event("""
            {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"flat-1","name":"read_file","function":{"arguments":"{\"path\":\"a.txt\"}"}}]},"finish_reason":null}]}
        """))
        val events = adapter.parseSse(event("""
            {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
        """))

        assertEquals("read_file", events.filterIsInstance<ModelEvent.ToolCall>().single().name)
    }

    @Test
    fun missingNameFailsOnceInsteadOfExecutingAnEmptyToolInALoop() {
        val adapter = OpenAiChatAdapter(profile)
        adapter.parseSse(event("""
            {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"broken","function":{"arguments":"{}"}}]},"finish_reason":null}]}
        """))
        val events = adapter.parseSse(event("""
            {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
        """))

        assertTrue(events.filterIsInstance<ModelEvent.ToolCall>().isEmpty())
        assertEquals(true, events.filterIsInstance<ModelEvent.Error>().single().retryable)
    }

    private fun event(json: String) = SseEvent(data = json.trimIndent().trim())
}
