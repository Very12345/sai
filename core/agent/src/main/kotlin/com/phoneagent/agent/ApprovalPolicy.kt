package com.phoneagent.agent

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Authorization(
    val allowed: Boolean,
    val confirmationRequired: Boolean,
    val explanation: String,
)

class ApprovalPolicy {
    fun authorize(
        mode: AgentMode,
        capabilities: Set<ToolCapability>,
        argumentsJson: String,
        sessionWorkspaceWriteAllowed: Boolean,
        sessionNormalShellAllowed: Boolean = false,
    ): Authorization {
        if (mode == AgentMode.PLAN && capabilities.any { it != ToolCapability.WORKSPACE_READ && it != ToolCapability.NETWORK }) {
            return Authorization(false, false, "Plan 模式禁止写文件和执行命令")
        }
        val highRisk = capabilities.intersect(HIGH_RISK)
        if (highRisk.isNotEmpty()) return Authorization(true, true, "高风险能力：${highRisk.joinToString()}")
        if (ToolCapability.SHELL in capabilities) {
            val commandRisk = classifyCommand(argumentsJson)
            if (commandRisk != null && !(sessionNormalShellAllowed && commandRisk == NORMAL_SHELL_REASON)) {
                return Authorization(true, true, commandRisk)
            }
        }
        if (ToolCapability.WORKSPACE_WRITE in capabilities && !sessionWorkspaceWriteAllowed) {
            return Authorization(true, true, "本会话尚未授权工作区写入")
        }
        return Authorization(true, false, "符合当前权限策略")
    }

    fun classifyCommand(command: String): String? {
        val decoded = runCatching {
            Json.parseToJsonElement(command).jsonObject["command"]?.jsonPrimitive?.content
        }.getOrNull() ?: command
        val lower = decoded.lowercase(Locale.ROOT).trim()
        return when {
            DANGEROUS.any { it.containsMatchIn(lower) } -> "命令可能删除数据、改写 Git 历史或修改系统/软件包"
            lower.length > 4_000 -> "命令过长，需要人工检查"
            READ_ONLY.any { it.matches(lower) } -> null
            else -> NORMAL_SHELL_REASON
        }
    }

    companion object {
        const val NORMAL_SHELL_REASON = "Shell 命令不在只读检查白名单中"
        private val HIGH_RISK = setOf(
            ToolCapability.DELETE,
            ToolCapability.PACKAGE_INSTALL,
            ToolCapability.EXTERNAL_STORAGE,
            ToolCapability.GIT_HISTORY,
            ToolCapability.DEVICE_CONTROL,
            ToolCapability.BROWSER_CONTROL,
        )
        private val DANGEROUS = listOf(
            Regex("\\brm\\s+-(?:[a-z]*r[a-z]*f|[a-z]*f[a-z]*r)\\b"),
            Regex("\\bgit\\s+(?:reset\\s+--hard|clean\\s+-|push\\b.*--force)"),
            Regex("\\b(?:apt|apt-get)\\s+(?:install|remove|purge|upgrade|dist-upgrade)\\b"),
            Regex("\\bdpkg\\s+-i\\b"), Regex("\\b(?:chmod|chown)\\s+-[a-z]*r\\b"),
            Regex("\\b(?:mkfs|mount|umount)\\b"), Regex("\\bdd\\s+.*\\b(?:if|of)="),
            Regex("(?:curl|wget)[^|]*\\|\\s*(?:ba)?sh\\b"), Regex("(?:^|[;&|])\\s*>?\\s*/dev/"),
        )
        private val READ_ONLY = listOf(
            Regex("^(?:pwd|ls(?:\\s.*)?|find(?:\\s.*)?|rg(?:\\s.*)?|grep(?:\\s.*)?|head(?:\\s.*)?|tail(?:\\s.*)?|cat(?:\\s.*)?|sed\\s+-n(?:\\s.*)?)$"),
            Regex("^git\\s+(?:status|diff|log|show|branch)(?:\\s.*)?$"),
            Regex("^(?:pytest|python3?\\s+-m\\s+pytest)(?:\\s.*)?$"),
        )
    }
}
