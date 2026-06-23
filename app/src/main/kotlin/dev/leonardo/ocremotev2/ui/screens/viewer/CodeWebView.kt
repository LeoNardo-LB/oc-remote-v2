package dev.leonardo.ocremotev2.ui.screens.viewer

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.leonardo.ocremotev2.R

private const val TAG = "CodeWebView"

/** Maps file extension to Highlight.js language name. */
private fun extToLanguage(filePath: String): String {
    val ext = filePath.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "xml" -> "xml"
        "json" -> "json"
        "py" -> "python"
        "js" -> "javascript"
        "ts" -> "typescript"
        "go" -> "go"
        "rs" -> "rust"
        "c", "h" -> "c"
        "cpp", "cc", "cxx" -> "cpp"
        "cs" -> "csharp"
        "rb" -> "ruby"
        "swift" -> "swift"
        "php" -> "php"
        "sh", "bash" -> "bash"
        "sql" -> "sql"
        "yaml", "yml" -> "yaml"
        "html", "htm" -> "xml"
        "css" -> "css"
        "md" -> "markdown"
        "gradle" -> "groovy"
        "properties" -> "properties"
        "dockerfile" -> "dockerfile"
        "toml" -> "ini"
        else -> ""
    }
}

/**
 * Stable bridge for JavaScript → Kotlin communication.
 *
 * Uses a mutable holder so the bridge object itself never changes
 * (WebView holds a reference to it). Callbacks are posted to the
 * main thread because @JavascriptInterface methods run on a binder
 * thread — Compose state updates from non-main threads fail silently.
 */
private class CodeViewerBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    var annotateCallback: ((String) -> Unit)? = null
    var copyCallback: ((String) -> Unit)? = null

    @JavascriptInterface
    fun onAnnotate(text: String) {
        Log.d(TAG, "onAnnotate called: '${text.take(50)}...'")
        mainHandler.post { annotateCallback?.invoke(text) }
    }

    @JavascriptInterface
    fun onCopy(text: String) {
        Log.d(TAG, "onCopy called: '${text.take(50)}...'")
        mainHandler.post { copyCallback?.invoke(text) }
    }
}

/**
 * WebView-based code viewer using Highlight.js.
 *
 * Replaces the hand-rolled Compose Text-based [CodeSourceView] to fix:
 * - Syntax highlighting quality (Highlight.js is industrial-grade)
 * - Text selection (WebView native selection works reliably)
 * - Custom context menu ("Annotate" injected via JavaScript)
 * - Gutter/code split scroll (CSS flexbox, gutter stays fixed)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeWebView(
    content: String,
    filePath: String,
    modifier: Modifier = Modifier,
    onAnnotate: ((String) -> Unit)? = null,
    onCopy: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val annotateLabel = androidx.compose.ui.res.stringResource(R.string.annotation_context_annotate)
    val copyLabel = androidx.compose.ui.res.stringResource(android.R.string.copy)
    val language = remember(filePath) { extToLanguage(filePath) }
    val escapedContent = remember(content) {
        content.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
    }

    // Stable bridge — created once, never recreated on recomposition.
    // Callbacks are updated in place so the WebView's reference stays valid.
    val bridge = remember { CodeViewerBridge() }
    bridge.annotateCallback = onAnnotate
    bridge.copyCallback = onCopy

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = "OCRemoteCodeViewer"
                settings.allowFileAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                addJavascriptInterface(bridge, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "onPageFinished, setting code (${escapedContent.length} chars, lang=$language)")
                        view?.evaluateJavascript(
                            "setCode(`$escapedContent`, '$language'); setTheme($isDark);",
                            null
                        )
                    }
                }

                val html = ctx.assets.open("code_viewer.html").bufferedReader().use { it.readText() }
                    .replace("__ANNOTATE_LABEL__", annotateLabel)
                    .replace("__COPY_LABEL__", copyLabel)
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.post {
                webView.evaluateJavascript(
                    "setCode(`$escapedContent`, '$language'); setTheme($isDark);",
                    null
                )
            }
        }
    )
}
