package com.phoneagent.app

import com.phoneagent.data.HarnessDefaultConfigEntity
import com.phoneagent.data.ManagerTaskLinkEntity
import com.phoneagent.data.SessionEntity
import com.phoneagent.data.WorkspaceEntity
import com.phoneagent.provider.MessageRole
import com.phoneagent.provider.ModelEvent
import com.phoneagent.provider.ModelMessage
import com.phoneagent.provider.ModelRequest
import com.phoneagent.provider.ProviderFactory
import com.phoneagent.runtime.RunRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ManagerDispatchResult(
    val managerSessionId: String,
    val targetSessionId: String,
    val workspaceId: String,
    val projectName: String,
    val harnessKind: String,
)

/**
 * Small application-orchestration harness. It deliberately has no arbitrary shell or file-write
 * tools: it creates a managed project/session and delegates real work to an installed harness.
 */
class SaiManagerHarness(private val container: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dispatch(rawRequest: String): ManagerDispatchResult = withContext(Dispatchers.IO) {
        val request = rawRequest.trim()
        require(request.isNotBlank()) { "任务不能为空" }
        val route = classify(request)
        val projectName = safeProjectName(route.projectName.ifBlank { request.take(18) })
        val dao = container.database.dao()
        val existing = dao.workspaces().firstOrNull { it.name.equals(projectName, ignoreCase = true) }
        val workspace = existing ?: createWorkspace(projectName)
        val default = dao.harnessDefault(workspace.id, route.harnessKind)
            ?: dao.harnessDefault(null, route.harnessKind)
        val profile = default?.providerId?.let(container.providerSettings::profileFor)
            ?: container.providerSettings.profile.value
        val model = default?.modelId?.takeIf(String::isNotBlank) ?: profile.defaultModel
        val reasoning = default?.reasoningConfigJson
            ?: "{\"mode\":\"AUTO\",\"effort\":null,\"budgetTokens\":null}"
        val managerSessionId = UUID.randomUUID().toString()
        val targetSessionId = UUID.randomUUID().toString()
        dao.upsertSession(SessionEntity(
            id = managerSessionId,
            workspaceId = workspace.id,
            title = "总管 · ${request.lineSequence().first().take(20)}",
            titleSource = "AUTO",
            autoTitleState = "COMPLETE",
            mode = "MANAGER",
            providerId = profile.id,
            model = model,
            reasoningConfigJson = reasoning,
            state = "COMPLETED",
            latestPreview = "已委派给 ${route.harnessKind}",
        ))
        dao.appendEvent(managerSessionId, "UserMessage", json.encodeToString(JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
            put("text", kotlinx.serialization.json.JsonPrimitive(request))
        }))
        dao.upsertSession(SessionEntity(
            id = targetSessionId,
            workspaceId = workspace.id,
            title = route.title.ifBlank { request.take(24) },
            mode = "AGENT",
            providerId = profile.id,
            model = model,
            reasoningConfigJson = reasoning,
            state = "RUNNING",
            queueState = "RUNNING",
            progressText = "由 sai 总管创建",
        ))
        dao.upsertManagerTaskLink(ManagerTaskLinkEntity(
            id = UUID.randomUUID().toString(),
            managerSessionId = managerSessionId,
            targetSessionId = targetSessionId,
            workspaceId = workspace.id,
            harnessKind = route.harnessKind,
            state = "RUNNING",
            summary = route.task,
        ))

        // DSH is the bundled execution runtime. Optional Codex/Claude adapters are selected once
        // their runtime packages are installed; until then the manager makes the fallback visible.
        val effectiveHarness = if (route.harnessKind == "DSH") "DSH" else "DSH"
        container.dshRuntime.ensureStarted()
        container.dshRuntime.awaitReady()
        val external = container.dshApi.ensureSession(targetSessionId, workspace.localPath)
        container.database.dao().upsertHarnessBinding(com.phoneagent.data.HarnessSessionBindingEntity(
            id = "DSH:$targetSessionId",
            sessionId = targetSessionId,
            workspaceId = workspace.id,
            harnessKind = effectiveHarness,
            externalSessionId = external,
            runtimeVersion = container.dshRuntime.state.value.runtimeVersion.orEmpty(),
        ))
        container.dshApi.prompt(external, route.task, steer = false)
        ManagerDispatchResult(managerSessionId, targetSessionId, workspace.id, projectName, effectiveHarness)
    }

    private suspend fun createWorkspace(name: String): WorkspaceEntity {
        val id = UUID.randomUUID().toString()
        val directory = File(container.projectsRoot, name).apply { mkdirs() }
        runCatching {
            container.runtime.run(RunRequest(
                command = "git init -b main && git config user.name sai && git config user.email sai@localhost && git add -A && git commit --allow-empty -m 'sai initial checkpoint'",
                workingDirectory = "/home/phoneagent",
                workspaceHostPath = directory.absolutePath,
                timeoutMillis = 60_000,
            ))
        }
        return WorkspaceEntity(id = id, name = name, localPath = directory.absolutePath).also {
            container.database.dao().upsertWorkspace(it)
        }
    }

    private suspend fun classify(request: String): ManagerRoute {
        val profile = container.providerSettings.profile.value
        val credential = container.providerSettings.credentialFor(profile.id) ?: return fallbackRoute(request)
        val prompt = """
            You route requests for a mobile coding workspace. Return JSON only:
            {"projectName":"short safe folder name","title":"short session title","harness":"DSH|CODEX|CLAUDE_CODE","task":"complete task for the selected harness"}
            Prefer DSH for general coding and local tools, CODEX for repository engineering, CLAUDE_CODE for long document/code analysis.
            Do not answer the task and do not include Markdown.
        """.trimIndent()
        val text = buildString {
            ProviderFactory.create(profile).stream(ModelRequest(
                model = profile.defaultModel,
                messages = listOf(ModelMessage(MessageRole.SYSTEM, prompt), ModelMessage(MessageRole.USER, request)),
                maxOutputTokens = 180,
            ), credential).collect { event -> if (event is ModelEvent.TextDelta) append(event.text) }
        }
        return runCatching {
            val root = json.parseToJsonElement(text.substringAfter('{').substringBeforeLast('}').let { "{$it}" }).jsonObject
            ManagerRoute(
                projectName = root["projectName"]?.jsonPrimitive?.content.orEmpty(),
                title = root["title"]?.jsonPrimitive?.content.orEmpty(),
                harnessKind = root["harness"]?.jsonPrimitive?.content.orEmpty().takeIf { it in setOf("DSH", "CODEX", "CLAUDE_CODE") } ?: "DSH",
                task = root["task"]?.jsonPrimitive?.content.orEmpty().ifBlank { request },
            )
        }.getOrElse { fallbackRoute(request) }
    }

    private fun fallbackRoute(request: String) = ManagerRoute(
        projectName = request.take(18),
        title = request.take(24),
        harnessKind = "DSH",
        task = request,
    )

    private fun safeProjectName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
        .replace(Regex("\\s+"), "-")
        .trim('.', '-', ' ')
        .take(40)
        .ifBlank { "sai-task-${System.currentTimeMillis()}" }

    private data class ManagerRoute(
        val projectName: String,
        val title: String,
        val harnessKind: String,
        val task: String,
    )
}
