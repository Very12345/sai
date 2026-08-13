package com.phoneagent.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.phoneagent.app.service.GoalRecoveryWorker
import com.phoneagent.data.EncryptedSecretStore
import com.phoneagent.data.PhoneAgentDatabase
import com.phoneagent.runtime.ProcessLinuxRuntime
import com.phoneagent.runtime.ProotCommandBuilder
import com.phoneagent.runtime.ProotConfig
import com.phoneagent.runtime.UnavailableLinuxRuntime
import com.phoneagent.runtime.NativeRuntimeAssets
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneAgentApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val startupEpochMillis = System.currentTimeMillis()
        val workspace = File(filesDir, "workspaces/default").apply { mkdirs() }
        val rootfs = File(filesDir, "runtime/debian")
        val nativeAssets = NativeRuntimeAssets.prepare(this)
        val runtime = if (nativeAssets != null) {
            val temporary = File(cacheDir, "runtime-tmp").apply { mkdirs() }
            ProcessLinuxRuntime(
                ProotCommandBuilder(ProotConfig(
                    executable = nativeAssets.proot,
                    rootfs = rootfs,
                    home = workspace,
                    tmp = temporary,
                    systemLinker = File("/system/bin/linker64"),
                    loader = nativeAssets.loader,
                    nativeLibraryDirectory = nativeAssets.libraryDirectory,
                )),
                rootfs,
            )
        } else {
            UnavailableLinuxRuntime("This build does not contain the ABI-matched native PRoot runtime.")
        }
        container = AppContainer(
            application = this,
            database = PhoneAgentDatabase.get(this),
            secretStore = EncryptedSecretStore(this),
            runtime = runtime,
            workspace = workspace,
        )
        val migrationPreferences = getSharedPreferences("sai-data-migrations", MODE_PRIVATE)
        if (!migrationPreferences.getBoolean("protected_legacy_v3_titles", false)) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                container.database.dao().protectLegacySessionTitles(startupEpochMillis)
                migrationPreferences.edit().putBoolean("protected_legacy_v3_titles", true).commit()
            }
        }
        WorkManager.getInstance(this).enqueueUniqueWork(
            GoalRecoveryWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GoalRecoveryWorker>()
                .setInputData(Data.Builder().putLong(GoalRecoveryWorker.CUTOFF_KEY, startupEpochMillis).build())
                .build(),
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    AGENT_CHANNEL_ID,
                    "sai Agent 任务",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "sai 本地长任务的可见运行状态" },
            )
        }
    }

    companion object {
        const val AGENT_CHANNEL_ID = "phoneagent-agent-runs"
    }
}
