# 实施计划：移动端 App 首页加载与显示异常修复

本计划实施 Android 引导页与本地测试 URL 路由的修正。

---

## 1. 详细实施步骤

### 步骤一：编辑 MainActivity.kt 修正布局与路由
- 打开 [MainActivity.kt](file:///C:/Users/Lenovo/Chinese-chess/deploy/android-app/app/src/main/java/com/example/MainActivity.kt)。
- **添加导入**:
  ```kotlin
  import androidx.compose.foundation.rememberScrollState
  import androidx.compose.foundation.verticalScroll
  ```
- **添加滚动修饰**:
  在 `ConfigScreen` 中的主 `Column` 上，添加 `.verticalScroll(rememberScrollState())` 修饰符，支持在超出屏幕高度时滚动。
- **状态同步修复**:
  在 `ConfigScreen` 中，将 `var inputUrl by remember { mutableStateOf(initialUrl) }` 改为 `var inputUrl by remember(initialUrl) { mutableStateOf(initialUrl) }`。
- **路径与端口修正**:
  - 修改 `连接模拟器开发环境` 按钮的 onClick 链接为 `"http://10.0.2.2:18388/online/index.html"`。
  - 修改 `测试人机对局 (本地)` 按钮的 onClick 链接为 `"http://127.0.0.1:18388/online/index.html#/practice"`。
  - 修改 `测试大厅首页 (本地)` 按钮的 onClick 链接为 `"http://127.0.0.1:18388/online/index.html#/home"`。

### 步骤二：本地编译校验
- 使用 Gradle 编译 Android 应用包：
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"; .\gradlew.bat assembleDebug
  ```
- 确认编译结果无语法错误，无依赖冲突，输出 `BUILD SUCCESSFUL`。

---

## 2. 预期成果
- 成功编译生成包含最新修复的 Android 调试包 `app-debug.apk`。
- 确认代码中去除了 `18488` 错误端口与 `/online/#/` 斜杠路由陷阱。
