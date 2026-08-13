package com.phoneagent.extensions

import java.io.File
import java.security.MessageDigest

data class SkillDefinition(
    val name: String,
    val description: String,
    val instructions: String,
    val directory: File,
    val digest: String,
)

class SkillLoader {
    fun discover(roots: List<File>): List<SkillDefinition> = roots
        .asSequence()
        .filter(File::isDirectory)
        .flatMap { root -> root.walkTopDown().maxDepth(3).filter { it.name == "SKILL.md" } }
        .mapNotNull(::load)
        .distinctBy { it.name }
        .sortedBy { it.name }
        .toList()

    fun load(file: File): SkillDefinition? {
        if (!file.isFile) return null
        val text = file.readText()
        val (metadata, body) = parseFrontMatter(text)
        val name = metadata["name"]?.takeIf(String::isNotBlank) ?: file.parentFile?.name ?: return null
        return SkillDefinition(
            name = name,
            description = metadata["description"].orEmpty(),
            instructions = body.trim(),
            directory = requireNotNull(file.parentFile),
            digest = sha256(text),
        )
    }

    private fun parseFrontMatter(text: String): Pair<Map<String, String>, String> {
        if (!text.startsWith("---\n") && !text.startsWith("---\r\n")) return emptyMap<String, String>() to text
        val normalized = text.replace("\r\n", "\n")
        val end = normalized.indexOf("\n---\n", 4)
        if (end < 0) return emptyMap<String, String>() to text
        val metadata = normalized.substring(4, end).lineSequence().mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim() to
                line.substring(separator + 1).trim().trim('"', '\'')
        }.toMap()
        return metadata to normalized.substring(end + 5)
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
