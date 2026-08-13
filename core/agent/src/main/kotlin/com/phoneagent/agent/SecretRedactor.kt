package com.phoneagent.agent

class SecretRedactor(secrets: Collection<String>) {
    private val values = secrets.filter { it.length >= 6 }.distinct().sortedByDescending(String::length)

    fun redact(value: String): String = values.fold(value) { text, secret -> text.replace(secret, REDACTED) }

    fun redact(result: ToolResult): ToolResult = result.copy(
        output = redact(result.output),
        metadata = result.metadata.mapValues { redact(it.value) },
    )

    companion object { const val REDACTED = "[REDACTED]" }
}
