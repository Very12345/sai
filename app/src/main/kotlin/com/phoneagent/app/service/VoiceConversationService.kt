package com.phoneagent.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.phoneagent.app.MainActivity
import com.phoneagent.app.PhoneAgentApplication
import com.phoneagent.app.R
import com.phoneagent.app.SpeechTextSanitizer
import com.phoneagent.app.StreamingAsrManager
import com.phoneagent.app.LocalAsrManager
import com.phoneagent.app.VoiceAudioCapture
import com.phoneagent.app.VoiceConversationController
import com.phoneagent.app.VoiceConversationKind
import com.phoneagent.app.VoiceConversationState
import com.phoneagent.app.VoiceConversationPhase
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceConversationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val capture by lazy { VoiceAudioCapture(this) }
    private val streaming by lazy { StreamingAsrManager(this) }
    private val finalAsr by lazy { LocalAsrManager(this) }
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var greetingPending = false
    private var captureActive = false
    private var activeSessionId: String? = null
    private var activeTurnId: String? = null
    private var spokenTurnId: String? = null
    private var preparingListening = false
    private var requestedSessionId: String? = null
    private var requestedWorkspaceId: String? = null
    private var requestedProviderId: String? = null
    private var requestedModelId: String? = null
    private var playbackStartedAt = 0L
    private var playbackInterruptedByUser = false
    private var conversationKind = VoiceConversationKind.CALL
    @Volatile private var dshTurnInFlight = false
    @Volatile private var latestHeardText = ""

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.SIMPLIFIED_CHINESE
                ttsReady = true
                if (greetingPending) scope.launch { playGreeting() }
            } else {
                greetingPending = false
                scope.launch { beginListening() }
            }
        }
            .also { engine -> engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId == GREETING_UTTERANCE_ID || utteranceId == activeTurnId) resumeListeningAfterPlayback()
                    }
                }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {
                    scope.launch {
                        if (utteranceId == GREETING_UTTERANCE_ID || utteranceId == activeTurnId) resumeListeningAfterPlayback()
                    }
                }
            }) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE && !VoiceConversationController.state.value.active) {
            val preferences = getSharedPreferences("sai-ui", MODE_PRIVATE)
            requestedSessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                ?: preferences.getString("active_session_id", null)
            requestedWorkspaceId = intent.getStringExtra(EXTRA_WORKSPACE_ID)
                ?: preferences.getString("active_workspace_id", null)
            requestedProviderId = intent.getStringExtra(EXTRA_PROVIDER_ID)
                ?: preferences.getString("active_provider_id", null)
            requestedModelId = intent.getStringExtra(EXTRA_MODEL_ID)
                ?: preferences.getString("active_model_id", null)
        }
        when (intent?.action) {
            ACTION_SPEAK -> {
                dshTurnInFlight = false
                speak(intent.getStringExtra(EXTRA_SPEAK_TEXT).orEmpty(), activeTurnId)
                return START_STICKY
            }
            ACTION_STOP -> { stopConversation(); return START_NOT_STICKY }
            ACTION_INPUT_CANCEL -> { stopConversation(); return START_NOT_STICKY }
            ACTION_INPUT_TOGGLE -> {
                if (VoiceConversationController.state.value.active && conversationKind == VoiceConversationKind.INPUT) {
                    scope.launch { finishListening() }
                } else {
                    conversationKind = VoiceConversationKind.INPUT
                    startInput()
                }
                return START_STICKY
            }
            ACTION_MUTE -> VoiceConversationController.update { it.copy(muted = !it.muted) }
            ACTION_TOGGLE -> if (VoiceConversationController.state.value.active) stopConversation() else startConversation()
            else -> if (!VoiceConversationController.state.value.active) startConversation()
        }
        updateNotification()
        return START_STICKY
    }

    private fun startConversation() {
        conversationKind = VoiceConversationKind.CALL
        startForeground(NOTIFICATION_ID, notification())
        activeSessionId = requestedSessionId
        VoiceConversationController.update {
            it.copy(
                active = true,
                phase = VoiceConversationPhase.SPEAKING,
                transcript = "",
                sessionId = activeSessionId,
                kind = VoiceConversationKind.CALL,
            )
        }
        scope.launch { playGreeting() }
    }

    private fun startInput() {
        startForeground(NOTIFICATION_ID, notification())
        activeSessionId = requestedSessionId
        VoiceConversationController.update {
            VoiceConversationState(
                active = true,
                phase = VoiceConversationPhase.PREPARING,
                sessionId = activeSessionId,
                kind = VoiceConversationKind.INPUT,
                resultSequence = it.resultSequence,
            )
        }
        beginListening()
    }

    private fun playGreeting() {
        if (!VoiceConversationController.state.value.active) return
        if (!ttsReady) {
            greetingPending = true
            return
        }
        if (!captureActive) {
            beginListening(stopPlayback = false, onStarted = { playGreeting() })
            return
        }
        greetingPending = false
        playbackInterruptedByUser = false
        playbackStartedAt = SystemClock.elapsedRealtime()
        VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.SPEAKING) }
        val result = tts?.speak(GREETING_TEXT, TextToSpeech.QUEUE_FLUSH, null, GREETING_UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) beginListening()
    }

    private fun beginListening(stopPlayback: Boolean = true, onStarted: (() -> Unit)? = null) {
        if (!VoiceConversationController.state.value.active) return
        if (captureActive) {
            onStarted?.invoke()
            return
        }
        if (preparingListening) return
        preparingListening = true
        if (stopPlayback) tts?.stop()
        scope.launch {
            val prepared = runCatching {
                withContext(Dispatchers.Default) {
                    streaming.ensureInstalled()
                    finalAsr.ensureInstalled { }
                    streaming.begin()
                }
            }
            preparingListening = false
            if (!VoiceConversationController.state.value.active) return@launch
            prepared.onFailure { error ->
                VoiceConversationController.update {
                    it.copy(phase = VoiceConversationPhase.ERROR, transcript = error.message ?: "本地语音模型初始化失败")
                }
                delay(1_200)
                stopConversation()
                return@launch
            }
            val startedAt = SystemClock.elapsedRealtime()
            VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.LISTENING, elapsedMillis = 0) }
            runCatching {
                capture.start(
                    teeToRecognizer = false,
                    onSpeechEnd = { scope.launch { finishListening() } },
                    onSpeechStart = {
                        scope.launch {
                            latestHeardText = ""
                            if (
                                VoiceConversationController.state.value.phase == VoiceConversationPhase.SPEAKING &&
                                playbackStartedAt > 0L &&
                                SystemClock.elapsedRealtime() - playbackStartedAt >= BARGE_IN_GRACE_MILLIS
                            ) {
                                playbackInterruptedByUser = true
                                tts?.stop()
                                VoiceConversationController.update {
                                    it.copy(phase = VoiceConversationPhase.LISTENING, transcript = "")
                                }
                            } else VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.LISTENING, transcript = "") }
                        }
                    },
                    onPcm = { bytes, count ->
                        val partial = runCatching { streaming.acceptPcm16(bytes, count) }.getOrDefault("")
                        if (partial.isNotBlank()) {
                            latestHeardText = partial
                            VoiceConversationController.update {
                                it.copy(transcript = partial, elapsedMillis = SystemClock.elapsedRealtime() - startedAt)
                            }
                        }
                    },
                    enablePlaybackEchoCancellation = true,
                    endSilenceMillis = ::adaptiveEndSilenceMillis,
                    initialSilenceTimeoutMillis = Long.MAX_VALUE,
                )
            }.onSuccess {
                captureActive = true
                onStarted?.invoke()
            }.onFailure { error ->
                VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.ERROR, transcript = error.message ?: "麦克风启动失败") }
                delay(1_000)
                stopConversation()
            }
        }
    }

    private suspend fun finishListening() {
        if (!captureActive) {
            if (conversationKind == VoiceConversationKind.INPUT && preparingListening) stopConversation()
            return
        }
        captureActive = false
        runCatching { streaming.finish() }
        val file = capture.stop(true) ?: return beginListening()
        VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.RECOGNIZING) }
        val text = runCatching { finalAsr.transcribe(file) }.also { file.delete() }.getOrDefault("").trim()
        if (conversationKind == VoiceConversationKind.INPUT) {
            if (text.isBlank()) {
                VoiceConversationController.update { it.copy(active = false, phase = VoiceConversationPhase.ERROR, transcript = "没有识别到有效语音") }
            } else {
                VoiceConversationController.update {
                    it.copy(
                        active = false,
                        phase = VoiceConversationPhase.STOPPED,
                        transcript = text,
                        resultText = text,
                        resultSequence = it.resultSequence + 1,
                    )
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (text.isBlank()) return beginListening()
        latestHeardText = text
        val turnId = UUID.randomUUID().toString()
        activeTurnId = turnId
        spokenTurnId = null
        VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.THINKING, transcript = text, voiceTurnId = turnId) }
        submit(text, turnId)
        // Keep local VAD active while the model thinks so speech becomes a Steer input.
        beginListening()
    }

    private fun submit(text: String, turnId: String) {
        val app = application as PhoneAgentApplication
        scope.launch(Dispatchers.IO) {
            val dao = app.container.database.dao()
            val workspace = requestedWorkspaceId?.let { dao.workspace(it) }
                ?: activeSessionId?.let { dao.session(it) }?.let { dao.workspace(it.workspaceId) }
                ?: dao.workspaces().firstOrNull()
            if (workspace == null) return@launch beginListening()
            runCatching {
                app.container.dshRuntime.ensureStarted()
                app.container.dshRuntime.awaitReady()
                val wasInFlight = dshTurnInFlight
                val session = app.container.dshApi.ensureSession(
                    sessionId = activeSessionId ?: requestedSessionId,
                    cwd = workspace.localPath,
                    agentPreset = "sai-voice",
                )
                bindSession(session)
                dshTurnInFlight = true
                app.container.dshApi.prompt(session, text, steer = wasInFlight)
            }.onFailure {
                dshTurnInFlight = false
                VoiceConversationController.update { state ->
                    state.copy(phase = VoiceConversationPhase.ERROR, transcript = it.message ?: "DSH 语音请求失败")
                }
                delay(900)
                beginListening()
            }
        }
    }

    private fun bindSession(sessionId: String) {
        activeSessionId = sessionId
        requestedSessionId = sessionId
        getSharedPreferences("sai-ui", MODE_PRIVATE).edit().putString("active_session_id", sessionId).apply()
        VoiceConversationController.update { it.copy(sessionId = sessionId) }
    }

    private fun speak(raw: String, turnId: String?) {
        if (turnId == null || turnId != activeTurnId || spokenTurnId == turnId) return
        spokenTurnId = turnId
        val text = SpeechTextSanitizer.clean(raw)
        if (text.isBlank() || VoiceConversationController.state.value.muted) return beginListening()
        val play: () -> Unit = {
            playbackInterruptedByUser = false
            playbackStartedAt = SystemClock.elapsedRealtime()
            VoiceConversationController.update { it.copy(phase = VoiceConversationPhase.SPEAKING) }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, turnId)
            Unit
        }
        // Keep one echo-cancelled microphone stream alive throughout playback.
        // Local VAD can therefore stop TTS and turn an interruption into Steer input.
        if (captureActive) play() else beginListening(stopPlayback = false, onStarted = play)
    }

    private fun resumeListeningAfterPlayback() {
        playbackStartedAt = 0L
        if (!VoiceConversationController.state.value.active) return
        if (captureActive) {
            VoiceConversationController.update {
                it.copy(phase = VoiceConversationPhase.LISTENING, transcript = latestHeardText)
            }
        } else beginListening()
    }

    /** Offline approximation of semantic turn detection used by realtime voice APIs. */
    private fun adaptiveEndSilenceMillis(): Long {
        val text = latestHeardText.trim()
        if (text.isBlank()) return 1_050L
        if (text.endsWithAny("嗯", "呃", "然后", "但是", "还有", "所以", "就是", "那个")) return 1_550L
        if (text.endsWithAny("。", "！", "？", ".", "!", "?")) return 620L
        return 920L
    }

    private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any(::endsWith)

    private fun updateNotification() = getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification())

    private fun notification() = NotificationCompat.Builder(this, PhoneAgentApplication.AGENT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_phone_agent)
        .setContentTitle("sai 语音通话")
        .setContentText("本地实时聆听；可随时静音或停止")
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 51, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(0, "静音", PendingIntent.getService(this, 52, Intent(this, VoiceConversationService::class.java).setAction(ACTION_MUTE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(0, "停止", PendingIntent.getService(this, 53, Intent(this, VoiceConversationService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    private fun stopConversation() {
        preparingListening = false
        captureActive = false
        capture.stop(false)
        tts?.stop()
        val previous = VoiceConversationController.state.value
        VoiceConversationController.update {
            VoiceConversationState(
                kind = previous.kind,
                resultText = previous.resultText,
                resultSequence = previous.resultSequence,
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        capture.stop(false); streaming.close(); finalAsr.close(); tts?.shutdown(); scope.cancel()
        VoiceConversationController.update { previous ->
            VoiceConversationState(
                kind = previous.kind,
                resultText = previous.resultText,
                resultSequence = previous.resultSequence,
            )
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TOGGLE = "com.phoneagent.app.action.VOICE_TOGGLE"
        const val ACTION_STOP = "com.phoneagent.app.action.VOICE_STOP"
        const val ACTION_MUTE = "com.phoneagent.app.action.VOICE_MUTE"
        const val ACTION_SPEAK = "com.phoneagent.app.action.VOICE_SPEAK"
        const val ACTION_INPUT_TOGGLE = "com.phoneagent.app.action.VOICE_INPUT_TOGGLE"
        const val ACTION_INPUT_CANCEL = "com.phoneagent.app.action.VOICE_INPUT_CANCEL"
        const val EXTRA_SPEAK_TEXT = "com.phoneagent.app.extra.VOICE_SPEAK_TEXT"
        const val EXTRA_SESSION_ID = "com.phoneagent.app.extra.VOICE_SESSION_ID"
        const val EXTRA_WORKSPACE_ID = "com.phoneagent.app.extra.VOICE_WORKSPACE_ID"
        const val EXTRA_PROVIDER_ID = "com.phoneagent.app.extra.VOICE_PROVIDER_ID"
        const val EXTRA_MODEL_ID = "com.phoneagent.app.extra.VOICE_MODEL_ID"
        private const val NOTIFICATION_ID = 1201
        private const val GREETING_UTTERANCE_ID = "sai-voice-greeting"
        private const val GREETING_TEXT = "你好，语音通话已开始"
        private const val BARGE_IN_GRACE_MILLIS = 280L
    }
}
