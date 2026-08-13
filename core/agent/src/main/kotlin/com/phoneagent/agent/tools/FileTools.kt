package com.phoneagent.agent.tools

import com.phoneagent.agent.AgentMode
import com.phoneagent.agent.Tool
import com.phoneagent.agent.ToolCapability
import com.phoneagent.agent.ToolExecutionContext
import com.phoneagent.agent.ToolResult
import com.phoneagent.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private fun objectSchema(vararg properties: Pair<String, JsonObject>, required: List<String> = properties.map { it.first }) =
    buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { properties.forEach { (name, schema) -> put(name, schema) } })
        put("required", kotlinx.serialization.json.buildJsonArray { required.forEach(::add) })
        put("additionalProperties", false)
    }

private fun stringSchema(description: String) = buildJsonObject { put("type", "string"); put("description", description) }
private fun integerSchema(description: String) = buildJsonObject { put("type", "integer"); put("description", description) }

class ListFilesTool : Tool {
    override val definition = ToolDefinition(
        "list_files", "List files below a workspace-relative directory.",
        objectSchema(
            "path" to stringSchema("Relative directory, use . for workspace root"),
            "depth" to integerSchema("Maximum recursion depth, 1-8"),
            required = listOf("path"),
        ),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val root = PathGuard(context.workspace).resolve(arguments.string("path"), allowMissing = false)
        require(root.isDirectory) { "Not a directory" }
        val maxDepth = arguments.int("depth", 2).coerceIn(1, 8)
        val lines = root.walkTopDown().maxDepth(maxDepth).take(1_000).map { file ->
            val relative = file.relativeTo(context.workspace).invariantSeparatorsPath.ifBlank { "." }
            if (file.isDirectory) "$relative/" else "$relative (${file.length()} bytes)"
        }.toList()
        ToolResult(true, lines.joinToString("\n"), truncated = lines.size >= 1_000)
    }
}

class ReadFileTool : Tool {
    override val definition = ToolDefinition(
        "read_file", "Read a UTF-8 text file with line numbers.",
        objectSchema(
            "path" to stringSchema("Workspace-relative file"),
            "start_line" to integerSchema("First 1-based line"),
            "end_line" to integerSchema("Last inclusive line"),
            required = listOf("path"),
        ),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val file = PathGuard(context.workspace).resolve(arguments.string("path"), allowMissing = false)
        require(file.isFile) { "Not a file" }
        require(file.length() <= 5_000_000) { "File is too large" }
        val lines = file.readLines()
        val start = arguments.int("start_line", 1).coerceAtLeast(1)
        if (lines.isNotEmpty()) require(start <= lines.size) { "start_line exceeds file length (${lines.size})" }
        val end = arguments.int("end_line", minOf(lines.size, start + 399)).coerceIn(start, lines.size.coerceAtLeast(start))
        val output = if (lines.isEmpty()) "" else lines.subList(start - 1, end.coerceAtMost(lines.size))
            .mapIndexed { index, text -> "${start + index}: $text" }.joinToString("\n")
        ToolResult(true, output, metadata = mapOf("totalLines" to lines.size.toString()), truncated = end < lines.size)
    }
}

class SearchFilesTool : Tool {
    override val definition = ToolDefinition(
        "search_files", "Search text files by regular expression.",
        objectSchema(
            "query" to stringSchema("Regular expression"),
            "path" to stringSchema("Relative directory"),
            required = listOf("query"),
        ),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_READ)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val regex = Regex(arguments.string("query"))
        val root = PathGuard(context.workspace).resolve(arguments.string("path", "."), allowMissing = false)
        val matches = mutableListOf<String>()
        root.walkTopDown().filter(File::isFile).filter { it.length() <= 2_000_000 }.forEach { file ->
            if (matches.size >= 500) return@forEach
            runCatching { file.useLines { lines -> lines.forEachIndexed { index, line ->
                if (regex.containsMatchIn(line) && matches.size < 500) {
                    matches += "${file.relativeTo(context.workspace).invariantSeparatorsPath}:${index + 1}:$line"
                }
            } } }
        }
        ToolResult(true, matches.joinToString("\n"), truncated = matches.size >= 500)
    }
}

