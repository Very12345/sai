package com.phoneagent.app

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phoneagent.app.browser.AgentBrowserSession
import com.phoneagent.app.device.PhoneAgentAccessibilityService
import com.phoneagent.app.service.VoiceConversationService
import com.phoneagent.dsh.DshBridgeEndpoint
import java.io.BufferedInputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Loopback-only authenticated bridge. The bearer token exists only for one app process. */
class SaiDshBridgeServer(
    private val context: Context,
    private val github: GitHubCliManager,
    private val providers: ProviderSettingsRepository,
) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tokenBytes = ByteArray(32).also(SecureRandom()::nextBytes)
    private val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
    private var server: ServerSocket? = null
    private val browsers = mutableMapOf<String, AgentBrowserSession>()
    private val _taskStatuses = MutableStateFlow<Map<String, SaiDshTaskStatus>>(emptyMap())
    val taskStatuses: StateFlow<Map<String, SaiDshTaskStatus>> = _taskStatuses.asStateFlow()

    @Synchronized fun endpoint(): DshBridgeEndpoint {
        val socket = server ?: ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).also { opened ->
            server = opened
            scope.launch {
                while (!opened.isClosed) runCatching { opened.accept() }.onSuccess { client -> launch { handle(client) } }
            }
        }
        return DshBridgeEndpoint("http://127.0.0.1:${socket.localPort}", token)
    }

    private suspend fun handle(socket: Socket) = socket.use { client ->
        client.soTimeout = 65_000
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
        val supplied = headers["authorization"]?.removePrefix("Bearer ")?.toByteArray() ?: byteArrayOf()
        if (!MessageDigest.isEqual(token.toByteArray(), supplied)) return respond(client, 401, "unauthorized")
        if (requestLine != "POST /v1/tools/call HTTP/1.1") return respond(client, 404, "not found")
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 1_048_576) ?: return respond(client, 400, "invalid length")
        val body = readExactly(input, length).toString(Charsets.UTF_8)
        val result = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            dispatch(root.getValue("operation").jsonPrimitive.content, root["payload"]?.jsonObject ?: JsonObject(emptyMap()))
        }
        result.fold(
            onSuccess = { respond(client, 200, it) },
            onFailure = { respond(client, 400, it.message?.take(500) ?: "bridge operation failed") },
        )
    }

    private suspend fun dispatch(operation: String, payload: JsonObject): String = when (operation) {
        "speak" -> {
            val text = payload["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            require(text.isNotBlank() && text.length <= 300) { "speech must contain 1..300 characters" }
            ContextCompat.startForegroundService(
                context,
                Intent(context, VoiceConversationService::class.java)
                    .setAction(VoiceConversationService.ACTION_SPEAK)
                    .putExtra(VoiceConversationService.EXTRA_SPEAK_TEXT, text),
            )
            "speech queued"
        }
        "github" -> {
            val args = payload["args"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            require(args.isNotEmpty() && args.size <= 64) { "github arguments are required" }
            github.run(args).getOrThrow()
        }
        "browser" -> browser(payload)
        "observe_device" -> observeDevice()
        "device_action" -> deviceAction(payload)
        "notify" -> {
            val title = payload["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "sai" }.take(80)
            val text = payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty().take(500)
            require(text.isNotBlank()) { "notification text is required" }
            context.getSystemService(NotificationManager::class.java).notify(
                1808,
                NotificationCompat.Builder(context, PhoneAgentApplication.AGENT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_phone_agent)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .build(),
            )
            "notification posted"
        }
        "task_status" -> {
            val sessionId = payload["sessionId"]?.jsonPrimitive?.contentOrNull?.take(160)
                ?: error("task status requires sessionId")
            val phase = payload["phase"]?.jsonPrimitive?.contentOrNull?.take(40) ?: "working"
            val detail = payload["detail"]?.jsonPrimitive?.contentOrNull.orEmpty().takeLast(240)
            if (phase in setOf("idle", "completed", "cancelled", "failed")) {
                _taskStatuses.update { it - sessionId }
            } else {
                _taskStatuses.update { current ->
                    current + (sessionId to SaiDshTaskStatus(sessionId, phase, detail, System.currentTimeMillis()))
                }
            }
            "status received"
        }
        "credential_resolve" -> {
            val ref = credentialRef(payload)
            val value = providers.resolveCredentialReference(ref)
            if (value == null) "{\"configured\":false}" else
                try {
                    buildJsonObject {
                        put("configured", true)
                        put("value", value.concatToString())
                    }.toString()
                } finally {
                    value.fill('\u0000')
                }
        }
        "credential_describe" -> {
            val ref = credentialRef(payload)
            buildJsonObject { put("configured", providers.hasCredentialReference(ref)) }.toString()
        }
        "credential_set" -> {
            val ref = credentialRef(payload)
            val value = payload["value"]?.jsonPrimitive?.contentOrNull?.toCharArray()
                ?: error("credential value is required")
            require(value.isNotEmpty()) { "credential value cannot be empty" }
            try { providers.putCredentialReference(ref, value) } finally { value.fill('\u0000') }
            "credential stored"
        }
        "credential_unset" -> {
            providers.removeCredentialReference(credentialRef(payload))
            "credential removed"
        }
        else -> error("Android capability '$operation' is not enabled in this build")
    }

    private suspend fun browser(payload: JsonObject): String {
        val projectId = payload["projectId"]?.jsonPrimitive?.contentOrNull
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(80)?.ifBlank { "default" } ?: "default"
        val browser = synchronized(browsers) {
            browsers.getOrPut(projectId) { AgentBrowserSession(context.applicationContext, projectId) }
        }
        return when (val action = payload["action"]?.jsonPrimitive?.contentOrNull ?: "observe") {
            "navigate" -> browser.navigate(payload["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required"))
            "observe" -> payload["url"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                ?.let { browser.navigate(it) } ?: browser.observe()
            "screenshot" -> buildJsonObject {
                put("ok", true)
                put("path", browser.screenshot().absolutePath)
                put("mimeType", "image/png")
            }.toString()
            "close" -> {
                synchronized(browsers) { browsers.remove(projectId) }
                browser.destroy()
                "browser closed"
            }
            else -> browser.action(
                action = action,
                nodeId = payload["nodeId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                text = payload["text"]?.jsonPrimitive?.contentOrNull,
                finalSubmit = payload["finalSubmit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true,
            ).let { result -> buildJsonObject {
                put("ok", result.success)
                put("output", result.output.take(100_000))
            }.toString() }
        }
    }

    private suspend fun observeDevice(): String = withContext(Dispatchers.Main) {
        val service = PhoneAgentAccessibilityService.instance
            ?: error("sai accessibility service is disabled; request the user to enable it")
        buildJsonObject {
            put("untrusted", true)
            put("nodes", kotlinx.serialization.json.buildJsonArray {
                service.observe().forEach { node -> add(buildJsonObject {
                    put("id", node.id)
                    put("class", node.className)
                    put("text", node.text)
                    put("description", node.description)
                    put("clickable", node.clickable)
                    put("editable", node.editable)
                    put("bounds", node.bounds)
                }) }
            })
        }.toString().take(100_000)
    }

    private suspend fun deviceAction(payload: JsonObject): String = withContext(Dispatchers.Main) {
        val service = PhoneAgentAccessibilityService.instance
            ?: error("sai accessibility service is disabled; request the user to enable it")
        val action = payload["action"]?.jsonPrimitive?.contentOrNull ?: error("device action is required")
        val success = when (action) {
            "click" -> service.click(
                payload["nodeId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
                payload["finalSubmit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true,
            )
            "input" -> service.input(
                payload["nodeId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
                payload["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "swipe" -> service.swipe(
                payload.float("startX"), payload.float("startY"), payload.float("endX"), payload.float("endY"),
            )
            "back" -> service.back()
            "home" -> service.home()
            "launch" -> service.launch(
                payload["packageName"]?.jsonPrimitive?.contentOrNull
                    ?: payload["appName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            else -> error("unsupported device action: $action")
        }
        buildJsonObject { put("ok", success); put("action", action) }.toString()
    }

    private fun JsonObject.float(name: String): Float =
        this[name]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f

    private fun credentialRef(payload: JsonObject): String =
        payload["ref"]?.jsonPrimitive?.contentOrNull?.also {
            require(Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(it)) { "invalid credential reference" }
        } ?: error("credential reference is required")

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw EOFException("request body ended early")
            offset += count
        }
        return bytes
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size <= 16_384) {
            val value = input.read()
            if (value < 0) return null
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    private fun respond(client: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; else -> "Not Found" }
        client.getOutputStream().use { output ->
            output.write("HTTP/1.1 $code $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            output.write(bytes)
        }
    }

    override fun close() {
        server?.close()
        tokenBytes.fill(0)
        synchronized(browsers) {
            browsers.values.forEach { browser ->
                android.os.Handler(context.mainLooper).post { scope.launch { browser.destroy() } }
            }
            browsers.clear()
        }
        android.os.Handler(context.mainLooper).postDelayed({ scope.cancel() }, 250)
    }
}

data class SaiDshTaskStatus(
    val sessionId: String,
    val phase: String,
    val detail: String,
    val updatedAt: Long,
)
