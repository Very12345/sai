package com.phoneagent.app.device

import android.os.SystemClock

data class DeviceAuthorization(
    val targetPackages: Set<String>,
    val lockToFirstTarget: Boolean,
    val allowScreenshots: Boolean,
    val allowTextInput: Boolean,
    val allowFinalSubmit: Boolean,
    val expiresAtElapsedRealtime: Long,
)

object DeviceControlAuthorization {
    @Volatile private var current: DeviceAuthorization? = null

    fun grant(
        targetPackages: Set<String> = emptySet(),
        screenshots: Boolean = true,
        textInput: Boolean = true,
        finalSubmit: Boolean = true,
    ) {
        current = DeviceAuthorization(
            targetPackages = targetPackages,
            lockToFirstTarget = targetPackages.isEmpty(),
            allowScreenshots = screenshots,
            allowTextInput = textInput,
            allowFinalSubmit = finalSubmit,
            expiresAtElapsedRealtime = SystemClock.elapsedRealtime() + 30 * 60 * 1_000L,
        )
    }

    fun require(packageName: String, textInput: Boolean = false, finalSubmit: Boolean = false): DeviceAuthorization {
        var authorization = current ?: error("手机控制会话尚未授权")
        if (SystemClock.elapsedRealtime() >= authorization.expiresAtElapsedRealtime) {
            current = null
            error("手机控制授权已过期")
        }
        require(packageName !in BLOCKED_PACKAGES) { "禁止操作系统安全界面" }
        require(packageName != PHONE_AGENT_PACKAGE) { "不会把 sai 自身作为手机控制目标" }
        if (authorization.lockToFirstTarget && authorization.targetPackages.isEmpty()) {
            authorization = authorization.copy(targetPackages = setOf(packageName), lockToFirstTarget = false)
            current = authorization
        }
        require(packageName in authorization.targetPackages) { "目标 App 不在本次即时授权范围内" }
        if (textInput) require(authorization.allowTextInput) { "未授权文字输入" }
        if (finalSubmit) require(authorization.allowFinalSubmit) { "未授权最终提交" }
        return authorization
    }

    fun revoke() { current = null }
    fun active(): Boolean = current?.expiresAtElapsedRealtime?.let { it > SystemClock.elapsedRealtime() } == true

    private val BLOCKED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    private const val PHONE_AGENT_PACKAGE = "com.phoneagent.app"
}
