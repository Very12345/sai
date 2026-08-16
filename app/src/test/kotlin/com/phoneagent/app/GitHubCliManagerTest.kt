package com.phoneagent.app

import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubCliManagerTest {
    @Test fun deviceFlowAnswersTheHeadlessGitCredentialPrompt() {
        val command = githubDeviceLoginCommand()
        assertTrue(command.contains("printf 'Y\\n' | gh auth login"))
        assertTrue(command.contains("--insecure-storage"))
        assertTrue(command.contains("GH_CONFIG_DIR"))
        assertTrue(command.contains("gh auth token --hostname github.com > '/run/sai-github-auth/token'"))
        assertTrue(!command.contains("SAI_GH_TOKEN="))
    }
}
