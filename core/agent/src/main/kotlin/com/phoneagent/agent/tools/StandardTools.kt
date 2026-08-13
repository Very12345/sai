package com.phoneagent.agent.tools

import com.phoneagent.agent.ToolRegistry
import com.phoneagent.runtime.LinuxRuntime

object StandardTools {
    fun create(runtime: LinuxRuntime): ToolRegistry = ToolRegistry(listOf(
        ListFilesTool(),
        ReadFileTool(),
        SearchFilesTool(),
        WriteFileTool(),
        ReplaceTextTool(),
        MovePathTool(),
        DeletePathTool(),
        ApplyPatchTool(runtime),
        ShellTool(runtime),
        PythonTool(runtime),
        StartJobTool(runtime),
        ListJobsTool(runtime),
        StopJobTool(runtime),
        HttpFetchTool(),
        GitStatusTool(runtime),
        GitCommitTool(runtime),
        GitInspectTool(runtime),
        CodeAnalysisTool(runtime),
    ))
}
