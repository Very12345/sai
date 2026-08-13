package com.phoneagent.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phoneagent.app.MainActivity
import com.phoneagent.app.PhoneAgentApplication
import com.phoneagent.app.R
import com.phoneagent.dsh.DshRuntimePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the sole DSH engine and the optional desktop link visible in background. */
class AgentForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var desktopConnectionMode = false
    private var dshMode = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as PhoneAgentApplication).container
        scope.launch {
            container.dshRuntime.state.collectLatest { dsh ->
                if (!dshMode) return@collectLatest
                when (dsh.phase) {
                    DshRuntimePhase.INSTALLING, DshRuntimePhase.STARTING, DshRuntimePhase.READY ->
                        startForeground(NOTIFICATION_ID, notification("sai · DeepSeek Harness", dsh.detail))
                    DshRuntimePhase.FAILED ->
                        startForeground(NOTIFICATION_ID, notification("sai · DSH 需要处理", dsh.detail))
                    else -> Unit
                }
            }
        }
        scope.launch {
            container.dshBridge.taskStatuses.collectLatest { tasks ->
                if (!dshMode || tasks.isEmpty()) return@collectLatest
                val latest = tasks.values.maxByOrNull { it.updatedAt }
                val waiting = tasks.values.count { it.phase == "waiting-approval" }
                val summary = buildString {
                    append("${tasks.size} 个 DSH 任务")
                    if (waiting > 0) append(" · $waiting 个等待审批")
                    latest?.detail?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                }
                startForeground(NOTIFICATION_ID, notification("sai · DeepSeek Harness", summary))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as PhoneAgentApplication).container
        if (intent?.action == ACTION_STOP_DESKTOP) {
            container.desktopConnection.disconnectFromService()
            desktopConnectionMode = false
            if (!dshMode) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        desktopConnectionMode = intent?.action == ACTION_DESKTOP || desktopConnectionMode
        dshMode = intent?.action == ACTION_DSH || dshMode
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                runCatching { container.dshApi.cancelAll() }
                container.dshRuntime.stop()
            }
            dshMode = false
            if (!desktopConnectionMode) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID,
            notification(
                if (desktopConnectionMode && !dshMode) "sai 电脑已连接" else "sai · DeepSeek Harness",
                if (desktopConnectionMode && !dshMode) "加密局域网文件与对话连接正在运行"
                else "任务会在离开界面后继续；点击查看进度",
            ),
        )
        return if (dshMode) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(title: String, text: String) = NotificationCompat.Builder(
        this,
        PhoneAgentApplication.AGENT_CHANNEL_ID,
    )
        .setSmallIcon(R.drawable.ic_phone_agent)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(
            PendingIntent.getActivity(
                this, 1, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .addAction(
            0,
            if (desktopConnectionMode && !dshMode) "断开" else "停止全部",
            PendingIntent.getService(
                this,
                2,
                Intent(this, AgentForegroundService::class.java)
                    .setAction(if (desktopConnectionMode && !dshMode) ACTION_STOP_DESKTOP else ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    companion object {
        const val ACTION_STOP = "com.phoneagent.app.action.STOP_AGENT"
        const val ACTION_DESKTOP = "com.phoneagent.app.action.DESKTOP_CONNECTED"
        const val ACTION_DSH = "com.phoneagent.app.action.DSH_RUNTIME"
        const val ACTION_STOP_DESKTOP = "com.phoneagent.app.action.DESKTOP_DISCONNECTED"
        const val NOTIFICATION_ID = 1001
    }
}
