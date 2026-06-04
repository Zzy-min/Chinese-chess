# Android 壳应用设计说明书 (Android Web App Wrapper Spec)

该应用旨在将“轻棋局 Online”项目封装为一个原生的 Android 应用安装在手机上。

## 1. 方案选择：WebView 套壳

### 为什么选择 WebView 套壳方案？
1. **完整功能保留**：该项目包含复杂的 Java 后端（包括房间对局管理、Negamax 象棋 AI 等），直接在 Android 本地部署完整的 Java 服务并保证算法效率十分复杂，且不利于多人联机。
2. **快速热更新**：借助于官方域名 `https://www.xiangqiarena.com/`，网站的前端和后端更新可以实时反映到 App 中，无需用户反复安装 APK。
3. **原生运行体验**：通过沉浸式全屏 WebView、隐藏状态栏和响应式布局，App 的体验可以与原生应用无异。

## 2. 核心技术栈
- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose (Android 推荐标准)
- **底层组件**：`android.webkit.WebView` 配合 `AndroidView` 进行 Compose 桥接。
- **构建工具**：Gradle (Android Gradle Plugin 9.x)

## 3. 关键配置与优化

### 3.1 网络与安全配置
- **Internet 权限**：必须在 `AndroidManifest.xml` 声明网络访问权限。
- **混合内容与明文传输**：为了确保在局域网测试时可以加载 `http://` 地址，配置 `android:usesCleartextTraffic="true"`。

### 3.2 WebView 性能与适配
- `settings.javaScriptEnabled = true`：项目为 Web 交互，必须启用 JS。
- `settings.domStorageEnabled = true`：启用 DOM 存储以支持本地状态/缓存（如登录 Token 或对局配置）。
- `settings.useWideViewPort = true` & `settings.loadWithOverviewMode = true`：自适应屏幕宽度。
- 自定义 `WebViewClient`：防止跳转页面时拉起系统浏览器，所有导航必须在 App 内部完成。

## 4. UI 结构设计
由于是 empty-activity Compose 模板，我们只需要在 `MainActivity.kt` 里的 `setContent` 块内，用 `AndroidView` 包裹自定义 WebView 覆盖全屏，屏蔽状态栏/导航栏的空白区以达到完美的全屏浸入效果。
