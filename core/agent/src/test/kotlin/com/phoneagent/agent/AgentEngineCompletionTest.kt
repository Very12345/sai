package com.phoneagent.agent

import com.phoneagent.provider.ModelEvent
import com.phoneagent.provider.ModelInfo
import com.phoneagent.provider.ModelRequest
import com.phoneagent.provider.ProviderAdapter
import com.phoneagent.provider.ProviderCredential
import com.phoneagent.provider.ProviderPresets
import com.phoneagent.provider.ProviderProbe
import com.phoneagent.provider.ReasoningMode
import com.phoneagent.provider.ToolDefinition
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class AgentEngineCompletionTest {
    @Test
    fun `reasoning-only response is asked once for a visible conclusion`() = runBlocking {
        var calls = 0
        val engine = engine { 
            calls++
            if (calls == 1) listOf(ModelEvent.ReasoningDelta("internal"), ModelEvent.Completed(finishReason = "stop"))
            else listOf(ModelEvent.TextDelta("visible result"), ModelEvent.Completed(finishReason = "stop"))
        }

        val events = engine.run(config(), "do work").toList()

        assertTrue(events.any { it is AgentEvent.TaskProgress && "可见结论" in it.label })
        assertTrue(events.any { it is AgentEvent.AssistantDelta && it.text == "visible result" })
        assertTrue(events.any { it is AgentEvent.StateChanged && it.state == AgentRunState.COMPLETED })
    }

    @Test
    fun `two empty visible responses fail instead of reporting completed`() = runBlocking {
        val engine = engine { listOf(ModelEvent.ReasoningDelta("internal"), ModelEvent.Completed(finishReason = "stop")) }

        val events = engine.run(config(), "do work").toList()

        assertTrue(events.any { it is AgentEvent.StateChanged && it.state == AgentRunState.FAILED })
        assertTrue(events.none { it is AgentEvent.StateChanged && it.state == AgentRunState.COMPLETED })
    }

    @Test
    fun `runtime voice policy is hidden from visible conversation`() = runBlocking {
        val requests = mutableListOf<ModelRequest>()
        val policy = "Voice mode policy: answer briefly and use speak when useful."
        val engine = AgentEngine(
            provider = object : ProviderAdapter {
                override val profile = ProviderPresets.all.first()
                override suspend fun probe(credential: ProviderCredential) = ProviderProbe(true, 0, "ok")
                override suspend fun listModels(credential: ProviderCredential): List<ModelInfo> = emptyList()
                override fun stream(request: ModelRequest, credential: ProviderCredential): Flow<ModelEvent> = flow {
                    requests += request
                    emit(ModelEvent.TextDelta("你好"))
                    emit(ModelEvent.Completed(finishReason = "stop"))
                }
            },
            credential = ProviderCredential("test-key"),
            tools = ToolRegistry(),
            approvalGate = ApprovalGate { ApprovalDecision.DENY },
        )

        val events = engine.run(
            config().copy(additionalSystemInstruction = policy),
            "今天天气怎么样",
        ).toList()

        assertEquals("今天天气怎么样", events.filterIsInstance<AgentEvent.UserMessage>().single().text)
        assertFalse(events.filterIsInstance<AgentEvent.UserMessage>().any { policy in it.text })
        assertTrue(requests.single().messages.any { it.role == com.phoneagent.provider.MessageRole.SYSTEM && policy in it.content })
        assertTrue(requests.single().messages.any { it.role == com.phoneagent.provider.MessageRole.USER && it.content == "今天天气怎么样" })
    }

    @Test
    fun `missing mandatory speak call is repaired with provider-enforced tool choice`() = runBlocking {
        val requests = mutableListOf<ModelRequest>()
        var call = 0
        val speak = object : Tool {
            override val definition = ToolDefinition("speak", "Broadcast concise speech", buildJsonObject { })
            override val capabilities = emptySet<ToolCapability>()
            override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = ToolResult(true, "spoken")
        }
        val engine = AgentEngine(
            provider = object : ProviderAdapter {
                override val profile = ProviderPresets.all.first { it.id == "deepseek" }
                override suspend fun probe(credential: ProviderCredential) = ProviderProbe(true, 0, "ok")
                override suspend fun listModels(credential: ProviderCredential): List<ModelInfo> = emptyList()
                override fun stream(request: ModelRequest, credential: ProviderCredential): Flow<ModelEvent> = flow {
                    requests += request
                    when (++call) {
                        1 -> emit(ModelEvent.TextDelta("任务已经完成"))
                        2 -> emit(ModelEvent.ToolCall("voice-1", "speak", "{}"))
                        else -> emit(ModelEvent.TextDelta("完成"))
                    }
                    emit(ModelEvent.Completed(finishReason = "stop"))
                }
            },
            credential = ProviderCredential("test-key"),
            tools = ToolRegistry(listOf(speak)),
            approvalGate = ApprovalGate { ApprovalDecision.DENY },
        )

        val events = engine.run(
            config().copy(model = "deepseek-v4-flash", requiredToolNameAfterVisibleResponse = "speak"),
            "完成这个语音任务",
        ).toList()

        assertEquals(null, requests.first().requiredToolName)
        assertEquals("speak", requests[1].requiredToolName)
        assertEquals(ReasoningMode.DISABLED, requests[1].reasoningSelection.mode)
        assertTrue(events.any { it is AgentEvent.ToolRequested && it.name == "speak" })
        assertTrue(events.any { it is AgentEvent.StateChanged && it.state == AgentRunState.COMPLETED })
    }

    private fun engine(events: () -> List<ModelEvent>): AgentEngine = AgentEngine(
        provider = object : ProviderAdapter {
            override val profile = ProviderPresets.all.first()
            override suspend fun probe(credential: ProviderCredential) = ProviderProbe(true, 0, "ok")
            override suspend fun listModels(credential: ProviderCredential): List<ModelInfo> = emptyList()
            override fun stream(request: ModelRequest, credential: ProviderCredential): Flow<ModelEvent> = flow {
                events().forEach { emit(it) }
            }
        },
        credential = ProviderCredential("test-key"),
        tools = ToolRegistry(),
        approvalGate = ApprovalGate { ApprovalDecision.DENY },
    )

    private fun config(): AgentRunConfig {
        val workspace = Files.createTempDirectory("phoneagent-agent-test").toFile()
        workspace.deleteOnExit()
        return AgentRunConfig(
            sessionId = "test-session",
            workspacePath = workspace.absolutePath,
            providerId = "test",
            model = "test-model",
            maxRounds = 4,
        )
    }
}
