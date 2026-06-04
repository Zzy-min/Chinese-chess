# Android 壳应用部署与实施计划 (Android Web App Wrapper Plan)

本计划定义了如何将“轻棋局 Online”项目打包为原生 Android APK 并一键部署到手机上。

## 实施步骤

### 第一步：初始化 Android 工程
- 使用 `android create empty-activity` 在 `C:\Users\Lenovo\Chinese-chess\deploy\android-app` 初始化项目。
- 模板参数：
  - 包名：`com.xiangqi.arena`
  - 应用名：`轻棋局`

### 第二步：配置 AndroidManifest.xml 权限
- 添加网络访问权限：
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  ```
- 在 `<application>` 标签下启用明文 HTTP 传输（以便局域网调试）：
  ```xml
  android:usesCleartextTraffic="true"
  ```

### 第三步：重写 MainActivity.kt 逻辑
- 使用 Jetpack Compose `AndroidView` 绑定原生 WebView。
- 开启 JavaScript 和 DOM 存储支持：
  ```kotlin
  settings.javaScriptEnabled = true
  settings.domStorageEnabled = true
  ```
- 默认加载地址配置为：`https://www.xiangqiarena.com/`。

### 第四步：编译并打包 APK
- 进入 `C:\Users\Lenovo\Chinese-chess\deploy\android-app` 目录。
- 运行 `./gradlew assembleDebug` 进行编译构建，生成 `app-debug.apk`。

### 第五步：自动化部署与安装
- 确认手机连接状态（序列号 `10AF530FSX002KA`）。
- 运行 `adb install -r <apk_path>` 将应用安装到手机上。
- 运行 `adb shell am start` 命令唤起应用。

## 验证计划
- 运行 `android screen capture` 截屏，检查手机上是否成功显示“轻棋局”应用的 WebView 加载画面，确保棋盘界面可以正常操作。
