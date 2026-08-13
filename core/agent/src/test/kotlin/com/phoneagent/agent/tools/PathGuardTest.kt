package com.phoneagent.agent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PathGuardTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun acceptsWorkspaceRelativePath() {
        val root = temporary.newFolder("workspace")
        assertEquals(root.resolve("src/main.py").canonicalFile, PathGuard(root).resolve("src/main.py"))
    }

    @Test
    fun rejectsTraversal() {
        val root = temporary.newFolder("workspace")
        assertThrows(IllegalArgumentException::class.java) { PathGuard(root).resolve("../secret") }
    }
}

