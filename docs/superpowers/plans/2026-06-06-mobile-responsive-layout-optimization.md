# 手机轻棋局 移动端响应式布局优化实施计划

本计划实施大厅和对局桌的移动端响应式优化，并通过测试辅助手段在设备上验证。

## 1. 详细实施步骤

### 步骤一：追加移动端响应式 CSS 样式
- 打开并编辑 [src/main/resources/online/app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css)。
- 在文件最末尾（3262行后）追加 `@media (max-width: 768px)` 样式的媒体查询，内容包含：
  - 隐藏首页 Hero 插图，更改 Hero 文本字号，按钮纵向排布。
  - 大厅 `.deskLobby` 改为单列，隐藏大厅左侧栏 `.deskSidebar`。
  - 对局桌 `.boardDesk` 单列化，隐藏步法记录侧边栏，将选手栏 `.boardRail` 重构为横排双头像并置于棋盘上方，自适应微调棋盘大小。

### 步骤二：修改安卓入口以辅助测试
- 打开并编辑 [deploy/android-app/app/src/main/java/com/example/MainActivity.kt](file:///C:/Users/Lenovo/Chinese-chess/deploy/android-app/app/src/main/java/com/example/MainActivity.kt)。
- 在 `ConfigScreen` 中新增两个快速测试按钮：
  - **“测试人机对局 (在线)”**：加载并进入 `https://www.xiangqiarena.com/online/#/practice`。
  - **“测试大厅首页 (在线)”**：加载并进入 `https://www.xiangqiarena.com/online/#/home`。
- 这有助于直接越过首页登录/跳转，直接进入并测量对局桌和新大厅的移动端真实比例。

### 步骤三：编译与推送 Android APK
- 在 `C:\Users\Lenovo\Chinese-chess\deploy\android-app` 执行打包：
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"; .\gradlew.bat assembleDebug
  ```
- 重新安装到手机设备：
  ```powershell
  & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 10AF530FSX002KA install -r "C:\Users\Lenovo\Chinese-chess\deploy\android-app\app\build\outputs\apk\debug\app-debug.apk"
  ```
- 拉起 Activity。

### 步骤四：通过快捷测试键进行截图验证
- 打开 App 后，在首屏中，通过 adb 分别点击“测试人机对局 (在线)”与“测试大厅首页 (在线)”对应的新按钮。
- 截图拉取到宿主机，验证：
  - 首页排版是否紧凑无截断。
  - 人机对弈页的棋盘是否占满大半个屏幕且状态时钟可点、可见。

## 2. 预期成果与证据

- **更新后的 CSS 源码**：[app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css)
- **更新后的安卓 Activity**：[MainActivity.kt](file:///C:/Users/Lenovo/Chinese-chess/deploy/android-app/app/src/main/java/com/example/MainActivity.kt)
- **对局页验证截图**：`mobile_practice_verified.png`
- **大厅页验证截图**：`mobile_home_verified.png`
