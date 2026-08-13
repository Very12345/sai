package com.phoneagent.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneagent.agent.AgentEvent
import com.phoneagent.agent.ConversationProjection
import com.phoneagent.agent.AgentMode
import com.phoneagent.agent.AgentRunState
import com.phoneagent.agent.TaskQueueState
import com.phoneagent.agent.ApprovalDecision
import com.phoneagent.agent.ApprovalRequest
import com.phoneagent.app.service.AgentForegroundService
import com.phoneagent.app.service.ScreenCaptureService
import com.phoneagent.app.service.PetOverlayService
import com.phoneagent.data.WorkspaceEntity
import com.phoneagent.data.SessionEntity
import com.phoneagent.data.ExtensionEntity
import com.phoneagent.data.ProviderModelEntity
import com.phoneagent.data.McpServerEntity
import com.phoneagent.data.HookConfigEntity
import com.phoneagent.data.DesktopPairingEntity
import com.phoneagent.extensions.CatalogExtension
import com.phoneagent.extensions.ExtensionCatalogClient
import com.phoneagent.extensions.ExtensionInstallPlan
import com.phoneagent.extensions.ExtensionInstaller
import com.phoneagent.extensions.ExtensionKind
import com.phoneagent.extensions.CapabilityDiagnostic
import com.phoneagent.extensions.HttpMcpClient
import com.phoneagent.extensions.McpServerConfig
import com.phoneagent.extensions.McpTransport
import com.phoneagent.provider.ProviderPresets
import com.phoneagent.provider.ProviderCredential
import com.phoneagent.provider.ProviderFactory
import com.phoneagent.provider.ProviderProtocol
import com.phoneagent.provider.ProviderProfile
import com.phoneagent.provider.ModelInfo
import com.phoneagent.provider.ModelVisionPolicy
import com.phoneagent.provider.ModelReasoningPolicy
import com.phoneagent.provider.ReasoningSelection
import com.phoneagent.runtime.PhoneAgentRootfs
import com.phoneagent.runtime.PtySession
import com.phoneagent.runtime.RootfsInstallState
import com.phoneagent.runtime.RunRequest
import com.phoneagent.runtime.RuntimeCapability
import com.phoneagent.runtime.RuntimeProvisioner
import com.phoneagent.runtime.RuntimePackageAction
import com.phoneagent.runtime.RuntimePackageGroup
import com.phoneagent.runtime.RuntimePackageManager
import com.phoneagent.runtime.RuntimePackagePlan
import com.phoneagent.runtime.RuntimePackageProgress
import com.phoneagent.runtime.RuntimePackageStatus
import com.phoneagent.runtime.TerminalEvent
import com.phoneagent.dsh.DshRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class MainSection { AGENT, FILES, TERMINAL, BROWSER, EXTENSIONS, SETTINGS }

enum class VoiceCallPhase { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

enum class VoiceInputGesture { TAP, HOLD }

enum class SessionPermissionMode { ASK, AUTO, YOLO }

data class FileItem(val path: String, val directory: Boolean, val size: Long)

data class RuntimePackageRequest(val group: RuntimePackageGroup, val action: RuntimePackageAction)

data class MainUiState(
    val section: MainSection = MainSection.AGENT,
    val events: List<AgentEvent> = emptyList(),
    val workspaces: List<WorkspaceEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val taskHandles: Map<String, TaskHandle> = emptyMap(),
    val selectedWorkspaceId: String = TaskSupervisor.DEFAULT_WORKSPACE_ID,
    val selectedSessionId: String? = null,
    val runState: AgentRunState = AgentRunState.IDLE,
    val approval: ApprovalRequest? = null,
    val prompt: String = "",
    val mode: AgentMode = AgentMode.AGENT,
    val sessionWriteAllowed: Boolean = false,
    val permissionMode: SessionPermissionMode = SessionPermissionMode.ASK,
    val taskPetVisible: Boolean = true,
    val taskPetMinimized: Boolean = false,
    val petTheme: String = "aurora",
    val appTheme: String = "aurora",
    val voiceInputGesture: VoiceInputGesture = VoiceInputGesture.TAP,
    val voiceModelPackInstalled: Boolean = false,
    val files: List<FileItem> = emptyList(),
    val currentDirectory: String = "",
    val fileSearch: String = "",
    val showHiddenFiles: Boolean = false,
    val selectedFile: String? = null,
    val editorText: String = "",
    val editorDirty: Boolean = false,
    val editorCloseConfirmation: Boolean = false,
    val editorReadOnly: Boolean = false,
    val terminalCommand: String = "",
    val terminalOutput: String = "",
    val terminalConnected: Boolean = false,
    val runtimeCapability: RuntimeCapability? = null,
    val dshRuntime: DshRuntimeState = DshRuntimeState(),
    val rootfsInstallState: RootfsInstallState = RootfsInstallState.NotInstalled,
    val runtimeSelfTestOutput: String = "",
    val runtimeSelfTestRunning: Boolean = false,
    val githubCliStatus: GitHubCliStatus = GitHubCliStatus(false, detail = "等待本地环境"),
    val githubTokenInput: String = "",
    val githubCliBusy: Boolean = false,
    val provider: ProviderProfile = ProviderPresets.all.first(),
    val providerProfiles: List<ProviderProfile> = emptyList(),
    val providerApiKey: String = "",
    val providerSaved: Boolean = false,
    val availableModels: List<ModelInfo> = emptyList(),
    val providerModels: List<ProviderModelEntity> = emptyList(),
    val modelDiscoveryRunning: Boolean = false,
    val modelDiscoveryError: String? = null,
    val runtimePackages: List<RuntimePackageStatus> = emptyList(),
    val runtimePackageRequest: RuntimePackageRequest? = null,
    val runtimePackagePlan: RuntimePackagePlan? = null,
    val runtimePackageOperation: String? = null,
    val runtimePackageProgress: RuntimePackageProgress? = null,
    val externalTreeUri: String? = null,
    val allFilesAccess: Boolean = hasAllFilesAccess(),
    val installedExtensions: List<ExtensionEntity> = emptyList(),
    val extensionQuery: String = "",
    val extensionResults: List<CatalogExtension> = emptyList(),
    val extensionSearchRunning: Boolean = false,
    val extensionPlan: ExtensionInstallPlan? = null,
    val extensionAudit: CapabilityDiagnostic? = null,
    val extensionError: String? = null,
    val extensionFeedTitle: String = "热门推荐",
    val mcpServers: List<McpServerEntity> = emptyList(),
    val hookConfigs: List<HookConfigEntity> = emptyList(),
    val desktopPairings: List<DesktopPairingEntity> = emptyList(),
    val desktopConnectionStatus: String = "未连接电脑",
    val pendingAttachments: List<String> = emptyList(),
    val voiceInputActive: Boolean = false,
    val voiceInputElapsedMillis: Long = 0,
    val voiceInputTranscript: String = "",
    val voiceInputCancelling: Boolean = false,
    val e2eTestRunning: Boolean = false,
    val voiceCallActive: Boolean = false,
    val voiceCallPhase: VoiceCallPhase = VoiceCallPhase.IDLE,
    val voiceCallTranscript: String = "",
    val activeVoiceTurnId: String? = null,
    val pendingVoiceAudioPath: String? = null,
    val latestCapturePath: String? = null,
    val attachLatestCapture: Boolean = false,
    val auxiliaryVisionModel: String = "",
    val auxiliaryVisionProviderId: String = "",
    val browserPreviewUrl: String = "",
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PhoneAgentApplication).container
    private val uiPreferences = application.getSharedPreferences("sai-ui", 0)
    private val _ui = MutableStateFlow(MainUiState(
        provider = ModelReasoningPolicy.normalize(container.providerSettings.profile.value),
        providerProfiles = container.providerSettings.profiles.value,
        rootfsInstallState = initialRootfsState(),
        taskPetVisible = uiPreferences.getBoolean("task_pet_visible", true),
        taskPetMinimized = uiPreferences.getBoolean("task_pet_minimized", false),
        petTheme = uiPreferences.getString("app_theme", uiPreferences.getString("pet_theme", "aurora")).orEmpty(),
        appTheme = uiPreferences.getString("app_theme", uiPreferences.getString("pet_theme", "aurora")).orEmpty(),
        voiceInputGesture = runCatching {
            VoiceInputGesture.valueOf(uiPreferences.getString("voice_input_gesture", VoiceInputGesture.TAP.name).orEmpty())
        }.getOrDefault(VoiceInputGesture.TAP),
        voiceModelPackInstalled = VoiceModelPack.isInstalled(application),
        auxiliaryVisionModel = uiPreferences.getString("auxiliary_vision_model", "").orEmpty(),
        auxiliaryVisionProviderId = uiPreferences.getString("auxiliary_vision_provider", "").orEmpty(),
    ))
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()
    private var terminalSession: PtySession? = null
    private var terminalReader: kotlinx.coroutines.Job? = null
    private val terminalWriteMutex = Mutex()
    private var runtimePackageJob: kotlinx.coroutines.Job? = null
    private var selectedSessionEventsJob: Job? = null
    private val eventJson = Json { ignoreUnknownKeys = true; classDiscriminator = "eventType" }
    private val extensionCatalog = ExtensionCatalogClient(
        githubTokenProvider = { container.secretStore.get("github:github.com:token") },
    )
    private val extensionInstaller by lazy { ExtensionInstaller(File(getApplication<Application>().filesDir, "extensions")) }
    private val desktopConnection = container.desktopConnection

