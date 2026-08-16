package com.phoneagent.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class GitHubDeviceAuthNotifier(private val context: Context) {
    fun show(code: String) {
        copyCode(context, code, showConfirmation = false)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val openGitHub = PendingIntent.getActivity(
            context,
            9202,
            Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_DEVICE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val copyCode = PendingIntent.getBroadcast(
            context,
            9203,
            Intent(context, GitHubDeviceAuthActionReceiver::class.java)
                .setAction(ACTION_COPY_CODE)
                .putExtra(EXTRA_CODE, code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PhoneAgentApplication.GITHUB_AUTH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_agent)
            .setContentTitle("GitHub 登录验证码：$code")
            .setContentText("验证码已复制，请在 GitHub 设备授权页粘贴")
            .setStyle(NotificationCompat.BigTextStyle().bigText("验证码 $code 已复制到剪贴板。在 GitHub 页面粘贴并确认授权后，sai 会自动完成登录。"))
            .setContentIntent(openGitHub)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setTimeoutAfter(10 * 60_000L)
            .addAction(0, "复制验证码", copyCode)
            .addAction(0, "打开 GitHub", openGitHub)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val GITHUB_DEVICE_URL = "https://github.com/login/device"
        const val ACTION_COPY_CODE = "com.phoneagent.app.action.COPY_GITHUB_DEVICE_CODE"
        const val EXTRA_CODE = "github_device_code"
        const val NOTIFICATION_ID = 9201

        internal fun copyCode(context: Context, code: String, showConfirmation: Boolean) {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("GitHub 登录验证码", code))
            if (showConfirmation) Toast.makeText(context, "已复制 GitHub 验证码 $code", Toast.LENGTH_SHORT).show()
        }
    }
}

class GitHubDeviceAuthActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GitHubDeviceAuthNotifier.ACTION_COPY_CODE) return
        val code = intent.getStringExtra(GitHubDeviceAuthNotifier.EXTRA_CODE)?.takeIf(String::isNotBlank) ?: return
        GitHubDeviceAuthNotifier.copyCode(context, code, showConfirmation = true)
    }
}
