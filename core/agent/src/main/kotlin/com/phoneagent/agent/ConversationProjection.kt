package com.phoneagent.agent

object ConversationProjection {
    /** Returns only the latest assistant message following the latest user turn. */
    fun latestAssistantText(events: List<AgentEvent>): String {
        val lastUser = events.indexOfLast { it is AgentEvent.UserMessage }
        if (lastUser < 0) return ""
        val currentTurn = events.drop(lastUser + 1)
        val lastStart = currentTurn.indexOfLast { it is AgentEvent.AssistantMessageStarted }
        return currentTurn.drop(if (lastStart >= 0) lastStart + 1 else 0)
            .takeWhile { it !is AgentEvent.AssistantMessageCompleted }
            .filterIsInstance<AgentEvent.AssistantDelta>()
            .joinToString("") { it.text }
            .trim()
    }
}
