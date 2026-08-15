package com.phoneagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.phoneagent.app.service.PetOverlayService
import com.phoneagent.app.service.SailRobotView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import com.phoneagent.agent.AgentEvent
import com.phoneagent.agent.AgentMode
import com.phoneagent.agent.AgentRunState
import com.phoneagent.agent.ApprovalDecision
import com.phoneagent.agent.ToolCapability
import com.phoneagent.agent.TaskQueueState
import com.phoneagent.app.MainSection
import com.phoneagent.app.R
import com.phoneagent.app.MainUiState
import com.phoneagent.app.MainViewModel
import com.phoneagent.app.VoiceModelPack
import com.phoneagent.app.TaskHandle
import com.phoneagent.app.VoiceCallPhase
import com.phoneagent.app.VoiceInputGesture
import com.phoneagent.provider.ModelVisionPolicy
import com.phoneagent.app.SessionPermissionMode
import com.phoneagent.provider.ModelReasoningPolicy
import com.phoneagent.provider.ProviderPresets
import com.phoneagent.provider.ProviderProtocol
import com.phoneagent.provider.ReasoningEffort
import com.phoneagent.extensions.CatalogExtension
import com.phoneagent.extensions.ExtensionKind
import com.phoneagent.runtime.RootfsInstallState
import com.phoneagent.runtime.RuntimePackageAction
import com.phoneagent.runtime.RuntimePackageCatalog
import com.phoneagent.dsh.DshRuntimePhase
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

