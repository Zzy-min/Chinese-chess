# 规格设计：轻棋局 Android 移动端 App 重构设计

本设计旨在重构“轻棋局”Android 移动端应用。原来的应用仅包含单一的 WebView 且硬编码加载 `127.0.0.1:18389` 导致物理设备不可达。重构后，应用将采用 Jetpack Compose 结构，注入国风视觉体系，实现环境切换、音效与登录态持久化、以及防白屏自适应。

---

## 1. 重构背景与痛点
- **硬编码不可达**: 默认加载的手机环回地址 `127.0.0.1` 导致在真机调试时无法访问开发机。
- **配置固化**: 无法切换本地开发环境（如局域网开发机 IP `10.0.2.2` 或真机局域网 IP）与生产环境（`https://www.xiangqiarena.com/online`）。
- **用户状态丢失**: 原有的 WebView 未开启完整的 Dom Storage 及 Cookie 持久化，导致每次打开 App 都需要重新登录和重新设置落子音效。
- **白屏体验差**: WebView 加载或刷新时，会闪烁刺眼的默认白色背景，破坏了精心设计的国风水墨调性。

---

## 2. 重构设计方案

### 2.1 整体架构与入口设计
重构后的应用使用 **Kotlin + Jetpack Compose** 构建单 Activity 架构：
- **MainActivity**: 程序唯一入口。
- **AppState**: 包含 `url`（对局地址）和 `screenState`（配置引导状态或对局游戏状态）。

### 2.2 视觉与引导页设计 (ConfigScreen)
- **国风主题风格**: 背景使用墨绿色与纸黄色柔和过渡，显示大大的水墨圆圈“棋”字和古风“落子之间，自有风雅”的标语。
- **输入框与按钮**: 提供服务器地址输入框（默认占位填充公网生产地址 `https://www.xiangqiarena.com/online`）。
- **快速入口**:
  - `连接公网生产环境`：直接载入 `https://www.xiangqiarena.com/online`。
  - `连接局域网开发机`：便捷填充并载入 `http://10.0.2.2:18488/online`（针对安卓模拟器）。
  - `确认进入对弈`：使用输入框的自定义 URL。

### 2.3 游戏对局页设计 (GameScreen & WebView 深度优化)
- **WebView 配置升级**:
  ```kotlin
  settings.javaScriptEnabled = true
  settings.domStorageEnabled = true // 落地 Sound 与 Auth 持久化
  settings.databaseEnabled = true
  settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
  ```
- **消灭加载白屏**: 
  - 设置 WebView 容器及自身的背景色为 `#f6f3eb`（轻棋局经典国风纸黄色）。
  - 在网页未加载完毕前，遮罩显示古风过渡加载指示。
- **物理返回拦截**: 重写 Compose 或 WebView 端的 BackHandler，如果 WebView 可后退则返回上一页，否则退回到环境配置页。
