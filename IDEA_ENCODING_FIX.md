# IDEA 编码问题解决方案

## 问题
IDEA 编译的 class 文件编码不正确，导致棋子显示为数字。

## 解决方案

### 方法1：配置 IDEA 编译编码（推荐）

1. 打开 IDEA，进入 `File` -> `Settings` (或 `Ctrl+Alt+S`)
2. 导航到 `Editor` -> `File Encodings`
3. 设置以下三项为 `UTF-8`：
   - **Global Encoding**: UTF-8
   - **Project Encoding**: UTF-8
   - **Default encoding for properties files**: UTF-8
4. 点击 `Apply` 和 `OK`

5. 重新编译项目：
   - 选择 `Build` -> `Rebuild Project`

### 方法2：配置编译器选项

1. `File` -> `Settings` -> `Build, Execution, Deployment` -> `Compiler` -> `Java Compiler`
2. 确保以下设置：
   - **Additional command line parameters**: `-encoding UTF-8`
   - 或者在 "VM options" 中添加：`-Dfile.encoding=UTF-8`

### 方法3：清理并重建

1. 右键点击项目根目录
2. 选择 `Maven` -> `clean` 或 `Gradle` -> `clean`
3. 或者手动删除 `out` 和 `target` 文件夹
4. `Build` -> `Rebuild Project`

### 方法4：使用命令行编译的 class（快速方案）

1. 运行 `final_fix.bat` 脚本
2. 在 IDEA 中配置使用 `bin` 文件夹作为输出目录
3. 运行时选择正确的 class 文件

### 方法5：IDEA 运行配置

1. `Run` -> `Edit Configurations`
2. 找到主类的配置（XiangqiFrame）
3. 在 `VM options` 中添加：
   ```
   -Dfile.encoding=UTF-8
   -Duser.language=zh
   -Duser.country=CN
   ```
4. 保存配置并重新运行

---

## 验证

运行后，棋子应显示为：
- 红方：帅、仕、相、馬、車、砲、卒
- 黑方：将、士、象、马、车、炮、兵
