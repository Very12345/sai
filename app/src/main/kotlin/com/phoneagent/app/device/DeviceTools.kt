package com.phoneagent.app.device

import com.phoneagent.agent.Tool
import com.phoneagent.agent.ToolCapability
import com.phoneagent.agent.ToolExecutionContext
import com.phoneagent.agent.ToolResult
import com.phoneagent.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ObserveDeviceTool : Tool {
    override val definition = ToolDefinition(
        name = "device_observe",
        description = "Observe the current authorized Android app accessibility tree. Password nodes and system security windows are omitted.",
        parameters = buildJsonObject { put("type", "object"); put("properties", buildJsonObject { }) },
    )
    override val capabilities = setOf(ToolCapability.DEVICE_CONTROL)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.Main) {
        val service = PhoneAgentAccessibilityService.instance ?: return@withContext ToolResult(false, "sai accessibility service is disabled")
        val nodes = service.observe()
        ToolResult(true, buildJsonArray {
            nodes.forEach { node -> add(buildJsonObject {
                put("id", node.id); put("class", node.className); put("text", node.text)
                put("description", node.description); put("clickable", node.clickable)
                put("editable", node.editable); put("bounds", node.bounds)
            }) }
        }.toString().take(100_000))
    }
}

class DeviceActionTool : Tool {
    override val definition = ToolDefinition(
        name = "device_action",
        description = "Perform one action in the Android app approved just in time by the user: click, input, swipe, back, home, or launch. For launch, prefer the human-readable appName; sai resolves its package automatically. Final submit clicks must set finalSubmit=true.",
        parameters = buildJsonObject {
            put("type", "object")
            put("required", buildJsonArray { add(JsonPrimitive("action")) })
            put("properties", buildJsonObject {
                put("action", buildJsonObject { put("type", "string"); put("enum", buildJsonArray { listOf("click", "input", "swipe", "back", "home", "launch").forEach { add(JsonPrimitive(it)) } }) })
                put("nodeId", buildJsonObject { put("type", "integer") })
                put("text", buildJsonObject { put("type", "string") })
                put("appName", buildJsonObject { put("type", "string"); put("description", "Human-readable installed app name, for example 微信 or Chrome") })
                put("packageName", buildJsonObject { put("type", "string") })
                put("finalSubmit", buildJsonObject { put("type", "boolean") })
                listOf("startX", "startY", "endX", "endY").forEach { name -> put(name, buildJsonObject { put("type", "number") }) }
            })
        },
    )
    override val capabilities = setOf(ToolCapability.DEVICE_CONTROL)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String =
        "设备操作：${arguments["action"]?.jsonPrimitive?.contentOrNull.orEmpty()}。目标必须属于当前 30 分钟授权会话。"

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.Main) {
        val service = PhoneAgentAccessibilityService.instance ?: return@withContext ToolResult(false, "sai accessibility service is disabled")
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val success = when (action) {
            "click" -> service.click(arguments["nodeId"]?.jsonPrimitive?.intOrNull ?: -1, arguments["finalSubmit"]?.jsonPrimitive?.contentOrNull == "true")
            "input" -> service.input(arguments["nodeId"]?.jsonPrimitive?.intOrNull ?: -1, arguments["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
            "swipe" -> service.swipe(
                arguments.float("startX"), arguments.float("startY"), arguments.float("endX"), arguments.float("endY"),
            )
            "back" -> service.back()
            "home" -> service.home()
            "launch" -> service.launch(
                arguments["packageName"]?.jsonPrimitive?.contentOrNull
                    ?: arguments["appName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            else -> false
        }
        ToolResult(success, if (success) "$action completed" else "$action failed or was blocked")
    }

    private fun JsonObject.float(name: String): Float = this[name]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
}
