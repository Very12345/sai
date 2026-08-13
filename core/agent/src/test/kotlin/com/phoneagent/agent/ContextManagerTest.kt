package com.phoneagent.agent

import com.phoneagent.provider.MessageRole
import com.phoneagent.provider.ModelMessage
import com.phoneagent.provider.ModelToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {
    @Test
    fun preservesStableSystemPrefixAndRecentTail() {
        val system = ModelMessage(MessageRole.SYSTEM, "stable system")
        val messages = listOf(system) + (1..20).map { ModelMessage(MessageRole.USER, "message-$it " + "x".repeat(900)) }
        val result = ContextManager(charsPerToken = 1.0, compactAtRatio = 0.5)
            .compact(messages, contextWindow = 8_000, reservedOutputTokens = 1_000)
        assertEquals(system, result.messages.first())
        assertNotNull(result.summary)
        assertTrue(result.removedCount > 0)
        assertTrue(result.messages.last().content.startsWith("message-20"))
    }

    @Test
    fun neverSeparatesToolResultFromItsAssistantCall() {
        val call = ModelToolCall("call-1", "read_file", "{\"path\":\"large.txt\"}")
        val messages = listOf(
            ModelMessage(MessageRole.SYSTEM, "stable"),
            ModelMessage(MessageRole.USER, "x".repeat(5_000)),
            ModelMessage(MessageRole.ASSISTANT, "", toolCalls = listOf(call)),
            ModelMessage(MessageRole.TOOL, "y".repeat(5_000), toolCallId = call.id, name = call.name),
        )
        val compacted = ContextManager(charsPerToken = 1.0, compactAtRatio = 0.2)
            .compact(messages, contextWindow = 6_000, reservedOutputTokens = 1_000).messages
        val toolIndex = compacted.indexOfFirst { it.role == MessageRole.TOOL }
        assertTrue(toolIndex > 0)
        assertTrue(compacted[toolIndex - 1].toolCalls.any { it.id == call.id })
    }
}
