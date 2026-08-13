package com.phoneagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneAgentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspace(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspaces ORDER BY lastOpenedAt DESC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :workspaceId LIMIT 1")
    suspend fun workspace(workspaceId: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces ORDER BY createdAt")
    suspend fun workspaces(): List<WorkspaceEntity>

    @Query("DELETE FROM workspaces WHERE id = :workspaceId")
    suspend fun deleteWorkspace(workspaceId: String)

    @Query("UPDATE workspaces SET name = :name, lastOpenedAt = :updatedAt WHERE id = :workspaceId")
    suspend fun renameWorkspace(workspaceId: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workspaces SET lastOpenedAt = :updatedAt WHERE id = :workspaceId")
    suspend fun touchWorkspace(workspaceId: String, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: ProviderEntity)

    @Query("SELECT * FROM providers WHERE enabled = 1 ORDER BY id")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY enabled DESC, updatedAt DESC")
    suspend fun providers(): List<ProviderEntity>

    @Query("DELETE FROM providers WHERE id = :providerId")
    suspend fun deleteProvider(providerId: String)

    @Query("UPDATE providers SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :providerId")
    suspend fun setProviderEnabled(providerId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviderModels(models: List<ProviderModelEntity>)

    @Query("SELECT * FROM provider_models WHERE providerId = :providerId AND enabled = 1 ORDER BY displayName, modelId")
    fun observeProviderModels(providerId: String): Flow<List<ProviderModelEntity>>

    @Query("SELECT * FROM provider_models WHERE enabled = 1 ORDER BY providerId, displayName, modelId")
    fun observeAllProviderModels(): Flow<List<ProviderModelEntity>>

    @Query("SELECT * FROM provider_models WHERE enabled = 1 ORDER BY providerId, displayName, modelId")
    suspend fun allProviderModels(): List<ProviderModelEntity>

    @Query("DELETE FROM provider_models WHERE providerId = :providerId AND discovered = 1")
    suspend fun deleteDiscoveredProviderModels(providerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    fun observeSessions(workspaceId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, updatedAt DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, updatedAt DESC")
    suspend fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun session(sessionId: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE sessions SET title = :title, titleSource = 'MANUAL', autoTitleState = 'COMPLETE', updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET title = :title, titleSource = 'AUTO', autoTitleState = 'COMPLETE', updatedAt = :updatedAt WHERE id = :sessionId AND titleSource != 'MANUAL'")
    suspend fun applyAutoTitle(sessionId: String, title: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE sessions SET autoTitleState = :state, autoTitleAttempts = autoTitleAttempts + 1, updatedAt = :updatedAt WHERE id = :sessionId AND titleSource != 'MANUAL'")
    suspend fun updateAutoTitleAttempt(sessionId: String, state: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET providerId = :providerId, model = :model, reasoningConfigJson = :reasoningConfigJson, updatedAt = :updatedAt WHERE id = :sessionId AND state NOT IN ('RUNNING', 'WAITING_APPROVAL')")
    suspend fun updateSessionModel(sessionId: String, providerId: String, model: String, reasoningConfigJson: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE sessions SET titleSource = 'MANUAL', autoTitleState = 'COMPLETE' WHERE titleSource = 'INITIAL' AND autoTitleAttempts = 0 AND createdAt < :cutoff")
    suspend fun protectLegacySessionTitles(cutoff: Long): Int

    @Query("UPDATE sessions SET unread = :unread WHERE id = :sessionId")
    suspend fun setSessionUnread(sessionId: String, unread: Boolean)

    @Query("UPDATE sessions SET state = :state, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionState(sessionId: String, state: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET queueState = :queueState, progressText = :progressText, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionQueue(sessionId: String, queueState: String, progressText: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET latestPreview = :preview, unread = :unread, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionPreview(sessionId: String, preview: String, unread: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :sessionId")
    suspend fun setSessionPinned(sessionId: String, pinned: Boolean)

    @Query("SELECT * FROM sessions WHERE state IN ('RUNNING', 'WAITING_APPROVAL') AND updatedAt < :cutoff ORDER BY updatedAt DESC")
    suspend fun unfinishedSessionsBefore(cutoff: Long): List<SessionEntity>

    @Insert
    suspend fun appendEvent(event: AgentEventEntity)

    @Query("SELECT * FROM agent_events WHERE sessionId = :sessionId ORDER BY sequence")
    fun observeEvents(sessionId: String): Flow<List<AgentEventEntity>>

    @Query("SELECT * FROM agent_events WHERE sessionId = :sessionId ORDER BY sequence")
    suspend fun events(sessionId: String): List<AgentEventEntity>

    @Query("SELECT COALESCE(MAX(sequence), -1) + 1 FROM agent_events WHERE sessionId = :sessionId")
    suspend fun nextSequence(sessionId: String): Long

    @Query("SELECT MAX(sequence) FROM agent_events WHERE sessionId = :sessionId AND type = 'UserMessage'")
    suspend fun lastUserMessageSequence(sessionId: String): Long?

    @Query("SELECT sequence FROM agent_events WHERE sessionId = :sessionId AND type = 'UserMessage' ORDER BY sequence LIMIT 1 OFFSET :turnIndex")
    suspend fun userMessageSequence(sessionId: String, turnIndex: Int): Long?

    @Query("DELETE FROM agent_events WHERE sessionId = :sessionId AND sequence >= :sequence")
    suspend fun deleteEventsFrom(sessionId: String, sequence: Long)

    @Query("DELETE FROM task_checkpoints WHERE sessionId = :sessionId")
    suspend fun deleteCheckpoint(sessionId: String)

    @Query("UPDATE sessions SET state = 'IDLE', queueState = 'READY', progressText = '', latestPreview = '', updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun markSessionRewound(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun rewindLastTurn(sessionId: String): Boolean {
        val sequence = lastUserMessageSequence(sessionId) ?: return false
        deleteEventsFrom(sessionId, sequence)
        deleteCheckpoint(sessionId)
        markSessionRewound(sessionId)
        return true
    }

    @Transaction
    suspend fun rewindFromTurn(sessionId: String, turnIndex: Int): Boolean {
        val sequence = userMessageSequence(sessionId, turnIndex) ?: return false
        deleteEventsFrom(sessionId, sequence)
        deleteCheckpoint(sessionId)
        markSessionRewound(sessionId)
        return true
    }

    @Transaction
    suspend fun appendEvent(sessionId: String, type: String, payloadJson: String) {
        appendEvent(AgentEventEntity(sessionId = sessionId, sequence = nextSequence(sessionId), type = type, payloadJson = payloadJson))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtension(extension: ExtensionEntity)

    @Query("SELECT * FROM extensions ORDER BY kind, name")
    fun observeExtensions(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions ORDER BY kind, name")
    suspend fun extensions(): List<ExtensionEntity>

    @Query("DELETE FROM extensions WHERE id = :extensionId")
    suspend fun deleteExtension(extensionId: String)

    @Query("UPDATE extensions SET enabled = :enabled WHERE id = :extensionId")
    suspend fun setExtensionEnabled(extensionId: String, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: TaskCheckpointEntity)

    @Query("SELECT * FROM task_checkpoints WHERE sessionId = :sessionId LIMIT 1")
    suspend fun checkpoint(sessionId: String): TaskCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExtensionSource(source: ExtensionSourceEntity)

    @Query("SELECT * FROM extension_sources WHERE enabled = 1 ORDER BY displayName")
    fun observeExtensionSources(): Flow<List<ExtensionSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMcpServer(server: McpServerEntity)

    @Query("SELECT * FROM mcp_servers ORDER BY displayName")
    fun observeMcpServers(): Flow<List<McpServerEntity>>

    @Query("DELETE FROM mcp_servers WHERE id = :serverId")
    suspend fun deleteMcpServer(serverId: String)

    @Query("UPDATE mcp_servers SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :serverId")
    suspend fun setMcpServerEnabled(serverId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHookConfig(hook: HookConfigEntity)

    @Query("SELECT * FROM hook_configs ORDER BY displayName")
    fun observeHookConfigs(): Flow<List<HookConfigEntity>>

    @Query("DELETE FROM hook_configs WHERE id = :hookId")
    suspend fun deleteHookConfig(hookId: String)

    @Query("UPDATE hook_configs SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :hookId")
    suspend fun setHookEnabled(hookId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDesktopPairing(pairing: DesktopPairingEntity)

    @Query("SELECT * FROM desktop_pairings WHERE enabled = 1 ORDER BY lastConnectedAt DESC, createdAt DESC")
    fun observeDesktopPairings(): Flow<List<DesktopPairingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRuntimePackage(runtimePackage: RuntimePackageEntity)

    @Query("SELECT * FROM runtime_packages ORDER BY id")
    fun observeRuntimePackages(): Flow<List<RuntimePackageEntity>>
}
