# 实施计划：轻棋局 Android 移动端 App 重构实施

本计划旨在落实 Android 应用重构的各项重构设计。

---

## 1. 实施步骤规划

### 第一步：修改 Activity 声明与入口
- 在 [MainActivity.kt](file:///C:/Users/Lenovo/Chinese-chess/deploy/android-app/app/src/main/java/com/example/MainActivity.kt) 中：
  - 将继承关系从 `android.app.Activity` 修改为 `androidx.activity.ComponentActivity`。
  - 导入 `androidx.activity.compose.setContent`。
  - 定义哈希状态结构：`ScreenState { CONFIG, GAME }`，用于控制首屏显示。

### 第二步：编写古风环境引导页 (ConfigScreen)
- 使用 Compose 构建 `ConfigScreen`：
  - 水墨国风背景：暖黄微渐变底色（`#f6f3eb`），大大的毛笔字风格“棋”字。
  - 服务器地址输入框，提供默认的 `https://www.xiangqiarena.com/online` 公网域名。
  - 提供两个快速填充并载入的“按钮卡片”：
    1. **公网对决**：直接将 URL 设置为 `https://www.xiangqiarena.com/online` 并进入对局页。
    2. **模拟器开发**：直接将 URL 设置为 `http://10.0.2.2:18488/online` 并进入对局页。
  - 提供大红色的“进入棋局”确认按钮。

### 第三步：编写深度优化后的对弈页 (GameScreen)
- 使用 Compose 承载 `AndroidView` 包裹的 `WebView`：
  - 启用 `javaScriptEnabled`、`domStorageEnabled` 和 `databaseEnabled`。
  - 允许 `MIXED_CONTENT_ALWAYS_ALLOW`，支持开发和测试环境下的混合内容。
  - 设定 WebView 本身和容器背景色为暖黄色（`#f6f3eb`），避免闪烁白屏。
  - 在 WebView 顶部引入沉浸式效果，并在载入未完成时加入柔和过渡屏障。
  - 使用 Compose `BackHandler` 拦截物理返回键：如果 WebView 能返回则后退，否则退回到 `ConfigScreen` 允许用户重新配置连接环境。

### 第四步：本地编译验证
- 运行 `./gradlew assembleDebug` 或是通过 `android-cli` 执行打包编译，确认 Kotlin 语法、类库依赖与 Gradle 编译无误。
