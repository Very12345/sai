package com.phoneagent.app

import com.phoneagent.agent.Tool
import com.phoneagent.agent.ToolCapability
import com.phoneagent.agent.ToolExecutionContext
import com.phoneagent.agent.ToolResult
import com.phoneagent.provider.ToolDefinition
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SpeakTool(private val speak: suspend (String, String) -> Unit) : Tool {
    override val definition = ToolDefinition(
        name = "speak",
        description = "MANDATORY output channel in voice conversation mode. Call exactly once after completing each voice response to broadcast a short user-facing summary. Visible assistant text is NOT spoken automatically. Use at most two short sentences; never include code, diffs, logs, reasoning, URLs, emoji, or secrets.",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("text", buildJsonObject { put("type", "string"); put("maxLength", 300) }) })
            put("required", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("text")) })
            put("additionalProperties", false)
        },
    )
    override val capabilities = emptySet<ToolCapability>()
    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult {
        val text = arguments.getValue("text").jsonPrimitive.content.trim().take(300)
        require(text.isNotBlank()) { "朗读内容不能为空" }
        val id = UUID.randomUUID().toString()
        speak(id, text)
        return ToolResult(true, "Queued concise speech", metadata = mapOf("speechId" to id))
    }
}
