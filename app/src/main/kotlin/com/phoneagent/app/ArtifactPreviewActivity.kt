package com.phoneagent.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import io.github.rosemoe.sora.widget.CodeEditor
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Isolated in-app preview for URLs and model-produced artifacts. */
class ArtifactPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val file = intent.getStringExtra(EXTRA_PATH)?.let(::File)?.takeIf(File::isFile)
        if (url == null && file == null) {
            finish()
            return
        }
        val mime = intent.getStringExtra(EXTRA_MIME) ?: file?.let(::mimeFor) ?: "text/html"
        setContent {
            MaterialTheme {
                PreviewScreen(url, file, mime, ::finish, ::openExternal, ::share)
            }
        }
    }

    private fun openExternal(url: String?, file: File?, mime: String) {
        val intent = if (url != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            val uri = file?.let { FileProvider.getUriForFile(this, "$packageName.files", it) } ?: return
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun share(url: String?, file: File?, mime: String) {
        val send = if (url != null) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        } else {
            val uri = file?.let { FileProvider.getUriForFile(this, "$packageName.files", it) } ?: return
            Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "分享"))
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_PATH = "path"
        const val EXTRA_MIME = "mime"

        fun mimeFor(file: File): String = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewScreen(
    url: String?,
    file: File?,
    mime: String,
    close: () -> Unit,
    external: (String?, File?, String) -> Unit,
    share: (String?, File?, String) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(file?.name ?: url.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                IconButton(onClick = { share(url, file, mime) }) { Icon(Icons.Default.Share, "分享") }
                IconButton(onClick = { external(url, file, mime) }) { Icon(Icons.Default.OpenInBrowser, "使用默认浏览器或应用打开") }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                url != null -> SafeWebPreview(url = url)
                file == null -> Text("文件不可用", Modifier.padding(20.dp))
                mime == "application/pdf" || file.extension.equals("pdf", true) -> PdfPreview(file)
                mime.startsWith("image/") -> ImagePreview(file)
                mime.startsWith("video/") -> VideoPreview(file)
                mime == "text/html" || file.extension.equals("html", true) || file.extension.equals("htm", true) -> SafeWebPreview(file = file)
                file.extension.equals("md", true) || file.extension.equals("markdown", true) -> MarkdownPreview(file)
                file.extension.lowercase() in CODE_EXTENSIONS -> CodePreview(file)
                mime.startsWith("text/") || file.extension.lowercase() in TEXT_EXTENSIONS -> TextPreview(file)
                else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("此格式仅支持系统应用预览")
                    Button(onClick = { external(null, file, mime) }, Modifier.padding(top = 12.dp)) { Text("打开") }
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreview(file: File) {
    var markdown by remember(file) { mutableStateOf("正在读取…") }
    LaunchedEffect(file) {
        markdown = withContext(Dispatchers.IO) {
            file.inputStream().bufferedReader().use { it.readText().take(1_500_000) }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> TextView(context).apply { setPadding(24, 20, 24, 40); textSize = 16f } },
        update = { textView ->
            Markwon.builder(textView.context)
                .usePlugin(TablePlugin.create(textView.context))
                .usePlugin(JLatexMathPlugin.create(42f) { })
                .build()
                .setMarkdown(textView, markdown)
        },
    )
}

@Composable
private fun CodePreview(file: File) {
    var code by remember(file) { mutableStateOf("正在读取…") }
    LaunchedEffect(file) {
        code = withContext(Dispatchers.IO) {
            file.inputStream().bufferedReader().use { it.readText().take(2_000_000) }
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> CodeEditor(context).apply { isLineNumberEnabled = true; isEditable = false } },
        update = { editor -> if (editor.text.toString() != code) editor.setText(code) },
    )
}

@Composable
private fun VideoPreview(file: File) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                val controls = MediaController(context)
                controls.setAnchorView(this)
                setMediaController(controls)
                setVideoPath(file.absolutePath)
                setOnPreparedListener { player -> player.isLooping = false; start() }
            }
        },
        update = { },
    )
}

@Composable
private fun SafeWebPreview(url: String? = null, file: File? = null) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val loader = file?.parentFile?.let { root ->
                WebViewAssetLoader.Builder()
                    .addPathHandler("/artifact/", WebViewAssetLoader.InternalStoragePathHandler(context, root))
                    .build()
            }
            WebView(context).apply {
                settings.javaScriptEnabled = file != null
                settings.domStorageEnabled = file != null
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        loader?.shouldInterceptRequest(request.url) ?: super.shouldInterceptRequest(view, request)
                }
                loadUrl(url ?: "https://appassets.androidplatform.net/artifact/${Uri.encode(file!!.name)}")
            }
        },
        update = { },
    )
}

@Composable
private fun TextPreview(file: File) {
    var text by remember(file) { mutableStateOf("正在读取…") }
    LaunchedEffect(file) {
        text = withContext(Dispatchers.IO) {
            file.inputStream().bufferedReader().use { it.readText().take(1_500_000) }
        }
    }
    Text(
        text,
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun ImagePreview(file: File) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) } }
    bitmap?.let { Image(it.asImageBitmap(), file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
}

@Composable
private fun PdfPreview(file: File) {
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    pageCount = renderer.pageCount
                    renderer.openPage(pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))).use { page ->
                        val width = 1200
                        val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = pageIndex > 0, onClick = { pageIndex-- }) { Icon(Icons.Default.ChevronLeft, "上一页") }
            Text("${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}")
            IconButton(enabled = pageIndex + 1 < pageCount, onClick = { pageIndex++ }) { Icon(Icons.Default.ChevronRight, "下一页") }
        }
        bitmap?.let { Image(it.asImageBitmap(), "PDF 第 ${pageIndex + 1} 页", Modifier.weight(1f), contentScale = ContentScale.Fit) }
    }
    DisposableEffect(file) { onDispose { bitmap?.recycle() } }
}

private val TEXT_EXTENSIONS = setOf("txt", "log", "ini", "cfg", "csv")
private val CODE_EXTENSIONS = setOf("json", "xml", "yaml", "yml", "toml", "py", "kt", "java", "js", "ts", "css", "go", "rs", "c", "h", "cpp", "hpp", "sh", "gradle")
