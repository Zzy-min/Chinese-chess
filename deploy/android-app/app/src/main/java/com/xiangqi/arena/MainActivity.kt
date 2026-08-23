package com.xiangqi.arena

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val Paper = Color(0xFFF5F0E6)
private val Ink = Color(0xFF211D18)
private val MutedInk = Color(0xFF6F675C)
private val Cinnabar = Color(0xFF9D3023)
private val Pine = Color(0xFF315C4D)

private sealed interface AppSurface {
    data class Web(val endpoint: GameEndpoint, val reloadKey: Int = 0) : AppSurface
    data class Error(val endpoint: GameEndpoint, val message: String) : AppSurface
    data class Developer(val previous: GameEndpoint) : AppSurface
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = DeveloperSettingsStore(this)
        val initialEndpoint = LaunchPolicy.resolve(
            isDebug = BuildConfig.DEBUG,
            savedOverride = settings.selectedEndpoint(),
            oneShotDebugUrl = intent.getStringExtra("debugEndpoint"),
        )

        setContent {
            var surface: AppSurface by remember { mutableStateOf(AppSurface.Web(initialEndpoint)) }
            var webView by remember { mutableStateOf<WebView?>(null) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
                    when (val current = surface) {
                        is AppSurface.Web -> WebAppScreen(
                            endpoint = current.endpoint,
                            reloadKey = current.reloadKey,
                            onWebViewCreated = { webView = it },
                            onFailure = { message -> surface = AppSurface.Error(current.endpoint, message) },
                            onDeveloperUnlocked = {
                                if (BuildConfig.DEBUG) surface = AppSurface.Developer(current.endpoint)
                            },
                        )
                        is AppSurface.Error -> LoadErrorScreen(
                            message = current.message,
                            onRetry = { surface = AppSurface.Web(current.endpoint, current.hashCode()) },
                            onDeveloperSettings = if (BuildConfig.DEBUG) {
                                { surface = AppSurface.Developer(current.endpoint) }
                            } else null,
                        )
                        is AppSurface.Developer -> DeveloperSettingsScreen(
                            current = current.previous,
                            onApply = { endpoint ->
                                settings.save(endpoint.takeUnless { it == GameEndpoint.Production })
                                surface = AppSurface.Web(endpoint, endpoint.hashCode())
                            },
                            onCancel = { surface = AppSurface.Web(current.previous) },
                        )
                    }
                }
            }

            BackHandler(enabled = true) {
                when (val current = surface) {
                    is AppSurface.Developer -> surface = AppSurface.Web(current.previous)
                    is AppSurface.Error -> surface = AppSurface.Web(current.endpoint)
                    is AppSurface.Web -> if (webView?.canGoBack() == true) webView?.goBack() else finish()
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebAppScreen(
    endpoint: GameEndpoint,
    reloadKey: Int,
    onWebViewCreated: (WebView) -> Unit,
    onFailure: (String) -> Unit,
    onDeveloperUnlocked: () -> Unit,
) {
    var loading by remember(endpoint, reloadKey) { mutableStateOf(true) }
    var versionTapCount by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(Paper)) {
        key(endpoint.url, reloadKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "轻棋局网页内容" },
                factory = { context ->
                    WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.rgb(245, 240, 230))
                    WebView.setWebContentsDebuggingEnabled(true)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = false
                        loadWithOverviewMode = false
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportZoom(false)
                    }
                    webChromeClient = WebChromeClient()
                    installNativeBridge(
                        webView = this,
                        endpoint = endpoint,
                        onVersionTap = {
                            versionTapCount += 1
                            if (versionTapCount >= 7) {
                                versionTapCount = 0
                                onDeveloperUnlocked()
                            }
                        },
                        onShareRoom = { roomCode, shareUrl ->
                            post {
                                if (endpoint.allows(shareUrl)) {
                                    val text = buildString {
                                        append("轻棋局好友对弈")
                                        if (roomCode.isNotBlank()) append("\n房间码：$roomCode")
                                        append("\n$shareUrl")
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "轻棋局好友对弈")
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            },
                                            "分享房间",
                                        ),
                                    )
                                }
                            }
                        },
                        onHaptic = { style ->
                            post {
                                performHapticFeedback(
                                    if (style == "medium") HapticFeedbackConstants.CONFIRM
                                    else HapticFeedbackConstants.CLOCK_TICK,
                                )
                            }
                        },
                    )
                    webViewClient = SafeWebViewClient(
                        endpoint = endpoint,
                        onPageStarted = { loading = true },
                        onPageFinished = { loading = false },
                        onFailure = onFailure,
                    )
                    onWebViewCreated(this)
                    loadUrl(endpoint.url)
                    }
                },
                update = { view ->
                    if (view.url.isNullOrBlank()) view.loadUrl(endpoint.url)
                },
            )
        }

        if (loading) LoadingCurtain()
    }
}

private class SafeWebViewClient(
    private val endpoint: GameEndpoint,
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val onFailure: (String) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        onPageStarted()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onPageFinished()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val candidate = request?.url?.toString().orEmpty()
        if (endpoint.allows(candidate)) return false
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            onFailure(error?.description?.toString().orEmpty().ifBlank { "暂时无法连接棋局服务" })
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
        handler?.cancel()
        onFailure("安全连接校验失败，请稍后重试")
    }
}

@Composable
private fun LoadingCurtain() {
    Box(modifier = Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("棋", color = Cinnabar, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Cinnabar)
            Spacer(Modifier.height(16.dp))
            Text("正在展开棋局…", color = MutedInk, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LoadErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onDeveloperSettings: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("连接未落定", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(message, color = MutedInk, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cinnabar),
            shape = RoundedCornerShape(14.dp),
        ) { Text("重新连接") }
        if (onDeveloperSettings != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onDeveloperSettings, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("开发者设置", color = Ink)
            }
        }
    }
}

@Composable
private fun DeveloperSettingsScreen(
    current: GameEndpoint,
    onApply: (GameEndpoint) -> Unit,
    onCancel: () -> Unit,
) {
    var customUrl by remember(current) { mutableStateOf(current.url) }
    var error by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("开发者设置", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("仅 Debug 构建可用", color = MutedInk, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        EnvironmentButton("生产环境", Cinnabar) { onApply(GameEndpoint.Production) }
        EnvironmentButton("Android 模拟器", Pine) { onApply(GameEndpoint.Emulator) }
        OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it; error = false },
            label = { Text("自定义 HTTP/HTTPS 地址") },
            isError = error,
            supportingText = if (error) ({ Text("请输入有效的 HTTP/HTTPS 地址") }) else null,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        Button(
            onClick = {
                val endpoint = GameEndpoint.customOrNull(customUrl)
                error = endpoint == null
                endpoint?.let(onApply)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cinnabar),
        ) { Text("应用自定义地址") }
        Text(
            "返回轻棋局",
            color = MutedInk,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(18.dp).clickable(onClick = onCancel),
        )
    }
}

@Composable
private fun EnvironmentButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(14.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}
