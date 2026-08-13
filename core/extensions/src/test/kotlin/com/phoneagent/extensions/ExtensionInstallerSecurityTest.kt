package com.phoneagent.extensions

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionInstallerSecurityTest {
    @Test
    fun installsReviewedFilesInsideVersionDirectoryAndDefaultsAreExternal() {
        val root = Files.createTempDirectory("phoneagent-extension-test").toFile()
        val plan = ExtensionInstallPlan(
            id = "owner/repo/skill",
            name = "Safe skill",
            version = "abc123",
            source = "https://example.invalid/repo",
            kind = ExtensionKind.SKILL,
            sourceDigest = "deadbeef",
            files = listOf(StagedExtensionFile("SKILL.md", "---\nname: safe\n---\nReview files.", "digest")),
            permissions = setOf(ExtensionPermission.WORKSPACE_READ),
            warnings = emptyList(),
            safeToStage = true,
        )
        val installed = ExtensionInstaller(root).install(plan)
        assertTrue(installed.resolve("SKILL.md").isFile)
        assertTrue(installed.canonicalPath.startsWith(root.canonicalPath))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blocksPathTraversalEvenForPrebuiltPlan() {
        val root = Files.createTempDirectory("phoneagent-extension-test").toFile()
        val plan = ExtensionInstallPlan(
            id = "unsafe",
            name = "Unsafe",
            version = "1",
            source = "test",
            kind = ExtensionKind.SKILL,
            sourceDigest = "bad",
            files = listOf(StagedExtensionFile("../../escaped", "bad", "bad")),
            permissions = emptySet(),
            warnings = emptyList(),
            safeToStage = true,
        )
        ExtensionInstaller(root).install(plan)
        assertFalse(root.parentFile.resolve("escaped").exists())
    }
}
