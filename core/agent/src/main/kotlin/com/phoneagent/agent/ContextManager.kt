package com.phoneagent.agent

import com.phoneagent.provider.MessageRole
import com.phoneagent.provider.ModelMessage
import com.phoneagent.provider.textContent

data class CompactionResult(
    val messages: List<ModelMessage>,
    val removedCount: Int,
    val summary: String?,
)

class ContextManager(
    private val charsPerToken: Double = 3.5,
    private val compactAtRatio: Double = 0.85,
) {
    fun compact(messages: List<ModelMessage>, contextWindow: Int, reservedOutputTokens: Int): CompactionResult {
        val available = (contextWindow - reservedOutputTokens).coerceAtLeast(4_000)
        val estimate = messages.sumOf(::estimateMessageTokens)
        if (estimate < available * compactAtRatio) return CompactionResult(messages, 0, null)

        val system = messages.takeWhile { it.role == MessageRole.SYSTEM }
        val tailBudget = (available * 0.45).toInt()
        val tailGroups = mutableListOf<List<ModelMessage>>()
        var used = 0
        val groups = atomicGroups(messages.drop(system.size))
        for (group in groups.asReversed()) {
            val tokens = group.sumOf(::estimateMessageTokens)
            if (used + tokens > tailBudget && tailGroups.isNotEmpty()) break
            tailGroups += group
            used += tokens
        }
        val tail = tailGroups.asReversed().flatten()
        val removed = messages.drop(system.size).dropLast(tail.size)
        val summary = summarize(removed)
        val compacted = system + ModelMessage(
            MessageRole.SYSTEM,
            "Earlier session checkpoint (preserve this structure and all unresolved work):\n$summary",
        ) + tail
        return CompactionResult(compacted, removed.size, summary)
    }

    fun estimateTokens(text: String): Int = (text.length / charsPerToken).toInt().coerceAtLeast(1)

    private fun estimateMessageTokens(message: ModelMessage): Int = estimateTokens(message.textContent()) +
        message.toolCalls.sumOf { estimateTokens(it.id) + estimateTokens(it.name) + estimateTokens(it.arguments) }

    private fun atomicGroups(messages: List<ModelMessage>): List<List<ModelMessage>> {
        val groups = mutableListOf<List<ModelMessage>>()
        var index = 0
        while (index < messages.size) {
            val message = messages[index]
            if (message.role == MessageRole.ASSISTANT && message.toolCalls.isNotEmpty()) {
                val group = mutableListOf(message)
                var next = index + 1
                while (next < messages.size && messages[next].role == MessageRole.TOOL) {
                    group += messages[next]
                    next++
                }
                groups += group
                index = next
            } else {
                groups += listOf(message)
                index++
            }
        }
        return groups
    }

    private fun summarize(messages: List<ModelMessage>): String {
        if (messages.isEmpty()) return "No earlier messages."
        val transcript = messages.takeLast(36).joinToString("\n") { message ->
            val content = message.textContent().replace(Regex("\\s+"), " ").take(500)
            val calls = message.toolCalls.joinToString { "${it.name}[${it.id}](${it.arguments.take(160)})" }
            "- ${message.role}${if (calls.isNotEmpty()) " tools=$calls" else ""}: $content"
        }.take(10_000)
        return """
            ## User constraints and decisions
            Preserve all explicit requirements in the transcript below.
            ## Unfinished plan and next action
            Infer only from explicit pending tool calls or incomplete user requests; do not invent completion.
            ## Files, tool calls, and verification
            Preserve tool call IDs, file paths, failures, and test results verbatim where present.
            ## Recent compacted transcript
            $transcript
        """.trimIndent()
    }
}
