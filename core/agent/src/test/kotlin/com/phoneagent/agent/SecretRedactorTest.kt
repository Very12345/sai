package com.phoneagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactorTest {
    @Test
    fun removesKnownCredentialFromTextAndMetadata() {
        val secret = "sk-phoneagent-secret-value"
        val result = SecretRedactor(listOf(secret)).redact(
            ToolResult(true, "token=$secret", metadata = mapOf("debug" to "Bearer $secret")),
        )
        assertFalse(result.output.contains(secret))
        assertFalse(result.metadata.values.single().contains(secret))
        assertEquals("token=[REDACTED]", result.output)
    }
}
