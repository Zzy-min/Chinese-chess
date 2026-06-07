# 手机轻棋局 Android App 编译与部署实施计划

本计划落实“手机轻棋局 App”的编译与部署验证。

## 1. 详细实施步骤

### 步骤一：编译环境确认
- 确认 JDK 17 及相关环境变量可用。
- 确认当前已连接的设备为 `10AF530FSX002KA`。

### 步骤二：项目编译构建 (assembleDebug)
- 进入目录 `C:\Users\Lenovo\Chinese-chess\deploy\android-app`。
- 执行编译：
  ```powershell
  .\gradlew.bat assembleDebug
  ```
- 确认编译是否成功。如果存在依赖或语法报错，需依据报错内容修改 `build.gradle.kts` 或 Kotlin 源码。
- 确认 APK 产物路径在：
  `C:\Users\Lenovo\Chinese-chess\deploy\android-app\app\build\outputs\apk\debug\app-debug.apk`

### 步骤三：应用部署安装
- 将 APK 推送到已连接的手机上：
  ```powershell
  & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 10AF530FSX002KA install -r "C:\Users\Lenovo\Chinese-chess\deploy\android-app\app\build\outputs\apk\debug\app-debug.apk"
  ```

### 步骤四：拉起与激活 App
- 在设备上调起 `MainActivity`：
  ```powershell
  & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 10AF530FSX002KA shell am start -n com.example/com.example.MainActivity
  ```

### 步骤五：画面与功能验证
- 截图保存手机的屏幕画面，以便确认是否进入古风 `ConfigScreen`：
  ```powershell
  & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 10AF530FSX002KA shell screencap -p /sdcard/screen.png
  & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s 10AF530FSX002KA pull /sdcard/screen.png C:\Users\Lenovo\.gemini\antigravity-cli\brain\97274f14-5066-4afb-9186-d47ff35e0154\screen.png
  ```
- 检查截图，确保“轻棋局”引导页能完整、自适应地显示。

## 2. 成果验证

- **产物文件**：[app-debug.apk](file:///C:/Users/Lenovo/Chinese-chess/deploy/android-app/app/build/outputs/apk/debug/app-debug.apk)
- **验证截图**：[screen.png](file:///C:/Users/Lenovo/.gemini/antigravity-cli/brain/97274f14-5066-4afb-9186-d47ff35e0154/screen.png)
