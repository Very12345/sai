package com.phoneagent.provider

import com.phoneagent.network.ProtectedHttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

abstract class HttpProviderAdapter(
    final override val profile: ProviderProfile,
    protected val client: OkHttpClient = defaultClient(),
) : ProviderAdapter {
    protected val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun probe(credential: ProviderCredential): ProviderProbe = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        runCatching { listModels(credential) }
            .fold(
                onSuccess = { ProviderProbe(true, elapsedMs(start), "${it.size} models") },
                onFailure = { ProviderProbe(false, elapsedMs(start), it.message ?: "Connection failed") },
            )
    }

    override suspend fun listModels(credential: ProviderCredential): List<ModelInfo> = withContext(Dispatchers.IO) {
        val path = profile.modelsPath ?: return@withContext listOf(ModelInfo(profile.defaultModel))
        val request = requestBuilder(profile.baseUrl.trimEnd('/') + path, credential).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw ProviderHttpError(response.code, body)
            parseModels(body).ifEmpty { listOf(ModelInfo(profile.defaultModel)) }
        }
    }

    override fun stream(request: ModelRequest, credential: ProviderCredential): Flow<ModelEvent> = channelFlow {
        resetStreamState()
        val call = client.newCall(buildStreamingRequest(request, credential))
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body.string()
                        send(ModelEvent.Error("HTTP ${response.code}: ${body.take(500)}", response.code in 429..599, response.code))
                        return@use
                    }
                    val decoder = SseDecoder()
                    response.body.charStream().buffered().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            decoder.accept(line)?.let { event ->
                                for (modelEvent in parseSse(event)) send(modelEvent)
                            }
                        }
                    }
                    decoder.finish()?.let { event ->
                        for (modelEvent in parseSse(event)) send(modelEvent)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                send(ModelEvent.Error(error.message ?: error::class.java.simpleName, true))
            } finally {
                channel.close()
            }
        }
        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    protected abstract fun buildStreamingRequest(request: ModelRequest, credential: ProviderCredential): Request
    protected abstract fun parseSse(event: SseEvent): List<ModelEvent>
    protected open fun resetStreamState() = Unit

    protected open fun parseModels(body: String): List<ModelInfo> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["data"] as? JsonArray ?: root["models"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"].stringOrNull() ?: obj["name"].stringOrNull()?.substringAfterLast('/')
            id?.let {
                ModelInfo(
                    id = it,
                    displayName = obj["name"].stringOrNull() ?: it,
                    contextWindow = obj["context_length"]?.jsonPrimitive?.intOrNull,
                    reasoningCapabilities = parseReasoningCapabilities(obj),
                )
            }
        }
    }

    private fun parseReasoningCapabilities(model: JsonObject): ModelReasoningCapabilities? {
        val reasoning = model["reasoning"] as? JsonObject ?: return null
        val efforts = (reasoning["supported_efforts"] as? JsonArray).orEmpty().mapNotNull { value ->
            when (value.stringOrNull()?.lowercase()) {
                "minimal" -> ReasoningEffort.MINIMAL
                "low" -> ReasoningEffort.LOW
                "medium" -> ReasoningEffort.MEDIUM
                "high" -> ReasoningEffort.HIGH
                "xhigh" -> ReasoningEffort.XHIGH
                "max" -> ReasoningEffort.MAX
                else -> null
            }
        }
        val mandatory = reasoning["mandatory"]?.jsonPrimitive?.booleanOrNull == true
        val supportsBudget = reasoning["supports_max_tokens"]?.jsonPrimitive?.booleanOrNull == true
        val defaultEffort = when (reasoning["default_effort"].stringOrNull()?.lowercase()) {
            "minimal" -> ReasoningEffort.MINIMAL
            "low" -> ReasoningEffort.LOW
            "medium" -> ReasoningEffort.MEDIUM
            "high" -> ReasoningEffort.HIGH
            "xhigh" -> ReasoningEffort.XHIGH
            "max" -> ReasoningEffort.MAX
            else -> null
        }
        if (efforts.isEmpty() && !supportsBudget && !mandatory) return null
        val modes = buildSet {
            add(ReasoningMode.AUTO)
            add(ReasoningMode.ENABLED)
            if (!mandatory) add(ReasoningMode.DISABLED)
        }
        return ModelReasoningCapabilities(
            supportedModes = modes,
            supportedEfforts = efforts,
            defaultSelection = defaultEffort?.let { ReasoningSelection(ReasoningMode.ENABLED, it) } ?: ReasoningSelection(),
            mandatory = mandatory,
            parameterFormat = ReasoningParameterFormat.OPENAI_EFFORT,
            source = ReasoningCapabilitySource.PROVIDER_METADATA,
        )
    }

    protected fun requestBuilder(url: String, credential: ProviderCredential): Request.Builder {
        val builder = Request.Builder().url(url)
        profile.customHeaders.forEach(builder::header)
        credential.organization?.let { builder.header("OpenAI-Organization", it) }
        return when (profile.protocol) {
            ProviderProtocol.ANTHROPIC_MESSAGES -> builder.header("x-api-key", credential.apiKey)
            else -> builder.header("Authorization", "Bearer ${credential.apiKey}")
        }
    }

    protected fun postJson(url: String, credential: ProviderCredential, body: JsonObject): Request =
        requestBuilder(url, credential)
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = ProtectedHttpClients.model()

        private fun elapsedMs(start: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
    }
}
