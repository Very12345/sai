package com.phoneagent.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.phoneagent.app.MainUiState
import com.phoneagent.app.MainViewModel
import com.phoneagent.app.PhoneAgentApplication
import com.phoneagent.data.TerminalTabEntity
import com.phoneagent.runtime.LinuxRuntime
import com.phoneagent.runtime.PtySession
import com.phoneagent.runtime.TerminalEvent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal enum class WorkbenchWebMode(val queryValue: String) { FILES("files"), TERMINAL("terminal") }

@Composable
internal fun WorkbenchWebScreen(
    mode: WorkbenchWebMode,
    state: MainUiState,
    viewModel: MainViewModel,
    requestPhoneFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestState = rememberUpdatedState(state)
    val latestImport = rememberUpdatedState(requestPhoneFiles)
    if (mode == WorkbenchWebMode.FILES) {
        WorkbenchWebView(
            mode = mode,
            payload = filesPayload(state, viewModel),
            onMessage = { raw, reply ->
                val message = parseMessage(raw) ?: return@WorkbenchWebView
                val current = latestState.value
                fun currentPath(name: String): String = listOf(current.currentDirectory, name.trim())
                    .filter(String::isNotBlank).joinToString("/")
                when (message.action) {
                    "ready", "refresh" -> viewModel.refreshFilesAndStorage()
                    "select-root" -> message.string("id")?.let(viewModel::selectFileRoot)
                    "open-directory" -> message.string("path")?.let(viewModel::openDirectory)
                    "open-file" -> message.string("path")?.let(viewModel::openFile)
                    "open-path" -> message.string("value")?.let(viewModel::openTypedFilePath)
                    "up" -> viewModel.directoryUp()
                    "search" -> viewModel.setFileSearch(message.string("value").orEmpty())
                    "hidden" -> viewModel.toggleHiddenFiles()
                    "copy" -> message.string("path")?.let { viewModel.setFileClipboard(it, false) }
                    "cut" -> message.string("path")?.let { viewModel.setFileClipboard(it, true) }
                    "paste" -> viewModel.pasteFileClipboard()
                    "trash" -> message.string("path")?.let(viewModel::moveToTrash)
                    "share" -> message.string("path")?.let(viewModel::shareFileOrFolder)
                    "import" -> latestImport.value.invoke()
                    "new-folder" -> message.string("name")?.takeIf(String::isNotBlank)?.let { viewModel.createDirectory(currentPath(it)) }
                    "new-file" -> message.string("name")?.takeIf(String::isNotBlank)?.let { viewModel.createFile(currentPath(it)) }
                    "editor-save" -> {
                        viewModel.updateEditor(message.string("text").orEmpty())
                        viewModel.saveEditor()
                    }
                    "editor-close" -> viewModel.closeEditorDiscarding()
                }
                reply.postMessage("{\"ok\":true}")
            },
            modifier = modifier,
        )
        return
    }

    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as PhoneAgentApplication
    val workspace = state.workspaces.firstOrNull { it.id == state.selectedWorkspaceId }
    val workspaceId = workspace?.id ?: state.selectedWorkspaceId
    val workspacePath = workspace?.localPath ?: application.container.workspace.absolutePath
    val controller = remember(workspaceId, workspacePath) {
        WebTerminalControllerRegistry.get(application, workspaceId, workspacePath)
    }
    controller.start()
    val terminalState by controller.state.collectAsState()
    WorkbenchWebView(
        mode = mode,
        payload = terminalPayload(terminalState),
        onMessage = { raw, reply ->
            val message = parseMessage(raw) ?: return@WorkbenchWebView
            when (message.action) {
                "ready" -> Unit
                "terminal-new" -> controller.createTab()
                "terminal-select" -> message.string("id")?.let(controller::select)
                "terminal-close" -> message.string("id")?.let(controller::closeTab)
                "terminal-start" -> controller.open()
                "terminal-stop" -> controller.stop()
                "terminal-write" -> message.string("data")?.let(controller::write)
                "terminal-resize" -> controller.resize(message.int("cols") ?: 80, message.int("rows") ?: 24)
            }
            reply.postMessage("{\"ok\":true}")
        },
        modifier = modifier,
    )
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun WorkbenchWebView(
    mode: WorkbenchWebMode,
    payload: JsonObject,
    onMessage: (String, JavaScriptReplyProxy) -> Unit,
    modifier: Modifier,
) {
    val latestPayload = rememberUpdatedState(payload)
    val latestMessage = rememberUpdatedState(onMessage)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.host != APP_ASSET_HOST

                    override fun onPageFinished(view: WebView, url: String) {
                        render(view, latestPayload.value)
                    }
                }
                check(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    "当前 WebView 不支持安全消息通道"
                }
                WebViewCompat.addWebMessageListener(
                    this,
                    BRIDGE_NAME,
                    setOf(APP_ORIGIN),
                ) { _, message: WebMessageCompat, sourceOrigin: Uri, isMainFrame: Boolean, reply: JavaScriptReplyProxy ->
                    if (isMainFrame && sourceOrigin.toString().trimEnd('/') == APP_ORIGIN) {
                        latestMessage.value(message.data.orEmpty(), reply)
                    }
                }
                loadUrl("$APP_ORIGIN/assets/workbench/index.html?mode=${mode.queryValue}")
            }
        },
        update = { render(it, payload) },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

