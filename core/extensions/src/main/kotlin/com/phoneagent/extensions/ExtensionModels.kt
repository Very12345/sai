package com.phoneagent.extensions

import kotlinx.serialization.Serializable

@Serializable
enum class ExtensionKind { SKILL, COMMAND, HOOK, MCP, PLUGIN }

@Serializable
enum class ExtensionPermission { WORKSPACE_READ, WORKSPACE_WRITE, SHELL, NETWORK, SECRETS }

@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val kind: ExtensionKind,
    val description: String = "",
    val license: String? = null,
    val source: String,
    val permissions: Set<ExtensionPermission> = emptySet(),
    val entrypoint: String? = null,
)

@Serializable
data class ExtensionTrust(
    val manifestId: String,
    val sourceDigest: String,
    val grantedPermissions: Set<ExtensionPermission>,
    val trustedAtEpochMillis: Long,
)

class ExtensionPolicy {
    fun requiresConfirmation(manifest: ExtensionManifest, previous: ExtensionTrust?): Boolean {
        if (previous == null) return true
        if (previous.manifestId != manifest.id) return true
        return !previous.grantedPermissions.containsAll(manifest.permissions)
    }

    fun highRiskPermissions(manifest: ExtensionManifest): Set<ExtensionPermission> =
        manifest.permissions.intersect(setOf(
            ExtensionPermission.WORKSPACE_WRITE,
            ExtensionPermission.SHELL,
            ExtensionPermission.NETWORK,
            ExtensionPermission.SECRETS,
        ))
}
