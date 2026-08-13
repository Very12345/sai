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
    ],
    version = 4,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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
    }
}
