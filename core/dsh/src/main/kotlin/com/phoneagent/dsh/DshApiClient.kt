package com.phoneagent.dsh

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Small native client for lifecycle-owned actions such as voice and notifications. */
class DshApiClient(
    private val state: () -> DshRuntimeState,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureSession(sessionId: String?, cwd: String, agentPreset: String? = null): String {
        if (!sessionId.isNullOrBlank()) {
            val rows = call("session.list", JsonObject(emptyMap()), allowFailure = true)
                ?.get("items") as? JsonArray
            val existing = rows?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                ?.firstOrNull { it["sessionId"]?.jsonPrimitive?.contentOrNull == sessionId }
            if (existing != null && (agentPreset == null ||
                    existing["agentPreset"]?.jsonPrimitive?.contentOrNull == agentPreset)) return sessionId
        }
        val desired = (if (agentPreset == null) sessionId?.takeIf(String::isNotBlank) else null)
            ?: UUID.randomUUID().toString()
        val value = call("session.create", buildJsonObject {
            put("cwd", cwd)
            put("sessionId", desired)
            agentPreset?.let { put("agentPreset", it) }
        }) ?: error("DSH did not create a session")
        return value["sessionId"]?.jsonPrimitive?.contentOrNull ?: desired
    }

    suspend fun prompt(sessionId: String, text: String, steer: Boolean): Boolean {
        call("session.prompt", buildJsonObject {
            put("sessionId", sessionId)
            put("mode", if (steer) "steer" else "queue")
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", text) })
            })
            put("clientTimeZone", java.util.TimeZone.getDefault().id)
        })
        return true
    }

    suspend fun cancel(sessionId: String): Boolean =
        call("session.cancel", buildJsonObject { put("sessionId", sessionId) }) != null

    suspend fun sessionIds(): List<String> =
        call("session.list", JsonObject(emptyMap()), allowFailure = true)
            ?.get("items")?.let { it as? JsonArray }.orEmpty()
            .mapNotNull { item ->
                runCatching { item.jsonObject["sessionId"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            }

    suspend fun cancelAll(): Int {
        var cancelled = 0
        sessionIds().forEach { if (runCatching { cancel(it) }.getOrDefault(false)) cancelled++ }
        return cancelled
    }

    private suspend fun call(method: String, payload: JsonObject, allowFailure: Boolean = false): JsonObject? =
        withContext(Dispatchers.IO) {
            val base = state().webUrl?.takeIf { state().phase == DshRuntimePhase.READY }
                ?: error("sai Agent 尚未就绪")
            val rpcId = UUID.randomUUID().toString()
            val envelope = buildJsonObject {
                put("type", "client-request")
                put("rpcId", rpcId)
                put("method", method)
                put("payload", payload)
            }
            val request = Request.Builder()
                .url(base.trimEnd('/') + "/api/$method")
                .header("Cookie", "sai_auth=${java.net.URLEncoder.encode(state().accessToken ?: error("DSH token missing"), "UTF-8")}")
                .post(envelope.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (allowFailure) return@withContext null
                    error("DSH $method failed with HTTP ${response.code}")
                }
                val root = response.body?.string()?.let(json::parseToJsonElement)?.jsonObject
                    ?: if (allowFailure) return@withContext null else error("DSH returned an empty response")
                val result = root["result"]?.jsonObject
                    ?: if (allowFailure) return@withContext null else error("DSH returned an invalid response")
                if (!result["ok"]!!.jsonPrimitive.content.toBoolean()) {
                    if (allowFailure) return@withContext null
                    val error = result["error"]?.jsonObject
                    throw IllegalStateException(error?.get("message")?.jsonPrimitive?.contentOrNull ?: "DSH request failed")
                }
                result["value"]?.jsonObject ?: JsonObject(emptyMap())
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
