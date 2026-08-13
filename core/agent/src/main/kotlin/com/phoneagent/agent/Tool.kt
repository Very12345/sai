package com.phoneagent.agent

import com.phoneagent.provider.ToolDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ToolResult(
    val success: Boolean,
    val output: String,
    val metadata: Map<String, String> = emptyMap(),
    val truncated: Boolean = false,
)

data class ToolExecutionContext(
    val workspace: File,
    val mode: AgentMode,
    val sessionId: String,
)

interface Tool {
    val definition: ToolDefinition
    val capabilities: Set<ToolCapability>

    suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String? = null
    suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult
}

class ToolRegistry(tools: Collection<Tool> = emptyList()) {
    private val tools = sortedMapOf<String, Tool>()

    init { tools.forEach(::register) }

    @Synchronized
    fun register(tool: Tool) {
        require(tool.definition.name.matches(Regex("[a-zA-Z0-9_.-]+"))) { "Invalid tool name" }
        tools[tool.definition.name] = tool
    }

    @Synchronized
    fun get(name: String): Tool? = tools[name]

    @Synchronized
    fun definitions(): List<ToolDefinition> = tools.values.map { it.definition }

    /** Byte-stable provider-visible contract used to detect prompt-cache shape drift. */
    @Synchronized
    fun contractSnapshot(): String = Json.encodeToString(tools.values.map { it.definition })

    @Synchronized
    fun contractHash(): String = MessageDigest.getInstance("SHA-256")
        .digest(contractSnapshot().encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
}

fun interface ApprovalGate {
    suspend fun request(request: ApprovalRequest): ApprovalDecision
}
