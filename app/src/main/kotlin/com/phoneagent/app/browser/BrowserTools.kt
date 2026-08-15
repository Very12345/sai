package com.phoneagent.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** A project-isolated, non-UI WebView used only through constrained Agent tools. */
data class BrowserActionResult(val success: Boolean, val output: String)

class AgentBrowserSession(private val context: Context, private val projectId: String) {
    private var webView: WebView? = null
    @Volatile private var pageLoaded = CompletableDeferred<Unit>()
    @Volatile private var generation = 0L
    @Volatile private var loadError: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureWebView(): WebView = withContext(Dispatchers.Main) {
        webView ?: WebView(context.applicationContext).also { view ->
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val profile = "agent_${projectId.replace(Regex("[^A-Za-z0-9_]"), "_")}".take(70)
                ProfileStore.getInstance().getOrCreateProfile(profile)
                WebViewCompat.setProfile(view, profile)
            }
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.allowFileAccess = false
            view.settings.allowContentAccess = false
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoaded.complete(Unit)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        loadError = "${error?.errorCode}: ${error?.description ?: "页面加载失败"}"
                        pageLoaded.complete(Unit)
                    }
                }
            }
            webView = view
        }
    }

    suspend fun navigate(rawUrl: String): String {
        val url = normalizeUrl(rawUrl)
        val view = ensureWebView()
        loadError = null
        pageLoaded = CompletableDeferred()
        withContext(Dispatchers.Main) { view.loadUrl(url) }
        withTimeoutOrNull(25_000) { pageLoaded.await() }
        loadError?.let { error("Agent 浏览器无法访问 $url：$it") }
        return observe()
    }

    suspend fun observe(): String {
        val view = ensureWebView()
        val currentGeneration = ++generation
        val script = """
            (() => {
              const nodes = Array.from(document.querySelectorAll('a,button,input,textarea,select,[role="button"],[contenteditable="true"]')).slice(0,300);
              return JSON.stringify({
                url: location.href,
                title: document.title,
                generation: $currentGeneration,
                text: (document.body?.innerText || '').slice(0,20000),
                nodes: nodes.map((e, i) => {
                  e.setAttribute('data-phoneagent-node', String(i));
                  e.setAttribute('data-phoneagent-generation', '$currentGeneration');
                  const r = e.getBoundingClientRect();
                  return {id:i, tag:e.tagName, text:(e.innerText || e.value || e.getAttribute('aria-label') || '').slice(0,300), type:e.type || '', x:Math.round(r.x), y:Math.round(r.y), w:Math.round(r.width), h:Math.round(r.height)};
                })
              });
            })()
        """.trimIndent()
        return evaluate(view, script).take(100_000)
    }

    suspend fun action(action: String, nodeId: Int?, text: String?, finalSubmit: Boolean): BrowserActionResult {
        val view = ensureWebView()
        val result = when (action) {
            "click" -> evaluate(view, nodeScript(nodeId, """
                const label=(e.innerText || e.value || e.getAttribute('aria-label') || '').toLowerCase();
                const consequential=e.type==='submit' || /(submit|confirm|purchase|pay|order|install|delete|提交|确认|支付|购买|安装|删除)/.test(label);
                if(consequential && !${if (finalSubmit) "true" else "false"}) return 'blocked: finalSubmit=true required';
                e.click(); return 'clicked';
            """.trimIndent()))
            "input" -> evaluate(view, nodeScript(nodeId, "e.focus(); e.value=${jsString(text.orEmpty())}; e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); return 'input';"))
            "select" -> evaluate(view, nodeScript(nodeId, "e.focus(); e.value=${jsString(text.orEmpty())}; e.dispatchEvent(new Event('change',{bubbles:true})); return 'selected';"))
            "submit" -> evaluate(view, nodeScript(nodeId, "const f=e.form || e.closest('form'); if(!f) return 'form not found'; if(!${if (finalSubmit) "true" else "false"}) return 'blocked: finalSubmit=true required'; f.requestSubmit ? f.requestSubmit() : f.submit(); return 'submitted';"))
            "scroll_down" -> evaluate(view, "window.scrollBy({top: Math.max(500, innerHeight*.8), behavior:'smooth'}); 'scrolled'")
            "scroll_up" -> evaluate(view, "window.scrollBy({top: -Math.max(500, innerHeight*.8), behavior:'smooth'}); 'scrolled'")
            "back" -> withContext(Dispatchers.Main) { if (view.canGoBack()) { view.goBack(); "back" } else "no history" }
            "forward" -> withContext(Dispatchers.Main) { if (view.canGoForward()) { view.goForward(); "forward" } else "no forward history" }
            "reload" -> withContext(Dispatchers.Main) { view.reload(); "reloading" }
            "wait" -> { delay((text?.toLongOrNull() ?: 1_000L).coerceIn(100L, 10_000L)); "waited" }
            else -> return BrowserActionResult(false, "Unknown browser action: $action")
        }
        return BrowserActionResult(!result.contains("node not found", true) && !result.startsWith("blocked:"), "$result\n${observe()}")
    }

    suspend fun destroy() = withContext(Dispatchers.Main) {
        webView?.apply { stopLoading(); destroy() }
        webView = null
    }

    suspend fun screenshot(): File = withContext(Dispatchers.Main) {
        val view = ensureWebView()
        val width = view.width.takeIf { it > 0 } ?: 1080
        val height = view.height.takeIf { it > 0 } ?: 1920
        if (view.width <= 0 || view.height <= 0) {
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File(context.cacheDir, "browser/$projectId/browser-${System.currentTimeMillis()}.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        output
    }

    private suspend fun evaluate(view: WebView, script: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { raw ->
                val decoded = runCatching { Json.parseToJsonElement(raw).jsonPrimitive.contentOrNull }.getOrNull() ?: raw
                if (continuation.isActive) continuation.resume(decoded)
            }
        }
    }

    private fun nodeScript(nodeId: Int?, body: String): String = """
        (() => { const e=document.querySelector('[data-phoneagent-node="${nodeId ?: -1}"][data-phoneagent-generation="$generation"]'); if(!e) return 'node not found or stale generation'; $body })()
    """.trimIndent()

    private fun normalizeUrl(value: String): String = value.trim().let {
        when {
            it.startsWith("https://") || it.startsWith("http://") -> it
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}"
        }
    }

    private fun jsString(value: String): String = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
}
