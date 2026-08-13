package com.phoneagent.app

import com.phoneagent.data.AgentEventEntity
import com.phoneagent.data.PhoneAgentDatabase
import com.phoneagent.data.SessionEntity
import com.phoneagent.data.WorkspaceEntity
import com.phoneagent.dsh.DshRuntimeProvisioner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Writes a one-way, non-secret handoff consumed by @sai/dsh-legacy-import.
 * Room remains untouched until DSH has durably imported the sessions.
 */
class LegacyDshMigrationWriter(
    private val database: PhoneAgentDatabase,
    provisioner: DshRuntimeProvisioner,
    private val workspaceRoot: File,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val migrationRoot = File(provisioner.home, "migrations")

    suspend fun writePendingMigration() = withContext(Dispatchers.IO) {
        val dao = database.dao()
        val workspaces = dao.workspaces().associateBy(WorkspaceEntity::id)
        val sessions = dao.sessions()
        migrationRoot.mkdirs()
        val attachmentRoot = File(migrationRoot, "legacy-attachments").apply { mkdirs() }
        val encodedSessions = buildJsonArray {
            sessions.forEach { session ->
                val events = dao.events(session.id)
                if (events.isEmpty()) return@forEach
                val converted = convert(session, workspaces[session.workspaceId], events)
                if (converted.legacyEvents.isNotEmpty()) {
                    atomicWrite(
                        File(attachmentRoot, safeFileName(session.id) + ".json"),
                        json.encodeToString(JsonArray.serializer(), JsonArray(converted.legacyEvents)),
                    )
                }
                add(converted.session)
            }
        }
        val document = buildJsonObject {
            put("schemaVersion", 1)
            put("generatedAt", System.currentTimeMillis())
            put("sessions", encodedSessions)
        }
        atomicWrite(
            File(migrationRoot, "legacy-sessions.json"),
            json.encodeToString(JsonObject.serializer(), document),
        )
    }

    private fun convert(
        session: SessionEntity,
        workspace: WorkspaceEntity?,
        events: List<AgentEventEntity>,
    ): Converted {
        val turns = mutableListOf<Turn>()
        val legacy = mutableListOf<JsonObject>()
        var current: Turn? = null
        events.forEach { event ->
            val payload = runCatching { json.parseToJsonElement(event.payloadJson).jsonObject }.getOrNull()
            when (event.type) {
                "UserMessage" -> payload?.string("text")?.takeIf(String::isNotBlank)?.let { text ->
                    current?.takeIf { it.user.isNotBlank() }?.let(turns::add)
                    current = Turn(user = text)
                }
                "AssistantDelta" -> payload?.string("text")?.let { delta ->
                    current = (current ?: Turn(user = "[旧会话缺失用户消息]")).also { it.assistant.append(delta) }
                }
                "SteerApplied" -> payload?.string("text")?.takeIf(String::isNotBlank)?.let { steer ->
                    current = (current ?: Turn(user = steer)).also {
                        if (it.user != steer) it.user += "\n\n[改变方向] $steer"
                    }
                }
                "Usage", "AssistantMessageStarted", "AssistantMessageCompleted", "RunStarted",
                "StateChanged", "ReasoningStarted", "ReasoningDelta", "ReasoningCompleted" -> Unit
                else -> legacy += buildJsonObject {
                    put("sequence", event.sequence)
                    put("type", event.type)
                    put("createdAt", event.createdAt)
                    put("payload", payload ?: JsonPrimitive(event.payloadJson))
                }
            }
        }
        current?.takeIf { it.user.isNotBlank() }?.let(turns::add)
        val cwd = workspace?.localPath?.let(::linuxPathFor) ?: "/home/phoneagent"
        return Converted(
            session = buildJsonObject {
                put("id", session.id)
                put("title", session.title)
                put("cwd", cwd)
                put("createdAt", session.createdAt)
                put("provider", session.providerId.ifBlank { "legacy" })
                put("model", session.model.ifBlank { "legacy" })
                put("legacyAttachment", if (legacy.isEmpty()) "" else "legacy-attachments/${safeFileName(session.id)}.json")
                put("turns", buildJsonArray {
                    turns.forEach { turn ->
                        add(buildJsonObject {
                            put("user", turn.user)
                            put("assistant", turn.assistant.toString())
                        })
                    }
                })
            },
            legacyEvents = legacy,
        )
    }

    private fun linuxPathFor(hostPath: String): String {
        val root = runCatching { workspaceRoot.canonicalFile }.getOrDefault(workspaceRoot.absoluteFile)
        val candidate = runCatching { File(hostPath).canonicalFile }.getOrDefault(File(hostPath).absoluteFile)
        return if (candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)) {
            val relative = candidate.relativeTo(root).invariantSeparatorsPath
            if (relative.isBlank()) "/home/phoneagent" else "/home/phoneagent/$relative"
        } else "/home/phoneagent"
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun atomicWrite(target: File, content: String) {
        val temporary = File(target.parentFile, target.name + ".sai.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        check(temporary.renameTo(target) || run {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }) { "Unable to write DSH migration handoff" }
    }

    private fun safeFileName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private data class Turn(var user: String, val assistant: StringBuilder = StringBuilder())
    private data class Converted(val session: JsonObject, val legacyEvents: List<JsonObject>)
}
