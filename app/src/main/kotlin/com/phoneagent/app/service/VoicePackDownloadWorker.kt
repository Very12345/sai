package com.phoneagent.app.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.phoneagent.app.AppUpdateManager
import com.phoneagent.app.PhoneAgentApplication
import com.phoneagent.app.R
import com.phoneagent.app.VoiceModelPack

/** Persistent Voice Pack download; AppUpdateManager owns Range/ETag resume and verification. */
class VoicePackDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        setForeground(ForegroundInfo(NOTIFICATION_ID, notification("正在查询 GitHub Release…", 0, 0)))
        val application = applicationContext as PhoneAgentApplication
        val manager = AppUpdateManager(
            context = applicationContext,
            githubTokenProvider = { application.container.secretStore.get("github:github.com:token") },
        )
        return runCatching {
            val asset = manager.latestApkAsset(VoiceModelPack.ASSET_NAME)
                ?: error("当前 GitHub Releases 中没有可安装的 sai Voice Pack")
            setProgress(workDataOf(KEY_STAGE to "找到 ${asset.tag}，准备断点续传…"))
            val apk = manager.downloadModule(asset, VoiceModelPack.PACKAGE_NAME) { copied, total ->
                setProgressAsync(workDataOf(
                    KEY_STAGE to "正在下载语音模型包",
                    KEY_DOWNLOADED to copied,
                    KEY_TOTAL to total,
                ))
                applicationContext.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification("正在下载离线语音模型", copied, total))
            }
            workDataOf(KEY_APK_PATH to apk.absolutePath, KEY_STAGE to "校验完成")
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error -> Result.failure(workDataOf(KEY_ERROR to (error.message ?: "语音模型包下载失败"))) },
        )
    }

    private fun notification(stage: String, copied: Long, total: Long): Notification =
        NotificationCompat.Builder(applicationContext, PhoneAgentApplication.AGENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_agent)
            .setContentTitle("sai Voice Pack")
            .setContentText(stage)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, if (total > 0) ((copied * 100 / total).coerceIn(0, 100)).toInt() else 0, total <= 0)
            .build()

    companion object {
        const val UNIQUE_WORK = "sai-voice-pack-download-v2"
        const val KEY_STAGE = "stage"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_APK_PATH = "apk_path"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 5408
    }
}
