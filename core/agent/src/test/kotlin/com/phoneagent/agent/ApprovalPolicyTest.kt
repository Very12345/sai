package com.phoneagent.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalPolicyTest {
    private val policy = ApprovalPolicy()

    @Test
    fun planModeRejectsWrites() {
        val result = policy.authorize(AgentMode.PLAN, setOf(ToolCapability.WORKSPACE_WRITE), "{}", false)
        assertFalse(result.allowed)
    }

    @Test
    fun workspaceReadsAreAutomatic() {
        val result = policy.authorize(AgentMode.AGENT, setOf(ToolCapability.WORKSPACE_READ), "{}", false)
        assertTrue(result.allowed)
        assertFalse(result.confirmationRequired)
    }

    @Test
    fun destructiveShellRequiresConfirmation() {
        val result = policy.authorize(
            AgentMode.AGENT,
            setOf(ToolCapability.SHELL),
            "{\"command\":\"git reset --hard HEAD~1\"}",
            true,
        )
        assertTrue(result.allowed)
        assertTrue(result.confirmationRequired)
    }

    @Test
    fun whitespaceCannotHideRecursiveDelete() {
        assertTrue(policy.classifyCommand("{\"command\":\"rm    -fr ./build\"}") != null)
    }

    @Test
    fun readOnlyGitStatusIsAutomatic() {
        assertTrue(policy.classifyCommand("{\"command\":\"git status --short\"}") == null)
    }

    @Test
    fun yoloAllowsNormalShellButNeverDangerousShell() {
        val normal = policy.authorize(
            AgentMode.AGENT,
            setOf(ToolCapability.SHELL),
            "{\"command\":\"npm test\"}",
            sessionWorkspaceWriteAllowed = true,
            sessionNormalShellAllowed = true,
        )
        assertFalse(normal.confirmationRequired)
        val destructive = policy.authorize(
            AgentMode.AGENT,
            setOf(ToolCapability.SHELL),
            "{\"command\":\"rm -rf build\"}",
            sessionWorkspaceWriteAllowed = true,
            sessionNormalShellAllowed = true,
        )
        assertTrue(destructive.confirmationRequired)
    }
}