private fun render(webView: WebView, payload: JsonObject) {
    webView.evaluateJavascript("window.saiWorkbench&&window.saiWorkbench.render($payload);", null)
}

private data class BridgeMessage(val action: String, val body: JsonObject) {
    fun string(name: String): String? = body[name]?.jsonPrimitive?.contentOrNull
    fun int(name: String): Int? = body[name]?.jsonPrimitive?.intOrNull
}

private fun parseMessage(raw: String): BridgeMessage? = runCatching {
    val body = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
    BridgeMessage(body["action"]?.jsonPrimitive?.contentOrNull ?: return null, body)
}.getOrNull()

private fun filesPayload(state: MainUiState, viewModel: MainViewModel) = buildJsonObject {
    put("rootId", state.fileRootId)
    put("rootTitle", state.fileRootTitle)
    put("directory", state.currentDirectory)
    put("search", state.fileSearch)
    put("clipboard", state.fileClipboardPath != null)
    put("usedBytes", state.storageProjectBytes)
    put("availableBytes", state.storageAvailableBytes)
    put("message", state.message.orEmpty())
    put("selectedFile", state.selectedFile.orEmpty())
    put("editorText", state.editorText)
    put("editorReadOnly", state.editorReadOnly)
    put("files", buildJsonArray {
        state.files.forEach { item -> add(buildJsonObject {
            put("path", item.path); put("directory", item.directory); put("size", item.size)
        }) }
    })
    put("locations", buildJsonArray {
        viewModel.fileLocations().forEach { location -> add(buildJsonObject {
            put("id", location.id); put("title", location.title)
        }) }
    })
}

private data class WebTerminalState(
    val tabs: List<TerminalTabEntity> = emptyList(),
    val selectedId: String? = null,
    val connected: Boolean = false,
    val output: String = "",
    val message: String = "",
)

private fun terminalPayload(state: WebTerminalState) = buildJsonObject {
    put("selectedTabId", state.selectedId.orEmpty())
    put("connected", state.connected)
    put("output", state.output)
    put("message", state.message)
    put("tabs", buildJsonArray { state.tabs.forEach { tab -> add(buildJsonObject {
        put("id", tab.id); put("title", tab.title); put("cwd", tab.cwd)
    }) } })
}

