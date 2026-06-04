# 国风水墨“轻棋局”界面重构部署与实施计划 (Guofeng UI Plan)

本计划定义了如何通过生成高画质水墨插画、利用 Python 进行高效 WebP 压缩、Base64 内联以及重构 JS/CSS 视图代码，在真机上部署并呈现极致美学国风界面的具体实施步骤。

## 实施步骤

### 1. 生成国风水墨艺术插画
使用 `generate_image` 工具生成 3 张高画质艺术图片：
1. **首屏圆形插图 (`welcome_illust.png`)**: 中间圆形插图，包含写意山水、竹林、楚河汉界棋盘、立体“帅/将”、以及散落的五子棋黑白子。
2. **象棋选择页顶部插画 (`detail_xiangqi.png`)**: 写意山水背景，木质楚河汉界棋盘，上有立体雕刻“帅”和“将”棋子。
3. **五子棋选择页顶部插画 (`detail_gomoku.png`)**: 水墨竹林背景，木纹棋盘，立体的黑白棋子，右下角带有石制棋罐。

### 2. 压缩图片并获取 Base64
在 `C:\Users\Lenovo\.gemini\antigravity-cli\brain\02d99a20-6fdc-4d38-9cf2-d596a6a912ab/scratch/` 创建 Python 脚本：
- 读取生成的 3 张图片。
- 缩放尺寸（`welcome_illust` 设为 300x300 圆形；其他两张设为 500x300，即 5:3 比例以契合详情页顶部高度）。
- 转换为 WebP 格式并使用 65% 质量压缩，降低大小至 10-18KB。
- 输出 Base64 编码字符串。

### 3. CSS 样式整合与 Base64 注入
编辑 [app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css)：
- 注入 3 张压缩 WebP 图片的 Base64 字符串作为 CSS 背景属性。
- 精细化布局：
  - 调整 `.welcomePane` 大 Logo 字体及“开始对局”红色圆角大按钮的排版。
  - 为大厅增加聊天气泡样式，重绘分类标签和象棋/五子棋双入口卡片，卡片右上角使用 `detail_xiangqi` / `detail_gomoku` 的缩略图作为背景。
  - 重设最近对局列表风格，使用红圈“帅”字和黑白太极子作为象棋和五子棋的圆头像。
  - 重构选择模式页（象棋与五子棋），保证顶部有返回按钮 `<` 和精美水墨背景，使“你将获得”列表和“选择模式”卡片整齐左右分栏，且底部按钮居中占满宽度。
  - 完善底部导航栏（首页高亮，中间“+”悬浮大按钮，对局、排行、我的图标排版）。

### 4. JS 渲染逻辑重构
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js)：
- 修正 `renderPlay(route)` 的子路由分流，确保当路由为 `play/xiangqi` 与 `play/gomoku` 时，分别渲染象棋和五子棋详情页。
- 增强 `bindCommon` 点击绑定，让返回按钮可以正确执行 `location.hash = '#/home'`。
- 支持在象棋模式选择中点击“标准模式”、“3分钟快棋”、“友谊对局”卡片时，视觉切换选中状态，并挂载相应的立即开局事件。
- 恢复 `shouldShowAuthOverlay` 的原本登录拦截校验逻辑，保证系统安全性。

### 5. 项目构建与真机部署
- 在 `deploy/android-app/` 运行 `.\gradlew.bat assembleDebug` 重新编译 APK。
- 统一使用 `C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe` 执行 ADB 重装与端口转发：
  - `adb reverse tcp:18389 tcp:18389`
  - `adb install -r app-debug.apk`
- 运行应用并截取 4 个页面的最终屏幕画面，生成验证截图，提交最终评审说明。

