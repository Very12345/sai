package com.phoneagent.app

import com.phoneagent.harness.HarnessKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CodexDeviceLogin(
    val loginId: String,
    val verificationUrl: String,
    val userCode: String,
)

data class CodexAccountStatus(
    val authenticated: Boolean = false,
    val email: String? = null,
    val planType: String? = null,
)

/** Uses only the trusted loopback Codex GUI bridge; no account token enters Compose. */
class CodexAccountManager(private val runtime: HarnessWebRuntimeSupervisor) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun startDeviceLogin(): CodexDeviceLogin {
        awaitRuntime()
        val root = request("/codex-api/accounts/login/device/start", post = true)
        val data = root["data"]?.jsonObject ?: error(root["message"]?.jsonPrimitive?.contentOrNull ?: "Codex 未返回设备码")
        return CodexDeviceLogin(
            loginId = data["loginId"]?.jsonPrimitive?.contentOrNull ?: error("Codex 未返回登录 ID"),
            verificationUrl = data["verificationUrl"]?.jsonPrimitive?.contentOrNull ?: error("Codex 未返回验证地址"),
            userCode = data["userCode"]?.jsonPrimitive?.contentOrNull ?: error("Codex 未返回验证码"),
        )
    }

    suspend fun waitForAuthorization(timeoutMillis: Long = 10 * 60_000L): CodexAccountStatus = withTimeout(timeoutMillis) {
        while (true) {
            val status = status()
            if (status.authenticated) {
                runCatching { request("/codex-api/accounts/refresh", post = true) }
                return@withTimeout status
            }
            delay(2_000)
        }
        @Suppress("UNREACHABLE_CODE")
        CodexAccountStatus()
    }

    suspend fun status(): CodexAccountStatus {
        awaitRuntime()
        val root = request("/codex-api/accounts/login/device/status")
        val data = root["data"]?.jsonObject ?: return CodexAccountStatus()
        return CodexAccountStatus(
            authenticated = data["authenticated"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true,
            email = data["email"]?.jsonPrimitive?.contentOrNull,
            planType = data["planType"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun awaitRuntime() {
        runtime.ensureStarted(HarnessKind.CODEX)
        val state = withTimeout(75_000) {
            runtime.states.first { states ->
                states[HarnessKind.CODEX]?.let { it.ready || it.phase == HarnessWebPhase.FAILED } == true
            }[HarnessKind.CODEX]
        } ?: error("Codex 运行时状态不可用")
        check(state.ready) { state.detail.ifBlank { "Codex 运行时未就绪" } }
    }

    private fun request(path: String, post: Boolean = false) = client.newCall(
        Request.Builder().url("http://127.0.0.1:3090$path").apply {
            if (post) post("{}".toRequestBody(JSON)) else get()
        }.build(),
    ).execute().use { response ->
        val body = response.body.string()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { error("Codex 返回了无法解析的响应") }
        if (!response.isSuccessful) error(root["message"]?.jsonPrimitive?.contentOrNull ?: "Codex HTTP ${response.code}")
        root
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