private class WebTerminalController(
    application: PhoneAgentApplication,
    private val workspaceId: String,
    private val workspacePath: String,
) {
    private val runtime: LinuxRuntime = application.container.runtime
    private val dao = application.container.database.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = mutableMapOf<String, PtySession>()
    private val readers = mutableMapOf<String, Job>()
    private val outputs = mutableMapOf<String, String>()
    private val _state = MutableStateFlow(WebTerminalState())
    val state = _state.asStateFlow()
    @Volatile private var started = false

    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch { loadTabs() }
        }
    }

    fun createTab() { scope.launch {
        val tabs = dao.terminalTabs(workspaceId)
        val tab = TerminalTabEntity(UUID.randomUUID().toString(), workspaceId, "终端 ${tabs.size + 1}", "/home/phoneagent", sortIndex = tabs.size)
        dao.upsertTerminalTab(tab)
        _state.update { it.copy(tabs = tabs + tab, selectedId = tab.id, connected = false, output = "") }
        open()
    } }

    fun select(id: String) {
        if (_state.value.tabs.none { it.id == id }) return
        _state.update { it.copy(selectedId = id, connected = sessions.containsKey(id), output = outputs[id].orEmpty()) }
    }

    fun closeTab(id: String) {
        sessions.remove(id)?.close()
        readers.remove(id)?.cancel()
        outputs.remove(id)
        scope.launch {
            dao.deleteTerminalTab(id)
            var tabs = dao.terminalTabs(workspaceId)
            if (tabs.isEmpty()) {
                val replacement = TerminalTabEntity(UUID.randomUUID().toString(), workspaceId, "终端 1", "/home/phoneagent")
                dao.upsertTerminalTab(replacement)
                tabs = listOf(replacement)
            }
            val selected = tabs.first().id
            _state.value = WebTerminalState(tabs, selected, sessions.containsKey(selected), outputs[selected].orEmpty())
            if (!sessions.containsKey(selected)) open()
        }
    }

    fun open() {
        val id = _state.value.selectedId ?: return createTab()
        if (sessions.containsKey(id) || readers[id]?.isActive == true) return
        readers[id] = scope.launch {
            runCatching { runtime.openPty("/home/phoneagent", workspaceHostPath = workspacePath) }
                .onSuccess { session ->
                    sessions[id] = session
                    dao.upsertTerminalTab(_state.value.tabs.first { it.id == id }.copy(state = "CONNECTED", lastActiveAt = System.currentTimeMillis()))
                    append(id, "\r\n\u001b[36m[sai PTY 已连接]\u001b[0m\r\n")
                    _state.update { it.copy(connected = true) }
                    session.events.collect { event -> when (event) {
                        is TerminalEvent.Output -> append(id, event.bytes.toString(Charsets.UTF_8))
                        is TerminalEvent.Closed -> { append(id, "\r\n[PTY 已退出：${event.exitCode}]\r\n"); sessions.remove(id); _state.update { it.copy(connected = false) } }
                        is TerminalEvent.Failure -> { append(id, "\r\n[PTY 错误：${event.message}]\r\n"); sessions.remove(id); _state.update { it.copy(connected = false, message = event.message) } }
                    } }
                }.onFailure { error -> _state.update { it.copy(connected = false, message = error.message ?: "无法启动 PTY") } }
        }
    }

    fun write(data: String) { scope.launch { sessions[_state.value.selectedId]?.write(data.toByteArray()) } }
    fun resize(columns: Int, rows: Int) { scope.launch { sessions[_state.value.selectedId]?.resize(columns.coerceIn(20, 300), rows.coerceIn(5, 150)) } }
    fun stop() {
        val id = _state.value.selectedId ?: return
        sessions.remove(id)?.close(); readers.remove(id)?.cancel()
        _state.update { it.copy(connected = false) }
        scope.launch { _state.value.tabs.firstOrNull { it.id == id }?.let { dao.upsertTerminalTab(it.copy(state = "DISCONNECTED", lastActiveAt = System.currentTimeMillis())) } }
    }

    fun close() {
        sessions.values.forEach(PtySession::close)
        readers.values.forEach(Job::cancel)
        scope.cancel()
    }

    private suspend fun loadTabs() {
        var tabs = dao.terminalTabs(workspaceId)
        if (tabs.isEmpty()) {
            val tab = TerminalTabEntity(UUID.randomUUID().toString(), workspaceId, "终端 1", "/home/phoneagent")
            dao.upsertTerminalTab(tab); tabs = listOf(tab)
        }
        val selected = tabs.first().id
        _state.value = WebTerminalState(tabs, selected, false, outputs[selected].orEmpty())
        open()
    }

    private fun append(id: String, text: String) {
        val output = (outputs[id].orEmpty() + text).takeLast(400_000)
        outputs[id] = output
        if (_state.value.selectedId == id) _state.update { it.copy(output = output) }
    }
}

/** Keeps project PTYs alive while the user moves between Agent panes. */
private object WebTerminalControllerRegistry {
    private val controllers = mutableMapOf<String, WebTerminalController>()

    @Synchronized
    fun get(application: PhoneAgentApplication, workspaceId: String, workspacePath: String): WebTerminalController =
        controllers.getOrPut("$workspaceId\u0000$workspacePath") {
            WebTerminalController(application, workspaceId, workspacePath)
        }
}

private const val APP_ASSET_HOST = "appassets.androidplatform.net"
private const val APP_ORIGIN = "https://$APP_ASSET_HOST"
private const val BRIDGE_NAME = "saiBridge"
