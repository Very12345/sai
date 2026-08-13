package com.phoneagent.app

import com.phoneagent.data.PhoneAgentDatabase
import com.phoneagent.data.ProviderModelEntity
import com.phoneagent.dsh.DshRuntimeProvisioner
import com.phoneagent.provider.ProviderProfile
import com.phoneagent.provider.ProviderProtocol
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Mirrors non-secret provider metadata into DSH. API keys remain credential references. */
class DshSettingsSynchronizer(
    private val providers: ProviderSettingsRepository,
    private val database: PhoneAgentDatabase,
    provisioner: DshRuntimeProvisioner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val json = Json { prettyPrint = true }
    private val settingsFile = File(provisioner.home, "settings.yaml")
    private val allModels = database.dao().observeAllProviderModels()

    init {
        scope.launch {
            combine(providers.profiles, providers.profile, allModels) { profiles, active, models ->
                Snapshot(profiles, active.id, models)
            }.debounce(250).collect { write(it) }
        }
    }

    suspend fun syncNow() = write(Snapshot(
        providers.profiles.value,
        providers.profile.value.id,
        database.dao().allProviderModels(),
    ))

    private suspend fun write(snapshot: Snapshot) = writeMutex.withLock {
        settingsFile.parentFile?.mkdirs()
        val modelByProvider = snapshot.models.groupBy(ProviderModelEntity::providerId)
        val routeByProvider = snapshot.profiles.associate { it.id to routeFor(it) }
        val sections = buildJsonObject {
            put("llm-pi-ai", buildJsonObject {
                put("providers", buildJsonObject {
                    snapshot.profiles.forEach { profile ->
                        put(routeByProvider.getValue(profile.id), providerJson(profile, modelByProvider[profile.id].orEmpty()))
                    }
                })
            })
            snapshot.profiles.firstOrNull { it.id == snapshot.activeProviderId }?.let { active ->
                put("agent-default-model", buildJsonObject {
                    put("provider", routeByProvider.getValue(active.id))
                    put("model", active.defaultModel)
                    active.reasoningSelection.effort?.let { effort ->
                        when (effort.name) {
                            "AUTO" -> null
                            "NONE" -> "off"
                            else -> effort.name.lowercase()
                        }?.let { put("reasoningEffort", it) }
                    }
                })
            }
        }
        val temporary = File(settingsFile.parentFile, "${settingsFile.name}.sai.tmp")
        temporary.writeText(json.encodeToString(JsonObject.serializer(), sections), Charsets.UTF_8)
        check(temporary.renameTo(settingsFile) || run {
            temporary.copyTo(settingsFile, overwrite = true)
            temporary.delete()
        }) { "Unable to update DSH settings" }
    }

    private fun providerJson(profile: ProviderProfile, discovered: List<ProviderModelEntity>) = buildJsonObject {
        put("displayName", profile.displayName)
        put("apiKeyEnv", providers.credentialRefForProvider(profile.id))
        if (profile.protocol != ProviderProtocol.GEMINI_NATIVE) {
            put("api", when (profile.protocol) {
                ProviderProtocol.OPENAI_RESPONSES -> "openai-responses"
                ProviderProtocol.OPENAI_CHAT -> "openai-completions"
                ProviderProtocol.ANTHROPIC_MESSAGES -> "anthropic-messages"
                ProviderProtocol.GEMINI_NATIVE -> error("unreachable")
            })
            put("baseURL", apiRoot(profile))
        }
        val safeHeaders = profile.customHeaders.filterKeys { key ->
            !Regex("(?i)^(authorization|x-api-key|api-key|proxy-authorization)$").matches(key.trim())
        }
        if (safeHeaders.isNotEmpty()) put("headers", JsonObject(safeHeaders.mapValues { JsonPrimitive(it.value) }))
        put("defaultContextWindow", profile.contextWindow)
        put("defaultMaxTokens", profile.maxOutputTokens)
        val models = discovered.ifEmpty {
            listOf(ProviderModelEntity(
                id = "${profile.id}:${profile.defaultModel}",
                providerId = profile.id,
                modelId = profile.defaultModel,
                displayName = profile.defaultModel,
                contextWindow = profile.contextWindow,
            ))
        }
        put("models", buildJsonArray {
            models.distinctBy(ProviderModelEntity::modelId).forEach { model -> add(modelJson(model, profile)) }
        })
    }

    private fun modelJson(model: ProviderModelEntity, profile: ProviderProfile) = buildJsonObject {
        put("id", model.modelId)
        put("name", model.displayName.ifBlank { model.modelId })
        put("contextWindow", model.contextWindow.coerceAtLeast(1))
        put("maxTokens", profile.maxOutputTokens.coerceAtLeast(1))
        val efforts = runCatching { json.parseToJsonElement(model.reasoningEffortsJson).jsonArray }
            .getOrDefault(JsonArray(emptyList())).mapNotNull { it.jsonPrimitive.contentOrNull }
        val supportedEfforts = efforts.mapNotNull { effort ->
            when (val normalized = effort.lowercase()) {
                "off", "none", "disabled" -> "off" to null
                "minimal", "low", "medium", "high", "xhigh", "max" -> normalized to normalized
                // AUTO/ENABLED/ADAPTIVE mean that no explicit wire effort is selected.
                // They are modes in sai, not valid DSH/pi-ai reasoning level ids.
                else -> null
            }
        }.distinctBy { it.first }
        if (supportedEfforts.isNotEmpty()) put("reasoningEfforts", buildJsonObject {
            supportedEfforts.forEach { (name, wire) ->
                if (wire == null) put(name, JsonNull) else put(name, wire)
            }
        })
    }

    private fun routeFor(profile: ProviderProfile): String {
        if (profile.protocol == ProviderProtocol.GEMINI_NATIVE && profile.id == "gemini") return "google"
        val base = profile.id.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').ifBlank { "provider" }
        val suffix = MessageDigest.getInstance("SHA-256").digest(profile.id.toByteArray())
            .take(3).joinToString("") { "%02x".format(it) }
        return "sai-$base-$suffix"
    }

    private fun apiRoot(profile: ProviderProfile): String {
        val path = profile.requestPath
            .removeSuffix("/chat/completions")
            .removeSuffix("/responses")
            .removeSuffix("/messages")
            .trimEnd('/')
        return profile.baseUrl.trimEnd('/') + path
    }

    private data class Snapshot(
        val profiles: List<ProviderProfile>,
        val activeProviderId: String,
        val models: List<ProviderModelEntity>,
    )
}
