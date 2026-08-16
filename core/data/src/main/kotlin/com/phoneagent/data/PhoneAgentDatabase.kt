package com.phoneagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        ProviderEntity::class,
        ProviderModelEntity::class,
        SessionEntity::class,
        AgentEventEntity::class,
        ExtensionEntity::class,
        TaskCheckpointEntity::class,
        AttachmentEntity::class,
        ExtensionSourceEntity::class,
        McpServerEntity::class,
        HookConfigEntity::class,
        DesktopPairingEntity::class,
        RuntimePackageEntity::class,
        TerminalTabEntity::class,
        HarnessRuntimeEntity::class,
        HarnessSessionBindingEntity::class,
        HarnessDefaultConfigEntity::class,
        TrashEntryEntity::class,
        ManagerTaskLinkEntity::class,
        ManagerTriggerEntity::class,
        PetOverlayStateEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class PhoneAgentDatabase : RoomDatabase() {
    abstract fun dao(): PhoneAgentDao

    companion object {
        @Volatile private var instance: PhoneAgentDatabase? = null

        fun get(context: Context): PhoneAgentDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PhoneAgentDatabase::class.java,
                "phoneagent.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workspaces ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workspaces ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN queueState TEXT NOT NULL DEFAULT 'READY'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN progressText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN latestPreview TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN unread INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN worktreePath TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN branchName TEXT")
                db.execSQL("ALTER TABLE extensions ADD COLUMN version TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE extensions ADD COLUMN sourceDigest TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE extensions ADD COLUMN previousManifestJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_checkpoints (sessionId TEXT NOT NULL, sequence INTEGER NOT NULL, phase TEXT NOT NULL, payloadJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(sessionId), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_checkpoints_sessionId ON task_checkpoints(sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id TEXT NOT NULL, sessionId TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, localPath TEXT NOT NULL, sizeBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_sessionId ON attachments(sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS extension_sources (id TEXT NOT NULL, kind TEXT NOT NULL, displayName TEXT NOT NULL, endpoint TEXT NOT NULL, enabled INTEGER NOT NULL, metadataJson TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS mcp_servers (id TEXT NOT NULL, workspaceId TEXT, displayName TEXT NOT NULL, configJson TEXT NOT NULL, enabled INTEGER NOT NULL, lastStatus TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS runtime_packages (id TEXT NOT NULL, packagesJson TEXT NOT NULL, installedByPhoneAgent INTEGER NOT NULL, installedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS provider_models (id TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, displayName TEXT NOT NULL, capabilitiesJson TEXT NOT NULL, pricingJson TEXT NOT NULL, contextWindow INTEGER NOT NULL, reasoningEffortsJson TEXT NOT NULL, discovered INTEGER NOT NULL, enabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(providerId) REFERENCES providers(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_provider_models_providerId ON provider_models(providerId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_provider_models_providerId_modelId ON provider_models(providerId, modelId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS hook_configs (id TEXT NOT NULL, workspaceId TEXT, displayName TEXT NOT NULL, event TEXT NOT NULL, configJson TEXT NOT NULL, enabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hook_configs_workspaceId ON hook_configs(workspaceId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS desktop_pairings (id TEXT NOT NULL, displayName TEXT NOT NULL, publicKey TEXT NOT NULL, endpoint TEXT NOT NULL, scopesJson TEXT NOT NULL, enabled INTEGER NOT NULL, lastConnectedAt INTEGER, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN titleSource TEXT NOT NULL DEFAULT 'INITIAL'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN autoTitleState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN autoTitleAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN reasoningConfigJson TEXT NOT NULL DEFAULT '{\"mode\":\"AUTO\",\"effort\":null,\"budgetTokens\":null}'")
                // v3 did not record whether a title was manually renamed. Preserve every legacy title.
                db.execSQL("UPDATE sessions SET titleSource = 'MANUAL', autoTitleState = 'COMPLETE'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN visionProviderId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN visionModelId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN visionSelectionSource TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL("ALTER TABLE extensions ADD COLUMN installState TEXT NOT NULL DEFAULT 'INSTALLED'")
                db.execSQL("ALTER TABLE extensions ADD COLUMN profileId TEXT")
                db.execSQL("ALTER TABLE extensions ADD COLUMN rollbackVersion TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS terminal_tabs (id TEXT NOT NULL, workspaceId TEXT NOT NULL, title TEXT NOT NULL, cwd TEXT NOT NULL, state TEXT NOT NULL, lastActiveAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(workspaceId) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_terminal_tabs_workspaceId ON terminal_tabs(workspaceId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE extensions ADD COLUMN workspaceId TEXT")
                db.execSQL("ALTER TABLE extensions ADD COLUMN harnessKind TEXT")
                db.execSQL("ALTER TABLE extensions ADD COLUMN scope TEXT NOT NULL DEFAULT 'GLOBAL'")
                db.execSQL("ALTER TABLE extensions ADD COLUMN autoUpdate INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE extensions ADD COLUMN compatibilityRange TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE terminal_tabs ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE terminal_tabs ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE terminal_tabs SET createdAt = lastActiveAt WHERE createdAt = 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS harness_runtimes (harnessKind TEXT NOT NULL, version TEXT NOT NULL, installState TEXT NOT NULL, binaryPath TEXT, capabilitiesJson TEXT NOT NULL, previousVersion TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(harnessKind))")
                db.execSQL("CREATE TABLE IF NOT EXISTS harness_session_bindings (id TEXT NOT NULL, sessionId TEXT NOT NULL, workspaceId TEXT NOT NULL, harnessKind TEXT NOT NULL, externalSessionId TEXT NOT NULL, runtimeVersion TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_harness_session_bindings_sessionId ON harness_session_bindings(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_harness_session_bindings_workspaceId ON harness_session_bindings(workspaceId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_harness_session_bindings_harnessKind_externalSessionId ON harness_session_bindings(harnessKind, externalSessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS harness_default_configs (id TEXT NOT NULL, workspaceId TEXT, harnessKind TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, reasoningConfigJson TEXT NOT NULL, permissionMode TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_harness_default_configs_workspaceId ON harness_default_configs(workspaceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_harness_default_configs_harnessKind ON harness_default_configs(harnessKind)")
                db.execSQL("CREATE TABLE IF NOT EXISTS trash_entries (id TEXT NOT NULL, workspaceId TEXT NOT NULL, originalPath TEXT NOT NULL, trashPath TEXT NOT NULL, displayName TEXT NOT NULL, directory INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, deletedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(workspaceId) REFERENCES workspaces(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_workspaceId ON trash_entries(workspaceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_entries_deletedAt ON trash_entries(deletedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS manager_task_links (id TEXT NOT NULL, managerSessionId TEXT NOT NULL, targetSessionId TEXT NOT NULL, workspaceId TEXT NOT NULL, harnessKind TEXT NOT NULL, state TEXT NOT NULL, summary TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manager_task_links_managerSessionId ON manager_task_links(managerSessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manager_task_links_targetSessionId ON manager_task_links(targetSessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS manager_triggers (id TEXT NOT NULL, displayName TEXT NOT NULL, triggerType TEXT NOT NULL, configJson TEXT NOT NULL, actionJson TEXT NOT NULL, enabled INTEGER NOT NULL, pausedByExit INTEGER NOT NULL, lastRunAt INTEGER, nextRunAt INTEGER, lastResult TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manager_triggers_enabled ON manager_triggers(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_manager_triggers_nextRunAt ON manager_triggers(nextRunAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS pet_overlay_state (id TEXT NOT NULL, xFraction REAL NOT NULL, yFraction REAL NOT NULL, docked INTEGER NOT NULL, keepAlive INTEGER NOT NULL, radialMenuEnabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}
