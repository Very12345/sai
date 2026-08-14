package com.phoneagent.app

import com.phoneagent.agent.AgentRunState
import com.phoneagent.agent.TaskQueueState

/** UI-only shape retained while old Room sessions are offered for DSH import. */
data class TaskHandle(
    val sessionId: String,
    val workspaceId: String,
    val title: String,
    val runState: AgentRunState,
    val queueState: TaskQueueState,
    val progressText: String = "",
    val worktreePath: String? = null,
    val startedAt: Long? = null,
)

const val DEFAULT_WORKSPACE_ID = "default"
