package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationProjectionTest {
    @Test fun latestTurnNeverReplaysFirstReply() {
        val events = listOf(
            AgentEvent.UserMessage("first"), AgentEvent.AssistantMessageStarted("a"),
            AgentEvent.AssistantDelta("old reply"), AgentEvent.AssistantMessageCompleted("a"),
            AgentEvent.UserMessage("second"), AgentEvent.AssistantMessageStarted("b"),
            AgentEvent.AssistantDelta("new "), AgentEvent.AssistantDelta("reply"),
            AgentEvent.AssistantMessageCompleted("b"),
        )
        assertEquals("new reply", ConversationProjection.latestAssistantText(events))
    }

    @Test fun speechRequestsDoNotPolluteAssistantText() {
        val events = listOf(
            AgentEvent.UserMessage("hello"), AgentEvent.AssistantMessageStarted("b"),
            AgentEvent.AssistantDelta("visible"), AgentEvent.SpeechRequested("s", "spoken", "turn-2"),
            AgentEvent.AssistantMessageCompleted("b"),
        )
        assertEquals("visible", ConversationProjection.latestAssistantText(events))
    }
}
