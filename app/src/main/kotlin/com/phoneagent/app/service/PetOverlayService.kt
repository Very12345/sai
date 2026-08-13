package com.phoneagent.app.service

import android.animation.ValueAnimator
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat
import com.phoneagent.app.MainActivity
import com.phoneagent.app.PhoneAgentApplication
import com.phoneagent.app.R
import com.phoneagent.app.VoiceStartActivity
import com.phoneagent.app.VoiceConversationController
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Compact transparent companion used only when sai is detached above other apps. */
class PetOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var petView: SailRobotView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        scope.launch {
            (application as PhoneAgentApplication).container.dshBridge.taskStatuses.collectLatest { active ->
                petView?.taskCount = active.size
                petView?.waitingApproval = active.values.any { it.phase == "waiting-approval" }
                petView?.statusText = active.values.maxByOrNull { it.updatedAt }?.detail.orEmpty()
            }
        }
        scope.launch {
            VoiceConversationController.state.collectLatest { state ->
                petView?.voiceActive = state.active
                petView?.statusText = state.transcript.takeIf { state.active && it.isNotBlank() } ?: petView?.statusText.orEmpty()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val preferences = getSharedPreferences("sai-ui", 0)
        if (intent?.action == ACTION_HIDE) {
            preferences.edit().putBoolean("system_pet_enabled", false).putBoolean("task_pet_visible", true).putBoolean("task_pet_minimized", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        preferences.edit().putBoolean("system_pet_enabled", true).putBoolean("task_pet_visible", false).putBoolean("task_pet_minimized", false).apply()
        petView?.theme = preferences.getString("app_theme", preferences.getString("pet_theme", "aurora")).orEmpty()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, PhoneAgentApplication.AGENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone_agent)
                .setContentTitle("sai 任务机器人")
                .setContentText("透明悬浮帆船显示任务；点击麦克风可开始语音通话")
                .setOngoing(true)
                .setContentIntent(openSaiIntent(41))
                .addAction(
                    0,
                    "收回",
                    PendingIntent.getService(
                        this,
                        42,
                        Intent(this, PetOverlayService::class.java).setAction(ACTION_HIDE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build(),
        )
        showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (petView != null) return
        val density = resources.displayMetrics.density
        val fullWidth = (76 * density).toInt()
        val fullHeight = (72 * density).toInt()
        val minimizedWidth = (52 * density).toInt()
        val minimizedHeight = (46 * density).toInt()
        val saved = getSharedPreferences("sai-ui", 0)
        saved.edit().putBoolean("system_pet_minimized", false).apply()
        val initiallyMinimized = false
        var restoredX = saved.getInt("system_pet_x", resources.displayMetrics.widthPixels - fullWidth)
        var restoredY = saved.getInt("system_pet_y", (90 * density).toInt())
        val layout = WindowManager.LayoutParams(
            if (initiallyMinimized) minimizedWidth else fullWidth,
            if (initiallyMinimized) minimizedHeight else fullHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (initiallyMinimized) resources.displayMetrics.widthPixels - minimizedWidth else restoredX
            y = if (initiallyMinimized) (72 * density).toInt() else restoredY
        }
        val view = SailRobotView(
            this,
            theme = saved.getString("app_theme", saved.getString("pet_theme", "aurora")).orEmpty(),
            onMove = { dx, dy ->
                layout.x = (layout.x + dx).coerceIn(0, (resources.displayMetrics.widthPixels - layout.width).coerceAtLeast(0))
                layout.y = (layout.y + dy).coerceIn(0, (resources.displayMetrics.heightPixels - layout.height).coerceAtLeast(0))
                windowManager.updateViewLayout(petView, layout)
            },
            onOpen = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
            onVoice = {
                startActivity(
                    Intent(this, VoiceStartActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onPositionSaved = {
                if (petView?.minimized != true) {
                    restoredX = layout.x
                    restoredY = layout.y
                    saved.edit().putInt("system_pet_x", restoredX).putInt("system_pet_y", restoredY).apply()
                }
            },
            onMinimize = {
                saved.edit()
                    .putBoolean("system_pet_enabled", false)
                    .putBoolean("system_pet_minimized", false)
                    .putBoolean("task_pet_visible", true)
                    .apply()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                stopSelf()
            },
            onRestore = {
                saved.edit().putBoolean("system_pet_minimized", false).apply()
                petView?.minimized = false
                layout.width = fullWidth
                layout.height = fullHeight
                layout.x = restoredX.coerceIn(0, (resources.displayMetrics.widthPixels - fullWidth).coerceAtLeast(0))
                layout.y = restoredY.coerceIn(0, (resources.displayMetrics.heightPixels - fullHeight).coerceAtLeast(0))
                windowManager.updateViewLayout(petView, layout)
            },
        )
        view.minimized = initiallyMinimized
        petView = view
        windowManager.addView(view, layout)
    }

    private fun openSaiIntent(requestCode: Int) = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        petView?.let { runCatching { windowManager.removeView(it) } }
        petView = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.phoneagent.app.action.SHOW_PET"
        const val ACTION_HIDE = "com.phoneagent.app.action.HIDE_PET"
        private const val NOTIFICATION_ID = 1101
    }
}

private class SailRobotView(
    context: android.content.Context,
    theme: String,
    private val onMove: (Int, Int) -> Unit,
    private val onOpen: () -> Unit,
    private val onVoice: () -> Unit,
    private val onPositionSaved: () -> Unit,
    private val onMinimize: () -> Unit,
    private val onRestore: () -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var phase = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    var theme: String = theme
        set(value) { field = value; invalidate() }
    var minimized: Boolean = false
        set(value) { field = value; invalidate() }
    var taskCount: Int = 0
        set(value) { field = value; invalidate() }
    var waitingApproval: Boolean = false
        set(value) { field = value; invalidate() }
    var statusText: String = ""
        set(value) { field = value; invalidate() }
    var voiceActive: Boolean = false
        set(value) { field = value; invalidate() }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1_100
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = themePalette(theme)
        if (minimized) {
            canvas.save()
            canvas.rotate(18f, width / 2f, height / 2f)
            val miniScale = minOf(width / 52f, height / 46f)
            canvas.scale(miniScale, miniScale)
            paint.style = Paint.Style.FILL
            paint.color = colors[3]
            path.reset(); path.moveTo(4f, 32f); path.quadTo(27f, 40f, 49f, 31f); path.quadTo(42f, 43f, 15f, 42f); path.close(); canvas.drawPath(path, paint)
            paint.color = Color.rgb(255, 218, 73); canvas.drawRect(23f, 4f, 25f, 34f, paint)
            paint.color = colors[0]
            path.reset(); path.moveTo(26f, 5f); path.lineTo(26f, 31f); path.lineTo(47f, 31f); path.quadTo(39f, 12f, 26f, 5f); path.close(); canvas.drawPath(path, paint)
            paint.color = colors[2]; path.reset(); path.moveTo(21f, 13f); path.lineTo(21f, 30f); path.lineTo(8f, 29f); path.quadTo(13f, 18f, 21f, 13f); path.close(); canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL; paint.color = colors[4]; canvas.drawRoundRect(RectF(14f, 24f, 24f, 33f), 2f, 2f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.7f; paint.color = Color.rgb(255, 216, 77)
            canvas.drawRoundRect(RectF(14f, 24f, 24f, 33f), 2f, 2f, paint)
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(54, 201, 230)
            canvas.drawOval(RectF(3f, 40f, 48f, 43f), paint)
            canvas.restore()
            return
        }
        val scale = width / 72f
        val wave = sin(phase * Math.PI * 2).toFloat()
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(if (taskCount > 0 && !waitingApproval) wave * 2.4f else 0f, wave * if (voiceActive) 2.8f else 1.4f)
        canvas.rotate(
            when {
                voiceActive -> wave * 6.5f
                waitingApproval -> wave * 1.2f
                taskCount > 0 -> wave * 4f
                else -> wave * 2f
            },
            36f,
            55f,
        )

        // Animated splashes; the window itself has no card, circle, or opaque background.
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((145 + phase * 90).toInt(), 54, 201, 230)
        canvas.drawOval(RectF(8f - phase * 2f, 59f, 30f, 62f), paint)
        canvas.drawOval(RectF(42f, 58f, 67f + phase * 2f, 61f), paint)
        canvas.drawCircle(64f, 53f - phase * 4f, 1.8f, paint)
        canvas.drawCircle(6f, 55f - phase * 3f, 1.4f, paint)
        repeat(8) { index ->
            val seed = index / 8f
            val t = (phase + seed) % 1f
            val x = 5f + seed * 62f + (t - .5f) * (if (index % 2 == 0) 8f else -7f)
            val y = 60f - sin(t * Math.PI).toFloat() * (3f + index % 3)
            paint.alpha = (210 * (1f - t)).toInt().coerceIn(35, 210)
            canvas.drawCircle(x, y, 0.8f + (index % 3) * .45f, paint)
        }
        paint.alpha = 255
        if (voiceActive) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            paint.color = Color.argb((90 + phase * 120).toInt(), 42, 139, 255)
            canvas.drawCircle(56f, 54f, 16f + phase * 2f, paint)
            paint.style = Paint.Style.FILL
        }

        // Long single-direction hull.
        path.reset()
        path.moveTo(5f, 49f); path.quadTo(36f, 57f, 68f, 47f)
        path.quadTo(59f, 63f, 26f, 61f); path.quadTo(10f, 59f, 5f, 49f); path.close()
        paint.color = colors[3]; canvas.drawPath(path, paint)
        paint.color = Color.rgb(255, 218, 73); canvas.drawRect(28f, 6f, 31f, 52f, paint)

        // Flowing asymmetric sail with high-contrast color blocks.
        canvas.save()
        if (waitingApproval) canvas.scale(.72f + phase * .18f, 1f, 31f, 46f)
        path.reset(); path.moveTo(32f, 8f); path.lineTo(32f, 46f); path.lineTo(66f, 47f)
        path.quadTo(53f, 19f, 32f, 8f); path.close()
        paint.color = colors[0]; canvas.drawPath(path, paint)
        path.reset(); path.moveTo(34f, 12f); path.lineTo(35f, 31f); path.lineTo(57f, 40f)
        path.quadTo(49f, 20f, 34f, 12f); path.close()
        paint.color = colors[1]; paint.alpha = 205; canvas.drawPath(path, paint); paint.alpha = 255
        path.reset(); path.moveTo(26f, 18f); path.quadTo(15f, 27f, 11f, 43f); path.quadTo(18f, 39f, 26f, 41f); path.close()
        paint.color = colors[2]; canvas.drawPath(path, paint)
        canvas.restore()

        // Hollow robot on deck. Running task count lives on the robot, not in a separate badge.
        paint.style = Paint.Style.FILL; paint.color = colors[4]
        canvas.drawRoundRect(RectF(18f, 31f, 31f, 43f), 3f, 3f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = Color.rgb(255, 216, 77)
        canvas.drawRoundRect(RectF(18f, 31f, 31f, 43f), 3f, 3f, paint)
        canvas.drawLine(20f, 43f, 18f, 48f, paint); canvas.drawLine(29f, 43f, 32f, 47f, paint)
        canvas.drawLine(18f, 37f, 13f, 33f - wave * 4f, paint); canvas.drawLine(31f, 37f, 36f, 33f + wave * 4f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE; canvas.drawCircle(22f, 35f, 1.2f, paint); canvas.drawCircle(27f, 35f, 1.2f, paint)
        if (taskCount > 0) {
            paint.color = Color.rgb(255, 224, 91); paint.textAlign = Paint.Align.CENTER; paint.textSize = 7.5f
            canvas.drawText(taskCount.coerceAtMost(9).toString(), 24.5f, 41.5f, paint)
        }

        // Microphone is attached to the pet and remains the only small control surface.
        paint.color = colors[1]; canvas.drawCircle(56f, 54f, 15f, paint)
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.7f
        canvas.drawRoundRect(RectF(52f, 46.5f, 60f, 56.5f), 4f, 4f, paint)
        canvas.drawArc(RectF(49.5f, 51f, 62.5f, 61f), 0f, 180f, false, paint)
        canvas.drawLine(56f, 61f, 56f, 65f, paint)
        paint.style = Paint.Style.FILL

        val activity = when {
            voiceActive && statusText.isNotBlank() -> "听：${statusText.takeLast(8)}"
            voiceActive -> "冲浪倾听"
            waitingApproval -> "收帆待批"
            taskCount > 0 -> "划船 · ${statusText.ifBlank { "执行任务" }.take(8)}"
            else -> "扬帆巡航"
        }
        paint.color = Color.argb(220, 16, 24, 44)
        canvas.drawRoundRect(RectF(1.5f, 0.5f, 70.5f, 11.5f), 5f, 5f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 5.6f
        canvas.drawText(activity, 36f, 8.2f, paint)

        canvas.restore()

        // Fixed control is outside the bobbing transform, keeping the entire 44dp target visible.
        val closeRadius = minOf(width, height) * .105f
        val closeX = width - closeRadius - 1f
        val closeY = closeRadius + 1f
        paint.color = Color.argb(238, 17, 21, 38); paint.style = Paint.Style.FILL
        canvas.drawCircle(closeX, closeY, closeRadius, paint)
        paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 1.5f
        canvas.drawLine(closeX - closeRadius * .4f, closeY - closeRadius * .4f, closeX + closeRadius * .4f, closeY + closeRadius * .4f, paint)
        canvas.drawLine(closeX + closeRadius * .4f, closeY - closeRadius * .4f, closeX - closeRadius * .4f, closeY + closeRadius * .4f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; lastX = downX; lastY = downY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (minimized && (abs(event.rawX - downX) > 8 || abs(event.rawY - downY) > 8)) {
                    onRestore()
                    return true
                }
                val dx = (event.rawX - lastX).toInt(); val dy = (event.rawY - lastY).toInt()
                if (dx != 0 || dy != 0) onMove(dx, dy)
                lastX = event.rawX; lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (minimized) {
                    onRestore()
                    return true
                }
                onPositionSaved()
                if (abs(event.rawX - downX) < 12 && abs(event.rawY - downY) < 12) {
                    if (event.x > width * .72f && event.y < height * .32f) onMinimize()
                    else if (event.x > width * .36f && event.y > height * .32f) onVoice()
                    else onOpen()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

private fun themePalette(theme: String): IntArray = when (theme.lowercase()) {
    "ocean" -> intArrayOf(
        Color.rgb(0, 119, 255), Color.rgb(0, 219, 222), Color.rgb(122, 92, 255),
        Color.rgb(13, 44, 83), Color.rgb(255, 74, 149),
    )
    "sunset" -> intArrayOf(
        Color.rgb(255, 78, 80), Color.rgb(255, 177, 66), Color.rgb(177, 66, 255),
        Color.rgb(84, 31, 56), Color.rgb(35, 211, 255),
    )
    "forest" -> intArrayOf(
        Color.rgb(0, 176, 116), Color.rgb(159, 230, 79), Color.rgb(19, 121, 92),
        Color.rgb(22, 62, 54), Color.rgb(255, 87, 143),
    )
    else -> intArrayOf(
        Color.rgb(55, 87, 255), Color.rgb(0, 224, 213), Color.rgb(255, 49, 147),
        Color.rgb(29, 26, 78), Color.rgb(255, 96, 67),
    )
}
