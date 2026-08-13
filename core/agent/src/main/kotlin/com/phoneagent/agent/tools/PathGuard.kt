package com.phoneagent.agent.tools

import java.io.File

class PathGuard(private val workspace: File) {
    private val root = workspace.canonicalFile

    fun resolve(relative: String, allowMissing: Boolean = true): File {
        require(relative.isNotBlank()) { "Path cannot be blank" }
        require(!File(relative).isAbsolute) { "Absolute paths are not allowed" }
        val candidate = File(root, relative).canonicalFile
        require(candidate == root || candidate.path.startsWith(root.path + File.separator)) { "Path escapes workspace" }
        if (!allowMissing) require(candidate.exists()) { "Path does not exist: $relative" }
        return candidate
    }
}

