package dev.leonardo.ocremotev2.ui.screens.viewer

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.leonardo.ocremotev2.ui.theme.SpacingTokens

/**
 * PDF viewer using PDF.js in WebView.
 * Loads base64-encoded PDF data and renders pages to canvas.
 *
 * @param base64Data Base64-encoded PDF content from API
 * @param visible Whether the viewer is visible
 * @param modifier Compose modifier
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PdfViewer(
    base64Data: String,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val escapedBase64 = remember(base64Data) {
        base64Data.replace("\\", "\\\\").replace("'", "\\'")
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = true          // 需要加载 assets 中的 pdf.js
                        allowContentAccess = false
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    // JS Interface for callbacks from pdf_viewer.html
                    addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun onPdfLoaded(total: Int) {
                                totalPages = total
                                isLoading = false
                            }

                            @android.webkit.JavascriptInterface
                            fun onPageRendered(current: Int, total: Int) {
                                currentPage = current
                                totalPages = total
                            }

                            @android.webkit.JavascriptInterface
                            fun onError(message: String) {
                                isLoading = false
                                hasError = true
                            }
                        },
                        "PdfViewerInterface"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                "loadPdfFromBase64('$escapedBase64')",
                                null
                            )
                        }
                    }

                    webChromeClient = WebChromeClient()
                    loadUrl("file:///android_asset/pdfjs/pdf_viewer.html")
                }
            },
            update = { webView ->
                webView.visibility = if (visible) View.VISIBLE else View.GONE
                webViewRef = webView
            }
        )

        // ── Toolbar overlay (page navigation) ──
        if (!isLoading && !hasError && totalPages > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpacingTokens.SM.dp, vertical = SpacingTokens.XS.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            webViewRef?.evaluateJavascript("prevPage()", null)
                        },
                        enabled = currentPage > 1
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous page"
                        )
                    }
                    Text(
                        text = "$currentPage / $totalPages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = SpacingTokens.MD.dp)
                    )
                    IconButton(
                        onClick = {
                            webViewRef?.evaluateJavascript("nextPage()", null)
                        },
                        enabled = currentPage < totalPages
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next page"
                        )
                    }
                }
            }
        }

        // ── Loading indicator ──
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
