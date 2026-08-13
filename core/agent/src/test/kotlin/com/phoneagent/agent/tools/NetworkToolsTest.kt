package com.phoneagent.agent.tools

import com.phoneagent.agent.AgentMode
import com.phoneagent.agent.ToolExecutionContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class NetworkToolsTest {
    @Test
    fun blocksLoopbackBeforeSendingRequest() = runBlocking {
        val result = runCatching {
            HttpFetchTool().execute(
                buildJsonObject { put("url", "http://127.0.0.1:8080/private") },
                ToolExecutionContext(createTempDirectory("phoneagent-network-").toFile(), AgentMode.AGENT, "test"),
            )
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("blocked", ignoreCase = true))
    }
}
