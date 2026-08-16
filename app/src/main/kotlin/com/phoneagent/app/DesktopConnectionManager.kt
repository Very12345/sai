package com.phoneagent.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.phoneagent.app.service.AgentForegroundService
import com.phoneagent.data.DesktopPairingEntity
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.NamedParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Collections
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class DesktopConnectionManager(
    private val context: Context,
    private val container: AppContainer,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val remoteSessions = Collections.synchronizedSet(mutableSetOf<String>())
    private val seenNonces = Collections.synchronizedSet(mutableSetOf<String>())
    private val sendLock = Any()
    private val _status = MutableStateFlow("未连接电脑")
    val status: StateFlow<String> = _status.asStateFlow()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var sessionKey: ByteArray? = null
    @Volatile private var client: OkHttpClient? = null
    @Volatile private var grantedScopes: Set<String> = emptySet()

    fun pair(qrPayload: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _status.value = "电脑加密连接需要 Android 13 或更高版本"
            return
        }
        scope.launch {
            runCatching { parseOffer(qrPayload) }
                .onSuccess(::connect)
                .onFailure { _status.value = "电脑配对失败：${it.message ?: "无效二维码"}" }
        }
    }

    fun disconnect() = disconnectInternal(notifyService = true)

    internal fun disconnectFromService() = disconnectInternal(notifyService = false)

    private fun disconnectInternal(notifyService: Boolean) {
        socket?.close(1000, "sai user disconnected")
        socket = null
        sessionKey?.fill(0)
        sessionKey = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        seenNonces.clear()
        grantedScopes = emptySet()
        _status.value = "未连接电脑"
        if (notifyService) runCatching {
            context.startService(Intent(context, AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_STOP_DESKTOP))
        }
    }

    private data class Offer(
        val endpoint: String,
        val nonce: ByteArray,
        val publicKey: ByteArray,
        val certificatePin: ByteArray,
        val scopes: Set<String>,
    )

    private fun parseOffer(raw: String): Offer {
        val root = json.parseToJsonElement(raw).jsonObject
        require(root["v"]?.jsonPrimitive?.content == "1") { "不支持的配对协议版本" }
        val endpoint = root.requiredString("endpoint")
        val uri = URI(endpoint)
        require(uri.scheme == "wss" && uri.path == "/pair") { "只允许 wss://…/pair 地址" }
        val address = InetAddress.getByName(requireNotNull(uri.host) { "二维码缺少主机" })
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) {
            "电脑地址不是局域网地址"
        }
        val scopes = root["scopes"]?.let { element ->
            element.toString().let { Regex("\"([^\"]+)\"").findAll(it).map { match -> match.groupValues[1] }.toSet() }
        }.orEmpty()
        val allowed = setOf("project.read", "project.write", "chat", "harness.sync")
        require(scopes.isNotEmpty() && scopes.all(allowed::contains)) { "二维码请求了未知权限" }
        return Offer(
            endpoint,
            decoder.decode(root.requiredString("nonce")).also { require(it.size == 24) { "nonce 长度错误" } },
            decoder.decode(root.requiredString("publicKey")).also { require(it.size == 32) { "电脑公钥长度错误" } },
            decoder.decode(root.requiredString("pin")).also { require(it.size == 32) { "证书指纹长度错误" } },
            scopes,
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun connect(offer: Offer) {
        disconnectInternal(notifyService = false)
        _status.value = "正在验证电脑并建立加密连接…"
        val keyPair = KeyPairGenerator.getInstance("XDH").apply {
            initialize(NamedParameterSpec("X25519"))
        }.generateKeyPair()
        val desktopPublic = KeyFactory.getInstance("XDH").generatePublic(
            X509EncodedKeySpec(X25519_PREFIX + offer.publicKey),
        )
        val shared = KeyAgreement.getInstance("XDH").run {
            init(keyPair.private)
            doPhase(desktopPublic, true)
            generateSecret()
        }
        val derived = DesktopCrypto.hkdfSha256(shared, offer.nonce, "phoneagent-lan-v1".encodeToByteArray())
        shared.fill(0)
        val trust = CertificatePinTrustManager(offer.certificatePin)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), secureRandom) }
        val localClient = OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { _, _ -> true }
            .build()
        client = localClient
        val request = Request.Builder().url(offer.endpoint).build()
        socket = localClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val publicRaw = keyPair.public.encoded.takeLast(32).toByteArray()
                val proof = DesktopCrypto.hmac(derived, "phoneagent-phone".encodeToByteArray())
                webSocket.send(buildJsonObject {
                    put("type", "pair")
                    put("nonce", encoder.encodeToString(offer.nonce))
                    put("publicKey", encoder.encodeToString(publicRaw))
                    put("proof", encoder.encodeToString(proof))
                }.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = json.parseToJsonElement(text).jsonObject
                    when (message["type"]?.jsonPrimitive?.contentOrNull) {
                        "paired" -> {
                            val proof = decoder.decode(message.requiredString("proof"))
                            require(MessageDigest.isEqual(proof, DesktopCrypto.hmac(derived, "phoneagent-desktop".encodeToByteArray()))) {
                                "电脑配对证明无效"
                            }
                            sessionKey = derived.copyOf()
                            grantedScopes = offer.scopes
                            _status.value = "已加密连接 ${URI(offer.endpoint).host}"
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_DESKTOP),
                            )
                            scope.launch {
                                container.database.dao().upsertDesktopPairing(
                                    DesktopPairingEntity(
                                        id = sha256Hex(offer.publicKey).take(24),
                                        displayName = "sai Desktop · ${URI(offer.endpoint).host}",
                                        publicKey = encoder.encodeToString(offer.publicKey),
                                        endpoint = offer.endpoint,
                                        scopesJson = buildJsonArray { offer.scopes.sorted().forEach { add(JsonPrimitive(it)) } }.toString(),
                                        lastConnectedAt = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                        "encrypted" -> handleEncrypted(message)
                        else -> error("电脑发送了未知握手消息")
                    }
                }.onFailure {
                    _status.value = "电脑连接被拒绝：${it.message}"
                    webSocket.close(1008, "protocol error")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                sessionKey?.fill(0); sessionKey = null
                _status.value = "电脑连接已关闭"
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                sessionKey?.fill(0); sessionKey = null
                _status.value = "电脑连接失败：${t.message ?: "网络错误"}"
            }
        })
    }

    private fun handleEncrypted(envelope: JsonObject) {
        val key = sessionKey ?: error("尚未完成配对")
        val nonceText = envelope.requiredString("nonce")
        require(seenNonces.add(nonceText)) { "检测到重放消息" }
        val nonce = decoder.decode(nonceText).also { require(it.size == 12) { "消息 nonce 长度错误" } }
        val encrypted = decoder.decode(envelope.requiredString("ciphertext"))
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            doFinal(encrypted)
        }
        val request = json.parseToJsonElement(plain.decodeToString()).jsonObject
        scope.launch { processRequest(request) }
    }

    private suspend fun processRequest(request: JsonObject) {
        val command = request.requiredString("type")
        val id = request.requiredString("id")
        val payload = request["payload"]?.jsonObject ?: request
        runCatching {
            require(requiredScope(command) in grantedScopes) { "电脑未获授权执行 $command" }
            when (command) {
                "state.list" -> stateList()
                "project.files" -> projectFiles(payload)
                "file.read" -> readFile(payload)
                "file.write" -> writeFile(payload)
                "chat.send" -> startChat(payload)
                "harness.sync.manifest" -> harnessSyncManifest()
                "harness.sync.read" -> readHarnessSession(payload)
                "harness.sync.write" -> writeHarnessSession(payload)
                else -> error("不支持的桌面命令：$command")
            }
        }.fold(
            onSuccess = { result -> respond(id, command, true, result) },
            onFailure = { error -> respond(id, command, false, buildJsonObject { put("message", error.message ?: "请求失败") }) },
        )
    }

    private suspend fun stateList(): JsonObject {
        val workspaces = container.database.dao().workspaces()
        val sessions = container.database.dao().sessions()
        return buildJsonObject {
            put("projects", buildJsonArray {
                workspaces.forEach { workspace -> add(buildJsonObject {
                    put("id", workspace.id); put("name", workspace.name); put("updatedAt", workspace.lastOpenedAt)
                }) }
            })
            put("sessions", buildJsonArray {
                sessions.take(100).forEach { session -> add(buildJsonObject {
                    put("id", session.id); put("projectId", session.workspaceId); put("title", session.title); put("state", session.state)
                }) }
            })
        }
    }

    private suspend fun projectFiles(payload: JsonObject): JsonObject {
        val workspace = workspace(payload.requiredString("projectId"))
        val relative = payload["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val directory = safePath(File(workspace.localPath), relative)
        require(directory.isDirectory) { "目录不存在" }
        val items = directory.listFiles().orEmpty().sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
        return buildJsonObject {
            put("path", relative)
            put("files", buildJsonArray {
                items.take(1_000).forEach { file -> add(buildJsonObject {
                    val path = file.relativeTo(File(workspace.localPath)).invariantSeparatorsPath
                    put("name", file.name); put("path", path); put("directory", file.isDirectory); put("size", file.length())
                }) }
            })
        }
    }

    private suspend fun readFile(payload: JsonObject): JsonObject {
        val workspace = workspace(payload.requiredString("projectId"))
        val relative = payload.requiredString("path")
        val file = safePath(File(workspace.localPath), relative)
        require(file.isFile && file.length() <= MAX_REMOTE_FILE) { "只能读取不超过 2 MB 的普通文件" }
        val bytes = file.readBytes()
        require(bytes.none { it == 0.toByte() }) { "桌面预览暂不支持二进制文件" }
        return buildJsonObject {
            put("path", relative); put("sha256", sha256Hex(bytes)); put("content", Base64.getEncoder().encodeToString(bytes))
        }
    }

    private suspend fun writeFile(payload: JsonObject): JsonObject {
        val workspace = workspace(payload.requiredString("projectId"))
        val relative = payload.requiredString("path")
        val target = safePath(File(workspace.localPath), relative)
        require(target.isFile) { "目标文件不存在" }
        val expected = payload.requiredString("expectedSha256")
        require(MessageDigest.isEqual(expected.encodeToByteArray(), sha256Hex(target.readBytes()).encodeToByteArray())) {
            "手机端文件已变化，拒绝覆盖；请重新打开后再编辑"
        }
        val content = Base64.getDecoder().decode(payload.requiredString("content"))
        require(content.size <= MAX_REMOTE_FILE) { "远程写入限制为 2 MB" }
        val temporary = File(target.parentFile, ".${target.name}.phoneagent-${UUID.randomUUID()}.tmp")
        temporary.writeBytes(content)
        runCatching { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            .getOrElse { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return buildJsonObject { put("path", relative); put("sha256", sha256Hex(content)) }
    }

    private suspend fun startChat(payload: JsonObject): JsonObject {
        val text = payload.requiredString("text").trim()
        require(text.isNotEmpty() && text.length <= 100_000) { "消息为空或过长" }
        val workspaceId = payload["projectId"]?.jsonPrimitive?.contentOrNull
            ?: container.database.dao().workspaces().firstOrNull()?.id
            ?: error("手机上没有可用项目")
        val workspace = workspace(workspaceId)
        container.dshRuntime.ensureStarted()
        container.dshRuntime.awaitReady()
        val requestedSession = payload["sessionId"]?.jsonPrimitive?.contentOrNull
        val sessionId = container.dshApi.ensureSession(requestedSession, workspace.localPath)
        container.dshApi.prompt(sessionId, text, steer = false)
        remoteSessions += sessionId
        ContextCompat.startForegroundService(
            context,
            Intent(context, AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_DSH),
        )
        return buildJsonObject { put("sessionId", sessionId) }
    }

    /**
     * Lists the native durable conversation artifacts rather than converting
     * them to sai's Room schema. Keeping the source format means a desktop
     * Codex/Claude/DSH client can resume the exact same conversation after a
     * conflict-checked copy.
     */
    private fun harnessSyncManifest(): JsonObject = buildJsonObject {
        put("version", 1)
        put("files", buildJsonArray {
            harnessSessionRoots().forEach { (harness, root) ->
                if (!root.isDirectory) return@forEach
                root.walkTopDown()
                    .onEnter { directory -> !Files.isSymbolicLink(directory.toPath()) }
                    .filter { file -> file.isFile && !Files.isSymbolicLink(file.toPath()) }
                    .take(MAX_SYNC_FILES)
                    .forEach { file ->
                        val relative = file.relativeTo(root).invariantSeparatorsPath
                        if (file.length() <= MAX_SYNC_FILE) add(buildJsonObject {
                            put("harness", harness)
                            put("path", relative)
                            put("size", file.length())
                            put("modifiedAt", file.lastModified())
                            put("sha256", sha256Hex(file.readBytes()))
                        })
                    }
            }
        })
    }

    private fun readHarnessSession(payload: JsonObject): JsonObject {
        val harness = payload.requiredString("harness")
        val relative = payload.requiredString("path")
        val root = harnessSessionRoots()[harness] ?: error("未知 Harness")
        val file = safePath(root, relative)
        require(file.isFile && file.length() <= MAX_SYNC_FILE) { "会话文件不存在或超过 16 MB" }
        val bytes = file.readBytes()
        return buildJsonObject {
            put("harness", harness)
            put("path", relative)
            put("sha256", sha256Hex(bytes))
            put("content", Base64.getEncoder().encodeToString(bytes))
        }
    }

    private fun writeHarnessSession(payload: JsonObject): JsonObject {
        val harness = payload.requiredString("harness")
        val relative = payload.requiredString("path")
        val root = harnessSessionRoots()[harness] ?: error("未知 Harness")
        val target = safePath(root, relative)
        val content = Base64.getDecoder().decode(payload.requiredString("content"))
        require(content.size <= MAX_SYNC_FILE) { "会话文件超过 16 MB" }
        val expected = payload["expectedSha256"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (target.exists()) {
            require(target.isFile && expected.isNotBlank() && sha256Hex(target.readBytes()) == expected) {
                "手机端会话已变化；请重新同步后再覆盖"
            }
        } else {
            require(expected.isEmpty()) { "会话文件已被移动或删除" }
        }
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.sai-sync-${UUID.randomUUID()}.tmp")
        temporary.writeBytes(content)
        runCatching { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            .getOrElse { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        return buildJsonObject { put("harness", harness); put("path", relative); put("sha256", sha256Hex(content)) }
    }

    private fun harnessSessionRoots(): Map<String, File> = mapOf(
        "codex" to File(container.dshProvisioner.home, ".codex/sessions"),
        "claude-code" to File(container.dshProvisioner.home, ".claude/projects"),
        "dsh" to File(container.dshProvisioner.home, "sessions"),
    )

    private fun requiredScope(command: String): String = when (command) {
        "state.list", "project.files", "file.read" -> "project.read"
        "file.write" -> "project.write"
        "chat.send" -> "chat"
        "harness.sync.manifest", "harness.sync.read", "harness.sync.write" -> "harness.sync"
        else -> error("不支持的桌面命令：$command")
    }

    private suspend fun workspace(id: String) = container.database.dao().workspace(id) ?: error("项目不存在")

    private fun safePath(root: File, relative: String): File {
        require(relative.isEmpty() || (!relative.startsWith('/') && !relative.startsWith('\\') && ':' !in relative)) { "非法路径" }
        require(relative.split('/', '\\').none { it == ".." }) { "路径越界" }
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relative).canonicalFile
        require(candidate == canonicalRoot || candidate.path.startsWith(canonicalRoot.path + File.separator)) { "路径越过项目边界" }
        return candidate
    }

    private fun respond(id: String, command: String, ok: Boolean, result: JsonElement) {
        sendEncrypted(buildJsonObject {
            put("type", "response"); put("id", id); put("command", command); put("ok", ok)
            if (ok) put("result", result) else put("error", result.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "请求失败")
        })
    }

    private fun sendEncrypted(value: JsonObject) {
        val key = sessionKey ?: return
        synchronized(sendLock) {
            val nonce = ByteArray(12).also(secureRandom::nextBytes)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                doFinal(value.toString().encodeToByteArray())
            }
            socket?.send(buildJsonObject {
                put("type", "encrypted"); put("nonce", encoder.encodeToString(nonce)); put("ciphertext", encoder.encodeToString(encrypted))
            }.toString())
        }
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    override fun close() { disconnect(); scope.cancel() }

    private class CertificatePinTrustManager(private val expected: ByteArray) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val certificate = chain?.firstOrNull() ?: throw CertificateException("电脑未提供证书")
            val actual = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            if (!MessageDigest.isEqual(expected, actual)) throw CertificateException("电脑 TLS 证书与二维码指纹不符")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val MAX_REMOTE_FILE = 2L * 1024 * 1024
        private const val MAX_SYNC_FILE = 16L * 1024 * 1024
        private const val MAX_SYNC_FILES = 5_000
        private val X25519_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00)
    }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: error("缺少 $name")

internal object DesktopCrypto {
    fun hkdfSha256(secret: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val prk = hmac(salt, secret)
        return hmac(prk, info + byteArrayOf(1)).copyOf(32).also { prk.fill(0) }
    }

    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
    }
}
