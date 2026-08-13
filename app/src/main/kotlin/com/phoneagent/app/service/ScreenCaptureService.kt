package com.phoneagent.app.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.phoneagent.app.MainActivity
import com.phoneagent.app.PhoneAgentApplication
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.parcelableIntent(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopCapture()
            return START_NOT_STICKY
        }
        runCatching { capture(resultCode, data) }.onFailure {
            results.tryEmit(Result.failure(it))
            stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun capture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = requireNotNull(manager.getMediaProjection(resultCode, data)) { "系统未创建屏幕捕获会话" }.also { mediaProjection ->
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCapture() }
            }, mainExecutorHandler())
        }
        val metrics = resources.displayMetrics
        val window = getSystemService(WindowManager::class.java)
        val bounds = if (Build.VERSION.SDK_INT >= 30) window.currentWindowMetrics.bounds else null
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { imageReader ->
            imageReader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                runCatching {
                    image.use {
                        val plane = it.planes[0]
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * width
                        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
                        padded.recycle()
                        val output = File(filesDir, "captures/${System.currentTimeMillis()}.png").apply { parentFile?.mkdirs() }
                        FileOutputStream(output).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                        bitmap.recycle()
                        output
                    }
                }.also(results::tryEmit)
                stopCapture()
            }, mainExecutorHandler())
            projection?.createVirtualDisplay(
                "PhoneAgentCapture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null,
            )
        }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 31, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 32, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, PhoneAgentApplication.AGENT_CHANNEL_ID)
            .setSmallIcon(com.phoneagent.app.R.drawable.ic_phone_agent)
            .setContentTitle("sai 正在捕获屏幕")
            .setContentText("系统授权仅用于本次按需截图")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "停止", stop)
            .build()
    }

    private fun stopCapture() {
        reader?.close()
        reader = null
        val activeProjection = projection
        projection = null
        activeProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun mainExecutorHandler() = android.os.Handler(mainLooper)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { reader?.close(); reader = null; projection = null; super.onDestroy() }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableIntent(key: String): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Intent::class.java)
    } else getParcelableExtra<Parcelable>(key) as? Intent

    companion object {
        const val ACTION_STOP = "com.phoneagent.app.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val NOTIFICATION_ID = 1002
        private val results = MutableSharedFlow<Result<File>>(extraBufferCapacity = 2)
        val captureResults = results.asSharedFlow()
    }
}