    init {
        initializeDefaultWorkspace()
        refreshFiles()
        probeRuntime()
        refreshGitHubCli()
        if (_ui.value.rootfsInstallState is RootfsInstallState.NotInstalled) installRootfs()
        if (container.providerSettings.hasCredential()) refreshModels()
        loadExtensionRecommendations()
        viewModelScope.launch {
            container.dshRuntime.state.collectLatest { dsh ->
                _ui.update { it.copy(dshRuntime = dsh) }
            }
        }
        viewModelScope.launch {
            container.coordinator.sessionEvents.collectLatest { envelope ->
                val event = envelope.event
                _ui.update { state ->
                    if (state.selectedSessionId != envelope.sessionId) return@update state
                    val events = state.events.toMutableList()
                    val merged = when {
                        event is AgentEvent.AssistantDelta && events.lastOrNull() is AgentEvent.AssistantDelta -> {
                            val previous = events.removeAt(events.lastIndex) as AgentEvent.AssistantDelta
                            previous.copy(text = previous.text + event.text)
                        }
                        event is AgentEvent.ReasoningDelta && events.lastOrNull() is AgentEvent.ReasoningDelta -> {
                            val previous = events.removeAt(events.lastIndex) as AgentEvent.ReasoningDelta
                            previous.copy(text = previous.text + event.text)
                        }
                        else -> event
                    }
                    events += merged
                    state.copy(events = events.takeLast(1_000))
                }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeWorkspaces().collectLatest { workspaces ->
                _ui.update { state ->
                    val selected = state.selectedWorkspaceId.takeIf { id -> workspaces.any { it.id == id } }
                        ?: workspaces.firstOrNull()?.id
                        ?: TaskSupervisor.DEFAULT_WORKSPACE_ID
                    state.copy(workspaces = workspaces, selectedWorkspaceId = selected)
                }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeAllSessions().collectLatest { sessions ->
                _ui.update { state ->
                    val selected = state.selectedSessionId?.takeIf { id -> sessions.any { it.id == id } }
                    state.copy(sessions = sessions, selectedSessionId = selected)
                }
            }
        }
        viewModelScope.launch {
            container.coordinator.tasks.collectLatest { handles ->
                _ui.update { state ->
                    val selectedHandle = state.selectedSessionId?.let(handles::get)
                    state.copy(
                        taskHandles = handles,
                        runState = selectedHandle?.runState ?: state.selectedSessionEntity()?.runState() ?: AgentRunState.IDLE,
                    )
                }
            }
        }
        viewModelScope.launch {
            container.coordinator.approvals.collectLatest { approvals ->
                _ui.update { state -> state.copy(approval = state.selectedSessionId?.let(approvals::get)) }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeExtensions().collectLatest { extensions ->
                _ui.update { it.copy(installedExtensions = extensions) }
            }
        }
        viewModelScope.launch {
            container.providerSettings.profiles.collectLatest { profiles ->
                _ui.update { it.copy(providerProfiles = profiles) }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeAllProviderModels().collectLatest { models ->
                _ui.update { state -> state.copy(providerModels = models).withAutomaticVisionModel() }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeMcpServers().collectLatest { servers ->
                _ui.update { it.copy(mcpServers = servers) }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeHookConfigs().collectLatest { hooks ->
                _ui.update { it.copy(hookConfigs = hooks) }
            }
        }
        viewModelScope.launch {
            desktopConnection.status.collectLatest { status ->
                _ui.update { it.copy(desktopConnectionStatus = status) }
            }
        }
        viewModelScope.launch {
            container.database.dao().observeDesktopPairings().collectLatest { pairings ->
                _ui.update { it.copy(desktopPairings = pairings) }
            }
        }
        viewModelScope.launch {
            ScreenCaptureService.captureResults.collectLatest { result ->
                result.onSuccess { file -> _ui.update { it.copy(latestCapturePath = file.absolutePath, message = "已按需捕获屏幕，可作为图片附件发送") } }
                    .onFailure { error -> _ui.update { it.copy(message = error.message ?: "屏幕捕获失败") } }
            }
        }
    }

    private fun initializeDefaultWorkspace() {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = container.database.dao()
            val workspace = WorkspaceEntity(
                id = TaskSupervisor.DEFAULT_WORKSPACE_ID,
                name = "默认项目",
                localPath = container.workspace.absolutePath,
            )
            if (dao.workspace(workspace.id) == null) dao.upsertWorkspace(workspace)
            val runtimeReady = container.rootfsInstaller.rootfsDir.resolve("usr/bin/git").isFile
            val failures = if (runtimeReady) dao.workspaces().mapNotNull { existing ->
                ensureGitRepository(File(existing.localPath)).exceptionOrNull()?.let { error -> "${existing.name}：${error.message}" }
            } else emptyList()
            if (failures.isNotEmpty()) {
                _ui.update { it.copy(message = "项目 Git 初始化失败：${failures.joinToString("；")}") }
            }
        }
    }

    fun selectSection(section: MainSection) {
        _ui.update { it.copy(section = section) }
        if (section == MainSection.TERMINAL && _ui.value.runtimeCapability?.available == true) openTerminal()
    }
    fun setPrompt(prompt: String) = _ui.update { it.copy(prompt = prompt) }
    fun appendVoiceText(text: String) = _ui.update { state ->
        state.copy(prompt = listOf(state.prompt.trim(), text.trim()).filter(String::isNotBlank).joinToString(" "))
    }

    fun beginVoiceInput() = _ui.update {
        it.copy(
            voiceInputActive = true,
            voiceInputElapsedMillis = 0,
            voiceInputTranscript = "",
            voiceInputCancelling = false,
        )
    }

    fun updateVoiceInput(
        transcript: String? = null,
        elapsedMillis: Long? = null,
        cancelling: Boolean? = null,
    ) = _ui.update {
        it.copy(
            voiceInputTranscript = transcript ?: it.voiceInputTranscript,
            voiceInputElapsedMillis = elapsedMillis ?: it.voiceInputElapsedMillis,
            voiceInputCancelling = cancelling ?: it.voiceInputCancelling,
        )
    }

    fun endVoiceInput() = _ui.update {
        it.copy(
            voiceInputActive = false,
            voiceInputElapsedMillis = 0,
            voiceInputTranscript = "",
            voiceInputCancelling = false,
        )
    }

    fun beginVoiceCall() = _ui.update {
        it.copy(
            section = MainSection.AGENT,
            voiceCallActive = true,
            voiceCallPhase = VoiceCallPhase.LISTENING,
            voiceCallTranscript = "正在聆听…",
        )
    }

    fun endVoiceCall() = _ui.update {
        it.copy(voiceCallActive = false, voiceCallPhase = VoiceCallPhase.IDLE, voiceCallTranscript = "")
    }

    fun syncVoiceConversation(state: VoiceConversationState) {
        val previouslySelected = _ui.value.selectedSessionId
        _ui.update {
            it.copy(
                voiceCallActive = state.active,
                voiceCallPhase = when (state.phase) {
                    VoiceConversationPhase.STOPPED -> VoiceCallPhase.IDLE
                    VoiceConversationPhase.PREPARING, VoiceConversationPhase.LISTENING, VoiceConversationPhase.RECOGNIZING -> VoiceCallPhase.LISTENING
                    VoiceConversationPhase.THINKING -> VoiceCallPhase.THINKING
                    VoiceConversationPhase.SPEAKING -> VoiceCallPhase.SPEAKING
                    VoiceConversationPhase.ERROR -> VoiceCallPhase.ERROR
                },
                voiceCallTranscript = state.transcript,
                activeVoiceTurnId = state.voiceTurnId,
            )
        }
        state.sessionId?.takeIf { it != previouslySelected }?.let { selectSession(it, loadPersisted = false) }
    }

    fun updateVoiceCallTranscript(text: String) = _ui.update {
        if (it.voiceCallActive) it.copy(voiceCallTranscript = text, voiceCallPhase = VoiceCallPhase.LISTENING) else it
    }

    fun submitVoiceCall(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank()) return
        val voiceTurnId = UUID.randomUUID().toString()
        val runningSession = _ui.value.selectedSessionId?.takeIf { id ->
            _ui.value.taskHandles[id]?.runState in setOf(AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL)
        }
        if (runningSession != null && container.coordinator.steer(
                runningSession,
                prompt,
                voiceTurnId,
                VOICE_CONVERSATION_POLICY,
            )) {
            _ui.update { it.copy(voiceCallTranscript = prompt, voiceCallPhase = VoiceCallPhase.THINKING, activeVoiceTurnId = voiceTurnId) }
        } else {
            _ui.update { it.copy(prompt = prompt, voiceCallTranscript = prompt, voiceCallPhase = VoiceCallPhase.THINKING, activeVoiceTurnId = voiceTurnId) }
            startAgent(additionalSystemInstruction = VOICE_CONVERSATION_POLICY)
        }
    }

    fun markVoiceCallSpeaking() = _ui.update {
        if (it.voiceCallActive) it.copy(voiceCallPhase = VoiceCallPhase.SPEAKING) else it
    }

    fun resumeVoiceCallListening() = _ui.update {
        if (it.voiceCallActive) it.copy(voiceCallPhase = VoiceCallPhase.LISTENING, voiceCallTranscript = "正在聆听…") else it
    }

    fun failVoiceCall(message: String) = _ui.update {
        if (it.voiceCallActive) it.copy(voiceCallPhase = VoiceCallPhase.ERROR, voiceCallTranscript = message) else it
    }

    fun supportsNativeAudioInput(): Boolean {
        val model = _ui.value.provider.defaultModel.lowercase()
        val protocolSupportsAudio = _ui.value.provider.protocol in setOf(ProviderProtocol.OPENAI_CHAT, ProviderProtocol.GEMINI_NATIVE)
        return protocolSupportsAudio && (
            _ui.value.provider.capabilities.audioInput ||
                listOf("audio", "realtime", "native-audio", "live").any(model::contains)
            )
    }

    fun attachVoiceAudio(path: String?) = _ui.update {
        it.copy(pendingVoiceAudioPath = path, message = path?.let { "当前模型支持原生音频，发送时将附带本次录音" })
    }

    fun visibleAssistantText(): String {
        return ConversationProjection.latestAssistantText(_ui.value.events)
        .replace(Regex("```[\\s\\S]*?```"), "")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .takeLast(20_000)
    }
    fun setMode(mode: AgentMode) = _ui.update { it.copy(mode = mode) }
    fun setSessionWriteAllowed(allowed: Boolean) = setPermissionMode(
        if (allowed) SessionPermissionMode.AUTO else SessionPermissionMode.ASK,
    )

    fun setPermissionMode(mode: SessionPermissionMode) = _ui.update {
        it.copy(permissionMode = mode, sessionWriteAllowed = mode != SessionPermissionMode.ASK)
    }

    fun setTaskPetVisible(visible: Boolean) {
        val editor = uiPreferences.edit()
            .putBoolean("task_pet_visible", visible)
            .putBoolean("task_pet_minimized", false)
        if (visible) {
            // The in-app dock and the system overlay are two presentations of the
            // same pet. Enabling one must retire the other instead of duplicating it.
            editor.putBoolean("system_pet_enabled", false).putBoolean("system_pet_minimized", false)
            getApplication<Application>().stopService(Intent(getApplication(), PetOverlayService::class.java))
        }
        editor.apply()
        _ui.update { it.copy(taskPetVisible = visible, taskPetMinimized = false) }
    }

    fun setTaskPetMinimized(minimized: Boolean) {
        uiPreferences.edit().putBoolean("task_pet_minimized", minimized).putBoolean("task_pet_visible", true).apply()
        _ui.update { it.copy(taskPetVisible = true, taskPetMinimized = minimized) }
    }

    fun setPetTheme(theme: String) {
        val normalized = theme.takeIf { it in setOf("aurora", "ocean", "sunset", "forest") } ?: "aurora"
        uiPreferences.edit().putString("app_theme", normalized).putString("pet_theme", normalized).apply()
        _ui.update { it.copy(petTheme = normalized, appTheme = normalized) }
        if (uiPreferences.getBoolean("system_pet_enabled", false)) {
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), PetOverlayService::class.java).setAction(PetOverlayService.ACTION_SHOW),
            )
        }
    }

    fun setVoiceInputGesture(gesture: VoiceInputGesture) {
        uiPreferences.edit().putString("voice_input_gesture", gesture.name).apply()
        _ui.update { it.copy(voiceInputGesture = gesture) }
    }

    fun enableExtension(extensionId: String) {
        viewModelScope.launch { container.database.dao().setExtensionEnabled(extensionId, true) }
    }

    fun refreshTaskPetPreference() {
        val visible = uiPreferences.getBoolean("task_pet_visible", true)
        val minimized = uiPreferences.getBoolean("task_pet_minimized", false)
        _ui.update { it.copy(taskPetVisible = visible, taskPetMinimized = minimized) }
    }

    fun startAgent(immediate: Boolean = false, additionalSystemInstruction: String? = null) {
        val state = _ui.value
        if (state.prompt.isBlank()) return
        val workspace = selectedWorkspace() ?: return _ui.update { it.copy(message = "请先选择项目") }
        val workspaceBusy = state.taskHandles.values.any {
            it.workspaceId == workspace.id && it.queueState !in setOf(TaskQueueState.FINISHED, TaskQueueState.PAUSED)
        }
        if (immediate && workspaceBusy) container.coordinator.stopWorkspace(workspace.id)
        val attachments = buildList {
            if (state.attachLatestCapture) state.latestCapturePath?.let(::add)
            state.pendingVoiceAudioPath?.let(::add)
            addAll(state.pendingAttachments)
        }
        val continuingSessionId = state.selectedSessionId?.takeIf { sessionId ->
            state.events.isNotEmpty() &&
                state.sessions.any { it.id == sessionId && it.workspaceId == workspace.id } &&
                state.taskHandles[sessionId]?.runState !in setOf(AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL)
        }
        val workspaceRef = WorkspaceRef(workspace.id, workspace.name, workspace.localPath, workspace.externalTreeUri)
        val result = if (continuingSessionId != null) container.coordinator.continueSession(
            sessionId = continuingSessionId,
            prompt = state.prompt.trim(),
            mode = state.mode,
            sessionWriteAllowed = state.sessionWriteAllowed,
            sessionNormalShellAllowed = state.permissionMode == SessionPermissionMode.YOLO,
            profile = state.provider,
            workspace = workspaceRef,
            attachmentPaths = attachments,
            auxiliaryVisionModel = state.auxiliaryVisionModel,
            auxiliaryVisionProviderId = state.auxiliaryVisionProviderId,
            queueBehindWorkspace = !immediate,
            priority = immediate,
            voiceTurnId = state.activeVoiceTurnId,
            additionalSystemInstruction = additionalSystemInstruction,
        ) else container.coordinator.start(
            prompt = state.prompt.trim(),
            mode = state.mode,
            sessionWriteAllowed = state.sessionWriteAllowed,
            sessionNormalShellAllowed = state.permissionMode == SessionPermissionMode.YOLO,
            profileOverride = state.provider,
            workspaceOverride = workspaceRef,
            attachmentPaths = attachments,
            auxiliaryVisionModel = state.auxiliaryVisionModel,
            auxiliaryVisionProviderId = state.auxiliaryVisionProviderId,
            queueBehindWorkspace = !immediate,
            priority = immediate,
            voiceTurnId = state.activeVoiceTurnId,
            additionalSystemInstruction = additionalSystemInstruction,
        )
        result.onSuccess { sessionId ->
            if (continuingSessionId == null) selectSession(sessionId, loadPersisted = false)
            _ui.update { current -> current.copy(
                prompt = "",
                attachLatestCapture = false,
                pendingVoiceAudioPath = null,
                pendingAttachments = emptyList(),
                message = null,
                events = if (continuingSessionId == null) emptyList() else current.events,
            ) }
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), AgentForegroundService::class.java),
            )
        }.onFailure { error -> _ui.update { it.copy(message = error.message) } }
    }

    fun stopAgent() {
        val sessionId = _ui.value.selectedSessionId ?: return
        _ui.update { it.copy(runState = AgentRunState.CANCELLED, message = "正在停止任务…") }
        container.coordinator.stop(sessionId)
    }

    fun stopTask(sessionId: String) {
        if (_ui.value.selectedSessionId == sessionId) {
            _ui.update { it.copy(runState = AgentRunState.CANCELLED, message = "正在停止任务…") }
        }
        container.coordinator.stop(sessionId)
    }

    fun undoFromTurn(turnIndex: Int, restoreProjectState: Boolean) {
        val sessionId = _ui.value.selectedSessionId ?: return
        container.coordinator.stop(sessionId)
        viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            if (restoreProjectState) {
                val restored = container.coordinator.restoreProjectCheckpoint(sessionId)
                if (restored.isFailure) {
                    _ui.update { it.copy(message = restored.exceptionOrNull()?.message ?: "项目状态恢复失败") }
                    return@launch
                }
            }
            val rewound = container.database.dao().rewindFromTurn(sessionId, turnIndex)
            val remainingEvents = if (rewound) {
                container.database.dao().events(sessionId).mapNotNull { entity ->
                    runCatching { eventJson.decodeFromString<AgentEvent>(entity.payloadJson) }.getOrNull()
                }
            } else null
            container.coordinator.clearFinishedHandle(sessionId)
            _ui.update {
                it.copy(
                    runState = AgentRunState.IDLE,
                    events = remainingEvents?.let(::mergeEventDeltas) ?: it.events,
                    message = if (rewound) {
                        if (restoreProjectState) "已撤回所选轮次及后续对话，并恢复 Git 项目状态" else "已撤回所选轮次及后续对话"
                    } else "没有可撤回的对话",
                )
            }
            if (restoreProjectState) refreshFiles()
        }
    }

    fun resolveApproval(decision: ApprovalDecision) {
        _ui.value.selectedSessionId?.let { container.coordinator.resolveApproval(it, decision) }
    }

    fun selectWorkspace(workspaceId: String) {
        val workspace = _ui.value.workspaces.firstOrNull { it.id == workspaceId } ?: return
        closeTerminal()
        _ui.update {
            it.copy(
                selectedWorkspaceId = workspace.id,
                selectedSessionId = null,
                events = emptyList(),
                selectedFile = null,
                editorText = "",
                editorDirty = false,
            )
        }
        uiPreferences.edit().putString("active_workspace_id", workspace.id).remove("active_session_id").apply()
        viewModelScope.launch { container.database.dao().touchWorkspace(workspaceId) }
        refreshFiles()
    }

    fun createProject(name: String) {
        val normalized = name.trim()
        val error = validateProjectName(normalized)
        if (error != null) return _ui.update { it.copy(message = error) }
        viewModelScope.launch(Dispatchers.IO) {
            if (_ui.value.workspaces.any { it.name.equals(normalized, ignoreCase = true) }) {
                return@launch _ui.update { it.copy(message = "项目名称已存在，请重新命名") }
            }
            val directory = File(container.projectsRoot, normalized).canonicalFile
            require(directory.parentFile == container.projectsRoot.canonicalFile) { "项目路径越界" }
            if (directory.exists()) return@launch _ui.update { it.copy(message = "Project 目录中已存在同名文件夹") }
            check(directory.mkdirs()) { "无法创建项目目录" }
            val initialized = ensureGitRepository(directory)
            if (initialized.isFailure) {
                directory.deleteRecursively()
                return@launch _ui.update {
                    it.copy(message = "项目创建失败：${initialized.exceptionOrNull()?.message ?: "Git 初始化失败"}")
                }
            }
            val workspace = WorkspaceEntity(
                id = UUID.randomUUID().toString(),
                name = normalized,
                localPath = directory.absolutePath,
            )
            container.database.dao().upsertWorkspace(workspace)
            _ui.update { it.copy(selectedWorkspaceId = workspace.id, message = "已创建 Project/$normalized，并初始化 Git main 分支") }
            refreshFiles()
        }
    }

    fun importProjectZip(uri: Uri, requestedName: String) {
        val normalized = requestedName.removeSuffix(".zip").trim()
        validateProjectName(normalized)?.let { error -> return _ui.update { it.copy(message = error) } }
        viewModelScope.launch(Dispatchers.IO) {
            if (_ui.value.workspaces.any { it.name.equals(normalized, ignoreCase = true) }) {
                return@launch _ui.update { it.copy(message = "项目名称已存在，请重命名 ZIP 或新建不同项目名") }
            }
            val directory = File(container.projectsRoot, normalized).canonicalFile
            require(directory.parentFile == container.projectsRoot.canonicalFile) { "项目路径越界" }
            if (directory.exists()) return@launch _ui.update { it.copy(message = "Project 目录中已存在同名文件夹") }
            val importer = ProjectArchiveImporter(
                getApplication<Application>().contentResolver,
                File(getApplication<Application>().cacheDir, "project-import"),
            )
            runCatching { importer.extract(uri, directory) }
                .onSuccess { report ->
                    val checkpoint = commitImportedProject(directory)
                    if (checkpoint.isFailure) {
                        val trash = File(container.projectsRoot.parentFile, ".trash/import-${System.currentTimeMillis()}-$normalized")
                        trash.parentFile?.mkdirs()
                        directory.renameTo(trash)
                        return@onSuccess _ui.update {
                            it.copy(message = "ZIP 已解压但 Git 检查点失败，项目已移入回收站：${checkpoint.exceptionOrNull()?.message}")
                        }
                    }
                    val workspace = WorkspaceEntity(UUID.randomUUID().toString(), normalized, directory.absolutePath)
                    container.database.dao().upsertWorkspace(workspace)
                    _ui.update {
                        it.copy(
                            selectedWorkspaceId = workspace.id,
                            message = "已导入 $normalized：${report.fileCount} 个文件，${formatBytes(report.expandedBytes)}，Git 检查点已建立",
                        )
                    }
                    refreshFiles()
                }
                .onFailure { error -> _ui.update { it.copy(message = "项目 ZIP 导入失败：${error.message}") } }
        }
    }

    private suspend fun commitImportedProject(directory: File): Result<Unit> = runCatching {
        val result = container.runtime.run(
            RunRequest(
                command = """
                    set -eu
                    mkdir -p .phoneagent-disabled-hooks
                    if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
                      git init -q -b main 2>/dev/null || { git init -q; git branch -M main; }
                    fi
                    git config user.name sai
                    git config user.email sai@localhost
                    git config core.hooksPath .phoneagent-disabled-hooks
                    git config core.fsmonitor false
                    git add -A
                    git commit --allow-empty -qm '[sai] import checkpoint'
                """.trimIndent(),
                workingDirectory = "/home/phoneagent",
                workspaceHostPath = directory.absolutePath,
                timeoutMillis = 120_000,
            ),
        )
        check(result.exitCode == 0) { (result.stderr.ifBlank { result.stdout }).takeLast(2_000) }
    }

    fun deleteProject(workspaceId: String) {
        val workspace = _ui.value.workspaces.firstOrNull { it.id == workspaceId }
            ?: return _ui.update { it.copy(message = "项目不存在") }
        container.coordinator.stopWorkspace(workspaceId)
        viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            val source = runCatching { File(workspace.localPath).canonicalFile }.getOrElse { error ->
                return@launch _ui.update { it.copy(message = "无法解析项目目录：${error.message}") }
            }
            val workspacesRoot = File(getApplication<Application>().filesDir, "workspaces").canonicalFile
            if (source != workspacesRoot && !source.path.startsWith(workspacesRoot.path + File.separator)) {
                return@launch _ui.update { it.copy(message = "拒绝删除应用私有工作区之外的目录") }
            }
            val trashRoot = File(workspacesRoot, ".trash").apply { mkdirs() }.canonicalFile
            val trashEntry = File(
                trashRoot,
                "${workspace.name.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]"), "_")}-${workspace.id.take(8)}-${System.currentTimeMillis()}",
            )
            if (source.exists() && !source.renameTo(trashEntry)) {
                return@launch _ui.update { it.copy(message = "项目正被占用，无法移入回收站；请稍后重试") }
            }
            val worktreeRoot = File(source.parentFile, ".phoneagent-worktrees/$workspaceId")
            if (worktreeRoot.exists()) {
                val worktreeTrash = File(trashEntry, ".phoneagent-task-worktrees")
                runCatching {
                    trashEntry.mkdirs()
                    worktreeRoot.renameTo(worktreeTrash)
                }
            }
            container.database.dao().deleteWorkspace(workspaceId)
            container.coordinator.clearWorkspaceHandles(workspaceId)

            val remaining = _ui.value.workspaces.count { it.id != workspaceId }
            if (remaining == 0) {
                val defaultDirectory = container.workspace.apply { mkdirs() }
                val gitResult = ensureGitRepository(defaultDirectory)
                if (gitResult.isFailure) {
                    return@launch _ui.update { it.copy(message = "项目已移入回收站，但默认项目 Git 初始化失败：${gitResult.exceptionOrNull()?.message}") }
                }
                container.database.dao().upsertWorkspace(
                    WorkspaceEntity(
                        id = TaskSupervisor.DEFAULT_WORKSPACE_ID,
                        name = "默认项目",
                        localPath = defaultDirectory.absolutePath,
                    ),
                )
            }
            _ui.update { state ->
                val fallbackWorkspaceId = state.workspaces.firstOrNull { it.id != workspaceId }?.id
                    ?: TaskSupervisor.DEFAULT_WORKSPACE_ID
                state.copy(
                    selectedWorkspaceId = if (state.selectedWorkspaceId == workspaceId) fallbackWorkspaceId else state.selectedWorkspaceId,
                    selectedSessionId = state.selectedSessionId?.takeUnless { sessionId ->
                        state.sessions.any { it.id == sessionId && it.workspaceId == workspaceId }
                    },
                    events = if (state.selectedWorkspaceId == workspaceId) emptyList() else state.events,
                    message = "项目“${workspace.name}”已移入 sai 回收站",
                )
            }
        }
    }

    fun renameProject(workspaceId: String, name: String) {
        val normalized = name.trim()
        validateProjectName(normalized)?.let { error -> return _ui.update { it.copy(message = error) } }
        viewModelScope.launch { container.database.dao().renameWorkspace(workspaceId, normalized) }
    }

    fun selectSession(sessionId: String, loadPersisted: Boolean = true) {
        selectedSessionEventsJob?.cancel()
        val session = _ui.value.sessions.firstOrNull { it.id == sessionId }
        val selection = session?.let { decodeSessionReasoning(it.reasoningConfigJson) } ?: ReasoningSelection()
        val savedProvider = session?.let { selected ->
            _ui.value.providerProfiles.firstOrNull { it.id == selected.providerId }?.copy(
                defaultModel = selected.model,
                reasoningEffort = selection.legacyEffort(),
                reasoningSelection = selection,
            )
        }
        val missingProvider = session != null && savedProvider == null
        val sessionProvider = savedProvider ?: session?.let { selected ->
            _ui.value.provider.copy(
                id = selected.providerId,
                displayName = "${selected.providerId}（已删除）",
                defaultModel = selected.model,
                reasoningEffort = selection.legacyEffort(),
                reasoningSelection = selection,
            )
        }
        _ui.update {
            it.copy(
                selectedSessionId = sessionId,
                selectedWorkspaceId = session?.workspaceId ?: it.selectedWorkspaceId,
                events = emptyList(),
                runState = it.taskHandles[sessionId]?.runState ?: session?.runState() ?: AgentRunState.IDLE,
                provider = sessionProvider ?: it.provider,
                message = if (missingProvider) "此会话的模型提供商已删除，请重新绑定模型" else it.message,
            )
        }
        uiPreferences.edit()
            .putString("active_session_id", sessionId)
            .putString("active_workspace_id", session?.workspaceId ?: _ui.value.selectedWorkspaceId)
            .putString("active_provider_id", sessionProvider?.id ?: _ui.value.provider.id)
            .putString("active_model_id", sessionProvider?.defaultModel ?: _ui.value.provider.defaultModel)
            .apply()
        viewModelScope.launch { container.database.dao().setSessionUnread(sessionId, false) }
        selectedSessionEventsJob = viewModelScope.launch {
            container.database.dao().observeEvents(sessionId).collectLatest { entities ->
                val decoded = entities.mapNotNull { entity ->
                    runCatching { eventJson.decodeFromString<AgentEvent>(entity.payloadJson) }.getOrNull()
                }
                _ui.update { state -> if (state.selectedSessionId == sessionId) state.copy(events = mergeEventDeltas(decoded)) else state }
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val normalized = title.trim().take(100)
        if (normalized.isEmpty()) return
        viewModelScope.launch { container.database.dao().renameSession(sessionId, normalized) }
    }

    fun deleteSession(sessionId: String) {
        container.coordinator.stop(sessionId)
        viewModelScope.launch {
            container.database.dao().deleteSession(sessionId)
            _ui.update { state -> if (state.selectedSessionId == sessionId) state.copy(selectedSessionId = null, events = emptyList()) else state }
        }
    }

    fun toggleSessionPinned(sessionId: String) {
        val session = _ui.value.sessions.firstOrNull { it.id == sessionId } ?: return
        viewModelScope.launch { container.database.dao().setSessionPinned(sessionId, !session.pinned) }
    }

    fun resumeSession(sessionId: String) {
        container.coordinator.resume(sessionId).onFailure { error -> _ui.update { it.copy(message = error.message) } }
        ContextCompat.startForegroundService(getApplication(), Intent(getApplication(), AgentForegroundService::class.java))
    }

    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = selectedWorkspaceDirectory()
            val state = _ui.value
            val directory = if (state.currentDirectory.isBlank()) root else safeFile(state.currentDirectory)
            val search = state.fileSearch.trim()
            val sequence = if (search.isNotEmpty()) root.walkTopDown().drop(1) else directory.listFiles().orEmpty().asSequence()
            val items = sequence
                .filter { state.showHiddenFiles || !it.name.startsWith('.') }
                .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
                .take(2_000).map {
                FileItem(it.relativeTo(root).invariantSeparatorsPath, it.isDirectory, it.length())
            }.sortedWith(compareByDescending<FileItem> { it.directory }.thenBy { it.path.lowercase() }).toList()
            _ui.update { it.copy(files = items) }
        }
    }

    fun openDirectory(path: String) {
        val directory = runCatching { safeFile(path) }.getOrNull() ?: return
        if (!directory.isDirectory) return
        _ui.update { it.copy(currentDirectory = path, selectedFile = null, editorText = "", editorDirty = false) }
        refreshFiles()
    }

    fun directoryUp() {
        val current = _ui.value.currentDirectory
        val parent = current.substringBeforeLast('/', "")
        openDirectory(parent)
    }

    fun setFileSearch(value: String) {
        _ui.update { it.copy(fileSearch = value) }
        refreshFiles()
    }

    fun toggleHiddenFiles() {
        _ui.update { it.copy(showHiddenFiles = !it.showHiddenFiles) }
        refreshFiles()
    }

    fun openFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = safeFile(path)
            if (!file.isFile) return@launch
            val readOnly = file.length() > 5_000_000 || looksBinary(file)
            val text = if (looksBinary(file)) {
                "二进制文件（${file.length()} 字节）\n\n此类型仅支持只读预览。"
            } else file.inputStream().bufferedReader().use { it.readText().take(5_000_000) }
            _ui.update { it.copy(
                selectedFile = path,
                editorText = text,
                editorDirty = false,
                editorReadOnly = readOnly,
                section = MainSection.FILES,
            ) }
        }
    }

    fun openArtifact(target: String) {
        val workspace = selectedWorkspaceDirectory().canonicalFile
        val cleaned = target.trim().removePrefix("file://").removeSurrounding("`")
        val candidate = runCatching {
            val direct = File(cleaned)
            (if (direct.isAbsolute) direct else File(workspace, cleaned.removePrefix("./"))).canonicalFile
        }.getOrNull() ?: return showMessage("文件路径无效")
        if (candidate != workspace && !candidate.path.startsWith(workspace.path + File.separator)) {
            return showMessage("文件不在当前项目中")
        }
        if (!candidate.isFile) return showMessage("文件不存在：${candidate.name}")
        openFile(candidate.relativeTo(workspace).invariantSeparatorsPath)
    }

    fun resolveArtifactPath(target: String): String? {
        val workspace = selectedWorkspaceDirectory().canonicalFile
        val app = getApplication<Application>()
        val allowedRoots = listOf(workspace, app.filesDir.canonicalFile, app.cacheDir.canonicalFile)
        val cleaned = target.trim().removePrefix("file://").removeSurrounding("`")
        return runCatching {
            val direct = File(cleaned)
            val candidate = (if (direct.isAbsolute) direct else File(workspace, cleaned.removePrefix("./"))).canonicalFile
            candidate.takeIf { file ->
                file.isFile && allowedRoots.any { root -> file == root || file.path.startsWith(root.path + File.separator) }
            }?.absolutePath
        }.getOrNull()
    }

    fun openBrowserUrl(url: String) = _ui.update {
        it.copy(section = MainSection.BROWSER, browserPreviewUrl = url.trim())
    }

    fun updateEditor(text: String) = _ui.update { if (it.editorReadOnly) it else it.copy(editorText = text, editorDirty = true) }

    fun saveEditor() {
        val state = _ui.value
        val path = state.selectedFile ?: return
        if (state.editorReadOnly) return
        viewModelScope.launch(Dispatchers.IO) {
            safeFile(path).writeText(state.editorText)
            _ui.update { it.copy(editorDirty = false, message = "Saved $path") }
            refreshFiles()
        }
    }

    fun requestCloseEditor() {
        if (_ui.value.editorDirty) _ui.update { it.copy(editorCloseConfirmation = true) }
        else closeEditorDiscarding()
    }

    fun cancelCloseEditor() = _ui.update { it.copy(editorCloseConfirmation = false) }

    fun closeEditorDiscarding() = _ui.update { it.copy(
        selectedFile = null,
        editorText = "",
        editorDirty = false,
        editorReadOnly = false,
        editorCloseConfirmation = false,
    ) }

    fun saveAndCloseEditor() {
        saveEditor()
        closeEditorDiscarding()
    }

    fun createDirectory(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val directory = safeFile(path)
            check(directory.mkdirs() || directory.isDirectory)
            refreshFiles()
        }
    }

    fun moveToTrash(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = safeFile(path)
            val trashRoot = safeFile(".phoneagent/trash").apply { mkdirs() }
            val target = File(trashRoot, "${System.currentTimeMillis()}-${source.name}")
            check(source.renameTo(target)) { "无法移动到项目回收站" }
            if (_ui.value.selectedFile == path) closeEditorDiscarding()
            refreshFiles()
        }
    }

    fun createFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = safeFile(path)
            file.parentFile?.mkdirs()
            check(file.createNewFile() || file.isFile)
            refreshFiles()
            openFile(path)
        }
    }

    fun setTerminalCommand(command: String) = _ui.update { it.copy(terminalCommand = command) }

    fun updateTerminalCommandRealtime(command: String) {
        val previous = _ui.value.terminalCommand
        if (command == previous) return
        _ui.update { it.copy(terminalCommand = command) }
        val session = terminalSession ?: return
        val bytes = when {
            command.startsWith(previous) -> command.removePrefix(previous).toByteArray()
            previous.startsWith(command) -> ByteArray(previous.length - command.length) { 127.toByte() }
            else -> byteArrayOf(21) + command.toByteArray() // Ctrl-U, then replace the current shell line.
        }
        if (bytes.isEmpty()) return
        viewModelScope.launch {
            terminalWriteMutex.withLock {
                runCatching { session.write(bytes) }
                    .onFailure { error -> _ui.update { it.copy(message = error.message ?: "PTY 输入失败") } }
            }
        }
    }

    fun submitTerminalInput() {
        val session = terminalSession ?: return
        _ui.update { it.copy(terminalCommand = "") }
        viewModelScope.launch {
            terminalWriteMutex.withLock {
                runCatching { session.write(byteArrayOf('\n'.code.toByte())) }
                    .onFailure { error -> _ui.update { it.copy(message = error.message ?: "PTY 输入失败") } }
            }
        }
    }

    fun openTerminal() {
        if (terminalSession != null || terminalReader?.isActive == true) return
        terminalReader = viewModelScope.launch {
            val workspacePath = selectedWorkspaceDirectory().absolutePath
            runCatching { container.runtime.openPty("/home/phoneagent", workspaceHostPath = workspacePath) }
                .onSuccess { session ->
                    terminalSession = session
                    _ui.update { it.copy(
                        terminalConnected = true,
                        terminalOutput = (it.terminalOutput + "\n[PTY 已连接]\n").takeLast(200_000),
                    ) }
                    session.events.collect { event ->
                        when (event) {
                            is TerminalEvent.Output -> appendTerminal(event.bytes.toString(Charsets.UTF_8))
                            is TerminalEvent.Closed -> {
                                appendTerminal("\n[PTY 已退出：${event.exitCode}]\n")
                                terminalSession = null
                                _ui.update { it.copy(terminalConnected = false) }
                            }
                            is TerminalEvent.Failure -> {
                                appendTerminal("\n[PTY 错误：${event.message}]\n")
                                terminalSession = null
                                _ui.update { it.copy(terminalConnected = false) }
                            }
                        }
                    }
                }
                .onFailure { error ->
                    _ui.update { it.copy(terminalConnected = false, message = error.message ?: "无法启动 PTY") }
                }
        }
    }

    fun closeTerminal() {
        terminalSession?.close()
        terminalSession = null
        terminalReader?.cancel()
        terminalReader = null
        _ui.update { it.copy(terminalConnected = false) }
    }

    fun runTerminalCommand() {
        val command = _ui.value.terminalCommand.trim()
        if (command.isEmpty()) return
        val session = terminalSession
        if (session == null) {
            _ui.update { it.copy(message = "请先启动 PTY 终端") }
            return
        }
        _ui.update { it.copy(terminalCommand = "") }
        viewModelScope.launch {
            runCatching { session.write((command + "\n").toByteArray()) }
                .onFailure { error -> _ui.update { it.copy(message = error.message ?: "PTY 写入失败") } }
        }
    }

    fun sendTerminalInterrupt() {
        val session = terminalSession ?: return
        viewModelScope.launch { runCatching { session.write(byteArrayOf(3)) } }
    }

    fun selectProvider(profile: ProviderProfile) {
        container.providerSettings.select(profile.id)
        val normalized = ModelReasoningPolicy.normalize(profile)
        _ui.update {
            it.copy(
                provider = normalized,
                providerApiKey = "",
                providerSaved = false,
                availableModels = emptyList(),
                modelDiscoveryError = null,
            )
        }
        if (container.providerSettings.credentialFor(profile.id) != null) refreshModels()
        persistSelectedSessionModel(normalized)
    }

    fun addProvider(template: ProviderProfile = ProviderPresets.all.first()) {
        val suffix = UUID.randomUUID().toString().take(8)
        selectProvider(template.copy(id = "${template.id}-$suffix", displayName = "${template.displayName} $suffix"))
    }

    fun deleteActiveProvider() {
        val providerId = _ui.value.provider.id
        viewModelScope.launch {
            if (!container.providerSettings.delete(providerId)) {
                _ui.update { it.copy(message = "至少保留一个模型提供商") }
            } else {
                val next = container.providerSettings.profile.value
                _ui.update { it.copy(provider = next, providerApiKey = "", availableModels = emptyList()) }
                if (container.providerSettings.credentialFor(next.id) != null) refreshModels()
            }
        }
    }
    fun updateProvider(profile: ProviderProfile) {
        val normalized = ModelReasoningPolicy.normalize(profile)
        _ui.update { it.copy(provider = normalized, providerSaved = false) }
        uiPreferences.edit()
            .putString("active_provider_id", normalized.id)
            .putString("active_model_id", normalized.defaultModel)
            .apply()
        persistSelectedSessionModel(normalized)
    }
    fun updateApiKey(value: String) = _ui.update { it.copy(providerApiKey = value, providerSaved = false) }

    fun updateProviderProtocol(protocol: ProviderProtocol) {
        val current = _ui.value.provider
        val requestPath = when (protocol) {
            ProviderProtocol.OPENAI_RESPONSES -> "/v1/responses"
            ProviderProtocol.OPENAI_CHAT -> "/v1/chat/completions"
            ProviderProtocol.ANTHROPIC_MESSAGES -> "/v1/messages"
            ProviderProtocol.GEMINI_NATIVE -> "/v1beta/models/{model}:streamGenerateContent"
        }
        val modelsPath = if (protocol == ProviderProtocol.GEMINI_NATIVE) "/v1beta/models" else "/v1/models"
        val headers = if (protocol == ProviderProtocol.ANTHROPIC_MESSAGES) {
            current.customHeaders + ("anthropic-version" to "2023-06-01")
        } else current.customHeaders - "anthropic-version"
        updateProvider(current.copy(protocol = protocol, requestPath = requestPath, modelsPath = modelsPath, customHeaders = headers))
    }

    fun selectModel(model: ModelInfo) {
        updateProvider(
            _ui.value.provider.copy(
            defaultModel = model.id,
            contextWindow = model.contextWindow ?: _ui.value.provider.contextWindow,
            modelReasoningCapabilities = model.reasoningCapabilities?.let {
                _ui.value.provider.modelReasoningCapabilities + (model.id to it)
            } ?: _ui.value.provider.modelReasoningCapabilities,
            ),
        )
        _ui.update { it.withAutomaticVisionModel() }
    }

    fun saveProvider() {
        val state = _ui.value
        viewModelScope.launch {
            val chars = state.providerApiKey.takeIf(String::isNotBlank)?.toCharArray()
            container.providerSettings.save(state.provider, chars)
            chars?.fill('\u0000')
            _ui.update { it.copy(providerApiKey = "", providerSaved = true, message = "Provider settings encrypted and saved") }
            refreshModels()
        }
    }

    fun refreshModels() {
        if (_ui.value.modelDiscoveryRunning) return
        val state = _ui.value
        val enteredKey = state.providerApiKey.takeIf(String::isNotBlank)
        val credential = enteredKey?.let(::ProviderCredential)
            ?: container.providerSettings.credentialFor(state.provider.id)
        if (credential == null) {
            _ui.update { it.copy(modelDiscoveryError = "请先填写并保存 API Key") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(modelDiscoveryRunning = true, modelDiscoveryError = null) }
            runCatching { ProviderFactory.create(_ui.value.provider).listModels(credential) }
                .onSuccess { models ->
                    val providerId = _ui.value.provider.id
                    container.database.dao().deleteDiscoveredProviderModels(providerId)
                    container.database.dao().upsertProviderModels(models.distinctBy(ModelInfo::id).map { model ->
                        ProviderModelEntity(
                            id = "$providerId:${model.id}",
                            providerId = providerId,
                            modelId = model.id,
                            displayName = model.displayName,
                            contextWindow = model.contextWindow ?: _ui.value.provider.contextWindow,
                            capabilitiesJson = model.reasoningCapabilities?.let {
                                eventJson.encodeToString(com.phoneagent.provider.ModelReasoningCapabilities.serializer(), it)
                            } ?: "{}",
                            reasoningEffortsJson = model.reasoningCapabilities?.supportedEfforts?.let {
                                eventJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.phoneagent.provider.ReasoningEffort.serializer()), it)
                            } ?: "[]",
                        )
                    })
                    _ui.update { it.copy(
                        availableModels = models.distinctBy(ModelInfo::id).sortedBy(ModelInfo::id),
                        modelDiscoveryRunning = false,
                        modelDiscoveryError = null,
                        message = "已获取 ${models.size} 个可用模型",
                    ) }
                }
                .onFailure { error ->
                    _ui.update { it.copy(
                        modelDiscoveryRunning = false,
                        modelDiscoveryError = error.message ?: "模型列表获取失败",
                    ) }
                }
        }
    }

    fun probeRuntime() {
        viewModelScope.launch {
            val capability = container.runtime.probe()
            _ui.update { it.copy(runtimeCapability = capability) }
            if (capability.available) {
                refreshRuntimePackages()
                container.dshRuntime.ensureStarted()
                ContextCompat.startForegroundService(
                    getApplication(),
                    Intent(getApplication(), AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_DSH),
                )
            }
        }
    }

    fun restartDshRuntime() = viewModelScope.launch { container.dshRuntime.restart() }

    fun updateGitHubToken(value: String) = _ui.update { it.copy(githubTokenInput = value) }

    fun refreshVoiceModelPack() = _ui.update { it.copy(voiceModelPackInstalled = VoiceModelPack.isInstalled(getApplication())) }

    fun refreshGitHubCli() {
        if (_ui.value.githubCliBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(githubCliBusy = true) }
            val status = container.githubCli.installAndStatus()
            _ui.update { it.copy(githubCliBusy = false, githubCliStatus = status) }
        }
    }

    fun loginGitHub() {
        val token = _ui.value.githubTokenInput.trim()
        if (token.isBlank() || _ui.value.githubCliBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(githubCliBusy = true) }
            container.githubCli.loginWithToken(token.toCharArray())
                .onSuccess { status ->
                    _ui.update { it.copy(githubCliBusy = false, githubCliStatus = status, githubTokenInput = "", message = "GitHub 登录成功") }
                }
                .onFailure { error ->
                    _ui.update { it.copy(githubCliBusy = false, message = "GitHub 登录失败：${error.message}") }
                }
        }
    }

    fun logoutGitHub() {
        if (_ui.value.githubCliBusy) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(githubCliBusy = true) }
            val status = container.githubCli.logout()
            _ui.update { it.copy(githubCliBusy = false, githubCliStatus = status, message = "已退出 GitHub") }
        }
    }

    fun refreshRuntimePackages() {
        if (_ui.value.runtimePackageOperation != null) return
        viewModelScope.launch {
            runCatching { RuntimePackageManager(container.runtime).query() }
                .onSuccess { packages -> _ui.update { it.copy(runtimePackages = packages) } }
                .onFailure { error -> _ui.update { it.copy(message = error.message ?: "无法读取开发环境状态") } }
        }
    }

    fun requestRuntimePackage(group: RuntimePackageGroup, action: RuntimePackageAction) {
        if (_ui.value.runtimePackageOperation != null) return
        viewModelScope.launch {
            _ui.update { it.copy(runtimePackageOperation = "正在模拟安装计划…", runtimePackagePlan = null, runtimePackageProgress = null) }
            RuntimePackageManager(container.runtime).simulate(group, action) { stage ->
                _ui.update { it.copy(runtimePackageOperation = stage) }
            }
                .onSuccess { plan -> _ui.update { it.copy(
                    runtimePackageOperation = null,
                    runtimePackageRequest = RuntimePackageRequest(group, action),
                    runtimePackagePlan = plan,
                ) } }
                .onFailure { error -> _ui.update { it.copy(
                    runtimePackageOperation = null,
                    message = error.message ?: "无法生成 apt 安装预检",
                ) } }
        }
    }

    fun cancelRuntimePackageRequest() = _ui.update { it.copy(runtimePackageRequest = null, runtimePackagePlan = null) }

    fun confirmRuntimePackageRequest() {
        val request = _ui.value.runtimePackageRequest ?: return
        if (_ui.value.runtimePackageOperation != null) return
        runtimePackageJob = viewModelScope.launch {
            if (_ui.value.runtimePackagePlan?.allowed == false) return@launch
            _ui.update { it.copy(
                runtimePackageRequest = null,
                runtimePackagePlan = null,
                runtimePackageOperation = "准备中…",
                runtimePackageProgress = RuntimePackageProgress("准备中…", 0, "正在启动软件包管理器"),
            ) }
            RuntimePackageManager(container.runtime).change(request.group, request.action) { progress ->
                _ui.update { it.copy(runtimePackageOperation = progress.stage, runtimePackageProgress = progress) }
            }.onSuccess {
                _ui.update { it.copy(runtimePackageOperation = null, runtimePackageProgress = null, message = "${request.group.title}操作完成") }
                probeRuntime()
                refreshRuntimePackages()
            }.onFailure { error ->
                _ui.update { it.copy(runtimePackageOperation = null, runtimePackageProgress = null, message = error.message ?: "开发环境操作失败") }
                refreshRuntimePackages()
            }
        }
    }

    fun cancelRuntimePackageOperation() {
        runtimePackageJob?.cancel()
        runtimePackageJob = null
        _ui.update { it.copy(runtimePackageOperation = null, runtimePackageProgress = null, message = "已停止开发环境操作；下次操作会先修复未完成的 dpkg 配置") }
        refreshRuntimePackages()
    }

    fun runRuntimeSelfTest() {
        if (_ui.value.runtimeSelfTestRunning) return
        viewModelScope.launch {
            _ui.update { it.copy(runtimeSelfTestRunning = true, runtimeSelfTestOutput = "正在运行本地编码闭环自检…") }
            val result = container.runtime.run(RunRequest(
                command = RUNTIME_SELF_TEST,
                workingDirectory = "/home/phoneagent",
                timeoutMillis = 120_000,
                outputLimitBytes = 500_000,
            ))
            val output = buildString {
                append(result.stdout)
                if (result.stderr.isNotBlank()) appendLine().append(result.stderr)
                appendLine().append("exit=${result.exitCode}, duration=${result.durationMillis}ms")
            }
            _ui.update { it.copy(runtimeSelfTestRunning = false, runtimeSelfTestOutput = output) }
        }
    }

    fun runAgentE2eTest() {
        if (_ui.value.e2eTestRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(e2eTestRunning = true, message = "正在创建全链路测试项目") }
            val name = "sai-E2E-${System.currentTimeMillis()}"
            val directory = File(container.projectsRoot, name)
            runCatching {
                check(directory.mkdirs()) { "无法创建隔离测试项目" }
                ensureGitRepository(directory).getOrThrow()
                val workspace = WorkspaceEntity(UUID.randomUUID().toString(), name, directory.absolutePath)
                container.database.dao().upsertWorkspace(workspace)
                val prompt = """
                    在当前空项目中完成 sai 全链路验收，不要安装任何第三方依赖：
                    1. 创建 index.html、styles.css、app.js、server.py、test_server.py 和 README.md。
                    2. 页面实现简洁的登录 UI，HTML/CSS/JS 必须分文件；测试账号固定为 demo@example.com / sai-test。
                    3. server.py 只使用 Python 标准库，提供静态文件和 POST /api/login；正确账号返回 JSON success=true，错误账号返回 401。
                    4. 使用 unittest 运行后端测试，然后在随机可用的 127.0.0.1 端口启动后台服务。
                    5. 必须使用 browser_observe/browser_action 打开页面，定位表单节点，填写测试账号和密码，以 finalSubmit=true 提交，并观察成功状态。
                    6. 使用 browser_screenshot 保存结果截图，运行 git diff --check，并提交 [sai] E2E web login。
                    7. 最后用简短 Markdown 汇报文件、测试、浏览器操作和提交哈希。不得使用真实凭据或访问公网。
                """.trimIndent()
                container.dshRuntime.ensureStarted()
                container.dshRuntime.awaitReady(90_000)
                val sessionId = container.dshApi.ensureSession(
                    sessionId = null,
                    cwd = "/home/phoneagent/Project/$name",
                )
                check(container.dshApi.prompt(sessionId, prompt, steer = false)) { "DSH 拒绝了测试任务" }
                sessionId to workspace
            }.onSuccess { (sessionId, workspace) ->
                _ui.update {
                    it.copy(
                        e2eTestRunning = false,
                        selectedWorkspaceId = workspace.id,
                        selectedSessionId = sessionId,
                        section = MainSection.AGENT,
                        events = emptyList(),
                        message = "全链路测试已启动；结果、token、缓存命中和人民币费用将在任务底部显示",
                    )
                }
                ContextCompat.startForegroundService(
                    getApplication(),
                    Intent(getApplication(), AgentForegroundService::class.java).setAction(AgentForegroundService.ACTION_DSH),
                )
            }.onFailure { error ->
                if (directory.exists()) directory.deleteRecursively()
                _ui.update { it.copy(e2eTestRunning = false, message = "全链路测试无法启动：${error.message}") }
            }
        }
    }

    fun installRootfs() {
        if (_ui.value.rootfsInstallState is RootfsInstallState.Downloading ||
            _ui.value.rootfsInstallState is RootfsInstallState.CopyingEmbedded ||
            _ui.value.rootfsInstallState is RootfsInstallState.Extracting ||
            _ui.value.rootfsInstallState is RootfsInstallState.Provisioning) return
        viewModelScope.launch {
            val manifest = PhoneAgentRootfs.forDevice()
            val rootfsReady = container.rootfsInstaller.rootfsDir.resolve("bin/bash").isFile &&
                container.rootfsInstaller.rootfsDir.resolve("usr/bin/git").isFile &&
                container.rootfsInstaller.rootfsDir.resolve(".sai-offline-base-v1").isFile
            val installation = if (rootfsReady) Result.success(Unit) else {
                container.rootfsInstaller.install(manifest) { installState ->
                    _ui.update { it.copy(rootfsInstallState = installState) }
                }
            }
            installation.onSuccess {
                RuntimeProvisioner(container.runtime).provision { stage ->
                    _ui.update { it.copy(rootfsInstallState = RootfsInstallState.Provisioning(stage)) }
                }.onSuccess {
                    container.database.dao().workspaces().forEach { workspace ->
                        ensureGitRepository(File(workspace.localPath))
                    }
                    _ui.update { it.copy(
                        rootfsInstallState = RootfsInstallState.Ready(manifest.version),
                        message = "内置 Debian 与 Git 已离线初始化；Python 等工具链可按需安装",
                    ) }
                    probeRuntime()
                    refreshGitHubCli()
                }.onFailure { error ->
                    _ui.update { it.copy(rootfsInstallState = RootfsInstallState.Failed(error.message ?: "运行时初始化失败")) }
                }
            }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }
    fun showMessage(message: String) = _ui.update { it.copy(message = message) }

    fun pairDesktop(qrPayload: String) = desktopConnection.pair(qrPayload)

    fun disconnectDesktop() = desktopConnection.disconnect()
    fun toggleLatestCaptureAttachment() = _ui.update { state ->
        if (state.latestCapturePath == null) state.copy(message = "请先在设置中按需捕获屏幕")
        else state.copy(attachLatestCapture = !state.attachLatestCapture)
    }
    fun attachFiles(paths: List<String>) = _ui.update { state ->
        state.copy(
            pendingAttachments = (state.pendingAttachments + paths).distinct(),
            message = "已添加 ${paths.size} 个附件",
        )
    }
    fun attachProjectFile(path: String) {
        val file = runCatching { safeFile(path) }.getOrElse {
            return _ui.update { state -> state.copy(message = it.message ?: "项目附件路径无效") }
        }
        if (!file.isFile) return _ui.update { it.copy(message = "请选择项目中的文件") }
        attachFiles(listOf(file.absolutePath))
    }
    fun removeAttachment(path: String) = _ui.update { state ->
        state.copy(pendingAttachments = state.pendingAttachments - path)
    }
    fun setAuxiliaryVisionModel(providerId: String, modelId: String) {
        uiPreferences.edit()
            .putString("auxiliary_vision_provider", providerId)
            .putString("auxiliary_vision_model", modelId)
            .apply()
        _ui.update { it.copy(auxiliaryVisionProviderId = providerId, auxiliaryVisionModel = modelId) }
    }

    fun setAuxiliaryVisionModel(value: String) =
        setAuxiliaryVisionModel(_ui.value.provider.id, value)

    private fun MainUiState.withAutomaticVisionModel(): MainUiState {
        if (ModelVisionPolicy.supportsImageInput(provider)) return this
        val selectedStillExists = providerModels.any {
            it.providerId == auxiliaryVisionProviderId && it.modelId == auxiliaryVisionModel
        }
        if (selectedStillExists) return this
        val candidates = providerModels.mapNotNull { model ->
            val profile = providerProfiles.firstOrNull { it.id == model.providerId } ?: return@mapNotNull null
            model.takeIf { ModelVisionPolicy.isVisionCandidate(profile, it.modelId) }
        }.sortedWith(compareByDescending<ProviderModelEntity> { it.providerId == provider.id }.thenBy { it.displayName })
        val choice = candidates.firstOrNull() ?: return copy(auxiliaryVisionProviderId = "", auxiliaryVisionModel = "")
        uiPreferences.edit()
            .putString("auxiliary_vision_provider", choice.providerId)
            .putString("auxiliary_vision_model", choice.modelId)
            .apply()
        return copy(auxiliaryVisionProviderId = choice.providerId, auxiliaryVisionModel = choice.modelId)
    }
    fun setExtensionQuery(query: String) = _ui.update { it.copy(extensionQuery = query) }

    fun loadExtensionRecommendations() {
        if (_ui.value.extensionSearchRunning) return
        viewModelScope.launch {
            _ui.update { it.copy(extensionSearchRunning = true, extensionError = null, extensionFeedTitle = "热门推荐") }
            runCatching { extensionCatalog.recommendations() }
                .onSuccess { results -> _ui.update { it.copy(
                    extensionSearchRunning = false,
                    extensionResults = results,
                    extensionError = if (results.isEmpty()) "暂时无法加载热门推荐，请稍后重试" else null,
                ) } }
                .onFailure { error -> _ui.update { it.copy(
                    extensionSearchRunning = false,
                    extensionError = error.message ?: "热门推荐加载失败",
                ) } }
        }
    }

    fun searchExtensions() {
        val query = _ui.value.extensionQuery.trim()
        if (query.isBlank()) return loadExtensionRecommendations()
        if (query.length < 2 || _ui.value.extensionSearchRunning) return
        viewModelScope.launch {
            _ui.update { it.copy(extensionSearchRunning = true, extensionError = null, extensionResults = emptyList(), extensionFeedTitle = "搜索结果") }
            val skills = runCatching { extensionCatalog.searchSkills(query) }
            val mcp = runCatching { extensionCatalog.searchMcp(query) }
            val plugins = runCatching { extensionCatalog.searchPlugins(query) }
            val results = (skills.getOrDefault(emptyList()) + mcp.getOrDefault(emptyList()) + plugins.getOrDefault(emptyList()))
                .distinctBy { "${it.kind}:${it.id}" }
                .sortedWith(compareByDescending<CatalogExtension> { it.installs != null }.thenByDescending { it.installs ?: 0L })
            val error = if (results.isEmpty()) {
                listOfNotNull(skills.exceptionOrNull()?.message, mcp.exceptionOrNull()?.message, plugins.exceptionOrNull()?.message).distinct().joinToString("；").ifBlank { "没有结果" }
            } else null
            _ui.update { it.copy(extensionSearchRunning = false, extensionResults = results, extensionError = error) }
        }
    }

    fun inspectExtension(item: CatalogExtension) {
        if (item.kind != ExtensionKind.SKILL) {
            _ui.update { it.copy(message = "MCP 项目需要在 MCP 标签中填写服务器配置后进行 Live 探测") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(extensionSearchRunning = true, extensionError = null, extensionPlan = null, extensionAudit = null) }
            val planResult = runCatching { extensionCatalog.stageSkill(item) }
            val audit = runCatching { extensionCatalog.skillAudit(item.id) }.getOrNull()
            planResult.onSuccess { plan -> _ui.update { it.copy(
                extensionSearchRunning = false,
                extensionPlan = plan,
                extensionAudit = audit,
            ) } }.onFailure { error -> _ui.update { it.copy(
                extensionSearchRunning = false,
                extensionError = error.message,
                message = "无法匿名获取 Skill 快照；可在市场浏览页确认 Git 来源后安装",
            ) } }
        }
    }

    fun inspectExtensionZip(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ExtensionZipImporter(getApplication<Application>().contentResolver).inspect(uri, displayName) }
                .onSuccess { plan -> _ui.update { it.copy(extensionPlan = plan, extensionAudit = null, extensionError = null) } }
                .onFailure { error -> _ui.update { it.copy(extensionError = error.message ?: "扩展 ZIP 检查失败") } }
        }
    }

    fun saveMcpServer(displayName: String, configJson: String) {
        val name = displayName.trim()
        if (name.isBlank()) return _ui.update { it.copy(message = "MCP 名称不能为空") }
        val normalized = runCatching { eventJson.parseToJsonElement(configJson).toString() }.getOrElse {
            return _ui.update { state -> state.copy(message = "MCP 配置不是有效 JSON：${it.message}") }
        }
        viewModelScope.launch {
            container.database.dao().upsertMcpServer(
                McpServerEntity(
                    id = "mcp-${UUID.randomUUID()}",
                    workspaceId = _ui.value.selectedWorkspaceId,
                    displayName = name,
                    configJson = normalized,
                ),
            )
            _ui.update { it.copy(message = "MCP 配置已保存，默认禁用") }
        }
    }

    fun toggleMcpServer(server: McpServerEntity) = viewModelScope.launch {
        container.database.dao().setMcpServerEnabled(server.id, !server.enabled)
    }

    fun removeMcpServer(server: McpServerEntity) = viewModelScope.launch {
        container.database.dao().deleteMcpServer(server.id)
    }

    fun probeMcpServer(server: McpServerEntity) {
        viewModelScope.launch {
            val result = runCatching {
                val root = eventJson.parseToJsonElement(server.configJson).jsonObject
                val transport = McpTransport.valueOf(root["transport"]?.jsonPrimitive?.content ?: "STREAMABLE_HTTP")
                require(transport != McpTransport.STDIO) { "stdio MCP 将在 Agent Debian Runtime 中启动；当前诊断仅保存配置，不从 Android 进程执行命令" }
                val config = McpServerConfig(
                    id = server.id,
                    displayName = server.displayName,
                    transport = transport,
                    url = root["url"]?.jsonPrimitive?.contentOrNull,
                )
                HttpMcpClient(config).use { client ->
                    val protocol = client.initialize()
                    val tools = client.listTools()
                    "协议 $protocol · ${tools.size} 个工具"
                }
            }
            val status = result.fold(onSuccess = { "READY · $it" }, onFailure = { "ERROR · ${it.message?.take(160)}" })
            container.database.dao().upsertMcpServer(server.copy(lastStatus = status, updatedAt = System.currentTimeMillis()))
            _ui.update { it.copy(message = if (result.isSuccess) "MCP 探测成功：${result.getOrNull()}" else "MCP 探测失败：${result.exceptionOrNull()?.message}") }
        }
    }

    fun saveHook(displayName: String, event: String, command: String) {
        val name = displayName.trim()
        if (name.isBlank() || command.isBlank()) return _ui.update { it.copy(message = "Hook 名称和命令不能为空") }
        val config = kotlinx.serialization.json.buildJsonObject {
            put("command", command.trim())
            put("timeoutMillis", 10_000)
            put("failurePolicy", "block")
        }.toString()
        viewModelScope.launch {
            container.database.dao().upsertHookConfig(
                HookConfigEntity(
                    id = "hook-${UUID.randomUUID()}",
                    workspaceId = _ui.value.selectedWorkspaceId,
                    displayName = name,
                    event = event,
                    configJson = config,
                ),
            )
            _ui.update { it.copy(message = "Hook 已保存，默认禁用") }
        }
    }

    fun toggleHook(hook: HookConfigEntity) = viewModelScope.launch {
        container.database.dao().setHookEnabled(hook.id, !hook.enabled)
    }

    fun removeHook(hook: HookConfigEntity) = viewModelScope.launch {
        container.database.dao().deleteHookConfig(hook.id)
    }

    fun cancelExtensionInstall() = _ui.update { it.copy(extensionPlan = null, extensionAudit = null) }

    fun confirmExtensionInstall() {
        val plan = _ui.value.extensionPlan ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { extensionInstaller.install(plan) }
                .onSuccess { target ->
                    container.database.dao().upsertExtension(
                        ExtensionEntity(
                            id = plan.id,
                            kind = plan.kind.name,
                            name = plan.name,
                            source = plan.source,
                            manifestJson = eventJson.encodeToString(ExtensionInstallPlan.serializer(), plan),
                            enabled = false,
                            version = plan.version,
                            sourceDigest = plan.sourceDigest,
                        ),
                    )
                    _ui.update { it.copy(extensionPlan = null, extensionAudit = null, message = "已安装到 ${target.name}，默认为禁用") }
                }
                .onFailure { error -> _ui.update { it.copy(extensionError = error.message) } }
        }
    }

    fun toggleExtension(extension: ExtensionEntity) {
        viewModelScope.launch { container.database.dao().setExtensionEnabled(extension.id, !extension.enabled) }
    }

    fun removeExtension(extension: ExtensionEntity) {
        viewModelScope.launch { container.database.dao().deleteExtension(extension.id) }
    }

    fun authorizeExternalTree(uri: Uri) {
        viewModelScope.launch {
            container.database.dao().upsertWorkspace(WorkspaceEntity(
                id = _ui.value.selectedWorkspaceId,
                name = selectedWorkspace()?.name ?: "默认项目",
                localPath = selectedWorkspaceDirectory().absolutePath,
                externalTreeUri = uri.toString(),
            ))
            _ui.update { it.copy(externalTreeUri = uri.toString(), message = "External directory authorized") }
        }
    }

    fun refreshAllFilesAccess() = _ui.update { it.copy(allFilesAccess = hasAllFilesAccess()) }

    private fun safeFile(path: String): File {
        val root = selectedWorkspaceDirectory().canonicalFile
        val file = File(root, path).canonicalFile
        require(file == root || file.path.startsWith(root.path + File.separator)) { "Path escapes workspace" }
        return file
    }

    private fun selectedWorkspace(): WorkspaceEntity? =
        _ui.value.workspaces.firstOrNull { it.id == _ui.value.selectedWorkspaceId }
            ?: if (_ui.value.selectedWorkspaceId == TaskSupervisor.DEFAULT_WORKSPACE_ID) {
                WorkspaceEntity(
                    id = TaskSupervisor.DEFAULT_WORKSPACE_ID,
                    name = "默认项目",
                    localPath = container.workspace.absolutePath,
                )
            } else null

    private fun selectedWorkspaceDirectory(): File =
        File(selectedWorkspace()?.localPath ?: container.workspace.absolutePath).apply { mkdirs() }

    private fun validateProjectName(name: String): String? = when {
        name.isBlank() -> "项目名称不能为空"
        name.length > 64 -> "项目名称不能超过 64 个字符"
        name == "." || name == ".." -> "项目名称无效"
        name.any { it in "\\/:*?\"<>|" || it.code < 32 } -> "项目名称不能包含路径或控制字符"
        name.endsWith('.') || name.endsWith(' ') -> "项目名称不能以句点或空格结尾"
        else -> null
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024 * 1024 -> "%.2f GB".format(value / (1024.0 * 1024 * 1024))
        value >= 1024L * 1024 -> "%.1f MB".format(value / (1024.0 * 1024))
        value >= 1024L -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }

    private fun appendTerminal(text: String) {
        val readable = ANSI_ESCAPE.replace(text, "")
        _ui.update { it.copy(terminalOutput = (it.terminalOutput + readable).takeLast(200_000)) }
    }

    private fun looksBinary(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val buffer = ByteArray(8_192)
            val length = stream.read(buffer)
            length > 0 && buffer.take(length).any { it == 0.toByte() }
        }
    }.getOrDefault(true)

    private fun initialRootfsState(): RootfsInstallState {
        val rootfs = container.rootfsInstaller.rootfsDir
        container.rootfsInstaller.repairRuntimeLayout(rootfs)
        if (!rootfs.resolve("bin/bash").isFile) return RootfsInstallState.NotInstalled
        if (!rootfs.resolve("usr/bin/git").isFile) return RootfsInstallState.NotInstalled
        if (!rootfs.resolve(".sai-offline-base-v1").isFile) return RootfsInstallState.NotInstalled
        if (!rootfs.resolve(".phoneagent-provisioned-v1").isFile) return RootfsInstallState.NotInstalled
        val version = rootfs.resolve(".phoneagent-version").takeIf(File::isFile)?.readText()?.trim()
            ?.takeIf(String::isNotEmpty) ?: "Debian 13"
        return RootfsInstallState.Ready(version)
    }

    private suspend fun ensureGitRepository(directory: File): Result<Unit> = runCatching {
        directory.mkdirs()
        val result = container.runtime.run(
            RunRequest(
                command = """
                    set -eu
                    if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
                      git init -q -b main 2>/dev/null || { git init -q; git branch -M main; }
                    fi
                    git config user.name sai
                    git config user.email sai@localhost
                    if ! git rev-parse --verify HEAD >/dev/null 2>&1; then
                      git commit --allow-empty -qm '[sai] initial checkpoint'
                    fi
                """.trimIndent(),
                workingDirectory = "/home/phoneagent",
                workspaceHostPath = directory.absolutePath,
                timeoutMillis = 60_000,
            ),
        )
        check(result.exitCode == 0) {
            (result.stderr.ifBlank { result.stdout }).takeLast(2_000).ifBlank { "本地 Git 不可用，请先完成 Debian 初始化" }
        }
    }

    private fun decodeSessionReasoning(raw: String): ReasoningSelection =
        runCatching { eventJson.decodeFromString<ReasoningSelection>(raw) }.getOrDefault(ReasoningSelection())

    private fun persistSelectedSessionModel(profile: ProviderProfile) {
        val state = _ui.value
        val sessionId = state.selectedSessionId ?: return
        val session = state.sessions.firstOrNull { it.id == sessionId } ?: return
        val taskState = state.taskHandles[sessionId]?.runState ?: session.runState()
        if (taskState in setOf(AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL)) {
            _ui.update { it.copy(message = "任务运行中不能切换模型") }
            return
        }
        viewModelScope.launch {
            container.database.dao().updateSessionModel(
                sessionId = sessionId,
                providerId = profile.id,
                model = profile.defaultModel,
                reasoningConfigJson = eventJson.encodeToString(ReasoningSelection.serializer(), profile.reasoningSelection),
            )
        }
    }

    override fun onCleared() {
        closeTerminal()
        super.onCleared()
    }

    companion object {
        private const val VOICE_CONVERSATION_POLICY = "Voice conversation mode is active. You MUST call the speak tool exactly once in every completed response to broadcast a concise spoken summary of no more than two short sentences. The application will not read the visible answer automatically, so never rely on visible text being spoken. Keep the visible response useful but concise. Never send the spoken summary as a user message. Never speak reasoning, code, diffs, logs, URLs, emoji, or secrets. If the user interrupts, treat the new speech as changed direction. This instruction is hidden runtime policy and must not be repeated to the user."
        private val ANSI_ESCAPE = Regex("(?:\\u001B\\[[0-?]*[ -/]*[@-~])|(?:\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))")
        private val RUNTIME_SELF_TEST = """
            set -eu
            test_dir=${'$'}(mktemp -d /home/phoneagent/.phoneagent-selftest-XXXXXX)
            trap 'rm -rf "${'$'}test_dir"' EXIT
            cd "${'$'}test_dir"
            printf 'def add(a, b):\n    return a + b\n' > calculator.py
            printf 'from calculator import add\n\ndef test_add():\n    assert add(2, 3) == 5\n' > test_calculator.py
            python3 -m pytest -q
            git init -q
            git config user.name sai
            git config user.email sai@localhost
            git add -- calculator.py test_calculator.py
            git commit -qm 'self-test baseline'
            printf '\n# sai diff verification\n' >> calculator.py
            git diff --check
            git diff -- calculator.py
            python3 -c 'import sys; print("PYTHON=" + sys.version.split()[0])'
            git --version
            printf 'PHONEAGENT_SELF_TEST=PASS\n'
        """.trimIndent()
    }
}

private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

private fun MainUiState.selectedSessionEntity(): SessionEntity? =
    selectedSessionId?.let { id -> sessions.firstOrNull { it.id == id } }

private fun SessionEntity.runState(): AgentRunState =
    runCatching { AgentRunState.valueOf(state) }.getOrDefault(AgentRunState.IDLE)

private fun mergeEventDeltas(source: List<AgentEvent>): List<AgentEvent> {
    val result = mutableListOf<AgentEvent>()
    source.forEach { event ->
        val merged = when {
            event is AgentEvent.AssistantDelta && result.lastOrNull() is AgentEvent.AssistantDelta -> {
                val previous = result.removeAt(result.lastIndex) as AgentEvent.AssistantDelta
                previous.copy(text = previous.text + event.text)
            }
            event is AgentEvent.ReasoningDelta && result.lastOrNull() is AgentEvent.ReasoningDelta -> {
                val previous = result.removeAt(result.lastIndex) as AgentEvent.ReasoningDelta
                previous.copy(text = previous.text + event.text)
            }
            else -> event
        }
        result += merged
    }
    return result.takeLast(1_000)
}