private data class Destination(val section: MainSection, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination(MainSection.AGENT, "Agent", Icons.Default.Home),
    Destination(MainSection.FILES, "文件", Icons.Default.Folder),
    Destination(MainSection.TERMINAL, "终端", Icons.Default.Terminal),
    Destination(MainSection.EXTENSIONS, "扩展", Icons.Default.Build),
    Destination(MainSection.SETTINGS, "设置", Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAgentApp(
    viewModel: MainViewModel,
    requestExternalDirectory: () -> Unit,
    requestPhoneFiles: () -> Unit,
    requestProjectZip: () -> Unit,
    requestExtensionZip: () -> Unit,
    requestAllFilesAccess: () -> Unit,
    startVoiceInput: () -> Unit,
    finishVoiceInput: (Boolean) -> Unit,
    toggleVoiceCall: () -> Unit,
    openAccessibilitySettings: () -> Unit,
    authorizeDeviceControl: (String) -> Unit,
    requestScreenCapture: () -> Unit,
    scanDesktopPairing: () -> Unit,
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    val configuration = LocalConfiguration.current
    val rootDensity = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(rootDensity) > 0
    val wide = configuration.screenWidthDp >= 700 ||
        (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 600)
    var sidebarOpen by remember { mutableStateOf(false) }
    var dshMenuToggleNonce by remember { mutableStateOf(0) }
    var dshOverlayVisible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                ProjectSessionSidebar(state, viewModel, Modifier.width(292.dp).fillMaxHeight(), requestProjectZip = requestProjectZip)
                Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(8.dp))
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = state.section == destination.section,
                            onClick = { viewModel.selectSection(destination.section) },
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label, maxLines = 1) },
                        )
                    }
                }
                Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
    Scaffold(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!wide) IconButton(onClick = {
                        if (state.section == MainSection.AGENT && state.dshRuntime.ready) {
                            dshMenuToggleNonce += 1
                        } else {
                            sidebarOpen = true
                        }
                    }) {
                        Icon(Icons.Default.Menu, "打开项目与会话")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_phone_agent),
                            null,
                            Modifier.size(40.dp),
                            tint = Color.Unspecified,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("sai", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                            Text(
                                if (state.runtimeCapability?.available == true) "本地环境已就绪" else "本地环境待初始化",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.runtimeCapability?.available == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.selectedWorkspaceId != null) {
                        IconButton(onClick = viewModel::openSelectedProjectFiles) {
                            Icon(Icons.Default.FolderOpen, "打开当前项目目录")
                        }
                    }
                    if (state.runState in setOf(AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL)) {
                        IconButton(onClick = viewModel::stopAgent) { Icon(Icons.Default.Stop, "停止 Agent") }
                    }
                    // The in-app pet is drawn above the scaffold so its launch animation can
                    // leave the app smoothly. Reserve a real action slot for it instead of
                    // letting it cover the project-folder and stop buttons underneath.
                    if (state.taskPetVisible && !sidebarOpen) Spacer(Modifier.width(82.dp))
                },
            )
        },
        bottomBar = {
            if (!wide && !imeVisible) NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = state.section == destination.section,
                        onClick = { viewModel.selectSection(destination.section) },
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.section) {
                MainSection.AGENT -> DshAgentScreen(
                    state = state,
                    viewModel = viewModel,
                    menuToggleNonce = dshMenuToggleNonce,
                    onOverlayVisibilityChanged = { dshOverlayVisible = it },
                    startVoiceInput = startVoiceInput,
                    finishVoiceInput = finishVoiceInput,
                    requestPhoneFiles = requestPhoneFiles,
                )
                MainSection.FILES -> FilesScreen(state, viewModel)
                MainSection.TERMINAL -> TerminalScreenV3(state, viewModel)
                MainSection.BROWSER -> BrowserScreen(state, viewModel)
                MainSection.EXTENSIONS -> ExtensionsScreen(state, viewModel, requestExtensionZip)
                MainSection.SETTINGS -> SettingsHub(
                    state,
                    viewModel,
                    requestExternalDirectory,
                    requestAllFilesAccess,
                    scanDesktopPairing,
                    toggleVoiceCall,
                )
            }
        }
    }
        }
        if (!wide && sidebarOpen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)).clickable { sidebarOpen = false },
            )
            Surface(
                modifier = Modifier.width(310.dp).fillMaxHeight().shadow(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ProjectSessionSidebar(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    onNavigate = { sidebarOpen = false },
                    requestProjectZip = requestProjectZip,
                )
            }
        }
        if (
            state.taskPetVisible &&
            !sidebarOpen &&
            !(state.section == MainSection.AGENT && dshOverlayVisible)
        ) {
            SaiPetDock(
                state = state,
                viewModel = viewModel,
                toggleVoiceCall = toggleVoiceCall,
                detachToSystemOverlay = {
                    if (Settings.canDrawOverlays(context)) {
                        viewModel.setTaskPetVisible(false)
                        context.getSharedPreferences("sai-ui", 0).edit().putBoolean("system_pet_enabled", true).apply()
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, PetOverlayService::class.java).setAction(PetOverlayService.ACTION_SHOW),
                        )
                    } else {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        Toast.makeText(context, "授予悬浮窗权限后，再拖出或点击悬浮按钮", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 4.dp, end = 8.dp),
            )
        }
    }
    state.approval?.let { approval ->
        val deviceApproval = ToolCapability.DEVICE_CONTROL in approval.capabilities
        val requestedPackage = Regex("\\\"packageName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(approval.argumentsJson)?.groupValues?.getOrNull(1)
            .orEmpty()
        val requestedAppName = Regex("\\\"appName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(approval.argumentsJson)?.groupValues?.getOrNull(1).orEmpty()
        val approve: (ApprovalDecision) -> Unit = { decision ->
            if (deviceApproval) authorizeDeviceControl(requestedPackage)
            viewModel.resolveApproval(decision)
        }
        AlertDialog(
            onDismissRequest = { },
            title = { Text("需要批准：${approval.toolName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(approval.riskExplanation)
                    approval.preview?.let { SelectionContainer { Text(it, fontFamily = FontFamily.Monospace) } }
                    if (deviceApproval) {
                        Text("这是 Agent 即时发起的手机控制请求，不需要预先配置包名。授权只覆盖本次目标 App，并在任务结束或 30 分钟无操作后失效。")
                        Text(
                            "目标：${requestedAppName.ifBlank { requestedPackage.ifBlank { "由 Agent 首次实际操作时自动识别并锁定" } }}",
                            fontFamily = FontFamily.Monospace,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = openAccessibilitySettings) { Text("启用无障碍") }
                            OutlinedButton(onClick = requestScreenCapture) { Text("授权屏幕观察") }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { approve(ApprovalDecision.ALLOW_ONCE) }) { Text("允许一次") }
                    TextButton(onClick = { approve(ApprovalDecision.ALLOW_SESSION) }) { Text("本会话允许") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.resolveApproval(ApprovalDecision.DENY) }) { Text("拒绝") } },
        )
    }
    if (state.voiceInputActive) VoiceInputOverlay(
        state = state,
        onFinish = { finishVoiceInput(true) },
        onCancel = { finishVoiceInput(false) },
    )
    state.runtimePackageRequest?.let { request ->
        val installing = request.action == RuntimePackageAction.INSTALL
        AlertDialog(
            onDismissRequest = viewModel::cancelRuntimePackageRequest,
            icon = { Icon(if (installing) Icons.Default.CloudDownload else Icons.Default.DeleteOutline, null) },
            title = { Text(if (installing) "安装 ${request.group.title}" else "卸载 ${request.group.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(request.group.description)
                    Text(request.group.sizeHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.runtimePackagePlan?.let { plan ->
                        Text(
                            "下载 ${formatBytes(plan.downloadBytes)} · 占用 ${formatBytes(plan.installedBytes)} · 可用 ${formatBytes(plan.availableBytes)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        plan.reason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    if (request.group.id == "latex" && installing) {
                        Text("LaTeX 套件体积较大，建议连接 Wi-Fi 并保持充足电量。", color = MaterialTheme.colorScheme.error)
                    }
                    if (!installing) Text("卸载可能使依赖该工具链的项目无法运行。")
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRuntimePackageRequest,
                    enabled = state.runtimePackagePlan?.allowed != false,
                ) { Text(if (installing) "确认安装" else "确认卸载") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRuntimePackageRequest) { Text("取消") } },
        )
    }
}

@Composable
private fun SaiPetDock(
    state: MainUiState,
    viewModel: MainViewModel,
    toggleVoiceCall: () -> Unit,
    detachToSystemOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busyTasks = state.taskHandles.values.filter { handle ->
        handle.queueState in setOf(TaskQueueState.QUEUED, TaskQueueState.STARTING, TaskQueueState.ACTIVE, TaskQueueState.WAITING_RESOURCE) ||
            handle.runState in setOf(AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL)
    }
    val waitingApproval = busyTasks.any { it.runState == AgentRunState.WAITING_APPROVAL }
    val petMotion = when {
        state.voiceCallActive -> "冲浪倾听"
        waitingApproval -> "收帆等待审批"
        busyTasks.isNotEmpty() -> "划船执行任务"
        else -> "扬帆巡航"
    }
    var expanded by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableStateOf(0f) }
    var launching by remember { mutableStateOf(false) }
    val launch = remember { Animatable(0f) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val launchDistance = (configuration.screenHeightDp * .58f - 72f).coerceAtLeast(220f)
    val launchTravel = if (launch.value <= .2f) {
        -kotlin.math.sin((launch.value / .2f) * Math.PI).toFloat() * 8f
    } else {
        val travel = ((launch.value - .2f) / .8f).coerceIn(0f, 1f)
        launchDistance * FastOutSlowInEasing.transform(travel)
    }
    val startLaunch = {
        if (!launching) {
            expanded = false
            launching = true
        }
    }

    LaunchedEffect(launching) {
        if (!launching) return@LaunchedEffect
        val targetY = with(density) { launchDistance.dp.roundToPx() }
        val targetX = (configuration.screenWidthDp - 84).coerceAtLeast(0)
        context.getSharedPreferences("sai-ui", 0).edit()
            .putInt("system_pet_x", with(density) { targetX.dp.roundToPx() })
            .putInt("system_pet_y", targetY)
            .apply()
        launch.snapTo(0f)
        launch.animateTo(.2f, tween(700, easing = FastOutSlowInEasing))
        launch.animateTo(1f, tween(2_500, easing = FastOutSlowInEasing))
        detachToSystemOverlay()
        launch.snapTo(0f)
        launching = false
    }

    Row(
        modifier.graphicsLayer {
            translationY = launchTravel * density.density
            translationX = -kotlin.math.sin(launch.value * Math.PI).toFloat() * 52f * density.density
            rotationY = kotlin.math.sin(launch.value * Math.PI).toFloat() * 90f
            rotationZ = (1f - launch.value) * if (launching) -7f else 0f
            val approachScale = 1f + launch.value * .11f
            scaleX = approachScale
            scaleY = approachScale
            cameraDistance = 14f * density.density
        },
        verticalAlignment = Alignment.Top,
    ) {
        if (expanded) {
            ElevatedCard(Modifier.widthIn(min = 212.dp, max = 260.dp).padding(top = 5.dp, end = 4.dp)) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(7.dp))
                        Text("任务机器人", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = detachToSystemOverlay, Modifier.size(30.dp)) {
                            Icon(Icons.Default.OpenInNew, "拖出为系统悬浮窗", Modifier.size(17.dp))
                        }
                        IconButton(onClick = { viewModel.setTaskPetMinimized(true) }, Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, "收起到右上角", Modifier.size(17.dp))
                        }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f)) {
                        Text(
                            if (state.voiceCallActive && state.voiceCallTranscript.isNotBlank()) "听见：${state.voiceCallTranscript}" else petMotion,
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (busyTasks.isEmpty()) {
                        Text("当前没有运行中的任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else busyTasks.take(3).forEach { task ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { viewModel.selectSession(task.sessionId); expanded = false },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SmartToy, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                    Text(task.progressText.ifBlank { "正在运行" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { viewModel.stopTask(task.sessionId) }, Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Stop, "停止任务", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        key(state.taskPetMinimized) {
            AndroidView(
                modifier = Modifier.size(width = 76.dp, height = 72.dp),
                factory = { androidContext ->
                    SailRobotView(
                        context = androidContext,
                        theme = state.petTheme,
                        onMove = { dx, dy -> dragDistance += kotlin.math.abs(dx) + kotlin.math.abs(dy) },
                        onOpen = {
                            if (state.taskPetMinimized) startLaunch() else expanded = !expanded
                        },
                        onVoice = toggleVoiceCall,
                        onPositionSaved = {
                            if (dragDistance > 42f) startLaunch()
                            dragDistance = 0f
                        },
                        onMinimize = { viewModel.setTaskPetMinimized(true) },
                        onRestore = { viewModel.setTaskPetMinimized(false) },
                    ).apply {
                        showCloseControl = false
                        showVoiceControl = false
                    }
                },
                update = { pet ->
                    pet.theme = state.petTheme
                    pet.taskCount = busyTasks.size
                    pet.waitingApproval = waitingApproval
                    pet.statusText = if (state.voiceCallActive && state.voiceCallTranscript.isNotBlank()) state.voiceCallTranscript else petMotion
                    pet.voiceActive = state.voiceCallActive
                    pet.dormant = state.taskPetMinimized && !launching
                    pet.launchProgress = launch.value
                    pet.showCloseControl = false
                    pet.showVoiceControl = false
                },
            )
        }
    }
}

private fun petThemeColors(theme: String): List<Color> = when (theme) {
    "ocean" -> listOf(Color(0xFF26C6DA), Color(0xFF2979FF), Color(0xFF0057B8))
    "sunset" -> listOf(Color(0xFFFFB74D), Color(0xFFFF4081), Color(0xFFE53935))
    "forest" -> listOf(Color(0xFF66D19E), Color(0xFF00A884), Color(0xFF167D52))
    else -> listOf(Color(0xFF36C9E6), Color(0xFF7C4DFF), Color(0xFF1751B5))
}

private fun formatBytes(value: Long?): String = when {
    value == null -> "待 apt 计算"
    value >= 1_000_000_000 -> String.format(Locale.ROOT, "%.2f GB", value / 1_000_000_000.0)
    value >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.ROOT, "%.1f KB", value / 1_000.0)
    else -> "$value B"
}

@Composable
private fun ProjectSessionSidebar(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {},
    requestProjectZip: () -> Unit = {},
) {
    var createProject by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var projectMenuId by remember { mutableStateOf<String?>(null) }
    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var sessionMenuId by remember { mutableStateOf<String?>(null) }
    var sessionToDelete by remember { mutableStateOf<String?>(null) }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_phone_agent),
                null,
                Modifier.size(36.dp),
                tint = Color.Unspecified,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("sai", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("项目与任务", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = requestProjectZip) { Icon(Icons.Default.CloudDownload, "导入项目 ZIP") }
            IconButton(onClick = { createProject = true }) { Icon(Icons.Default.Add, "新建项目") }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            state.workspaces.forEach { workspace ->
                item(key = "workspace:${workspace.id}") {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { viewModel.selectWorkspace(workspace.id); onNavigate() }
                            .background(
                                if (workspace.id == state.selectedWorkspaceId) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Folder, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Text(workspace.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton(onClick = { projectMenuId = workspace.id }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.MoreVert, "项目操作", Modifier.size(17.dp))
                            }
                            DropdownMenu(
                                expanded = projectMenuId == workspace.id,
                                onDismissRequest = { projectMenuId = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("删除项目", color = MaterialTheme.colorScheme.error) },
                                    onClick = { projectToDelete = workspace.id; projectMenuId = null },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                )
                            }
                        }
                    }
                }
                val sessions = state.sessions.filter { it.workspaceId == workspace.id }
                items(sessions, key = { "session:${it.id}" }) { session ->
                    val handle = state.taskHandles[session.id]
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { viewModel.selectSession(session.id); onNavigate() }
                            .background(
                                if (session.id == state.selectedSessionId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f)
                                else Color.Transparent,
                            )
                            .padding(start = 28.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SessionStatus(handle, session.state)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            val progress = handle?.progressText?.takeIf(String::isNotBlank) ?: session.latestPreview
                            if (progress.isNotBlank()) Text(
                                progress,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { sessionMenuId = session.id }, Modifier.size(32.dp)) {
                                Icon(Icons.Default.MoreVert, "会话操作", Modifier.size(17.dp))
                            }
                            DropdownMenu(
                                expanded = sessionMenuId == session.id,
                                onDismissRequest = { sessionMenuId = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (session.pinned) "取消置顶" else "置顶会话") },
                                    onClick = { viewModel.toggleSessionPinned(session.id); sessionMenuId = null },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                                    onClick = { sessionToDelete = session.id; sessionMenuId = null },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(
            onClick = { viewModel.selectSection(MainSection.SETTINGS); onNavigate() },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("设置与运行环境")
        }
    }
    if (createProject) AlertDialog(
        onDismissRequest = { createProject = false },
        title = { Text("新建项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("项目将创建在应用私有目录 Project/项目名，并自动初始化 Git main 分支和初始检查点。")
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { CompactFieldLabel("项目名称") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.createProject(projectName)
                createProject = false
                projectName = ""
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = { createProject = false }) { Text("取消") } },
    )
    sessionToDelete?.let { sessionId ->
        val title = state.sessions.firstOrNull { it.id == sessionId }?.title ?: "此会话"
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("删除会话？") },
            text = { Text("将删除“$title”的对话记录。正在运行的任务会先停止，此操作不可撤销。") },
            confirmButton = {
                Button(onClick = { viewModel.deleteSession(sessionId); sessionToDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("取消") } },
        )
    }
    projectToDelete?.let { workspaceId ->
        val project = state.workspaces.firstOrNull { it.id == workspaceId }
        if (project != null) AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("删除项目“${project.name}”？") },
            text = {
                Text("项目中的运行和排队任务会先停止；对话记录会删除，内部项目文件会移入 sai 私有回收站。外部 SAF 原目录不会被修改。")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteProject(workspaceId); projectToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除项目") }
            },
            dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SessionStatus(handle: TaskHandle?, persistedState: String) {
    val state = handle?.runState ?: runCatching { AgentRunState.valueOf(persistedState) }.getOrDefault(AgentRunState.IDLE)
    when (state) {
        AgentRunState.RUNNING -> CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
        AgentRunState.WAITING_APPROVAL -> Icon(Icons.Default.Security, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
        AgentRunState.PAUSED -> Text("Ⅱ", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        AgentRunState.COMPLETED -> Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        AgentRunState.FAILED -> Icon(Icons.Default.Stop, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        AgentRunState.CANCELLED, AgentRunState.IDLE -> Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun DshAgentScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    menuToggleNonce: Int,
    onOverlayVisibilityChanged: (Boolean) -> Unit,
    startVoiceInput: () -> Unit,
    finishVoiceInput: (Boolean) -> Unit,
    requestPhoneFiles: () -> Unit,
) {
    val runtime = state.dshRuntime
    val context = LocalContext.current
    val trustedUrl = runtime.webUrl
    var webContentReady by remember(runtime.runtimeVersion, trustedUrl) { mutableStateOf(false) }
    var webLoadFailed by remember(runtime.runtimeVersion, trustedUrl) { mutableStateOf(false) }
    var dshWebView by remember(runtime.runtimeVersion, trustedUrl) { mutableStateOf<WebView?>(null) }
    LaunchedEffect(webContentReady, dshWebView) {
        if (!webContentReady) {
            onOverlayVisibilityChanged(false)
            return@LaunchedEffect
        }
        while (true) {
            dshWebView?.evaluateJavascript(
                """
                (function () {
                  const dialogs = Array.from(document.querySelectorAll('[role="dialog"]'));
                  return dialogs.some((node) => {
                    const style = window.getComputedStyle(node);
                    const rect = node.getBoundingClientRect();
                    return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 1 && rect.height > 1;
                  });
                })()
                """.trimIndent(),
            ) { result -> onOverlayVisibilityChanged(result == "true") }
            delay(300)
        }
    }
    LaunchedEffect(menuToggleNonce, dshWebView) {
        if (menuToggleNonce > 0) {
            dshWebView?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('sai:navigation-toggle'))",
                null,
            )
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f),
                    MaterialTheme.colorScheme.background,
                ),
            ),
        ),
    ) {
        if (runtime.ready && trustedUrl != null) {
            if (!webContentReady) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text("正在启动 sai Agent", style = MaterialTheme.typography.titleMedium)
                }
            }
            key(runtime.runtimeVersion, trustedUrl, runtime.accessToken) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().alpha(if (webContentReady) 1f else 0f),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            dshWebView = this
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    val line = "${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                                    Log.e("SaiDshWeb", line)
                                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) webLoadFailed = true
                                    return true
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    val trusted = uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == 3080
                                    if (trusted) return false
                                    if (request.isForMainFrame && uri.scheme in setOf("https", "http")) {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    }
                                    return true
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError,
                                ) {
                                    if (request.isForMainFrame) {
                                        webLoadFailed = true
                                        viewModel.showMessage("DSH 页面加载失败：${error.description}")
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript(
                                        """
                                        (function () {
                                          if (document.getElementById('sai-android-dialog-fix')) return;
                                          const style = document.createElement('style');
                                          style.id = 'sai-android-dialog-fix';
                                          style.textContent = `@media (max-width: 600px) {
                                            html, body, #root { height: var(--sai-android-viewport-height) !important; min-height: var(--sai-android-viewport-height) !important; }
                                            [role='dialog'] { width: calc(100vw - 24px) !important; height: auto !important; max-height: none !important; overflow: auto !important; border-radius: 18px !important; }
                                            [role='dialog'] > div { max-height: none !important; min-height: min-content !important; overflow: visible !important; }
                                          }`;
                                          document.head.appendChild(style);
                                          const updateSaiViewport = () => {
                                            const height = Math.max(320, window.innerHeight || (window.visualViewport && window.visualViewport.height) || 0);
                                            document.documentElement.style.setProperty('--sai-android-viewport-height', height + 'px');
                                          };
                                          updateSaiViewport();
                                          window.addEventListener('resize', updateSaiViewport, { passive: true });
                                          if (window.visualViewport) window.visualViewport.addEventListener('resize', updateSaiViewport, { passive: true });
                                        })();
                                        """.trimIndent(),
                                        null,
                                    )
                                    fun probe(remaining: Int) {
                                        view.evaluateJavascript(
                                            "(function(){const r=document.querySelector('#root');const b=r&&r.getBoundingClientRect();return !!(r&&r.children.length>0&&b&&b.height>1&&document.body.innerText.trim().length>0)})()",
                                        ) { rendered ->
                                            if (rendered == "true") {
                                                webContentReady = true
                                                webLoadFailed = false
                                            } else if (remaining > 0) {
                                                view.postDelayed({ probe(remaining - 1) }, 500)
                                            } else {
                                                webLoadFailed = true
                                                Log.e("SaiDshWeb", "DSH page finished but rendered no visible client DOM: $url")
                                            }
                                        }
                                    }
                                    probe(12)
                                }

                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    webContentReady = false
                                    webLoadFailed = true
                                    Log.e("SaiDshWeb", "WebView renderer exited; crashed=${detail.didCrash()}")
                                    view.destroy()
                                    return true
                                }
                            }
                            val token = runtime.accessToken
                            if (token == null) loadUrl(trustedUrl) else {
                                val cookies = android.webkit.CookieManager.getInstance()
                                cookies.setAcceptCookie(true)
                                cookies.setCookie(
                                    trustedUrl,
                                    "sai_auth=${java.net.URLEncoder.encode(token, "UTF-8")}; HttpOnly; SameSite=Strict; Path=/",
                                ) { accepted ->
                                    if (accepted) {
                                        cookies.flush()
                                        post { loadUrl(trustedUrl) }
                                    } else post { viewModel.showMessage("无法建立 DSH 本地安全会话") }
                                }
                            }
                        }
                    },
                    update = {},
                )
            }
            if (webLoadFailed && !webContentReady) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .94f),
                    shape = CircleShape,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("DSH 界面未完成渲染，已启用本地界面", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = viewModel::restartDshRuntime) { Text("重试") }
                    }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (runtime.phase in setOf(DshRuntimePhase.INSTALLING, DshRuntimePhase.STARTING)) {
                    CircularProgressIndicator(progress = { runtime.progress ?: .2f })
                } else {
                    Icon(Icons.Default.Terminal, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text("sai Agent", style = MaterialTheme.typography.titleLarge)
                Text(
                    runtime.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (runtime.phase == DshRuntimePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (runtime.phase == DshRuntimePhase.FAILED) {
                    Button(onClick = viewModel::restartDshRuntime) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("重试")
                    }
                    if (state.dshRollbackAvailable) {
                        OutlinedButton(onClick = viewModel::rollbackDshRuntime) {
                            Text("回滚上一代运行时")
                        }
                    }
                    TextButton(onClick = viewModel::restoreBundledDshRuntime) {
                        Text("恢复 APK 内置运行时")
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCallBanner(phase: VoiceCallPhase, transcript: String, modifier: Modifier = Modifier) {
    val heardText = transcript.trim().takeIf { it.isNotEmpty() && it != "正在聆听…" }
        ?: if (phase == VoiceCallPhase.ERROR) "语音服务暂时不可用" else "正在听你说话…"
    Surface(
        modifier = modifier.widthIn(max = 330.dp),
        shape = CircleShape,
        color = Color(0xFF1565C0).copy(alpha = .92f),
        contentColor = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Mic, null, Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                heardText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun VoiceInputOverlay(state: MainUiState, onFinish: () -> Unit, onCancel: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "voice-input")
    val scale by transition.animateFloat(
        initialValue = .88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "voice-input-pulse",
    )
    val color = if (state.voiceInputCancelling) MaterialTheme.colorScheme.error else Color(0xFF1976D2)
    Surface(
        modifier = Modifier.fillMaxSize().clickable(onClick = onFinish),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = .72f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size((92 * scale).dp),
                shape = CircleShape,
                color = color,
                shadowElevation = 16.dp,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, Modifier.size(44.dp), tint = Color.White) }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "%02d:%02d.%d".format(
                    state.voiceInputElapsedMillis / 60_000,
                    state.voiceInputElapsedMillis / 1_000 % 60,
                    state.voiceInputElapsedMillis / 100 % 10,
                ),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                state.voiceInputTranscript.ifBlank {
                    if (state.voiceInputCancelling) "松开取消" else "正在本地识别 · 点按画面结束"
                },
                color = if (state.voiceInputCancelling) Color(0xFFFFCDD2) else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("取消语音输入")
            }
        }
    }
}

@Composable
private fun AgentComposerV2(
    state: MainUiState,
    viewModel: MainViewModel,
    startVoiceInput: () -> Unit,
    finishVoiceInput: (Boolean) -> Unit,
    requestPhoneFiles: () -> Unit,
    taskBusy: Boolean,
    inputExpanded: Boolean,
    onInputExpandedChange: (Boolean) -> Unit,
) {
    val canSend = state.prompt.isNotBlank()
    var sendImmediately by remember(state.selectedSessionId) { mutableStateOf(false) }
    var commandPaletteOpen by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }
    var commandPaletteSkillOnly by remember { mutableStateOf(false) }
    val modeEntries = AgentMode.entries.map { mode -> mode.displayLabel() to { viewModel.setMode(mode) } }
    val permissionEntries = SessionPermissionMode.entries.map { mode ->
        mode.permissionLabel() to { viewModel.setPermissionMode(mode) }
    }
    LaunchedEffect(state.prompt) {
        val token = state.prompt.substringAfterLast(' ').substringAfterLast('\n')
        if ((token.startsWith("/") || token.startsWith("$")) && token.length <= 48) {
            commandPaletteQuery = token.drop(1)
            commandPaletteSkillOnly = token.startsWith("$")
            commandPaletteOpen = true
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(7.dp, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            if (!state.voiceCallActive) {
                BasicTextField(
                    value = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 108.dp).padding(horizontal = 9.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().padding(vertical = 11.dp), contentAlignment = Alignment.TopStart) {
                            if (state.prompt.isEmpty()) Text(
                                if (taskBusy) "输入新指令（默认排队）" else "随心输入…",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .60f),
                            )
                            inner()
                        }
                    },
                )
            }
            if (state.voiceCallActive) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ComposerMenu(Icons.Default.PlayArrow, state.mode.displayLabel(), modeEntries, maxWidth = 118.dp, containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f))
                    ComposerMenu(Icons.Default.Security, state.permissionMode.permissionLabel(), permissionEntries, maxWidth = 118.dp, containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .62f))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            commandPaletteQuery = ""
                            commandPaletteSkillOnly = false
                            commandPaletteOpen = true
                        },
                        modifier = Modifier.size(40.dp),
                    ) { Icon(Icons.Default.Add, "添加模式、Skill、插件、网址或历史对话") }
                    PermissionComposerButton(state.permissionMode, viewModel::setPermissionMode)
                    Spacer(Modifier.weight(1f))
                    ModelReasoningComposerButton(state, viewModel, Modifier.widthIn(min = 112.dp, max = 190.dp))
                    Spacer(Modifier.width(3.dp))
                    val cancelDistance = with(LocalDensity.current) { 72.dp.toPx() }
                    val actionModifier = when {
                        canSend -> Modifier.size(42.dp).clickable {
                            viewModel.startAgent(taskBusy && sendImmediately)
                            onInputExpandedChange(false)
                        }
                        state.voiceInputGesture == VoiceInputGesture.TAP -> Modifier.size(42.dp).clickable { startVoiceInput() }
                        else -> Modifier.size(42.dp).pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                startVoiceInput()
                                var cancel = false
                                var pressed = true
                                while (pressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val nextCancel = change.position.y < down.position.y - cancelDistance
                                    if (nextCancel != cancel) {
                                        cancel = nextCancel
                                        viewModel.updateVoiceInput(cancelling = cancel)
                                    }
                                    change.consume()
                                    pressed = event.changes.any { it.pressed }
                                }
                                finishVoiceInput(!cancel)
                                onInputExpandedChange(false)
                            }
                        }
                    }
                    Surface(
                        modifier = actionModifier,
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (canSend) Icons.Default.ArrowUpward else Icons.Default.Mic,
                                if (canSend) "发送任务" else if (state.voiceInputGesture == VoiceInputGesture.TAP) "点击开始语音输入" else "按住说话，上滑取消",
                            )
                        }
                    }
                }
            }
        }
    }
    if (commandPaletteOpen) CommandPalette(
        state = state,
        initialQuery = commandPaletteQuery,
        skillOnly = commandPaletteSkillOnly,
        onDismiss = { commandPaletteOpen = false },
        onSelect = { invocation ->
            when {
                invocation == "/attach-phone" -> requestPhoneFiles()
                invocation.startsWith("/mode:") -> AgentMode.entries.firstOrNull {
                    it.name.equals(invocation.substringAfter(':'), true)
                }?.let(viewModel::setMode)
                invocation.startsWith("/history:") -> viewModel.selectSession(invocation.substringAfter(':'))
                else -> {
                    state.installedExtensions.firstOrNull { extension ->
                        extension.kind.equals("SKILL", true) && invocation == "\$${extension.name.replace(' ', '-')}"
                    }?.takeIf { !it.enabled }?.let { viewModel.enableExtension(it.id) }
                    val current = state.prompt
                    val tokenStart = maxOf(current.lastIndexOf(' '), current.lastIndexOf('\n')) + 1
                    val lastToken = current.substring(tokenStart)
                    val prefix = if (lastToken.startsWith("/") || lastToken.startsWith("$")) current.substring(0, tokenStart) else current + if (current.isBlank()) "" else " "
                    viewModel.setPrompt(prefix + invocation + " ")
                }
            }
            commandPaletteOpen = false
        },
    )
}