class WriteFileTool : Tool {
    override val definition = ToolDefinition(
        "write_file", "Create or replace a UTF-8 workspace file. Always returns a change preview.",
        objectSchema("path" to stringSchema("Workspace-relative file"), "content" to stringSchema("Complete new content")),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_WRITE)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String {
        val file = PathGuard(context.workspace).resolve(arguments.string("path"))
        val old = if (file.isFile) file.readText().take(8_000) else "<new file>"
        return simpleDiff(old, arguments.string("content").take(8_000))
    }

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val file = PathGuard(context.workspace).resolve(arguments.string("path"))
        file.parentFile?.mkdirs()
        val content = arguments.string("content")
        val temporary = File(file.parentFile, ".${file.name}.phoneagent.tmp")
        temporary.writeText(content)
        try {
            Files.move(
                temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        ToolResult(true, "Wrote ${file.relativeTo(context.workspace).invariantSeparatorsPath} (${content.length} chars)")
    }
}

class ReplaceTextTool : Tool {
    override val definition = ToolDefinition(
        "replace_text", "Replace one exact text block in a workspace file.",
        objectSchema(
            "path" to stringSchema("Workspace-relative file"),
            "old_text" to stringSchema("Exact existing text, must occur once"),
            "new_text" to stringSchema("Replacement text"),
        ),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_WRITE)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String =
        simpleDiff(arguments.string("old_text"), arguments.string("new_text"))

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val file = PathGuard(context.workspace).resolve(arguments.string("path"), allowMissing = false)
        val old = arguments.string("old_text")
        require(old.isNotEmpty()) { "old_text cannot be empty" }
        val current = file.readText()
        val occurrences = current.windowed(old.length, 1, partialWindows = false).count { it == old }
        require(occurrences == 1) { "Expected old_text exactly once, found $occurrences" }
        file.writeText(current.replaceFirst(old, arguments.string("new_text")))
        ToolResult(true, "Updated ${file.relativeTo(context.workspace).invariantSeparatorsPath}")
    }
}

class MovePathTool : Tool {
    override val definition = ToolDefinition(
        "move_path", "Move or rename one workspace path without overwriting.",
        objectSchema("source" to stringSchema("Existing path"), "destination" to stringSchema("New path")),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_WRITE)

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val guard = PathGuard(context.workspace)
        val source = guard.resolve(arguments.string("source"), allowMissing = false)
        val destination = guard.resolve(arguments.string("destination"))
        require(!destination.exists()) { "Destination already exists" }
        destination.parentFile?.mkdirs()
        check(source.renameTo(destination)) { "Move failed" }
        ToolResult(true, "Moved ${arguments.string("source")} to ${arguments.string("destination")}")
    }
}

class DeletePathTool : Tool {
    override val definition = ToolDefinition(
        "delete_path", "Delete one workspace file or an explicitly confirmed directory tree.",
        objectSchema("path" to stringSchema("Workspace-relative path"), "recursive" to buildJsonObject { put("type", "boolean") }, required = listOf("path")),
    )
    override val capabilities = setOf(ToolCapability.WORKSPACE_WRITE, ToolCapability.DELETE)

    override suspend fun preview(arguments: JsonObject, context: ToolExecutionContext): String {
        val file = PathGuard(context.workspace).resolve(arguments.string("path"), allowMissing = false)
        return if (file.isDirectory) "Delete directory containing ${file.walkTopDown().count() - 1} entries" else "Delete file (${file.length()} bytes)"
    }

    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext) = withContext(Dispatchers.IO) {
        val file = PathGuard(context.workspace).resolve(arguments.string("path"), allowMissing = false)
        require(file != context.workspace.canonicalFile) { "Cannot delete workspace root" }
        if (file.isDirectory) require(arguments.boolean("recursive", false)) { "recursive=true is required for directories" }
        check(if (file.isDirectory) file.deleteRecursively() else file.delete()) { "Delete failed" }
        ToolResult(true, "Deleted ${arguments.string("path")}")
    }
}

private fun simpleDiff(old: String, new: String): String {
    if (old == new) return "No change"
    val oldLines = old.lines()
    val newLines = new.lines()
    val prefix = oldLines.zip(newLines).takeWhile { it.first == it.second }.size
    return buildString {
        appendLine("--- before")
        appendLine("+++ after")
        oldLines.drop(prefix).take(40).forEach { appendLine("-$it") }
        newLines.drop(prefix).take(40).forEach { appendLine("+$it") }
        if (oldLines.size + newLines.size - prefix * 2 > 80) append("... diff truncated")
    }
}
