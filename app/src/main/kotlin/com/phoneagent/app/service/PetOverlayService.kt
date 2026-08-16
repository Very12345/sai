package com.phoneagent.app.service

import android.animation.ValueAnimator
import android.app.PendingIntent
import android.app.Service
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import kotlinx.coroutines.withContext

/** Compact transparent companion used only when sai is detached above other apps. */
class PetOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var petView: SailRobotView? = null
    private var overlayRoot: FrameLayout? = null
    private var overlayLayout: WindowManager.LayoutParams? = null
    private val radialActions = mutableListOf<View>()

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
        scope.launch {
            (application as PhoneAgentApplication).container.dshBridge.taskEvents.collectLatest { event ->
                when (event.phase) {
                    "completed" -> showTransientEvent("任务完成", event.detail.ifBlank { "任务已完成" }, false)
                    "failed" -> showTransientEvent("任务失败", event.detail.ifBlank { "点击宠物查看详情" }, true)
                    "waiting-approval" -> petView?.apply {
                        waitingApproval = true
                        statusText = event.detail.ifBlank { "需要批准，点击查看" }
                    }
                }
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
                .setContentText("双击宠物可输入文字、开始语音、停止或收起")
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
        if (overlayRoot != null) return
        val density = resources.displayMetrics.density
        val rootSize = (196 * density).toInt()
        val petWidth = (84 * density).toInt()
        val petHeight = (80 * density).toInt()
        val saved = getSharedPreferences("sai-ui", 0)
        var restoredX = saved.getInt("system_pet_x", resources.displayMetrics.widthPixels - rootSize)
        var restoredY = saved.getInt("system_pet_y", (90 * density).toInt())
        val layout = WindowManager.LayoutParams(
            rootSize,
            rootSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = restoredX.coerceIn(0, (resources.displayMetrics.widthPixels - rootSize).coerceAtLeast(0))
            y = restoredY.coerceIn(0, (resources.displayMetrics.heightPixels - rootSize).coerceAtLeast(0))
        }
        overlayLayout = layout
        val root = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        val view = SailRobotView(
            context = this,
            theme = saved.getString("app_theme", saved.getString("pet_theme", "aurora")).orEmpty(),
            onMove = { dx, dy ->
                layout.x = (layout.x + dx).coerceIn(0, (resources.displayMetrics.widthPixels - rootSize).coerceAtLeast(0))
                layout.y = (layout.y + dy).coerceIn(0, (resources.displayMetrics.heightPixels - rootSize).coerceAtLeast(0))
                windowManager.updateViewLayout(root, layout)
            },
            onOpen = {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            },
            onVoice = {},
            onPositionSaved = {
                restoredX = layout.x
                restoredY = layout.y
                saved.edit().putInt("system_pet_x", restoredX).putInt("system_pet_y", restoredY).apply()
            },
            onMinimize = {
                saved.edit().putBoolean("system_pet_enabled", false).putBoolean("task_pet_visible", true).apply()
                stopSelf()
            },
            onRestore = {},
            radialMenuEnabled = true,
            onRadialToggle = { toggleRadialMenu() },
        ).apply {
            showCloseControl = false
            showVoiceControl = false
        }
        petView = view
        root.addView(view, FrameLayout.LayoutParams(petWidth, petHeight, Gravity.CENTER))
        radialActions += root.addRadialAction("停", 75, 4) { showExitConfirmation() }
        radialActions += root.addRadialAction("文", 4, 75) { showTextComposer() }
        radialActions += root.addRadialAction("声", 146, 75) {
            toggleRadialMenu(false)
            startActivity(Intent(this, VoiceStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        radialActions += root.addRadialAction("收", 75, 146) {
            toggleRadialMenu(false)
            saved.edit().putBoolean("system_pet_enabled", false).putBoolean("task_pet_visible", true).apply()
            stopSelf()
        }
        overlayRoot = root
        windowManager.addView(root, layout)
    }

    private fun FrameLayout.addRadialAction(label: String, leftDp: Int, topDp: Int, action: () -> Unit): View {
        val density = resources.displayMetrics.density
        return TextView(this@PetOverlayService).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(238, 24, 35, 65))
                setStroke((1.2f * density).toInt(), Color.argb(210, 66, 213, 255))
            }
            elevation = 8 * density
            visibility = View.GONE
            setOnClickListener { action() }
            this@addRadialAction.addView(this, FrameLayout.LayoutParams((46 * density).toInt(), (46 * density).toInt()).apply {
                leftMargin = (leftDp * density).toInt()
                topMargin = (topDp * density).toInt()
            })
        }
    }

    private fun toggleRadialMenu(show: Boolean? = null) {
        val visible = show ?: radialActions.none { it.visibility == View.VISIBLE }
        closePanel()
        radialActions.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
        if (visible) Handler(Looper.getMainLooper()).postDelayed({ toggleRadialMenu(false) }, 8_000)
    }

    private fun showTextComposer() {
        toggleRadialMenu(false)
        val root = overlayRoot ?: return
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            tag = PANEL_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.argb(246, 20, 28, 48))
                setStroke((1 * density).toInt(), Color.argb(180, 64, 208, 255))
            }
        }
        val input = EditText(this).apply {
            hint = "告诉 sai 总管要做什么"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 3
            imeOptions = EditorInfo.IME_ACTION_SEND
            setBackgroundColor(Color.TRANSPARENT)
        }
        val send = TextView(this).apply {
            text = "发送"
            setTextColor(Color.rgb(83, 220, 255))
            textSize = 13f
            gravity = Gravity.CENTER
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(send, LinearLayout.LayoutParams((48 * density).toInt(), (42 * density).toInt()))
        root.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        fun submit() {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) submitManagerCommand(text)
            closePanel()
        }
        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
        }
        val layout = overlayLayout ?: return
        layout.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        layout.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        windowManager.updateViewLayout(root, layout)
        input.requestFocus()
        input.post { getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun submitManagerCommand(text: String) {
        petView?.statusText = "正在部署任务"
        scope.launch {
            runCatching { (application as PhoneAgentApplication).container.managerHarness.dispatch(text) }
                .onSuccess { result -> showTransientEvent("已部署任务", "${result.projectName} · ${result.harnessKind}", false) }
                .onFailure { error -> showTransientEvent("部署失败", error.message ?: "无法创建任务", true) }
        }
    }

    private fun showExitConfirmation() {
        toggleRadialMenu(false)
        val root = overlayRoot ?: return
        val density = resources.displayMetrics.density
        val panel = LinearLayout(this).apply {
            tag = PANEL_TAG
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply { cornerRadius = 18 * density; setColor(Color.argb(248, 28, 30, 43)) }
        }
        panel.addView(TextView(this).apply {
            text = "停止全部任务并退出 sai？"
            setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
        })
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(TextView(this).apply {
            text = "取消"; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setOnClickListener { closePanel() }
        }, LinearLayout.LayoutParams((70 * density).toInt(), (42 * density).toInt()))
        actions.addView(TextView(this).apply {
            text = "退出"; setTextColor(Color.rgb(255, 105, 120)); gravity = Gravity.CENTER
            setOnClickListener { shutdownApplication() }
        }, LinearLayout.LayoutParams((70 * density).toInt(), (42 * density).toInt()))
        panel.addView(actions)
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun closePanel() {
        overlayRoot?.findViewWithTag<View>(PANEL_TAG)?.let { panel ->
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(panel.windowToken, 0)
            overlayRoot?.removeView(panel)
        }
        restoreOverlayFocus()
    }

    private fun restoreOverlayFocus() {
        val root = overlayRoot ?: return
        val layout = overlayLayout ?: return
        layout.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        runCatching { windowManager.updateViewLayout(root, layout) }
    }

    private fun shutdownApplication() {
        petView?.statusText = "正在安全退出"
        scope.launch {
            val app = application as PhoneAgentApplication
            withContext(Dispatchers.IO) {
                runCatching { app.container.database.dao().pauseTriggersForExit() }
                runCatching { app.container.dshApi.cancelAll() }
                runCatching { app.container.dshRuntime.stop() }
            }
            getSharedPreferences("sai-ui", 0).edit()
                .putBoolean("system_pet_enabled", false)
                .putBoolean("task_pet_visible", true)
                .apply()
            stopService(Intent(this@PetOverlayService, VoiceConversationService::class.java))
            startActivity(Intent(this@PetOverlayService, MainActivity::class.java).apply {
                action = MainActivity.ACTION_EXIT_APPLICATION
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            stopSelf()
        }
    }

    private fun showTransientEvent(title: String, detail: String, error: Boolean) {
        petView?.statusText = detail.take(48)
        getSystemService(NotificationManager::class.java).notify(
            (System.currentTimeMillis() and 0x7fffffff).toInt(),
            NotificationCompat.Builder(this, PhoneAgentApplication.AGENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone_agent)
                .setContentTitle(title)
                .setContentText(detail)
                .setAutoCancel(true)
                .setContentIntent(openSaiIntent(44))
                .build(),
        )
        if (!error) petView?.launchProgress = 1f
        Handler(Looper.getMainLooper()).postDelayed({ petView?.launchProgress = 0f }, 4_000)
    }

    @Suppress("unused")
    private fun showOverlayLegacy() {
        if (petView != null) return
        val density = resources.displayMetrics.density
        val fullWidth = (84 * density).toInt()
        val fullHeight = (80 * density).toInt()
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
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        if (overlayRoot == null) petView?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        overlayLayout = null
        radialActions.clear()
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
        private const val PANEL_TAG = "sai-overlay-panel"
    }
}

/**
 * The single visual implementation of sai's companion.
 *
 * It is deliberately shared by the in-app dock and the system overlay so a
 * detach transition never swaps the robot or the boat for another asset.
 */
class SailRobotView(
    context: android.content.Context,
    theme: String,
    private val onMove: (Int, Int) -> Unit,
    private val onOpen: () -> Unit,
    private val onVoice: () -> Unit,
    private val onPositionSaved: () -> Unit,
    private val onMinimize: () -> Unit,
    private val onRestore: () -> Unit,
    private val radialMenuEnabled: Boolean = false,
    private val onRadialToggle: () -> Unit = {},
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var phase = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTapAt = 0L
    private val tapHandler = Handler(Looper.getMainLooper())
    private val singleTap = Runnable(onOpen)
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
    var dormant: Boolean = false
        set(value) { field = value; invalidate() }
    var launchProgress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var showCloseControl: Boolean = true
        set(value) { field = value; invalidate() }
    var showVoiceControl: Boolean = true
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
        val launchWave = sin(launchProgress.coerceAtMost(1f) * Math.PI).toFloat()
        canvas.translate(
            if (taskCount > 0 && !waitingApproval) wave * 2.4f else launchWave * 2.2f,
            when {
                launchProgress > 0f -> -launchWave * 3.5f
                dormant -> wave * .35f
                voiceActive -> wave * 2.8f
                else -> wave * 1.4f
            },
        )
        canvas.rotate(
            when {
                voiceActive -> wave * 6.5f
                waitingApproval -> wave * 1.2f
                taskCount > 0 -> wave * 4f
                dormant -> wave * .35f
                else -> wave * 2f
            },
            36f,
            55f,
        )

        // Animated splashes; the window itself has no card, circle, or opaque background.
        paint.style = Paint.Style.FILL
        if (launchProgress > 0f) {
            path.reset()
            path.moveTo(-8f, 57f)
            path.cubicTo(4f, 35f, 18f, 68f, 33f, 48f)
            path.cubicTo(45f, 32f, 57f, 66f, 80f, 43f)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 2.8f + launchProgress * 3.4f
            paint.color = Color.argb((120 + launchProgress * 110).toInt(), 36, 188, 229)
            canvas.drawPath(path, paint)
            paint.strokeWidth = 1.5f + launchProgress * 1.8f
            paint.color = Color.argb((95 + launchProgress * 105).toInt(), 255, 255, 255)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }
        val launchBoost = (launchProgress * 95f).toInt()
        paint.color = Color.argb((145 + phase * 70 + launchBoost).toInt().coerceAtMost(255), 54, 201, 230)
        canvas.drawOval(RectF(8f - phase * 2f - launchProgress * 5f, 59f, 30f, 62f), paint)
        canvas.drawOval(RectF(42f, 58f, 67f + phase * 2f + launchProgress * 7f, 61f), paint)
        canvas.drawCircle(64f, 53f - phase * 4f, 1.8f, paint)
        canvas.drawCircle(6f, 55f - phase * 3f, 1.4f, paint)
        repeat(8) { index ->
            val seed = index / 8f
            val t = (phase + seed) % 1f
            val x = 5f + seed * 62f + (t - .5f) * (if (index % 2 == 0) 8f else -7f)
            val y = 60f - sin(t * Math.PI).toFloat() * (3f + index % 3 + launchProgress * 4f)
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

        if (showVoiceControl) {
            // The microphone is an application-external overlay shortcut only.
            paint.color = colors[1]; canvas.drawCircle(56f, 54f, 15f, paint)
            paint.color = Color.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.7f
            canvas.drawRoundRect(RectF(52f, 46.5f, 60f, 56.5f), 4f, 4f, paint)
            canvas.drawArc(RectF(49.5f, 51f, 62.5f, 61f), 0f, 180f, false, paint)
            canvas.drawLine(56f, 61f, 56f, 65f, paint)
            paint.style = Paint.Style.FILL
        }

        val activity = when {
            voiceActive && statusText.isNotBlank() -> "听：${statusText.takeLast(8)}"
            voiceActive -> "冲浪倾听"
            waitingApproval -> "收帆待批"
            taskCount > 0 -> "划船 · ${statusText.ifBlank { "执行任务" }.take(8)}"
            launchProgress > 0f -> "扬帆出海"
            dormant -> "泊岸休息"
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
        if (showCloseControl) {
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
                    if (radialMenuEnabled) {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastTapAt <= 360L) {
                            tapHandler.removeCallbacks(singleTap)
                            lastTapAt = 0L
                            onRadialToggle()
                        } else {
                            lastTapAt = now
                            tapHandler.postDelayed(singleTap, 370L)
                        }
                    } else if (showCloseControl && event.x > width * .72f && event.y < height * .32f) onMinimize()
                    else if (showVoiceControl && event.x > width * .36f && event.y > height * .32f) onVoice()
                    else onOpen()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        tapHandler.removeCallbacksAndMessages(null)
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