@Composable
private fun PermissionComposerButton(
    selected: SessionPermissionMode,
    onSelect: (SessionPermissionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.height(40.dp).clickable { expanded = true },
            color = Color.Transparent,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.Security, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(selected.permissionLabel(), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text("应如何批准 sai 操作？", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
            SessionPermissionMode.entries.forEach { mode ->
                val detail = when (mode) {
                    SessionPermissionMode.ASK -> "编辑文件和使用网络时始终询问"
                    SessionPermissionMode.AUTO -> "仅对检测到的风险操作请求批准"
                    SessionPermissionMode.YOLO -> "允许访问工作区、网络并自动执行工具"
                }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(mode.permissionLabel(), color = if (mode == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Security, null, tint = if (mode == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = if (mode == selected) ({ Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                    onClick = { expanded = false; onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun ModelReasoningComposerButton(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val options = ModelReasoningPolicy.options(state.provider)
    val selectedEffort = options.firstOrNull { it.selection == state.provider.reasoningSelection }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    val modelLabel = state.provider.defaultModel.substringAfterLast('/').take(20)
    Surface(
        modifier = modifier.height(40.dp).clickable { open = true },
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
    ) {
        Row(
            Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(modelLabel, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            if (selectedEffort.isNotBlank()) Text(selectedEffort, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("模型与推理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("模型", style = MaterialTheme.typography.labelLarge)
                ProviderModelPicker(state, viewModel, Modifier.fillMaxWidth(), equalWidth = true, containerColor = MaterialTheme.colorScheme.surfaceVariant)
                if (options.isNotEmpty()) {
                    HorizontalDivider()
                    Text("推理强度", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { option ->
                            FilterChip(
                                selected = option.selection == state.provider.reasoningSelection,
                                onClick = {
                                    viewModel.updateProvider(
                                        state.provider.copy(reasoningEffort = option.effort, reasoningSelection = option.selection),
                                    )
                                },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
                if (!ModelVisionPolicy.supportsImageInput(state.provider)) {
                    HorizontalDivider()
                    AuxiliaryVisionPicker(state, viewModel, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("完成") } },
    )
}

private fun SessionPermissionMode.permissionLabel(): String = when (this) {
    SessionPermissionMode.ASK -> "请求批准"
    SessionPermissionMode.AUTO -> "帮我批准"
    SessionPermissionMode.YOLO -> "完全访问"
}

@Composable
private fun AgentComposer(
    state: MainUiState,
    viewModel: MainViewModel,
    startVoiceInput: () -> Unit,
    finishVoiceInput: (Boolean) -> Unit,
    requestPhoneFiles: () -> Unit,
    taskBusy: Boolean,
    inputExpanded: Boolean,
    onInputExpandedChange: (Boolean) -> Unit,
) {
    val canSend = state.prompt.isNotBlank()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var sendImmediately by remember(state.selectedSessionId) { mutableStateOf(false) }
    var commandPaletteOpen by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }
    var commandPaletteSkillOnly by remember { mutableStateOf(false) }
    val modeEntries = AgentMode.entries.map { it.displayLabel() to { viewModel.setMode(it) } }
    val reasoningOptions = ModelReasoningPolicy.options(state.provider)
    val reasoningEntries = reasoningOptions.map { option ->
        option.label to { viewModel.updateProvider(state.provider.copy(reasoningEffort = option.effort, reasoningSelection = option.selection)) }
    }
    LaunchedEffect(state.prompt) {
        val token = state.prompt.substringAfterLast(' ').substringAfterLast('\n')
        if ((token.startsWith("/") || token.startsWith("$")) && token.length <= 48) {
            commandPaletteQuery = token.drop(1)
            commandPaletteSkillOnly = token.startsWith("$")
            commandPaletteOpen = true
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(10.dp, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
    ) {
        Column {
            if (inputExpanded && !state.voiceCallActive) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = state.prompt,
                            onValueChange = viewModel::setPrompt,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.prompt.isEmpty()) Text(
                                        "给 sai 发送消息…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    inner()
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier.size(42.dp).clickable {
                            commandPaletteQuery = ""
                            commandPaletteSkillOnly = false
                            commandPaletteOpen = true
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("/", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.width(5.dp))
                    ComposerAddButton(state, viewModel, requestPhoneFiles)
                    Spacer(Modifier.width(5.dp))
                    val cancelDistance = with(LocalDensity.current) { 72.dp.toPx() }
                    val actionModifier = if (canSend) {
                        Modifier.size(42.dp).clickable {
                            viewModel.startAgent(taskBusy && sendImmediately)
                            onInputExpandedChange(false)
                        }
                    } else {
                        Modifier.size(42.dp).pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                startVoiceInput()
                                var cancel = false
                                var pressed = true
                                while (pressed) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val nextCancel = change.position.y < down.position.y - cancelDistance
                                    if (nextCancel != cancel) {
                                        cancel = nextCancel
                                        viewModel.updateVoiceInput(cancelling = cancel)
                                    }
                                    change.consume()
                                    pressed = event.changes.any { it.pressed }
                                }
                                finishVoiceInput(!cancel)
                                onInputExpandedChange(false)
                            }
                        }
                    }
                    Surface(
                        modifier = actionModifier,
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (canSend) Icons.Default.ArrowUpward else Icons.Default.Mic, if (canSend) "发送任务" else "按住说话，上滑取消")
                        }
                    }
                    IconButton(
                        onClick = { onInputExpandedChange(false) },
                        modifier = Modifier.size(38.dp),
                    ) { Icon(Icons.Default.ExpandMore, "折叠输入框", Modifier.size(21.dp)) }
                }
            } else if (!state.voiceCallActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.prompt.isBlank()) {
                            if (taskBusy) "任务运行中 · 点击输入新指令" else "点击展开输入框"
                        } else state.prompt,
                        modifier = Modifier.weight(1f).clickable { onInputExpandedChange(true) }.padding(horizontal = 6.dp, vertical = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.prompt.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (taskBusy && state.selectedSessionId?.let(state.taskHandles::get)?.queueState != TaskQueueState.FINISHED) {
                        IconButton(onClick = viewModel::stopAgent, Modifier.size(36.dp)) {
                            Icon(Icons.Default.Stop, "停止当前任务", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Icon(Icons.Default.ExpandLess, "展开输入框", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (state.voiceCallActive) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ComposerMenu(
                            Icons.Default.PlayArrow,
                            state.mode.displayLabel(),
                            modeEntries,
                            modifier = Modifier.weight(1f),
                            equalWidth = true,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f),
                        )
                        if (reasoningEntries.isNotEmpty()) ComposerMenu(
                            Icons.Default.Psychology,
                            reasoningOptions.firstOrNull { it.selection == state.provider.reasoningSelection }?.label ?: "自动",
                            reasoningEntries,
                            modifier = Modifier.weight(1f),
                            equalWidth = true,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .58f),
                        ) else Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
                        ) { Text("模型固定思考", Modifier.padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    PermissionModeSlider(state.permissionMode, viewModel::setPermissionMode, Modifier.fillMaxWidth())
                }
            } else if (inputExpanded && !imeVisible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 600.dp) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                ComposerMenu(
                                    Icons.Default.PlayArrow,
                                    state.mode.displayLabel(),
                                    modeEntries,
                                    modifier = Modifier.weight(1f),
                                    equalWidth = true,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f),
                                )
                                if (reasoningEntries.isNotEmpty()) ComposerMenu(
                                    Icons.Default.Psychology,
                                    reasoningOptions.firstOrNull { it.selection == state.provider.reasoningSelection }?.label ?: "自动",
                                    reasoningEntries,
                                    modifier = Modifier.weight(1f),
                                    equalWidth = true,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .58f),
                                ) else Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
                                ) { Text("模型固定思考", Modifier.padding(12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (taskBusy) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !sendImmediately,
                                        onClick = { sendImmediately = false },
                                        label = { Text("排队") },
                                        leadingIcon = { Icon(Icons.Default.Memory, null, Modifier.size(17.dp)) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    FilterChip(
                                        selected = sendImmediately,
                                        onClick = { sendImmediately = true },
                                        label = { Text("立刻发送 · 改变方向") },
                                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null, Modifier.size(17.dp)) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            PermissionModeSlider(state.permissionMode, viewModel::setPermissionMode, Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                ProviderModelPicker(
                                    state,
                                    viewModel,
                                    modifier = Modifier.fillMaxWidth(),
                                    equalWidth = true,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                            if (!ModelVisionPolicy.supportsImageInput(state.provider)) {
                                AuxiliaryVisionPicker(state, viewModel, Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ComposerMenu(Icons.Default.PlayArrow, state.mode.displayLabel(), modeEntries, modifier = Modifier.width(160.dp), containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f))
                            if (reasoningEntries.isNotEmpty()) ComposerMenu(Icons.Default.Psychology, reasoningOptions.firstOrNull { it.selection == state.provider.reasoningSelection }?.label ?: "自动", reasoningEntries, modifier = Modifier.width(160.dp), containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .58f))
                            PermissionModeSlider(state.permissionMode, viewModel::setPermissionMode, Modifier.width(330.dp))
                            ProviderModelPicker(state, viewModel, modifier = Modifier.width(190.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            if (!ModelVisionPolicy.supportsImageInput(state.provider)) {
                                AuxiliaryVisionPicker(state, viewModel, Modifier.width(230.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (commandPaletteOpen) CommandPalette(
        state = state,
        initialQuery = commandPaletteQuery,
        skillOnly = commandPaletteSkillOnly,
        onDismiss = { commandPaletteOpen = false },
        onSelect = { invocation ->
            val current = state.prompt
            val tokenStart = maxOf(current.lastIndexOf(' '), current.lastIndexOf('\n')) + 1
            val lastToken = current.substring(tokenStart)
            val prefix = if (lastToken.startsWith("/") || lastToken.startsWith("$")) current.substring(0, tokenStart) else current + if (current.isBlank()) "" else " "
            viewModel.setPrompt(prefix + invocation + " ")
            commandPaletteOpen = false
        },
    )
}

private data class CommandPaletteEntry(
    val invocation: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

@Composable
private fun CommandPalette(
    state: MainUiState,
    initialQuery: String,
    skillOnly: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    val builtIns = listOf(
        CommandPaletteEntry("/attach-phone", "文件和文件夹", "从手机选择文件并作为当前会话附件", Icons.Default.Folder),
        CommandPaletteEntry("/mode:agent", "执行模式", "读取项目、调用工具并按权限策略修改文件", Icons.Default.PlayArrow),
        CommandPaletteEntry("/mode:plan", "计划模式", "只读分析并先给出可审核的实施计划", Icons.Default.Psychology),
        CommandPaletteEntry("/mode:goal", "持续目标", "允许任务在前台服务中持续、暂停和恢复", Icons.Default.Memory),
        CommandPaletteEntry("/read-url", "阅读网址或仓库", "内置 Skill · 安全读取网页、README、许可证和仓库文件", Icons.Default.Language),
        CommandPaletteEntry("/memory", "项目记忆", "检索或更新 .sai/memory.md 中可追溯的记忆", Icons.Default.Memory),
        CommandPaletteEntry("/web-search", "搜索网络", "查找最新资料，再打开可信来源", Icons.Default.Search),
        CommandPaletteEntry("/terminal", "终端任务", "让 Agent 使用终端完成当前要求", Icons.Default.Terminal),
    )
    val history = state.sessions.take(12).map { session ->
        CommandPaletteEntry(
            "/history:${session.id}",
            session.title.ifBlank { "未命名对话" },
            "历史对话 · ${session.state}",
            Icons.Default.History,
        )
    }
    val installed = state.installedExtensions.map { extension ->
        val skill = extension.kind.equals("SKILL", ignoreCase = true)
        CommandPaletteEntry(
            invocation = if (skill) "\$${extension.name.replace(' ', '-')}" else "/plugin:${extension.name.replace(' ', '-')}",
            title = extension.name,
            detail = if (skill) {
                "${if (extension.enabled) "已启用" else "已安装 · 选中后启用"} Skill · ${extension.source}"
            } else {
                "${if (extension.enabled) "已启用" else "已安装"} ${extension.kind} · ${extension.source}"
            },
            icon = if (skill) Icons.Default.Psychology else Icons.Default.Hub,
        )
    }
    val sourceEntries = if (skillOnly) installed.filter { it.invocation.startsWith("$") } else builtIns + installed + history
    val entries = sourceEntries.filter { entry ->
        query.isBlank() || entry.title.contains(query, true) || entry.invocation.contains(query, true) || entry.detail.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (skillOnly) "选择 Skill" else "添加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Text("/", fontWeight = FontWeight.Bold) },
                    label = { CompactFieldLabel("搜索命令、Skill、插件、网址或记忆") },
                )
                Text("输入 / 唤起命令；输入 ${'$'} 后接名称可直接唤起 Skill。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(entries, key = { it.invocation }) { entry ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(entry.invocation) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(entry.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (!entry.invocation.startsWith("/history:") && !entry.invocation.startsWith("/mode:")) {
                                Text(entry.invocation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun PermissionModeSlider(
    selected: SessionPermissionMode,
    onSelected: (SessionPermissionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        Triple(SessionPermissionMode.ASK, "询问", Icons.Default.Security),
        Triple(SessionPermissionMode.AUTO, "自动", Icons.Default.CheckCircle),
        Triple(SessionPermissionMode.YOLO, "Yolo", Icons.Default.PlayArrow),
    )
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(3.dp)) {
            entries.forEach { (mode, label, icon) ->
                val active = selected == mode
                Surface(
                    modifier = Modifier.weight(1f).clickable { onSelected(mode) },
                    shape = CircleShape,
                    color = if (active) when (mode) {
                        SessionPermissionMode.ASK -> MaterialTheme.colorScheme.secondaryContainer
                        SessionPermissionMode.AUTO -> MaterialTheme.colorScheme.primaryContainer
                        SessionPermissionMode.YOLO -> MaterialTheme.colorScheme.errorContainer
                    } else Color.Transparent,
                ) {
                    Row(
                        Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(label, maxLines = 1, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageSummaryBar(state: MainUiState) {
    val usages = state.events.filterIsInstance<AgentEvent.Usage>()
    if (usages.isEmpty()) return
    val input = usages.sumOf { it.inputTokens }
    val output = usages.sumOf { it.outputTokens }
    val cached = usages.sumOf { it.cachedInputTokens }.coerceAtMost(input)
    val hitRate = if (input > 0) cached.toDouble() / input * 100 else 0.0
    val presetPricing = ProviderPresets.all.firstOrNull { it.id == state.provider.id }
        ?.modelPricing?.get(state.provider.defaultModel)
    val pricing = if (state.provider.id == "deepseek") presetPricing
        else state.provider.modelPricing[state.provider.defaultModel] ?: presetPricing
    val cost = pricing?.let {
        (cached * it.cachedInputPerMillion +
            (input - cached).coerceAtLeast(0) * it.uncachedInputPerMillion +
            output * it.outputPerMillion) / 1_000_000.0
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Token ${formatCompactNumber(input + output)}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("缓存 ${String.format(Locale.US, "%.1f%%", hitRate)}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            val currency = pricing?.currency?.uppercase(Locale.ROOT)
            val amount = cost?.let { String.format(Locale.US, "%.4f", it) }
            val costText = when {
                amount == null -> "费用 --"
                currency == "CNY" -> "费用 ¥$amount"
                currency == "USD" -> "费用 $$amount"
                else -> "费用 ${currency.orEmpty()} $amount"
            }
            Text(costText, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private fun formatCompactNumber(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

@Composable
private fun ComposerAddButton(state: MainUiState, viewModel: MainViewModel, requestPhoneFiles: () -> Unit) {
    var sourceMenu by remember { mutableStateOf(false) }
    var projectPicker by remember { mutableStateOf(false) }
    Box {
    Surface(
        modifier = Modifier.size(44.dp).clickable { sourceMenu = true },
        shape = CircleShape,
        color = if (state.pendingAttachments.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (state.pendingAttachments.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "添加附件") } }
    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
        DropdownMenuItem(
            text = { Text("项目文件") },
            leadingIcon = { Icon(Icons.Default.Folder, null) },
            onClick = { sourceMenu = false; projectPicker = true },
        )
        DropdownMenuItem(
            text = { Text("手机文件") },
            leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
            onClick = { sourceMenu = false; requestPhoneFiles() },
        )
        if (state.latestCapturePath != null) DropdownMenuItem(
            text = { Text(if (state.attachLatestCapture) "移除最近截图" else "附加最近截图") },
            leadingIcon = { Icon(Icons.Default.Visibility, null) },
            onClick = { sourceMenu = false; viewModel.toggleLatestCaptureAttachment() },
        )
    }
    }
    if (projectPicker) {
        AlertDialog(
            onDismissRequest = { projectPicker = false },
            title = { Text("选择项目文件") },
            text = {
                LazyColumn {
                    val files = state.files.filterNot { it.directory }
                    if (files.isEmpty()) item { Text("当前目录没有文件，请先在文件页进入目标目录") }
                    items(files, key = { it.path }) { file ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.attachProjectFile(file.path)
                                projectPicker = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Code, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(file.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { projectPicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ComposerMenu(
    icon: ImageVector,
    label: String,
    entries: List<Pair<String, () -> Unit>>,
    maxWidth: Dp = 150.dp,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor,
            modifier = Modifier
                .height(42.dp)
                .then(if (equalWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidth))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .clickable { expanded = true },
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (title, action) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; action() })
            }
        }
    }
}

@Composable
private fun ProviderModelPicker(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var query by remember(state.provider.id) { mutableStateOf("") }
    val providers = state.providerProfiles.ifEmpty { listOf(state.provider) }
    val models = remember(state.provider.id, state.provider.defaultModel, state.availableModels, query) {
        val source = state.availableModels.ifEmpty {
            listOf(com.phoneagent.provider.ModelInfo(state.provider.defaultModel))
        }
        val needle = query.trim()
        source.distinctBy { it.id }.filter { model ->
            needle.isEmpty() || model.id.contains(needle, ignoreCase = true) ||
                model.displayName.contains(needle, ignoreCase = true)
        }
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        modifier = modifier.height(42.dp)
            .then(if (equalWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 220.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .clickable { dialogOpen = true },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.Hub, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${state.provider.displayName} · ${state.provider.defaultModel}",
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (dialogOpen) AlertDialog(
        onDismissRequest = { dialogOpen = false },
        title = { Text("选择模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("模型提供商", style = MaterialTheme.typography.labelLarge)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { providerMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(state.provider.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(
                        expanded = providerMenuOpen,
                        onDismissRequest = { providerMenuOpen = false },
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(provider.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(provider.protocol.displayLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                trailingIcon = if (provider.id == state.provider.id) {
                                    { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                onClick = {
                                    providerMenuOpen = false
                                    query = ""
                                    if (provider.id != state.provider.id) viewModel.selectProvider(provider)
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { CompactFieldLabel("搜索 ${state.provider.displayName} 的模型") },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清除搜索") } }
                    } else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.modelDiscoveryRunning) "正在获取模型…" else "模型 · ${models.size}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = viewModel::refreshModels, enabled = !state.modelDiscoveryRunning) {
                        if (state.modelDiscoveryRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "刷新模型")
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    if (models.isEmpty() && !state.modelDiscoveryRunning) item {
                        Text(
                            if (query.isBlank()) "暂无模型。请保存 API Key 后刷新，或在设置中手动填写模型 ID。" else "没有匹配“$query”的模型",
                            Modifier.padding(vertical = 14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(models, key = { it.id }) { model ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.selectModel(model)
                                dialogOpen = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (model.displayName != model.id) Text(
                                    model.id,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (model.id == state.provider.defaultModel) Icon(
                                Icons.Default.CheckCircle,
                                null,
                                Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("关闭") } },
    )
}

@Composable
private fun AuxiliaryVisionPicker(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val candidates = remember(state.provider.id, state.providerProfiles, state.providerModels, query) {
        val needle = query.trim()
        state.providerModels.mapNotNull { model ->
            val profile = state.providerProfiles.firstOrNull { it.id == model.providerId } ?: return@mapNotNull null
            if (!ModelVisionPolicy.isVisionCandidate(profile, model.modelId)) return@mapNotNull null
            Triple(profile, model, model.providerId == state.provider.id)
        }.filter { (profile, model, _) ->
            needle.isBlank() || model.modelId.contains(needle, true) ||
                model.displayName.contains(needle, true) || profile.displayName.contains(needle, true)
        }.sortedWith(
            compareByDescending<Triple<com.phoneagent.provider.ProviderProfile, com.phoneagent.data.ProviderModelEntity, Boolean>> { it.third }
                .thenBy { it.first.displayName }
                .thenBy { it.second.displayName },
        )
    }
    val selectedProvider = state.providerProfiles.firstOrNull { it.id == state.auxiliaryVisionProviderId }
    val label = if (state.auxiliaryVisionModel.isBlank()) "识图 Skill · 选择看图模型"
        else "识图 · ${selectedProvider?.displayName ?: state.auxiliaryVisionProviderId} / ${state.auxiliaryVisionModel}"
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .66f),
        modifier = modifier.height(42.dp)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = .35f), MaterialTheme.shapes.small)
            .clickable { dialogOpen = true },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
        }
    }
    if (dialogOpen) AlertDialog(
        onDismissRequest = { dialogOpen = false },
        title = { Text("辅助看图模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "主模型仅支持文本，sai 已自动挂载识图 Skill。图片会先由下列视觉模型观察，再把结构化结果交给主模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { CompactFieldLabel("搜索提供商或看图模型") },
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    if (candidates.isEmpty()) item {
                        Text(
                            "没有发现支持图片的模型。请先在模型提供商中获取模型列表。",
                            Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(candidates, key = { "${it.first.id}:${it.second.modelId}" }) { (profile, model, sameProvider) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.setAuxiliaryVisionModel(profile.id, model.modelId)
                                dialogOpen = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(model.displayName.ifBlank { model.modelId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (sameProvider) "${profile.displayName} · 同一提供商优先" else profile.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sameProvider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (profile.id == state.auxiliaryVisionProviderId && model.modelId == state.auxiliaryVisionModel) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("关闭") } },
    )
}

private sealed interface ConversationBlock {
    val id: String
    val complete: Boolean

    data class Message(
        override val id: String,
        val user: Boolean,
        val text: String,
        override val complete: Boolean,
        val userTurnIndex: Int? = null,
    ) : ConversationBlock

    data class Reasoning(
        override val id: String,
        val text: String,
        override val complete: Boolean,
    ) : ConversationBlock

    data class Tool(
        override val id: String,
        val name: String,
        val arguments: String,
        val progress: String = "",
        val result: String? = null,
        val success: Boolean = true,
        override val complete: Boolean = false,
    ) : ConversationBlock

    data class Event(
        override val id: String,
        val event: AgentEvent,
        override val complete: Boolean = true,
    ) : ConversationBlock

    data class WorkHistory(
        override val id: String,
        val entries: List<ConversationBlock>,
        override val complete: Boolean = true,
    ) : ConversationBlock
}

private fun reduceAgentEvents(events: List<AgentEvent>, currentState: AgentRunState): List<ConversationBlock> {
    val blocks = mutableListOf<ConversationBlock>()
    var serial = 0
    var userTurnIndex = 0
    fun next(prefix: String) = "$prefix:${serial++}"
    fun closeOpenBlocks(state: AgentRunState) {
        if (state !in setOf(AgentRunState.COMPLETED, AgentRunState.CANCELLED, AgentRunState.FAILED, AgentRunState.PAUSED)) return
        val detail = when (state) {
            AgentRunState.CANCELLED -> "任务已由用户停止"
            AgentRunState.PAUSED -> "应用进程曾被系统回收，任务已暂停，可从会话恢复"
            AgentRunState.FAILED -> "任务异常结束"
            else -> "任务已完成"
        }
        blocks.indices.forEach { index ->
            blocks[index] = when (val block = blocks[index]) {
                is ConversationBlock.Message -> if (!block.user && !block.complete) block.copy(complete = true) else block
                is ConversationBlock.Reasoning -> if (!block.complete) block.copy(complete = true) else block
                is ConversationBlock.Tool -> if (!block.complete) block.copy(result = detail, success = state == AgentRunState.COMPLETED, complete = true) else block
                else -> block
            }
        }
    }
    events.forEach { event ->
        when (event) {
            is AgentEvent.UserMessage -> blocks += ConversationBlock.Message(
                next("user"), true, event.text, true, userTurnIndex++,
            )
            is AgentEvent.AssistantMessageStarted -> blocks += ConversationBlock.Message(event.messageId, false, "", false)
            is AgentEvent.AssistantDelta -> {
                val index = blocks.indexOfLast { it is ConversationBlock.Message && !it.user && !it.complete }
                if (index >= 0) {
                    val old = blocks[index] as ConversationBlock.Message
                    blocks[index] = old.copy(text = old.text + event.text)
                } else blocks += ConversationBlock.Message(next("assistant"), false, event.text, false)
            }
            is AgentEvent.AssistantMessageCompleted -> {
                val index = blocks.indexOfLast { it is ConversationBlock.Message && !it.user && !it.complete }
                if (index >= 0) blocks[index] = (blocks[index] as ConversationBlock.Message).copy(complete = true)
            }
            is AgentEvent.ReasoningStarted -> blocks += ConversationBlock.Reasoning(event.blockId, "", false)
            is AgentEvent.ReasoningDelta -> {
                val index = blocks.indexOfLast { it is ConversationBlock.Reasoning && !it.complete }
                if (index >= 0) {
                    val old = blocks[index] as ConversationBlock.Reasoning
                    blocks[index] = old.copy(text = old.text + event.text)
                } else blocks += ConversationBlock.Reasoning(next("reasoning"), event.text, false)
            }
            is AgentEvent.ReasoningCompleted -> {
                val index = blocks.indexOfLast { it is ConversationBlock.Reasoning && !it.complete }
                if (index >= 0) blocks[index] = (blocks[index] as ConversationBlock.Reasoning).copy(complete = true)
            }
            is AgentEvent.ToolRequested -> blocks += ConversationBlock.Tool(event.callId, event.name, event.argumentsJson)
            is AgentEvent.ToolProgress -> {
                val index = blocks.indexOfLast { it is ConversationBlock.Tool && it.id == event.callId }
                if (index >= 0) blocks[index] = (blocks[index] as ConversationBlock.Tool).copy(progress = event.detail)
            }
            is AgentEvent.ToolFinished -> {
                val index = blocks.indexOfLast { it is ConversationBlock.Tool && it.id == event.callId }
                if (index >= 0) blocks[index] = (blocks[index] as ConversationBlock.Tool).copy(
                    result = event.result.output,
                    success = event.result.success,
                    complete = true,
                ) else blocks += ConversationBlock.Tool(
                    event.callId,
                    event.name,
                    "",
                    result = event.result.output,
                    success = event.result.success,
                    complete = true,
                )
            }
            is AgentEvent.RunStarted,
            is AgentEvent.ApprovalRequested,
            is AgentEvent.ApprovalResolved,
            is AgentEvent.ContextCompacted -> Unit
            is AgentEvent.StateChanged -> closeOpenBlocks(event.state)
            is AgentEvent.TaskQueued,
            is AgentEvent.TaskProgress -> Unit
            is AgentEvent.Error,
            is AgentEvent.DiffProduced,
            is AgentEvent.AttachmentProduced,
            is AgentEvent.SteerApplied,
            is AgentEvent.SpeechRequested,
            is AgentEvent.BrowserObservation,
            is AgentEvent.DeviceAction -> blocks += ConversationBlock.Event(next("event"), event)
            is AgentEvent.Usage -> Unit // Rendered once as the live task summary below the conversation.
        }
    }
    closeOpenBlocks(currentState)
    val visible = blocks.filterNot { it is ConversationBlock.Message && it.text.isBlank() && it.complete }
    val collapsed = mutableListOf<ConversationBlock>()
    val history = mutableListOf<ConversationBlock>()
    fun flushHistory() {
        if (history.isNotEmpty()) {
            collapsed += ConversationBlock.WorkHistory("history:${history.first().id}:${history.last().id}", history.toList())
            history.clear()
        }
    }
    visible.forEach { block ->
        if ((block is ConversationBlock.Tool || block is ConversationBlock.Reasoning) && block.complete) history += block
        else { flushHistory(); collapsed += block }
    }
    flushHistory()
    return collapsed
}

@Composable
private fun ConversationBlockCard(
    block: ConversationBlock,
    state: MainUiState,
    viewModel: MainViewModel,
    onUndoTurn: (Int) -> Unit,
) {
    when (block) {
        is ConversationBlock.Message -> if (block.user) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                block.userTurnIndex?.let { turnIndex ->
                    IconButton(onClick = { onUndoTurn(turnIndex) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Undo, "从这条消息开始撤回", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.widthIn(max = 560.dp),
                ) { Text(block.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }
        } else if (block.text.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("sai", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(7.dp))
                StructuredMarkdown(block.text)
                ArtifactReferenceStrip(block.text, state, viewModel)
            }
        }
        is ConversationBlock.Reasoning -> ExpandableExecutionCard(
            id = block.id,
            title = if (block.complete) "已思考" else "正在思考",
            body = if (block.complete) block.text else block.text.latestLines(4),
            complete = block.complete,
            icon = Icons.Default.Psychology,
            success = true,
            expandWhileRunning = true,
        )
        is ConversationBlock.Tool -> ExpandableExecutionCard(
            id = block.id,
            title = buildString {
                append(block.name)
                if (block.complete) append(if (block.success) " · 已完成" else " · 失败")
                else if (block.progress.isNotBlank()) append(" · ${block.progress}")
            },
            body = listOfNotNull(
                block.arguments.takeIf(String::isNotBlank)?.let { "参数\n$it" },
                block.result?.takeIf(String::isNotBlank)?.let { "结果\n$it" },
            ).joinToString("\n\n"),
            complete = block.complete,
            icon = Icons.Default.Terminal,
            success = block.success,
            expandWhileRunning = false,
        )
        is ConversationBlock.Event -> when (val event = block.event) {
            is AgentEvent.AttachmentProduced -> OutputArtifactCard(
                OutputReference(OutputReferenceKind.FILE, event.localPath, event.displayName), state, viewModel,
            )
            is AgentEvent.BrowserObservation -> OutputArtifactCard(
                OutputReference(OutputReferenceKind.URL, event.url, event.title), state, viewModel,
            )
            else -> EventCard(event)
        }
        is ConversationBlock.WorkHistory -> {
            val tools = block.entries.filterIsInstance<ConversationBlock.Tool>()
            val reasoningCount = block.entries.count { it is ConversationBlock.Reasoning }
            ExpandableExecutionCard(
                id = block.id,
                title = buildString {
                    append("工作记录")
                    if (tools.isNotEmpty()) append(" · ${tools.size} 次工具")
                    if (reasoningCount > 0) append(" · $reasoningCount 段思考")
                },
                body = block.entries.joinToString("\n\n") { entry ->
                    when (entry) {
                        is ConversationBlock.Tool -> "[${entry.name}]\n${entry.result.orEmpty().ifBlank { entry.arguments }}"
                        is ConversationBlock.Reasoning -> "[思考]\n${entry.text}"
                        else -> ""
                    }
                },
                complete = true,
                icon = Icons.Default.CheckCircle,
                success = tools.all { it.success },
                expandWhileRunning = false,
            )
        }
    }
}

private fun String.latestLines(maxLines: Int): String = lineSequence().toList().takeLast(maxLines).joinToString("\n").takeLast(1_200)

@Composable
private fun ExpandableExecutionCard(
    id: String,
    title: String,
    body: String,
    complete: Boolean,
    icon: ImageVector,
    success: Boolean,
    expandWhileRunning: Boolean,
) {
    var expanded by remember(id) { mutableStateOf(!complete && expandWhileRunning) }
    LaunchedEffect(complete) { if (complete) expanded = false }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (success) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f)
            else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!complete) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(icon, null, Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandMore, if (expanded) "折叠" else "展开", Modifier.size(18.dp))
            }
            if (expanded && body.isNotBlank()) {
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        body,
                        Modifier.fillMaxWidth().padding(13.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StructuredMarkdown(text: String) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val markwon = remember(context, textColor, linkColor) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(JLatexMathPlugin.create(48f) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().textColor(textColor)
            })
            .build()
    }
    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 16f
                setLineSpacing(0f, 1.16f)
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            markwon.setMarkdown(view, normalizeMarkdownMath(text))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtifactReferenceStrip(text: String, state: MainUiState, viewModel: MainViewModel) {
    val references = remember(text) { OutputReferenceParser.parse(text) }
    if (references.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val urls = references.filter { it.kind == OutputReferenceKind.URL }
        if (urls.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            urls.forEach { reference ->
                AssistChip(
                    onClick = { viewModel.openBrowserUrl(reference.target) },
                    label = { Text(reference.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                    leadingIcon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) },
                )
            }
        }
        references.filter { it.kind == OutputReferenceKind.FILE }.take(6).forEach { reference ->
            OutputArtifactCard(reference, state, viewModel)
        }
    }
}

@Composable
private fun OutputArtifactCard(reference: OutputReference, state: MainUiState, viewModel: MainViewModel) {
    if (reference.kind == OutputReferenceKind.URL) {
        AssistChip(
            onClick = { viewModel.openBrowserUrl(reference.target) },
            label = { Text(reference.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) },
        )
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolved = remember(reference.target, state.selectedWorkspaceId, state.events.size) { viewModel.resolveArtifactPath(reference.target) }
    val file = resolved?.let(::File)
    val extension = reference.target.substringBeforeLast('#').substringAfterLast('.', "").lowercase()
    val mime = remember(extension) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
            "pdf" -> "application/pdf"
            "md", "txt", "py", "kt", "java", "js", "ts", "css", "html", "json", "xml", "yaml", "yml" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { destination ->
        if (destination != null && file != null) scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destination, "w").use { output ->
                    requireNotNull(output) { "无法创建目标文件" }
                    file.inputStream().use { it.copyTo(output) }
                }
            }.onSuccess { launch(Dispatchers.Main) { Toast.makeText(context, "文件已保存", Toast.LENGTH_SHORT).show() } }
             .onFailure { error -> launch(Dispatchers.Main) { Toast.makeText(context, "保存失败：${error.message}", Toast.LENGTH_LONG).show() } }
        }
    }
    fun contentUri(): Uri? = file?.let {
        runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }.getOrNull()
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.small,
                color = if (extension == "pdf") Color(0xFFFFE3E3) else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (extension == "pdf") Icons.Default.PictureAsPdf else Icons.Default.Code,
                        null,
                        tint = if (extension == "pdf") Color(0xFFB3261E) else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reference.label.ifBlank { file?.name ?: reference.target.substringAfterLast('/') }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    if (file == null) "文件尚不可用" else "${extension.uppercase().ifBlank { "文件" }} · ${formatBytes(file.length())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                enabled = file != null,
                onClick = {
                    if (extension in setOf("txt", "md", "py", "kt", "java", "js", "ts", "css", "html", "json", "xml", "yaml", "yml", "toml", "csv")) {
                        viewModel.openArtifact(reference.target)
                    } else contentUri()?.let { uri ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.onFailure { Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show() }
                    }
                },
            ) { Icon(Icons.Default.OpenInNew, "打开文件") }
            IconButton(
                enabled = file != null,
                onClick = { contentUri()?.let { uri ->
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "分享文件",
                    ))
                } },
            ) { Icon(Icons.Default.Share, "分享文件") }
            IconButton(enabled = file != null, onClick = { saveLauncher.launch(file?.name ?: reference.label) }) {
                Icon(Icons.Default.Save, "保存到手机")
            }
        }
    }
}

@Composable
private fun EventCard(event: AgentEvent) {
    when (event) {
        is AgentEvent.UserMessage -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 560.dp),
            ) { Text(event.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        }
        is AgentEvent.AssistantDelta -> Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text("sai", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(event.text, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        is AgentEvent.AssistantMessageStarted,
        is AgentEvent.AssistantMessageCompleted,
        is AgentEvent.ReasoningStarted,
        is AgentEvent.ReasoningCompleted -> Unit
        is AgentEvent.ReasoningDelta -> CompactEventCard(
            title = "思考过程",
            body = event.text,
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f),
            icon = Icons.Default.Psychology,
        )
        is AgentEvent.ToolRequested -> CompactEventCard(
            title = "调用 ${event.name}", body = event.argumentsJson,
            color = MaterialTheme.colorScheme.surfaceVariant, icon = Icons.Default.Terminal,
        )
        is AgentEvent.ToolFinished -> CompactEventCard(
            title = "${event.name} · ${if (event.result.success) "完成" else "失败"}",
            body = event.result.output,
            color = if (event.result.success) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
            icon = if (event.result.success) Icons.Default.CheckCircle else Icons.Default.Stop,
        )
        is AgentEvent.ToolProgress -> CompactEventCard(
            title = event.name,
            body = event.detail,
            color = MaterialTheme.colorScheme.surfaceVariant,
            icon = Icons.Default.Terminal,
        )
        is AgentEvent.DiffProduced -> CompactEventCard(
            title = "Diff · ${event.path}", body = event.unifiedDiff,
            color = MaterialTheme.colorScheme.surfaceVariant, icon = Icons.Default.Code,
        )
        is AgentEvent.AttachmentProduced -> CompactEventCard(
            title = "附件 · ${event.displayName}", body = event.localPath,
            color = MaterialTheme.colorScheme.surfaceVariant, icon = Icons.Default.Folder,
        )
        is AgentEvent.TaskQueued -> CompactEventCard(
            title = "已排队 · 第 ${event.position} 位", body = event.reason,
            color = MaterialTheme.colorScheme.surfaceVariant, icon = Icons.Default.Memory,
        )
        is AgentEvent.TaskProgress -> CompactEventCard(
            title = "任务进度", body = event.label,
            color = MaterialTheme.colorScheme.surfaceVariant, icon = Icons.Default.Memory,
        )
        is AgentEvent.SteerApplied -> CompactEventCard(
            title = "改变方向", body = event.text,
            color = MaterialTheme.colorScheme.primaryContainer, icon = Icons.Default.ArrowUpward,
        )
        is AgentEvent.SpeechRequested -> CompactEventCard(
            title = "语音摘要", body = event.text,
            color = Color(0xFFBBDEFB), icon = Icons.Default.Mic,
        )
        is AgentEvent.BrowserObservation -> CompactEventCard(
            title = "网页 · ${event.title}", body = "${event.url}\n${event.summary}",
            color = MaterialTheme.colorScheme.surfaceVariant, icon = Icons.Default.Hub,
        )
        is AgentEvent.DeviceAction -> CompactEventCard(
            title = "设备操作 · ${event.action}", body = "${event.target}\n${event.detail}",
            color = if (event.success) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
            icon = Icons.Default.Security,
        )
        is AgentEvent.ApprovalRequested -> CompactEventCard("等待审批", event.request.riskExplanation, MaterialTheme.colorScheme.errorContainer, Icons.Default.Security)
        is AgentEvent.ApprovalResolved -> CompactEventCard("审批结果", event.decision.name, MaterialTheme.colorScheme.surfaceVariant, Icons.Default.Security)
        is AgentEvent.ContextCompacted -> CompactEventCard("上下文已压缩", "移除 ${event.removedMessages} 条消息", MaterialTheme.colorScheme.surfaceVariant, Icons.Default.Memory)
        is AgentEvent.Usage -> Unit
        is AgentEvent.StateChanged -> if (event.state != AgentRunState.RUNNING) {
            Text(
                "${event.state.displayLabel()}${event.detail?.let { " · $it" } ?: ""}",
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is AgentEvent.Error -> CompactEventCard("错误", event.message, MaterialTheme.colorScheme.errorContainer, Icons.Default.Stop)
        is AgentEvent.RunStarted -> Text(
            "${event.mode.displayLabel()}模式 · 会话 ${event.sessionId.take(8)}",
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactEventCard(title: String, body: String, color: Color, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            }
            if (body.isNotBlank()) SelectionContainer {
                Text(body, modifier = Modifier.padding(top = 7.dp), maxLines = 24, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun AgentRunState.displayLabel(): String = when (this) {
    AgentRunState.IDLE -> "空闲"
    AgentRunState.RUNNING -> "运行中"
    AgentRunState.WAITING_APPROVAL -> "等待审批"
    AgentRunState.PAUSED -> "已暂停"
    AgentRunState.COMPLETED -> "已完成"
    AgentRunState.FAILED -> "失败"
    AgentRunState.CANCELLED -> "已取消"
}

private fun AgentMode.displayLabel(): String = when (this) {
    AgentMode.PLAN -> "规划"
    AgentMode.AGENT -> "执行"
    AgentMode.GOAL -> "持续目标"
}

private fun ReasoningEffort.compactLabel(): String = when (this) {
    ReasoningEffort.AUTO -> "自动思考"
    ReasoningEffort.NONE -> "不思考"
    ReasoningEffort.MINIMAL -> "最少思考"
    ReasoningEffort.LOW -> "低思考"
    ReasoningEffort.MEDIUM -> "均衡"
    ReasoningEffort.HIGH -> "高思考"
    ReasoningEffort.XHIGH -> "超高思考"
    ReasoningEffort.MAX -> "最大思考"
}

@Composable
private fun FilesScreen(state: MainUiState, viewModel: MainViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                FileList(state, viewModel, Modifier.width(300.dp).fillMaxHeight())
                Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                EditorPane(state, viewModel, Modifier.weight(1f))
            }
        } else if (state.selectedFile == null) FileList(state, viewModel, Modifier.fillMaxSize())
        else EditorPane(state, viewModel, Modifier.fillMaxSize())
    }
}

@Composable
private fun FileList(state: MainUiState, viewModel: MainViewModel, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::directoryUp, enabled = state.currentDirectory.isNotBlank()) {
                Icon(Icons.Default.ArrowBack, "返回上级目录")
            }
            Column(Modifier.weight(1f)) {
                Text("资源管理器", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.currentDirectory.isBlank()) "Project" else "Project/${state.currentDirectory}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = viewModel::toggleHiddenFiles) {
                Icon(Icons.Default.Visibility, "显示或隐藏点文件", tint = if (state.showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = viewModel::refreshFiles) { Icon(Icons.Default.Refresh, "刷新") }
        }
        OutlinedTextField(
            value = state.fileSearch,
            onValueChange = viewModel::setFileSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text("在项目中搜索", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
        )
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.files) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (item.directory) viewModel.openDirectory(item.path) else viewModel.openFile(item.path)
                    }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (item.directory) Icons.Default.Folder else Icons.Default.Code, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!item.directory) Text(formatBytes(item.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPane(state: MainUiState, viewModel: MainViewModel, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::requestCloseEditor, enabled = state.selectedFile != null) {
                Icon(Icons.Default.ArrowBack, "关闭文件")
            }
            Text(state.selectedFile ?: "请选择文件", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.editorReadOnly) AssistChip(onClick = {}, label = { Text("只读") })
            if (state.editorDirty) AssistChip(onClick = {}, label = { Text("未保存") })
            IconButton(onClick = viewModel::saveEditor, enabled = state.selectedFile != null && state.editorDirty && !state.editorReadOnly) { Icon(Icons.Default.Save, "保存") }
        }
        HorizontalDivider()
        if (state.selectedFile != null) {
            SoraEditor(
                text = state.editorText,
                onTextChange = viewModel::updateEditor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (state.editorCloseConfirmation) AlertDialog(
        onDismissRequest = viewModel::cancelCloseEditor,
        title = { Text("文件尚未保存") },
        text = { Text("保存更改后关闭，还是放弃本次修改？") },
        confirmButton = { Button(onClick = viewModel::saveAndCloseEditor) { Text("保存并关闭") } },
        dismissButton = {
            Row {
                TextButton(onClick = viewModel::cancelCloseEditor) { Text("继续编辑") }
                TextButton(onClick = viewModel::closeEditorDiscarding) { Text("放弃") }
            }
        },
    )
}

@Composable
private fun SoraEditor(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setText(text)
                isLineNumberEnabled = true
                subscribeEvent(ContentChangeEvent::class.java) { _, _ -> onTextChange(this.text.toString()) }
            }
        },
        update = { editor -> if (editor.text.toString() != text) editor.setText(text) },
    )
}

@Composable
private fun TerminalScreenV3(state: MainUiState, viewModel: MainViewModel) {
    val outputScroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val cursor = state.terminalCursor.coerceIn(0, state.terminalCommand.length)
    val fieldValue = TextFieldValue(state.terminalCommand, TextRange(cursor))
    LaunchedEffect(state.terminalOutput.length) { outputScroll.scrollTo(outputScroll.maxValue) }
    Column(Modifier.fillMaxSize().background(Color(0xFF07111F)).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.terminalConnected) "PTY 已连接 · 点击终端即可输入" else "PTY 未连接",
                color = if (state.terminalConnected) Color(0xFF5EEAD4) else Color(0xFFFCA5A5),
                modifier = Modifier.weight(1f),
            )
            if (state.terminalConnected) {
                OutlinedButton(onClick = viewModel::sendTerminalInterrupt, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))) { Text("Ctrl-C") }
                OutlinedButton(onClick = viewModel::closeTerminal, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))) { Text("关闭") }
            } else Button(onClick = viewModel::openTerminal) { Text("启动终端") }
        }
        Text(
            state.terminalOutput.ifBlank {
                "sai Debian terminal\n启动终端后，轻触画布即可输入。当前命令行支持光标定位、退格和实时 PTY 输入。\n"
            },
            color = Color(0xFFD1FAE5),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(outputScroll).padding(12.dp)
                .clickable(enabled = state.terminalConnected) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$ ", color = Color(0xFF5EEAD4), fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = fieldValue,
                onValueChange = { next -> viewModel.updateTerminalCommandRealtime(next.text, next.selection.end) },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                enabled = state.terminalConnected,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFFF8FAFC),
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(Color(0xFF5EEAD4)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.submitTerminalInput() }),
                decorationBox = { editor ->
                    if (state.terminalCommand.isEmpty()) Text(
                        "直接键入；回车执行",
                        color = Color(0xFF7890A8),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    editor()
                },
            )
        }
    }
}

@Composable
private fun TerminalScreenV2(state: MainUiState, viewModel: MainViewModel) {
    val outputScroll = rememberScrollState()
    LaunchedEffect(state.terminalOutput.length) { outputScroll.scrollTo(outputScroll.maxValue) }
    Column(Modifier.fillMaxSize().background(Color(0xFF07111F)).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.terminalConnected) "PTY 已连接 · 实时键入" else "PTY 未连接",
                color = if (state.terminalConnected) Color(0xFF5EEAD4) else Color(0xFFFCA5A5),
                modifier = Modifier.weight(1f),
            )
            if (state.terminalConnected) {
                OutlinedButton(onClick = viewModel::sendTerminalInterrupt, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))) { Text("Ctrl-C") }
                OutlinedButton(onClick = viewModel::closeTerminal, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))) { Text("关闭") }
            } else Button(onClick = viewModel::openTerminal) { Text("启动终端") }
        }
        BasicTextField(
            value = state.terminalCommand,
            onValueChange = viewModel::updateTerminalCommandRealtime,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            enabled = state.terminalConnected,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.submitTerminalInput() }),
            decorationBox = { hiddenEditor ->
                Box(Modifier.fillMaxSize().background(Color(0xFF07111F))) {
                    Text(
                        state.terminalOutput.ifBlank {
                            "sai Debian terminal\n启动终端后，轻触此画布即可直接键入。字符会实时发送到 PTY。\n"
                        },
                        color = Color(0xFFD1FAE5),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxSize().verticalScroll(outputScroll).padding(12.dp),
                    )
                    // Keep a real text input connection for the Android IME, but
                    // let the PTY echo be the only visible command line.
                    Box(Modifier.size(1.dp).graphicsLayer(alpha = 0f)) { hiddenEditor() }
                }
            },
        )
    }
}

@Composable
private fun TerminalScreen(state: MainUiState, viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize().background(Color(0xFF07111F))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.terminalConnected) "PTY 已连接" else "PTY 未连接",
                color = if (state.terminalConnected) Color(0xFF5EEAD4) else Color(0xFFFCA5A5),
                modifier = Modifier.weight(1f),
            )
            if (state.terminalConnected) {
                OutlinedButton(
                    onClick = viewModel::sendTerminalInterrupt,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                ) { Text("Ctrl-C") }
                OutlinedButton(
                    onClick = viewModel::closeTerminal,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0)),
                ) { Text("关闭") }
            } else {
                Button(onClick = viewModel::openTerminal) { Text("启动终端") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer {
                Text(
                    state.terminalOutput.ifBlank { "sai Debian terminal\n运行时安装完成后可在此执行 Bash、Python 和 Git。\n" },
                    color = Color(0xFFD1FAE5),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$", color = Color(0xFF5EEAD4), fontFamily = FontFamily.Monospace)
            OutlinedTextField(
                state.terminalCommand,
                viewModel::setTerminalCommand,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                placeholder = { Text("输入 Bash 命令", color = Color(0xFF8FA8C3), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFF8FAFC),
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    cursorColor = Color(0xFF5EEAD4),
                    focusedBorderColor = Color(0xFF5EEAD4),
                    unfocusedBorderColor = Color(0xFF52708F),
                    focusedContainerColor = Color(0xFF10243A),
                    unfocusedContainerColor = Color(0xFF0D1D30),
                ),
                singleLine = true,
            )
            Button(onClick = viewModel::runTerminalCommand, enabled = state.terminalConnected) { Text("发送") }
        }
    }
}

private class BrowserTabState(
    val id: Long,
    address: String = "https://www.google.com",
) {
    var address by mutableStateOf(address)
    var loadedUrl by mutableStateOf(address)
    var title by mutableStateOf("新标签")
    var webView: WebView? = null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(state: MainUiState, viewModel: MainViewModel) {
    val initialUrl = state.browserPreviewUrl.ifBlank { "https://www.google.com" }
    val tabs = remember { mutableStateListOf(BrowserTabState(System.currentTimeMillis(), initialUrl)) }
    var selectedId by remember { mutableStateOf(tabs.first().id) }
    val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    val profileName = "project_${state.selectedWorkspaceId.replace(Regex("[^A-Za-z0-9_]"), "_")}".take(70)
    val profileSupported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }
    LaunchedEffect(state.browserPreviewUrl) {
        state.browserPreviewUrl.takeIf(String::isNotBlank)?.let { url ->
            selected.address = url
            selected.loadedUrl = url
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.selectSection(MainSection.AGENT) }) { Icon(Icons.Default.Close, "关闭浏览器预览") }
            Text("Agent 浏览器预览", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("网页内容不可信", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            tabs.forEach { tab ->
                FilterChip(
                    selected = tab.id == selectedId,
                    onClick = { selectedId = tab.id },
                    label = { Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                )
            }
            IconButton(onClick = {
                val tab = BrowserTabState(System.currentTimeMillis())
                tabs += tab
                selectedId = tab.id
            }) { Icon(Icons.Default.Add, "新建标签") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { selected.webView?.takeIf { it.canGoBack() }?.goBack() }) { Icon(Icons.Default.ArrowBack, "后退") }
            IconButton(onClick = { selected.webView?.reload() }) { Icon(Icons.Default.Refresh, "刷新") }
            OutlinedTextField(
                value = selected.address,
                onValueChange = { selected.address = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { CompactFieldLabel("地址或搜索") },
            )
            IconButton(onClick = {
                val raw = selected.address.trim()
                selected.loadedUrl = if (raw.startsWith("http://") || raw.startsWith("https://")) raw
                else "https://www.google.com/search?q=${URLEncoder.encode(raw, StandardCharsets.UTF_8.name())}"
            }) { Icon(Icons.Default.ArrowUpward, "打开") }
        }
        if (!profileSupported) Text(
            "此设备 WebView 不支持多 Profile；浏览数据仍仅属于 sai，但不同项目间暂不隔离。",
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(8.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        key(selected.id, profileName) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        selected.webView = this
                        if (profileSupported) {
                            ProfileStore.getInstance().getOrCreateProfile(profileName)
                            WebViewCompat.setProfile(this, profileName)
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportZoom(true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                selected.title = title?.take(40).orEmpty().ifBlank { "网页" }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                url?.let { selected.address = it }
                            }
                        }
                        // Deliberately no addJavascriptInterface: page content is untrusted.
                        loadUrl(selected.loadedUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != selected.loadedUrl) webView.loadUrl(selected.loadedUrl)
                },
                onRelease = { webView ->
                    if (selected.webView === webView) selected.webView = null
                    webView.stopLoading()
                    webView.destroy()
                },
            )
        }
    }
}

@Composable
private fun ExtensionsScreen(state: MainUiState, viewModel: MainViewModel, requestExtensionZip: () -> Unit) {
    val tabs = listOf("已安装", "发现", "MCP", "Skills", "DSH 插件", "Hooks", "诊断")
    var tab by remember { mutableStateOf("发现") }
    var selectedCatalogItem by remember { mutableStateOf<CatalogExtension?>(null) }
    var catalogBrowserItem by remember { mutableStateOf<CatalogExtension?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("扩展中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            tabs.forEach { title -> FilterChip(selected = tab == title, onClick = { tab = title }, label = { Text(title) }) }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        when (tab) {
            "已安装" -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "随 sai 启用的 DSH 插件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    listOf(
                        Triple("sai-android", "Android 与 Agent 浏览器", "文件、终端、浏览器、通知及系统能力桥"),
                        Triple("sai-artifacts", "文件与网址卡片", "打开、预览、保存、分享和导出"),
                        Triple("sai-models", "多提供商模型", "模型发现、推理档位、会话绑定与计费"),
                        Triple("sai-vision", "辅助识图", "纯文本模型自动路由到优先视觉模型"),
                        Triple("sai-voice", "离线语音", "语音输入、连续通话、打断与朗读工具"),
                        Triple("sai-request-guard", "请求保护", "并发队列、冷却、限流与熔断"),
                        Triple("sai-market", "扩展市场", "MCP、Skills 与 DSH 插件发现和预检"),
                        Triple("sai-pet", "任务宠物", "任务、审批和语音状态联动"),
                        Triple("sai-ui", "移动会话界面", "紧凑输入、工具折叠与移动端布局"),
                    ),
                    key = { "builtin:${it.first}" },
                ) { (id, title, summary) ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold)
                                Text(id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AssistChip(onClick = {}, label = { Text("内置 · 已启用") })
                        }
                    }
                }
                item {
                    Text(
                        "预装的 DeepSeek 优化 Preset",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(state.bundledDshPresets, key = { "preset:${it.id}" }) { preset ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(preset.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "DSH Preset · ${preset.version} · 实验性",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                FilterChip(
                                    selected = preset.installed,
                                    onClick = { viewModel.toggleBundledDshPreset(preset) },
                                    label = { Text(if (preset.installed) "已安装" else "已卸载") },
                                )
                            }
                            Text(preset.description, style = MaterialTheme.typography.bodySmall)
                            Text(
                                preset.sourceUrl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "固定提交 ${preset.sourceCommit.take(8)} · 不会自动改写现有会话模式",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { viewModel.toggleBundledDshPreset(preset) }) {
                                Text(if (preset.installed) "卸载 Preset" else "重新安装")
                            }
                        }
                    }
                }
                item {
                    Text(
                        "用户安装的扩展",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (state.installedExtensions.isEmpty()) item { ExtensionCard("尚未安装扩展", "可在“发现”中搜索，或在 MCP/Skills 标签中添加自定义来源。") }
                items(state.installedExtensions, key = { it.id }) { extension ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(extension.name, fontWeight = FontWeight.Bold)
                                    Text("${extension.kind} · ${extension.version.ifBlank { extension.sourceDigest.take(12) }}", style = MaterialTheme.typography.labelSmall)
                                }
                                FilterChip(
                                    selected = extension.enabled,
                                    onClick = { viewModel.toggleExtension(extension) },
                                    label = { Text(if (extension.enabled) "已启用" else "已禁用") },
                                )
                            }
                            Text(extension.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { viewModel.removeExtension(extension) }) { Text("移除记录", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            "发现" -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.extensionQuery,
                        onValueChange = { value ->
                            viewModel.setExtensionQuery(value)
                            if (value.isBlank()) viewModel.loadExtensionRecommendations()
                        },
                        modifier = Modifier.weight(1f),
                        label = { CompactFieldLabel("搜索 MCP、Skills 与 DSH 插件") },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = viewModel::searchExtensions, enabled = !state.extensionSearchRunning) {
                        if (state.extensionSearchRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, "搜索")
                    }
                }
                state.extensionError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.extensionFeedTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.extensionQuery.isBlank()) Text(
                        "GitHub 实时搜索 · skills.sh 热榜 · 失败时使用缓存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.extensionResults, key = { "${it.kind}:${it.id}" }) { extension ->
                        ElevatedCard(Modifier.fillMaxWidth().clickable { selectedCatalogItem = extension }) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(extension.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                    AssistChip(onClick = {}, label = { Text(extension.kind.name) })
                                }
                                if (extension.description.isNotBlank()) Text(extension.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        extension.source,
                                        extension.version.takeIf(String::isNotBlank),
                                        extension.installs
                                            ?.takeUnless { extension.source.startsWith("sai 预装") }
                                            ?.let { "$it 次安装" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            "MCP" -> McpConfigurationPane(state, viewModel, onInspect = { selectedCatalogItem = it })
            "Skills" -> ExtensionImportPane(state, ExtensionKind.SKILL, "导入 Skill", requestExtensionZip) { selectedCatalogItem = it }
            "DSH 插件" -> ExtensionImportPane(state, ExtensionKind.PLUGIN, "导入 DSH 插件", requestExtensionZip) { selectedCatalogItem = it }
            "Hooks" -> HookConfigurationPane(state, viewModel)
            else -> ExtensionDiagnosticsPane(state)
        }
    }
    if (state.extensionPreflightRunning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("DSH 插件安装预检") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.extensionPreflightProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(state.extensionPreflightStage ?: "正在预检…")
                    Text(
                        "正在实时读取仓库清单、验证 DSH 入口、扫描权限并计算摘要。此过程不会执行第三方安装脚本。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
    }
    state.extensionPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = viewModel::cancelExtensionInstall,
            title = { Text("安装预检 · ${plan.name}") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("来源：${plan.source}") }
                    item { Text("固定摘要：${plan.sourceDigest.take(20)}…") }
                    item { Text("权限：${plan.permissions.joinToString().ifBlank { "仅说明文本" }}") }
                    state.extensionAudit?.let { audit -> item { Text("审计：${audit.summary}\n${audit.details.joinToString("\n")}") } }
                    if (plan.warnings.isNotEmpty()) item { Text(plan.warnings.joinToString("\n"), color = MaterialTheme.colorScheme.error) }
                    item { Text("文件（${plan.files.size}）：\n${plan.files.take(40).joinToString("\n") { it.path }}", fontFamily = FontFamily.Monospace) }
                    item { Text("安装完成后默认禁用。启用前仍需确认项目范围与能力。") }
                }
            },
            confirmButton = { Button(onClick = viewModel::confirmExtensionInstall, enabled = plan.safeToStage) { Text("确认安装") } },
            dismissButton = { TextButton(onClick = viewModel::cancelExtensionInstall) { Text("取消") } },
        )
    }
    selectedCatalogItem?.let { extension ->
        AlertDialog(
            onDismissRequest = { selectedCatalogItem = null },
            title = { Text(extension.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(extension.kind.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(extension.description.ifBlank { "此条目没有额外介绍。" })
                    Text("来源：${extension.source}", style = MaterialTheme.typography.bodySmall)
                    extension.auditSummary?.let { Text("审计提示：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("安装或启用前，sai 仍会在本机执行权限、路径和内容预检。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (extension.kind == ExtensionKind.SKILL) Button(onClick = {
                    selectedCatalogItem = null
                    viewModel.inspectExtension(extension)
                }) { Text("安装预检") }
                else if (extension.kind == ExtensionKind.PLUGIN) Button(onClick = {
                    selectedCatalogItem = null
                    viewModel.inspectExtension(extension)
                }) { Text("DSH 安装预检") }
            },
            dismissButton = {
                Row {
                    extension.homepage?.let { TextButton(onClick = {
                        selectedCatalogItem = null
                        catalogBrowserItem = extension
                    }) { Text("在 sai 内查看") } }
                    TextButton(onClick = { selectedCatalogItem = null }) { Text("关闭") }
                }
            },
        )
    }
    catalogBrowserItem?.let { item ->
        CatalogBrowserDialog(item, onDismiss = { catalogBrowserItem = null })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CatalogBrowserDialog(item: CatalogExtension, onDismiss: () -> Unit) {
    val url = item.homepage.orEmpty()
    var webView by remember { mutableStateOf<WebView?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(vertical = 18.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() else onDismiss() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                HorizontalDivider()
                if (url.isBlank()) {
                    Text(item.description.ifBlank { "没有可用的仓库或介绍页面。" }, Modifier.padding(18.dp))
                } else AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            settings.javaScriptEnabled = false
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                    val target = request.url
                                    return if (target.scheme == "https" || target.scheme == "http") {
                                        view.loadUrl(target.toString())
                                        true
                                    } else true
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    update = { view -> if (view.url == null) view.loadUrl(url) },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}

@Composable
private fun ExtensionInformation(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) { ExtensionCard(title, body) }
}

@Composable
private fun ExtensionImportPane(
    state: MainUiState,
    kind: ExtensionKind,
    title: String,
    requestExtensionZip: () -> Unit,
    onInspect: (CatalogExtension) -> Unit,
) {
    val recommendations = state.extensionResults.filter { it.kind == kind }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (kind == ExtensionKind.SKILL) "选择含 SKILL.md 的 ZIP；启用后同步到 DSH 原生 Skill 注册表。" else "仅接受含 DSH bundle/Cordis patch 和预构建 JavaScript 的 ZIP；旧 Codex、Claude、Reasonix 插件不再接纳。")
                    Button(onClick = requestExtensionZip, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("选择 ZIP")
                    }
                }
            }
        }
        item { ExtensionCard("安全规则", "阻止路径穿越、符号链接和 Git 元数据；脚本不会自动执行，安装完成后默认禁用。") }
        item { Text(if (kind == ExtensionKind.SKILL) "推荐 Skills" else "推荐 DSH 插件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (recommendations.isEmpty()) item { Text("推荐目录正在加载，可稍后刷新“发现”。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(recommendations, key = { "${it.kind}:${it.id}" }) { item -> CatalogRecommendationCard(item) { onInspect(item) } }
    }
}

@Composable
private fun McpConfigurationPane(state: MainUiState, viewModel: MainViewModel, onInspect: (CatalogExtension) -> Unit) {
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("STREAMABLE_HTTP") }
    var endpoint by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("添加 MCP 服务器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    CompactOutlinedField(name, { name = it }, "名称")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("STDIO", "STREAMABLE_HTTP", "SSE").forEach { item ->
                            FilterChip(selected = transport == item, onClick = { transport = item }, label = { Text(item) })
                        }
                    }
                    CompactOutlinedField(endpoint, { endpoint = it }, if (transport == "STDIO") "命令，例如：npx server" else "服务 URL")
                    Button(
                        onClick = {
                            val config = if (transport == "STDIO") {
                                """{"transport":"STDIO","command":${endpoint.trim().split(Regex("\\s+")).joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }}}"""
                            } else """{"transport":"$transport","url":"${endpoint.replace("\"", "\\\"")}"}"""
                            viewModel.saveMcpServer(name, config)
                            name = ""; endpoint = ""
                        },
                        enabled = name.isNotBlank() && endpoint.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存配置") }
                }
            }
        }
        items(state.mcpServers, key = { it.id }) { server ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(server.displayName, fontWeight = FontWeight.Bold)
                        Text(server.lastStatus, style = MaterialTheme.typography.labelSmall)
                    }
                    FilterChip(selected = server.enabled, onClick = { viewModel.toggleMcpServer(server) }, label = { Text(if (server.enabled) "已启用" else "已禁用") })
                    TextButton(onClick = { viewModel.probeMcpServer(server) }) { Text("探测") }
                    IconButton(onClick = { viewModel.removeMcpServer(server) }) { Icon(Icons.Default.DeleteOutline, "删除 MCP") }
                }
            }
        }
        item { Text("推荐 MCP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        val recommendations = state.extensionResults.filter { it.kind == ExtensionKind.MCP }
        if (recommendations.isEmpty()) item { Text("推荐目录正在加载，可稍后刷新“发现”。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(recommendations, key = { "mcp:${it.id}" }) { item -> CatalogRecommendationCard(item) { onInspect(item) } }
    }
}

@Composable
private fun CatalogRecommendationCard(item: CatalogExtension, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (item.description.isNotBlank()) Text(item.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HookConfigurationPane(state: MainUiState, viewModel: MainViewModel) {
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var event by remember { mutableStateOf("before_tool") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("添加 Hook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    CompactOutlinedField(name, { name = it }, "名称")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("session_start", "turn_start", "before_tool", "after_tool", "turn_end", "task_complete").forEach { item ->
                            FilterChip(selected = event == item, onClick = { event = item }, label = { Text(item) })
                        }
                    }
                    CompactOutlinedField(command, { command = it }, "Debian 命令")
                    Button(
                        onClick = { viewModel.saveHook(name, event, command); name = ""; command = "" },
                        enabled = name.isNotBlank() && command.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存 Hook") }
                    Text("Hook 默认禁用；命令启用前仍需审批。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(state.hookConfigs, key = { it.id }) { hook ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(hook.displayName, fontWeight = FontWeight.Bold)
                        Text(hook.event, style = MaterialTheme.typography.labelSmall)
                    }
                    FilterChip(selected = hook.enabled, onClick = { viewModel.toggleHook(hook) }, label = { Text(if (hook.enabled) "已启用" else "已禁用") })
                    IconButton(onClick = { viewModel.removeHook(hook) }) { Icon(Icons.Default.DeleteOutline, "删除 Hook") }
                }
            }
        }
    }
}

@Composable
private fun ExtensionDiagnosticsPane(state: MainUiState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ExtensionCard("静态诊断", "安装时检查路径逃逸、符号链接、危险命令、网络和凭据访问；错误输出会脱敏。") }
        item { ExtensionCard("当前状态", "已安装 ${state.installedExtensions.size} · MCP ${state.mcpServers.size} · Hooks ${state.hookConfigs.size}") }
    }
}

@Composable
private fun CompactOutlinedField(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { CompactFieldLabel(hint) },
    )
}

@Composable
private fun CompactFieldLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun GitHubAvatar(url: String?, login: String) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank() || !url.startsWith("https://avatars.githubusercontent.com/")) null else {
            withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().buffered().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
                    .getOrNull()
            }
        }
    }
    Surface(
        modifier = Modifier.size(52.dp).clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = "$login 的 GitHub 头像", contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(login.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExtensionCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body, Modifier.padding(top = 6.dp)) } }
}

private data class SettingsCategoryEntry(val id: String, val title: String, val detail: String, val icon: ImageVector)
private data class SettingsDetailEntry(
    val id: String,
    val categoryId: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

@Composable
private fun SettingsHub(
    state: MainUiState,
    viewModel: MainViewModel,
    requestExternalDirectory: () -> Unit,
    requestAllFilesAccess: () -> Unit,
    scanDesktopPairing: () -> Unit,
    toggleVoiceCall: () -> Unit,
) {
    var page by remember { mutableStateOf<String?>(null) }
    var detailPage by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val categories = listOf(
        SettingsCategoryEntry("appearance", "外观与宠物", "软件主题颜色、界面宠物和悬浮方式", Icons.Default.SmartToy),
        SettingsCategoryEntry("voice", "语音与输入", "点击录音或按住说话、语音通话设置", Icons.Default.Mic),
        SettingsCategoryEntry("models", "模型与推理", "提供商、API Key、模型、识图和思考强度", Icons.Default.Hub),
        SettingsCategoryEntry("runtime", "本地开发环境", "Debian、Git、Python 与可选工具链", Icons.Default.Terminal),
        SettingsCategoryEntry("files", "文件与电脑连接", "外部文件授权、桌面配对和诊断", Icons.Default.Folder),
        SettingsCategoryEntry("accounts", "账户", "GitHub 登录与请求限额", Icons.Default.AccountCircle),
    )
    val details = listOf(
        SettingsDetailEntry("appearance/theme", "appearance", "软件主题", "全局配色和显示风格", Icons.Default.Palette),
        SettingsDetailEntry("appearance/pet", "appearance", "任务宠物", "应用内与系统悬浮行为", Icons.Default.SmartToy),
        SettingsDetailEntry("voice/input", "voice", "语音输入", "点按或长按、实时离线字幕", Icons.Default.Mic),
        SettingsDetailEntry("voice/call", "voice", "语音通话", "连续监听、朗读与打断", Icons.Default.RecordVoiceOver),
        SettingsDetailEntry("voice/pack", "voice", "Voice Pack", "安装或卸载离线模型", Icons.Default.Download),
        SettingsDetailEntry("models/providers", "models", "模型提供商", "API、密钥和模型发现", Icons.Default.Hub),
        SettingsDetailEntry("models/routing", "models", "模型与识图", "推理档位和辅助视觉", Icons.Default.Psychology),
        SettingsDetailEntry("runtime/debian", "runtime", "Debian 与 DSH", "本地运行时状态和自检", Icons.Default.Memory),
        SettingsDetailEntry("runtime/toolchains", "runtime", "开发工具链", "安装、卸载与实时进度", Icons.Default.Code),
        SettingsDetailEntry("files/access", "files", "文件访问", "外部目录权限和项目导出", Icons.Default.Folder),
        SettingsDetailEntry("files/desktop", "files", "电脑连接", "局域网扫码配对", Icons.Default.Devices),
        SettingsDetailEntry("files/diagnostics", "files", "诊断与验收", "Agent 全链路测试", Icons.Default.CheckCircle),
        SettingsDetailEntry("accounts/github", "accounts", "GitHub", "gh 登录、状态与限额", Icons.Default.AccountCircle),
    )
    if (page == null && detailPage == null) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { CompactFieldLabel("搜索设置项") },
                )
            }
            val matchingDetails = details.filter { query.isNotBlank() && (it.title.contains(query, true) || it.detail.contains(query, true)) }
            val filtered = categories.filter { category ->
                query.isBlank() || category.title.contains(query, true) || category.detail.contains(query, true) ||
                    matchingDetails.any { it.categoryId == category.id }
            }
            items(filtered, key = { it.id }) { category ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { page = category.id }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { Icon(category.icon, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium)
                            Text(category.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ArrowBack, null, Modifier.graphicsLayer(rotationZ = 180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (query.isNotBlank()) items(matchingDetails, key = { "search-${it.id}" }) { detail ->
                OutlinedCard(Modifier.fillMaxWidth().clickable { page = detail.categoryId; detailPage = detail.id }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(detail.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(detail.title)
                            Text(detail.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("没有匹配的设置项", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        return
    }
    if (detailPage == null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = null }) { Icon(Icons.Default.ArrowBack, "返回设置") }
                Text(categories.firstOrNull { it.id == page }?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider()
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(details.filter { it.categoryId == page }, key = { it.id }) { detail ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { detailPage = detail.id }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(detail.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(detail.title, style = MaterialTheme.typography.titleMedium)
                                Text(detail.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ArrowBack, null, Modifier.graphicsLayer(rotationZ = 180f))
                        }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { detailPage = null }) { Icon(Icons.Default.ArrowBack, "返回上一级") }
            Text(details.firstOrNull { it.id == detailPage }?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        when (detailPage) {
            "appearance/theme" -> AppearanceAndVoiceSettings(state, viewModel, showAppearance = true)
            "voice/input", "voice/pack" -> AppearanceAndVoiceSettings(state, viewModel, showAppearance = false)
            "voice/call" -> VoiceCallSettings(state, toggleVoiceCall)
            else -> SettingsScreen(state, viewModel, requestExternalDirectory, requestAllFilesAccess, scanDesktopPairing, detailPage.orEmpty())
        }
    }
}

@Composable
private fun VoiceCallSettings(state: MainUiState, toggleVoiceCall: () -> Unit) {
    val phase = when (state.voiceCallPhase) {
        VoiceCallPhase.IDLE -> "尚未开始"
        VoiceCallPhase.LISTENING -> "正在聆听"
        VoiceCallPhase.THINKING -> "等待 Agent"
        VoiceCallPhase.SPEAKING -> "AI 正在广播，同时保持监听"
        VoiceCallPhase.ERROR -> "语音通话发生错误"
    }
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
        Text("连续语音通话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(phase, color = if (state.voiceCallPhase == VoiceCallPhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.voiceCallTranscript.isNotBlank() && state.voiceCallPhase == VoiceCallPhase.LISTENING) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(state.voiceCallTranscript, Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center)
            }
        }
        Button(
            onClick = toggleVoiceCall,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            colors = if (state.voiceCallActive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.buttonColors(),
        ) {
            Icon(if (state.voiceCallActive) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(if (state.voiceCallActive) "结束语音通话" else "开始语音通话", style = MaterialTheme.typography.titleMedium)
        }
        Text("Voice Pack 完全离线。模型只朗读主动调用 speak 工具提交的简短内容；朗读时仍监听用户插话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceAndVoiceSettings(state: MainUiState, viewModel: MainViewModel, showAppearance: Boolean) {
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showAppearance) {
            item { SettingsSectionTitle(Icons.Default.Psychology, "软件主题", "颜色应用于整个 sai，而不只是帆船") }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("aurora" to "极光蓝紫", "ocean" to "海洋蓝青", "sunset" to "落日橙粉", "forest" to "森林青绿").forEach { (id, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.setPetTheme(id) }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(24.dp).background(petThemeColors(id)[1], CircleShape))
                                Spacer(Modifier.width(12.dp))
                                Text(label, Modifier.weight(1f))
                                if (state.appTheme == id) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("应用内任务宠物", Modifier.weight(1f))
                            FilterChip(
                                selected = state.taskPetVisible,
                                onClick = { viewModel.setTaskPetVisible(!state.taskPetVisible) },
                                label = { Text(if (state.taskPetVisible) "显示" else "隐藏") },
                            )
                        }
                        Text("悬浮宠物点击收帆后会退出系统浮窗并回到 sai 内部。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item { SettingsSectionTitle(Icons.Default.Mic, "语音输入方式", "默认点击开始、再次点击结束；也可切换为按住说话") }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        VoiceInputGesture.entries.forEach { gesture ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.setVoiceInputGesture(gesture) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (gesture == VoiceInputGesture.TAP) Icons.Default.Mic else Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (gesture == VoiceInputGesture.TAP) "点击开始 / 再次点击结束" else "按住说话 / 上滑取消")
                                    Text(if (gesture == VoiceInputGesture.TAP) "推荐，适合较长语音" else "适合短句和习惯对讲操作", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (state.voiceInputGesture == gesture) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                        Text("识别期间会显示实时字幕和时长；点击遮罩或停止按钮即可结束，不会被锁在录音状态。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("离线语音模型", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.voiceModelPackInstalled) "sai Voice Pack 已安装；识别完全离线且不产生 API 费用。"
                            else "基础 APK 不包含约 250 MB 的语音模型。按需安装独立模型包后即可离线使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.voiceModelPackInstalled) {
                            OutlinedButton(
                                onClick = { context.startActivity(VoiceModelPack.uninstallIntent()) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("卸载语音模型包") }
                        } else {
                            Button(
                                onClick = {
                                    val uri = VoiceModelPack.downloadUri()
                                    if (uri != null) context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    else viewModel.showMessage("本地开发包未配置 GitHub Release 地址，请从项目 Release 安装 sai-voice-pack-zh-en.apk")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("从 GitHub Release 获取语音模型包") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    requestExternalDirectory: () -> Unit,
    requestAllFilesAccess: () -> Unit,
    scanDesktopPairing: () -> Unit,
    section: String,
) {
    val context = LocalContext.current
    var providerTemplateOpen by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (section == "models/providers") {
        item { SettingsSectionTitle(Icons.Default.Hub, "模型与推理", "连接服务商、选择在线模型并控制思考强度") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("模型提供商", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Box {
                            OutlinedButton(onClick = { providerTemplateOpen = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加")
                            }
                            DropdownMenu(expanded = providerTemplateOpen, onDismissRequest = { providerTemplateOpen = false }) {
                                ProviderPresets.all.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.displayName) },
                                        onClick = { providerTemplateOpen = false; viewModel.addProvider(preset) },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = viewModel::deleteActiveProvider, enabled = state.providerProfiles.size > 1) {
                            Icon(Icons.Default.DeleteOutline, "删除当前提供商")
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.providerProfiles.forEach { profile ->
                            FilterChip(
                                selected = state.provider.id == profile.id,
                                onClick = { viewModel.selectProvider(profile) },
                                label = { Text(profile.displayName) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.provider.displayName,
                        onValueChange = { viewModel.updateProvider(state.provider.copy(displayName = it)) },
                        label = { CompactFieldLabel("提供商名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("协议", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProviderProtocol.entries.forEach { protocol ->
                            FilterChip(
                                selected = state.provider.protocol == protocol,
                                onClick = { viewModel.updateProviderProtocol(protocol) },
                                label = { Text(protocol.displayLabel()) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.provider.baseUrl,
                        onValueChange = { viewModel.updateProvider(state.provider.copy(baseUrl = it)) },
                        label = { CompactFieldLabel("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.providerApiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { CompactFieldLabel("API Key（留空则使用已保存密钥）") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = viewModel::saveProvider, modifier = Modifier.weight(1f)) {
                            Text(if (state.providerSaved) "已加密保存" else "保存并获取模型")
                        }
                        OutlinedButton(
                            onClick = viewModel::refreshModels,
                            enabled = !state.modelDiscoveryRunning,
                        ) {
                            if (state.modelDiscoveryRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "刷新模型")
                        }
                    }
                    state.modelDiscoveryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        }
        if (section == "models/routing") {
        item {
            ModelAndReasoningCard(state, viewModel)
        }
        }
        if (section == "appearance/pet") {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_sai_pet), null, Modifier.size(42.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("任务宠物", style = MaterialTheme.typography.titleMedium)
                            Text("右上角固定机器人帆船；拖出后成为透明系统悬浮窗，麦克风入口就在宠物上", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilterChip(
                            selected = state.taskPetVisible,
                            onClick = { viewModel.setTaskPetVisible(!state.taskPetVisible) },
                            label = { Text(if (state.taskPetVisible) "界面内显示" else "界面内隐藏") },
                        )
                    }
                    Text("宠物主题", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "aurora" to "极光",
                            "ocean" to "海洋",
                            "sunset" to "落日",
                            "forest" to "森林",
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = state.petTheme == id,
                                onClick = { viewModel.setPetTheme(id) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Box(Modifier.size(14.dp).background(petThemeColors(id)[1], CircleShape))
                                },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (Settings.canDrawOverlays(context)) {
                                viewModel.setTaskPetVisible(false)
                                context.getSharedPreferences("sai-ui", 0).edit().putBoolean("system_pet_enabled", true).apply()
                                ContextCompat.startForegroundService(context, Intent(context, PetOverlayService::class.java).setAction(PetOverlayService.ACTION_SHOW))
                                Toast.makeText(context, "任务宠物已允许显示在其他 App 上", Toast.LENGTH_SHORT).show()
                            } else {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                Toast.makeText(context, "授权后返回，再点一次即可显示", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (Settings.canDrawOverlays(context)) "在其他 App 上显示宠物" else "授予悬浮窗权限") }
                }
            }
        }
        }
        if (section == "runtime/debian") {
        item { SettingsSectionTitle(Icons.Default.Memory, "本地开发环境", "Debian 13 · PRoot · 私有工作区") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.runtimeCapability?.available == true) Icons.Default.CheckCircle else Icons.Default.Terminal,
                            null,
                            tint = if (state.runtimeCapability?.available == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (state.runtimeCapability?.available == true) "运行时已就绪" else "等待初始化", style = MaterialTheme.typography.titleMedium)
                            Text(state.runtimeCapability?.detail ?: "正在检测…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = viewModel::probeRuntime) { Icon(Icons.Default.Refresh, "重新检测") }
                    }
                    HorizontalDivider()
                    Text("${state.runtimeCapability?.architecture ?: "-"}  ·  Python ${state.runtimeCapability?.pythonVersion ?: "未安装"}  ·  Git ${state.runtimeCapability?.gitVersion ?: "异常"}（已内置）")
                    Text(
                        "Git 是 sai 基础组件，可离线使用且不会出现在可卸载工具链中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = viewModel::runRuntimeSelfTest,
                        enabled = state.runtimeCapability?.available == true && !state.runtimeSelfTestRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.runtimeSelfTestRunning) "自检中…" else "运行编码环境自检") }
                    if (state.runtimeSelfTestOutput.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                state.runtimeSelfTestOutput,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    when (val install = state.rootfsInstallState) {
                        is RootfsInstallState.CopyingEmbedded -> {
                            val progress = if (install.total > 0) install.copied.toFloat() / install.total else 0f
                            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("正在复制内置 Debian 与 Git ${(progress * 100).toInt()}% · 无需联网")
                        }
                        is RootfsInstallState.Downloading -> {
                            val progress = if (install.total > 0) install.downloaded.toFloat() / install.total else 0f
                            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("下载 ${(progress * 100).toInt()}%")
                        }
                        RootfsInstallState.Verifying -> Text("正在校验 SHA-256…")
                        RootfsInstallState.Extracting -> Text("正在解压 Debian…")
                        is RootfsInstallState.Provisioning -> {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(install.stage)
                        }
                        is RootfsInstallState.Ready -> Text("已安装 ${install.version}")
                        is RootfsInstallState.Failed -> {
                            Text(install.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = viewModel::installRootfs) { Text("重试初始化") }
                        }
                        RootfsInstallState.NotInstalled -> Button(onClick = viewModel::installRootfs, modifier = Modifier.fillMaxWidth()) { Text("离线初始化 Debian 13 + Git") }
                    }
                }
            }
        }
        }
        if (section == "accounts/github") {
        item {
            LaunchedEffect(state.githubDeviceCode) {
                if (!state.githubDeviceCode.isNullOrBlank()) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/login/device")))
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("GitHub CLI", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.githubCliStatus.installed) "gh ${state.githubCliStatus.version ?: ""} · ${state.githubCliStatus.detail}" else state.githubCliStatus.detail,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = viewModel::refreshGitHubCli, enabled = !state.githubCliBusy) {
                            if (state.githubCliBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "刷新 GitHub 状态")
                        }
                    }
                    state.githubCliStatus.login?.let { login ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GitHubAvatar(state.githubCliStatus.avatarUrl, login)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("@$login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("已登录 · 凭据由 Android Keystore 加密", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(onClick = viewModel::logoutGitHub, modifier = Modifier.fillMaxWidth(), enabled = !state.githubCliBusy) {
                            Text("退出 GitHub")
                        }
                    } ?: run {
                        Button(
                            onClick = viewModel::loginGitHubWithDevice,
                            enabled = !state.githubCliBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.githubCliBusy) "等待 GitHub 授权…" else "浏览器网页登录（推荐）") }
                        state.githubDeviceCode?.let { code ->
                            Text("设备码：$code", style = MaterialTheme.typography.titleMedium)
                            Text("浏览器已打开 GitHub 授权页。输入上方设备码并确认后，sai 会自动完成登录。", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/login/device")))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("打开 GitHub 验证页") }
                        }
                        HorizontalDivider()
                        OutlinedTextField(
                            value = state.githubTokenInput,
                            onValueChange = viewModel::updateGitHubToken,
                            label = { CompactFieldLabel("GitHub Token（repo/read:org 按需授权）") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = viewModel::loginGitHub,
                            enabled = state.githubTokenInput.isNotBlank() && !state.githubCliBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("使用 Token 登录（高级）") }
                    }
                    Text(
                        "登录只用于 gh 和 GitHub 扩展操作；令牌不会进入模型提示词、Debian 环境文件或普通日志。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
        if (section == "runtime/toolchains") {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("开发工具链", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::refreshRuntimePackages, enabled = state.runtimeCapability?.available == true) { Text("刷新") }
            }
        }
        state.runtimePackageOperation?.let { stage ->
            item {
                var logExpanded by remember { mutableStateOf(false) }
                val progress = state.runtimePackageProgress
                val progressPercent = progress?.percent
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (progressPercent == null) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("$progressPercent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(stage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = viewModel::cancelRuntimePackageOperation) { Text("停止") }
                        }
                        if (progressPercent != null) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progressPercent.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        progress?.detail?.takeIf(String::isNotBlank)?.let {
                            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!progress?.logTail.isNullOrBlank()) {
                            TextButton(onClick = { logExpanded = !logExpanded }) {
                                Icon(if (logExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (logExpanded) "收起实时日志" else "查看实时日志")
                            }
                            if (logExpanded) {
                                SelectionContainer {
                                    Text(
                                        progress?.logTail.orEmpty(),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        items(RuntimePackageCatalog.groups) { group ->
            val status = state.runtimePackages.firstOrNull { it.group.id == group.id }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (status?.installed == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp),
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Code, null) } }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                        Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(status?.version ?: group.sizeHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (status?.installed == true) {
                        IconButton(
                            onClick = { viewModel.requestRuntimePackage(group, RuntimePackageAction.REMOVE) },
                            enabled = state.runtimePackageOperation == null,
                        ) { Icon(Icons.Default.DeleteOutline, "卸载 ${group.title}") }
                    } else {
                        Button(
                            onClick = { viewModel.requestRuntimePackage(group, RuntimePackageAction.INSTALL) },
                            enabled = state.runtimeCapability?.available == true && state.runtimePackageOperation == null,
                        ) { Text("安装") }
                    }
                }
            }
        }
        }
        if (section == "files/access") {
        item { SettingsSectionTitle(Icons.Default.Folder, "文件访问", "内部工作区优先，外部目录按需授权") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = requestExternalDirectory, modifier = Modifier.fillMaxWidth()) { Text("授权一个外部目录") }
                state.externalTreeUri?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                OutlinedButton(onClick = requestAllFilesAccess, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.allFilesAccess) "已授予所有文件访问" else "高级：授予所有文件访问")
                }
                Text("外部共享存储通常不支持完整 POSIX 权限和符号链接；Git 与编译建议在内部工作区进行。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        }
        if (section == "files/desktop") {
        item { SettingsSectionTitle(Icons.Default.Hub, "电脑连接", "局域网扫码配对；文件操作与基础对话端到端加密") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(state.desktopConnectionStatus, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "电脑只能访问已授权的项目读写和聊天；Shell、删除、手机控制及危险操作仍必须在手机审批。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = scanDesktopPairing, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Hub, null); Spacer(Modifier.width(8.dp)); Text("扫描电脑配对二维码")
                    }
                    if (state.desktopConnectionStatus.startsWith("已加密连接")) {
                        OutlinedButton(onClick = viewModel::disconnectDesktop, modifier = Modifier.fillMaxWidth()) { Text("断开电脑") }
                    }
                    state.desktopPairings.take(3).forEach { pairing ->
                        Text(
                            "${pairing.displayName} · ${pairing.endpoint}",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        }
        if (section == "files/diagnostics") {
        item { SettingsSectionTitle(Icons.Default.CheckCircle, "Agent 全链路验收", "使用当前 API Key 测试多文件、Python、Git 和 Agent 浏览器") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("会创建独立的 sai-E2E 项目，只使用虚构登录账号和 127.0.0.1。")
                    Button(
                        onClick = viewModel::runAgentE2eTest,
                        enabled = !state.e2eTestRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.e2eTestRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.e2eTestRunning) "正在启动…" else "运行全链路测试")
                    }
                }
            }
        }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsSectionTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelAndReasoningCard(state: MainUiState, viewModel: MainViewModel) {
    val reasoningOptions = ModelReasoningPolicy.options(state.provider)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("模型", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.provider.defaultModel,
                onValueChange = { viewModel.updateProvider(state.provider.copy(defaultModel = it)) },
                label = { CompactFieldLabel("模型 ID（可手动填写）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            ProviderModelPicker(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth(),
                equalWidth = true,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (reasoningOptions.isNotEmpty()) {
                Text("思考强度", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reasoningOptions.forEach { option ->
                        FilterChip(
                            selected = state.provider.reasoningSelection == option.selection,
                            onClick = { viewModel.updateProvider(state.provider.copy(reasoningEffort = option.effort, reasoningSelection = option.selection)) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text("档位按当前服务商、协议和模型自动匹配；自动使用服务商默认值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text(
                "当前模型没有可调思考强度，sai 不会发送无效的 reasoning 参数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("辅助视觉模型", style = MaterialTheme.typography.titleMedium)
            AuxiliaryVisionPicker(state, viewModel, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.auxiliaryVisionModel,
                onValueChange = viewModel::setAuxiliaryVisionModel,
                label = { CompactFieldLabel("视觉模型 ID（主模型不支持图片时使用）") },
                supportingText = { Text("留空表示不挂载；视觉观察仅在附加截图后按需调用", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

private fun ProviderProtocol.displayLabel(): String = when (this) {
    ProviderProtocol.OPENAI_RESPONSES -> "OpenAI Responses"
    ProviderProtocol.OPENAI_CHAT -> "OpenAI 兼容"
    ProviderProtocol.ANTHROPIC_MESSAGES -> "Anthropic"
    ProviderProtocol.GEMINI_NATIVE -> "Gemini"
}

private fun ReasoningEffort.displayLabel(): String = when (this) {
    ReasoningEffort.AUTO -> "自动"
    ReasoningEffort.NONE -> "关闭"
    ReasoningEffort.MINIMAL -> "最低"
    ReasoningEffort.LOW -> "低"
    ReasoningEffort.MEDIUM -> "中"
    ReasoningEffort.HIGH -> "高"
    ReasoningEffort.XHIGH -> "超高"
    ReasoningEffort.MAX -> "最大"
}
