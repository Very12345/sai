package com.phoneagent.agent

import com.phoneagent.agent.tools.ListFilesTool
import com.phoneagent.agent.tools.ReadFileTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolContractTest {
    @Test fun contractIsIndependentOfRegistrationOrder() {
        val first = ToolRegistry(listOf(ReadFileTool(), ListFilesTool()))
        val second = ToolRegistry(listOf(ListFilesTool(), ReadFileTool()))
        assertEquals(first.contractSnapshot(), second.contractSnapshot())
        assertEquals(first.contractHash(), second.contractHash())
        assertTrue(first.contractHash().matches(Regex("[0-9a-f]{64}")))
    }
}
