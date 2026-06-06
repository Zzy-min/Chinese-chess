package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class ScreenState {
    CONFIG, GAME
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            var screenState by remember { mutableStateOf(ScreenState.CONFIG) }
            var serverUrl by remember { mutableStateOf("https://www.xiangqiarena.com/online") }
            var webViewInstance by remember { mutableStateOf<WebView?>(null) }
            val isLoading = remember { mutableStateOf(true) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF6F3EB) // 轻棋局经典国风纸黄色
                ) {
                    when (screenState) {
                        ScreenState.CONFIG -> {
                            ConfigScreen(
                                initialUrl = serverUrl,
                                onEnterGame = { selectedUrl ->
                                    serverUrl = selectedUrl
                                    isLoading.value = true
                                    screenState = ScreenState.GAME
                                }
                            )
                        }
                        ScreenState.GAME -> {
                            GameScreen(
                                url = serverUrl,
                                isLoading = isLoading,
                                onWebViewCreated = { webViewInstance = it },
                                onBackToConfig = { screenState = ScreenState.CONFIG }
                            )
                            
                            // 物理返回键拦截与智能回退
                            BackHandler(enabled = true) {
                                val wv = webViewInstance
                                if (wv != null && wv.canGoBack()) {
                                    wv.goBack()
                                } else {
                                    screenState = ScreenState.CONFIG
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigScreen(
    initialUrl: String,
    onEnterGame: (String) -> Unit
) {
    var inputUrl by remember { mutableStateOf(initialUrl) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F3EB))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 国风书法水墨感“棋”字圆形图标
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF8C2E21), shape = RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "棋",
                    color = Color.White,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "轻 棋 局",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1A16)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "落子之间，自有风雅",
                fontSize = 15.sp,
                color = Color(0xFF6D6256)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 路由输入框
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                label = { Text("对局服务器 URL", color = Color(0xFF6D6256)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8C2E21),
                    unfocusedBorderColor = Color(0xFFD8CFBE),
                    focusedLabelColor = Color(0xFF8C2E21)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 快速连接按钮：生产环境 (红)
            Button(
                onClick = { onEnterGame("https://www.xiangqiarena.com/online") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C2E21))
            ) {
                Text("连接公网生产环境", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 快速连接按钮：局域网模拟器环境 (绿)
            Button(
                onClick = { onEnterGame("http://10.0.2.2:18488/online") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E4D3E))
            ) {
                Text("连接模拟器开发环境 (10.0.2.2)", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 自定义 URL 连接按钮
            OutlinedButton(
                onClick = { onEnterGame(inputUrl.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E1A16))
            ) {
                Text("确认进入对弈", fontSize = 16.sp)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameScreen(
    url: String,
    isLoading: MutableState<Boolean>,
    onWebViewCreated: (WebView) -> Unit,
    onBackToConfig: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F3EB))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#f6f3eb"))
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading.value = false
                        }
                        
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url != null) {
                                view?.loadUrl(url)
                            }
                            return true
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    onWebViewCreated(this)
                    loadUrl(url)
                }
            },
            update = {
                // WebView 维持当前实例并渲染更新
            }
        )

        // 国风过渡加载屏障
        if (isLoading.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF6F3EB)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF8C2E21))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("古风雅局，正在铺设...", color = Color(0xFF6D6256), fontSize = 15.sp)
                }
            }
        }
    }
}
