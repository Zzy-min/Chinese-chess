# 规格设计：移动端 App 首页加载与显示异常修复

本设计旨在修复“轻棋局”Android 移动端应用在加载本地测试页面时遭遇的 404 异常白屏以及配置界面可能存在的布局截断问题。

---

## 1. 现状与问题分析

在运行 Android 应用并尝试进行本地调试或连接本地服务器时，存在以下问题：
1. **404 路由陷阱**:
   - 本地 Undertow 服务器严格匹配路径。它注册了 `/online` (无斜杠) 与 `/online/index.html`，但未注册 `/online/` (带斜杠)。
   - Android App 中的“测试大厅首页 (本地)”与“测试人机对局 (本地)”按钮指向的 URL 分别为 `http://127.0.0.1:18388/online/#/home` 与 `http://127.0.0.1:18388/online/#/practice`。
   - 浏览器在请求这两个 URL 时，解析的 Path 为 `/online/`，由于路由不匹配，服务器直接返回 404，导致 WebView 显示“resource not found”或白屏异常。
2. **模拟器端口与路径错误**:
   - 本地开发机服务器端口是 `18388`，但模拟器快速连接按钮配置了错误的端口 `18488`，且路径为 `/online` 同样容易因重定向和匹配产生异常。
3. **Jetpack Compose 状态同步缺陷**:
   - `ConfigScreen` 中的 `inputUrl` 输入框使用 `remember { mutableStateOf(initialUrl) }` 保存状态。
   - 当用户点击快速连接按钮（使 `serverUrl` 改变）或从游戏页返回后，`initialUrl` 发生了变化，但因为 `remember` 没有绑定 Key，输入框内的文本依然维持着旧值，导致状态丢失。
4. **配置页面排版溢出风险**:
   - 引导页包含 5 个较大的按钮、输入框、标题和插图，总高度接近 560dp。如果手机屏幕较小、处于横屏模式或弹出软键盘时，未支持纵向滚动的 `Column` 会导致页面元素被硬性截断，无法向下滚动点击确认按钮。

---

## 2. 解决方案设计

### 2.1 路径与端口修正 (MainActivity.kt)
修改 `MainActivity.kt` 中的快速连接 URL 映射：
- 将所有指向本地开发的 URL 精确指定到 `/online/index.html`，绕开 Undertow 的斜杠路由陷阱。
- 将模拟器开发环境端口修正为 `18388`。

| 按钮 | 旧配置 | 新配置 |
| :--- | :--- | :--- |
| 连接模拟器开发环境 | `http://10.0.2.2:18488/online` | `http://10.0.2.2:18388/online/index.html` |
| 测试人机对局 (本地) | `http://127.0.0.1:18388/online/#/practice` | `http://127.0.0.1:18388/online/index.html#/practice` |
| 测试大厅首页 (本地) | `http://127.0.0.1:18388/online/#/home` | `http://127.0.0.1:18388/online/index.html#/home` |

### 2.2 状态同步与滚动支持 (MainActivity.kt)
- **状态同步**: 将 `remember { ... }` 替换为 `remember(initialUrl) { ... }`。以 `initialUrl` 作为重置的 Key，一旦初始 URL 发生改变，输入框中的状态就会自动刷新。
- **页面滚动**: 
  - 导入 `androidx.compose.foundation.rememberScrollState` 和 `androidx.compose.foundation.verticalScroll`。
  - 为 `ConfigScreen` 的主 `Column` 容器添加 `Modifier.verticalScroll(rememberScrollState())` 修饰符。

---

## 3. 验收标准
- 编译并安装运行 App 后，点击“测试大厅首页 (本地)”或“测试人机对局 (本地)”，WebView 能够流畅加载并显示相应的游戏大厅与棋局界面，不出现 404 错误。
- 从 WebView 返回引导页后，输入框内的 URL 会自动与当前选定的 URL 保持同步。
- 引导页具备滚动条，在小屏幕或键盘弹起时不会发生截断，用户能自由滚动交互。
