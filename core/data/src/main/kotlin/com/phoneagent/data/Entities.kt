package com.phoneagent.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val localPath: String,
    val externalTreeUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val profileJson: String,
    val secretAlias: String,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "provider_models",
    foreignKeys = [ForeignKey(
        entity = ProviderEntity::class,
        parentColumns = ["id"],
        childColumns = ["providerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("providerId"), Index(value = ["providerId", "modelId"], unique = true)],
)
data class ProviderModelEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String = "",
    val capabilitiesJson: String = "{}",
    val pricingJson: String = "{}",
    val contextWindow: Int = 128_000,
    val reasoningEffortsJson: String = "[]",
    val discovered: Boolean = true,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = WorkspaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["workspaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workspaceId")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String,
    val titleSource: String = "INITIAL",
    val autoTitleState: String = "PENDING",
    val autoTitleAttempts: Int = 0,
    val mode: String,
    val providerId: String,
    val model: String,
    val reasoningConfigJson: String = "{\"mode\":\"AUTO\",\"effort\":null,\"budgetTokens\":null}",
    val visionProviderId: String? = null,
    val visionModelId: String? = null,
    val visionSelectionSource: String = "AUTO",
    val state: String,
    val queueState: String = "READY",
    val progressText: String = "",
    val latestPreview: String = "",
    val unread: Boolean = false,
    val pinned: Boolean = false,
    val worktreePath: String? = null,
    val branchName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "agent_events",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["sessionId", "sequence"], unique = true)],
)
data class AgentEventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId: String,
    val sequence: Long,
    val type: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val source: String,
    val manifestJson: String,
    val enabled: Boolean = false,
    val trustedAt: Long? = null,
    val version: String = "",
    val sourceDigest: String = "",
    val previousManifestJson: String? = null,
    val installState: String = "INSTALLED",
    val profileId: String? = null,
    val rollbackVersion: String? = null,
)

@Entity(
    tableName = "terminal_tabs",
    foreignKeys = [ForeignKey(
        entity = WorkspaceEntity::class,
        parentColumns = ["id"],
        childColumns = ["workspaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workspaceId")],
)
data class TerminalTabEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String = "Terminal",
    val cwd: String,
    val state: String = "DISCONNECTED",
    val lastActiveAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_checkpoints",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class TaskCheckpointEntity(
    @PrimaryKey val sessionId: String,
    val sequence: Long,
    val phase: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "attachments", indices = [Index("sessionId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "extension_sources")
data class ExtensionSourceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val displayName: String,
    val endpoint: String,
    val enabled: Boolean = true,
    val metadataJson: String = "{}",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val workspaceId: String? = null,
    val displayName: String,
    val configJson: String,
    val enabled: Boolean = false,
    val lastStatus: String = "DISCONNECTED",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "hook_configs", indices = [Index("workspaceId")])
data class HookConfigEntity(
    @PrimaryKey val id: String,
    val workspaceId: String? = null,
    val displayName: String,
    val event: String,
    val configJson: String,
    val enabled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "desktop_pairings")
data class DesktopPairingEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val publicKey: String,
    val endpoint: String,
    val scopesJson: String = "[\"project.read\",\"project.write\",\"chat\"]",
    val enabled: Boolean = true,
    val lastConnectedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "runtime_packages")
data class RuntimePackageEntity(
    @PrimaryKey val id: String,
    val packagesJson: String,
    val installedByPhoneAgent: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
