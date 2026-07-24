package com.xiangqi.arena

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

private const val BRIDGE_OBJECT_NAME = "QingQijuApp"
private const val MAX_BRIDGE_MESSAGE_BYTES = 8_192

internal fun installNativeBridge(
    webView: WebView,
    endpoint: GameEndpoint,
    onVersionTap: () -> Unit,
    onShareRoom: (roomCode: String, shareUrl: String) -> Unit,
    onHaptic: (style: String) -> Unit,
) {
    val handler = NativeBridgeHandler(onVersionTap, onShareRoom, onHaptic)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_OBJECT_NAME,
            setOf(endpoint.origin),
        ) { view, message, sourceOrigin, isMainFrame, _ ->
            val data = message.data ?: return@addWebMessageListener
            if (!isMainFrame || sourceOrigin.toString() != endpoint.origin || !endpoint.allows(view.url.orEmpty())) {
                return@addWebMessageListener
            }
            handler.handle(data)
        }
        return
    }

    @Suppress("DEPRECATION")
    webView.addJavascriptInterface(
        LegacyNativeBridge(
            trusted = { endpoint.allows(webView.url.orEmpty()) },
            handler = handler,
        ),
        BRIDGE_OBJECT_NAME,
    )
}

private class NativeBridgeHandler(
    private val onVersionTap: () -> Unit,
    private val onShareRoom: (roomCode: String, shareUrl: String) -> Unit,
    private val onHaptic: (style: String) -> Unit,
) {
    fun versionTap() = onVersionTap()

    fun handle(message: String) {
        if (message.length > MAX_BRIDGE_MESSAGE_BYTES) return
        runCatching {
            val root = JSONObject(message)
            val payload = root.optJSONObject("payload") ?: JSONObject()
            when (root.optString("type")) {
                "appReady", "gameStateChanged", "networkStateChanged" -> Unit
                "versionTap" -> onVersionTap()
                "shareRoom" -> onShareRoom(
                    payload.optString("roomCode").take(24),
                    payload.optString("url").take(2_048),
                )
                "haptic" -> onHaptic(payload.optString("style").take(16))
            }
        }
    }
}

private class LegacyNativeBridge(
    private val trusted: () -> Boolean,
    private val handler: NativeBridgeHandler,
) {
    @JavascriptInterface
    fun versionTap() {
        if (trusted()) handler.versionTap()
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        if (trusted()) handler.handle(message)
    }
}
