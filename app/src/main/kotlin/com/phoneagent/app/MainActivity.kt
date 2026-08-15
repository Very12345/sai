package com.phoneagent.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.media.AudioFormat
import android.provider.Settings
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phoneagent.app.ui.PhoneAgentApp
import com.phoneagent.app.ui.PhoneAgentTheme
import com.phoneagent.app.device.DeviceControlAuthorization
import com.phoneagent.app.service.ScreenCaptureService
import com.phoneagent.app.service.PetOverlayService
import com.phoneagent.app.service.VoiceConversationService
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private enum class SpeechMode { INPUT, CALL }
    private val viewModel: MainViewModel by viewModels()
    private val desktopPairingScanner = registerForActivityResult(ScanContract()) { result ->
        val payload = result.contents?.trim().orEmpty()
        if (payload.isNotEmpty()) viewModel.pairDesktop(payload)
        else viewModel.showMessage("未识别到 sai Desktop 配对二维码")
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val directoryPermission = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        viewModel.authorizeExternalTree(uri)
    }
    private val phoneFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val root = File(filesDir, "attachments/drafts").apply { mkdirs() }
            val copied = uris.mapNotNull { uri ->
                runCatching {
                    val name = displayName(uri).replace(Regex("[\\/:*?\"<>|]"), "_")
                    val target = File(root, "${UUID.randomUUID()}-$name")
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取 $name" }
                        target.outputStream().use(input::copyTo)
                    }
                    target.absolutePath
                }.getOrNull()
            }
            runOnUiThread { viewModel.attachFiles(copied) }
        }
    }
    private val projectZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.importProjectZip(uri, displayName(uri))
    }
    private val extensionZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.inspectExtensionZip(uri, displayName(uri))
    }
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var voiceBase: String = ""
    private var requestedSpeechMode = SpeechMode.INPUT
    private var activeSpeechMode = SpeechMode.INPUT
    private var lastSpokenVoiceTurnId: String? = null
    private var lastSpeechRequestId: String? = null
    private var currentVoiceUtteranceId: String? = null
    private var currentSpokenVoiceTurnId: String? = null
    private var callResponsePending = false
    private val voiceAudioCapture by lazy { VoiceAudioCapture(this) }
    private val localAsrManager by lazy { LocalAsrManager(this) }
    private val streamingAsrManager by lazy { StreamingAsrManager(this) }
    private val streamingAsrExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "PhoneAgent-StreamingASR") }
    private var voiceCaptureSession: VoiceAudioCapture.Session? = null
    private var localCaptureActive = false
    private var voiceInitializationJob: Job? = null
    private var pressToTalkSend = false
    private var voiceTimerJob: Job? = null
    private var voiceStartedAt = 0L
    @Volatile private var lastPartialAt = 0L
    private var lastVoiceInputResultSequence = 0L
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && requestedSpeechMode == SpeechMode.CALL) startVoiceConversationService()
        else if (granted) startVoiceInputService()
        else {
            if (requestedSpeechMode == SpeechMode.CALL) viewModel.endVoiceCall()
            viewModel.showMessage("麦克风权限被拒绝，无法使用语音")
        }
    }
    private val screenCapturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch(Dispatchers.IO) { clearLegacyExtractedVoiceModels() }
        initializeVoice()
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (Settings.canDrawOverlays(this) && getSharedPreferences("sai-ui", 0).getBoolean("system_pet_enabled", false)) {
            getSharedPreferences("sai-ui", 0).edit().putBoolean("task_pet_visible", false).apply()
            ContextCompat.startForegroundService(this, Intent(this, PetOverlayService::class.java).setAction(PetOverlayService.ACTION_SHOW))
        }
        setContent {
            val uiState by viewModel.ui.collectAsState()
            PhoneAgentTheme(uiState.appTheme) {
                PhoneAgentApp(
                    viewModel = viewModel,
                    requestExternalDirectory = { directoryPermission.launch(null) },
                    requestPhoneFiles = { phoneFiles.launch(arrayOf("*/*")) },
                    requestProjectZip = { projectZip.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    requestExtensionZip = { extensionZip.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    requestAllFilesAccess = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            startActivity(Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ))
                        } else {
                            directoryPermission.launch(null)
                        }
                    },
                    startVoiceInput = {
                        requestedSpeechMode = SpeechMode.INPUT
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            startVoiceInputService()
                        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    finishVoiceInput = { send ->
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, VoiceConversationService::class.java).setAction(
                                if (send) VoiceConversationService.ACTION_INPUT_TOGGLE else VoiceConversationService.ACTION_INPUT_CANCEL,
                            ),
                        )
                    },
                    toggleVoiceCall = { toggleVoiceCall() },
                    openAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    authorizeDeviceControl = { packageName ->
                        val explicitTarget = packageName.trim().takeIf(String::isNotEmpty)
                        DeviceControlAuthorization.grant(explicitTarget?.let(::setOf) ?: emptySet())
                    },
                    requestScreenCapture = {
                        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
                        screenCapturePermission.launch(manager.createScreenCaptureIntent())
                    },
                    scanDesktopPairing = {
                        desktopPairingScanner.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("扫描电脑上 sai Desktop 的二维码")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false),
                        )
                    },
                )
            }
        }
        lifecycleScope.launch {
            VoiceConversationController.state.collectLatest { voiceState ->
                viewModel.syncVoiceConversation(voiceState)
                if (
                    voiceState.kind == VoiceConversationKind.INPUT &&
                    voiceState.resultSequence > lastVoiceInputResultSequence &&
                    voiceState.resultText.isNotBlank()
                ) {
                    lastVoiceInputResultSequence = voiceState.resultSequence
                    viewModel.setPrompt(voiceState.resultText)
                    viewModel.startAgent()
                }
            }
        }
        handleLaunchIntent(intent)
    }

    private fun clearLegacyExtractedVoiceModels() {
        val targets = listOf(File(filesDir, "local-asr"), File(cacheDir, "local-asr"))
        targets.forEach { target ->
            val canonical = runCatching { target.canonicalFile }.getOrNull() ?: return@forEach
            val allowedParent = if (target.parentFile == filesDir) filesDir.canonicalFile else cacheDir.canonicalFile
            if (canonical.parentFile == allowedParent) canonical.deleteRecursively()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE_VOICE_CALL) {
            intent.action = null
            toggleVoiceCall()
        }
    }

    private fun beginVoiceTimer() {
        if (viewModel.ui.value.voiceInputActive) return
        voiceStartedAt = android.os.SystemClock.elapsedRealtime()
        viewModel.beginVoiceInput()
        voiceTimerJob?.cancel()
        voiceTimerJob = lifecycleScope.launch {
            while (viewModel.ui.value.voiceInputActive) {
                viewModel.updateVoiceInput(elapsedMillis = android.os.SystemClock.elapsedRealtime() - voiceStartedAt)
                delay(100)
            }
        }
    }

    private fun startVoiceInputService() {
        val state = viewModel.ui.value
        ContextCompat.startForegroundService(
            this,
            Intent(this, VoiceConversationService::class.java)
                .setAction(VoiceConversationService.ACTION_INPUT_TOGGLE)
                .putExtra(VoiceConversationService.EXTRA_SESSION_ID, state.selectedSessionId)
                .putExtra(VoiceConversationService.EXTRA_WORKSPACE_ID, state.selectedWorkspaceId),
        )
    }

    private fun finishPressToTalk(send: Boolean) {
        pressToTalkSend = send
        if (!send) {
            voiceInitializationJob?.cancel()
            voiceInitializationJob = null
            speechRecognizer?.cancel()
            localCaptureActive = false
            finishNativeAudioCapture(keep = false)
            voiceAudioCapture.stop(false)
            viewModel.endVoiceInput()
            return
        }
        if (localCaptureActive) {
            finishLocalCaptureAndTranscribe()
        } else {
            // The recognizer may still be loading. A second tap must always be an escape hatch.
            voiceInitializationJob?.cancel()
            voiceInitializationJob = null
            pressToTalkSend = false
            viewModel.endVoiceInput()
            viewModel.showMessage("已停止语音输入")
        }
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        return fromProvider?.takeIf(String::isNotBlank) ?: "import-${System.currentTimeMillis()}.zip"
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAllFilesAccess()
        viewModel.refreshTaskPetPreference()
        viewModel.refreshVoiceModelPack()
    }

    private fun initializeVoice() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.SIMPLIFIED_CHINESE
            else viewModel.showMessage("系统文字转语音服务初始化失败")
        }.also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) {
                    if (utteranceId != null && utteranceId == currentVoiceUtteranceId) runOnUiThread { restartVoiceCallListening() }
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null && utteranceId == currentVoiceUtteranceId) runOnUiThread { restartVoiceCallListening() }
                }
            })
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onPartialResults(partialResults: Bundle?) = deliverSpeech(partialResults, final = false)
                    override fun onResults(results: Bundle?) {
                        finishNativeAudioCapture(keep = true)
                        deliverSpeech(results, final = true)
                    }
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (activeSpeechMode == SpeechMode.INPUT) viewModel.showMessage("正在聆听，请开始说话")
                    }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onError(error: Int) {
                        finishNativeAudioCapture(keep = false)
                        if (activeSpeechMode == SpeechMode.CALL && viewModel.ui.value.voiceCallActive && !callResponsePending) {
                            viewModel.failVoiceCall(speechErrorMessage(error))
                            lifecycleScope.launch {
                                delay(if (error == SpeechRecognizer.ERROR_NO_MATCH) 350 else 900)
                                restartVoiceCallListening()
                            }
                        } else {
                            pressToTalkSend = false
                            viewModel.endVoiceInput()
                            viewModel.showMessage(speechErrorMessage(error))
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
    }

    private fun startSpeechRecognition(mode: SpeechMode = SpeechMode.INPUT) {
        // Embedded sherpa-onnx is deterministic and free. Do not silently route through a
        // vendor RecognitionService whose availability and completion callbacks vary by ROM.
        startOrStopLocalRecognition(mode)
    }

    private fun startOrStopLocalRecognition(mode: SpeechMode) {
        if (localCaptureActive) {
            finishLocalCaptureAndTranscribe()
            return
        }
        beginLocalCapture(mode)
    }

    private fun beginLocalCapture(mode: SpeechMode) {
        activeSpeechMode = mode
        voiceBase = if (mode == SpeechMode.INPUT) viewModel.ui.value.prompt.trim() else ""
        if (mode == SpeechMode.CALL) viewModel.updateVoiceCallTranscript("正在加载内置语音模型…")
        else viewModel.updateVoiceInput(transcript = "正在加载内置语音模型…")
        voiceInitializationJob?.cancel()
        voiceInitializationJob = lifecycleScope.launch {
            val initialized = runCatching { kotlinx.coroutines.withContext(Dispatchers.Default) { streamingAsrManager.begin() } }
            if (initialized.isFailure) {
                val message = "内置语音模型启动失败：${initialized.exceptionOrNull()?.message ?: "未知错误"}"
                if (mode == SpeechMode.CALL) {
                    viewModel.failVoiceCall(message)
                    viewModel.endVoiceCall()
                } else {
                    pressToTalkSend = false
                    viewModel.endVoiceInput()
                    viewModel.showMessage(message)
                }
                return@launch
            }
            val stillRequested = if (mode == SpeechMode.CALL) viewModel.ui.value.voiceCallActive else viewModel.ui.value.voiceInputActive
            if (!stillRequested) {
                runCatching { streamingAsrManager.finish() }
                return@launch
            }
            if (mode == SpeechMode.CALL) viewModel.resumeVoiceCallListening()
            startLocalAudioCapture(mode)
        }
    }

    private fun startLocalAudioCapture(mode: SpeechMode) {
        runCatching {
            lastPartialAt = 0L
            voiceAudioCapture.start(
                teeToRecognizer = false,
                onSpeechEnd = {
                    runOnUiThread { if (localCaptureActive) finishLocalCaptureAndTranscribe() }
                },
                onPcm = { pcm, count ->
                    streamingAsrExecutor.execute {
                        val now = android.os.SystemClock.elapsedRealtime()
                        val partial = runCatching { streamingAsrManager.acceptPcm16(pcm, count) }.getOrDefault("")
                        if (partial.isNotBlank() && now - lastPartialAt >= 280) {
                            lastPartialAt = now
                            runOnUiThread {
                                if (mode == SpeechMode.CALL) viewModel.updateVoiceCallTranscript(partial)
                                else viewModel.updateVoiceInput(transcript = partial)
                            }
                        }
                    }
                },
            )
        }.onSuccess { session ->
            voiceCaptureSession = session
            localCaptureActive = true
            if (mode == SpeechMode.INPUT) viewModel.showMessage("本地语音识别正在聆听；再次点击麦克风可立即结束")
        }.onFailure { error ->
            val message = "无法开始录音：${error.message ?: "录音设备异常"}"
            if (mode == SpeechMode.CALL) {
                viewModel.failVoiceCall(message)
                viewModel.endVoiceCall()
            } else {
                pressToTalkSend = false
                viewModel.endVoiceInput()
                viewModel.showMessage(message)
            }
        }
    }

    private fun finishLocalCaptureAndTranscribe() {
        if (!localCaptureActive) return
        localCaptureActive = false
        voiceCaptureSession = null
        val mode = activeSpeechMode
        runCatching { streamingAsrManager.finish() }
        val file = voiceAudioCapture.stop(keepRecording = true)
        if (file == null) {
            handleLocalRecognitionFailure(mode, "没有录到语音")
            return
        }
        if (mode == SpeechMode.CALL) viewModel.updateVoiceCallTranscript("正在本地识别…")
        else viewModel.showMessage("正在本地识别…")
        lifecycleScope.launch(Dispatchers.Main) {
            runCatching { localAsrManager.transcribe(file) }
                .also { file.delete() }
                .onSuccess { text ->
                    if (text.isBlank()) handleLocalRecognitionFailure(mode, "没有识别到内容，请再说一次")
                    else deliverLocalSpeech(text, mode)
                }
                .onFailure { error -> handleLocalRecognitionFailure(mode, "本地语音识别失败：${error.message ?: "未知错误"}") }
        }
    }

    private fun deliverLocalSpeech(result: String, mode: SpeechMode) {
        if (mode == SpeechMode.CALL) {
            viewModel.updateVoiceCallTranscript(result)
            callResponsePending = true
            lastSpokenVoiceTurnId = null
            viewModel.submitVoiceCall(result)
        } else {
            val text = listOf(voiceBase, result).filter(String::isNotBlank).joinToString(" ")
            viewModel.setPrompt(text)
            voiceBase = text
            viewModel.endVoiceInput()
            if (pressToTalkSend) {
                pressToTalkSend = false
                viewModel.startAgent()
            }
        }
    }

    private fun handleLocalRecognitionFailure(mode: SpeechMode, message: String) {
        if (mode == SpeechMode.CALL && viewModel.ui.value.voiceCallActive) {
            viewModel.failVoiceCall(message)
            lifecycleScope.launch {
                delay(900)
                restartVoiceCallListening()
            }
        } else viewModel.showMessage(message)
        if (mode == SpeechMode.INPUT) {
            pressToTalkSend = false
            viewModel.endVoiceInput()
        }
    }

    private fun finishNativeAudioCapture(keep: Boolean) {
        if (voiceCaptureSession == null) return
        voiceCaptureSession = null
        val file = voiceAudioCapture.stop(keep)
        viewModel.attachVoiceAudio(file?.absolutePath)
    }

    private fun deliverSpeech(bundle: Bundle?, final: Boolean) {
        val result = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        if (activeSpeechMode == SpeechMode.CALL) {
            viewModel.updateVoiceCallTranscript(result.trim())
            if (final && result.isNotBlank()) {
                callResponsePending = true
                lastSpokenVoiceTurnId = null
                viewModel.submitVoiceCall(result)
            }
        } else {
            val text = listOf(voiceBase, result.trim()).filter(String::isNotBlank).joinToString(" ")
            viewModel.setPrompt(text)
            viewModel.updateVoiceInput(transcript = result.trim())
            if (final) voiceBase = text
            if (final) {
                viewModel.endVoiceInput()
                if (pressToTalkSend && result.isNotBlank()) {
                    pressToTalkSend = false
                    viewModel.startAgent()
                }
            }
        }
    }

    private fun toggleVoiceCall() {
        if (VoiceConversationController.state.value.active) {
            startService(Intent(this, VoiceConversationService::class.java).setAction(VoiceConversationService.ACTION_STOP))
            return
        }
        if (viewModel.ui.value.voiceCallActive) {
            callResponsePending = false
            voiceInitializationJob?.cancel()
            voiceInitializationJob = null
            speechRecognizer?.cancel()
            localCaptureActive = false
            finishNativeAudioCapture(keep = false)
            tts?.stop()
            viewModel.endVoiceCall()
            return
        }
        requestedSpeechMode = SpeechMode.CALL
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startVoiceConversationService()
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceConversationService() {
        val state = viewModel.ui.value
        ContextCompat.startForegroundService(
            this,
            Intent(this, VoiceConversationService::class.java)
                .setAction(VoiceConversationService.ACTION_TOGGLE)
                .putExtra(VoiceConversationService.EXTRA_SESSION_ID, state.selectedSessionId)
                .putExtra(VoiceConversationService.EXTRA_WORKSPACE_ID, state.selectedWorkspaceId)
                .putExtra(VoiceConversationService.EXTRA_PROVIDER_ID, state.provider.id)
                .putExtra(VoiceConversationService.EXTRA_MODEL_ID, state.provider.defaultModel),
        )
    }

    private fun restartVoiceCallListening() {
        if (!viewModel.ui.value.voiceCallActive || isFinishing || isDestroyed) return
        callResponsePending = false
        viewModel.resumeVoiceCallListening()
        speechRecognizer?.cancel()
        lifecycleScope.launch {
            delay(250)
            if (viewModel.ui.value.voiceCallActive) startSpeechRecognition(SpeechMode.CALL)
        }
    }

    private fun speakVoiceCallResponse(text: String, voiceTurnId: String) {
        val spoken = SpeechTextSanitizer.clean(text)
        if (spoken.isBlank()) {
            restartVoiceCallListening()
            return
        }
        val utteranceId = "sai-voice-$voiceTurnId-${UUID.randomUUID()}"
        currentVoiceUtteranceId = utteranceId
        currentSpokenVoiceTurnId = voiceTurnId
        viewModel.markVoiceCallSpeaking()
        tts?.speak(spoken.take(2_000), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }


    private fun speakVisibleAssistant() {
        val text = viewModel.visibleAssistantText()
        if (text.isBlank()) return
        text.split(Regex("(?<=[。！？.!?])\\s*"))
            .filter(String::isNotBlank)
            .forEachIndexed { index, sentence ->
                tts?.speak(
                    sentence,
                    if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                    null,
                    "phoneagent-$index",
                )
            }
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "录音设备不可用"
        SpeechRecognizer.ERROR_CLIENT -> "语音识别已取消"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音服务网络不可用"
        SpeechRecognizer.ERROR_NO_MATCH -> "没有识别到内容，请再说一次"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务正忙，请稍后重试"
        SpeechRecognizer.ERROR_SERVER -> "语音识别服务暂时不可用"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
        else -> "语音识别失败（$error）"
    }

    override fun onDestroy() {
        voiceTimerJob?.cancel()
        voiceInitializationJob?.cancel()
        speechRecognizer?.destroy()
        localCaptureActive = false
        finishNativeAudioCapture(keep = false)
        localAsrManager.close()
        streamingAsrManager.close()
        streamingAsrExecutor.shutdownNow()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE_VOICE_CALL = "com.phoneagent.app.action.TOGGLE_VOICE_CALL"
    }
}
