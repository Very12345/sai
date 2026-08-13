package com.phoneagent.dsh

import kotlinx.serialization.Serializable

@Serializable
data class DshRuntimeManifest(
    val schemaVersion: Int,
    val runtimeVersion: String,
    val dshVersion: String,
    val nodeVersion: String,
    val sourceCommit: String,
    val packageLockSha256: Map<String, String> = emptyMap(),
    val port: Int = 3080,
    val archives: Map<String, DshArchive> = emptyMap(),
)

@Serializable
data class DshArchive(
    val asset: String,
    val sha256: String,
    val bytes: Long,
)

enum class DshRuntimePhase { NOT_INSTALLED, INSTALLING, STARTING, READY, STOPPING, FAILED }

data class DshRuntimeState(
    val phase: DshRuntimePhase = DshRuntimePhase.NOT_INSTALLED,
    val detail: String = "DSH runtime is not installed",
    val progress: Float? = null,
    val webUrl: String? = null,
    val runtimeVersion: String? = null,
    val accessToken: String? = null,
) {
    val ready: Boolean get() = phase == DshRuntimePhase.READY && webUrl != null
}

data class DshBridgeEndpoint(val url: String, val token: String)
