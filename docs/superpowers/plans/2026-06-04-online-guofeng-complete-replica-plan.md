# Implementation Plan: Online Guofeng Complete Replica Redesign

本计划描述了将轻棋局站点的 HTML、CSS 和 JavaScript 彻底重构为参考图所示的古雅国风水墨界面的具体步骤。

## 实施路线图

### 1. 完善全局样式系统与静态资源 (CSS Foundation)
编辑 [app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css)：
- 确保全局米白纸张底色，将 body 的 background 更新为宣纸底色渐变，字体使用楷体 (标题) 与无衬线字体 (正文) 的组合。
- 在 `app.css` 顶部加挂 `.bg-welcome_illust` (象棋左大图背景)、`.bg-detail_gomoku` (五子棋右大图背景)、以及 `.bg-detail_xiangqi`。
- 新增或重写首页的专用样式：
  - `.deskHome`：首页布局主容器，支持主英雄区与底部三栏网格。
  - `.deskHero`：落子之间自有风雅核心英雄区，支持左右分居的古风艺术底图与居中的朱红/墨绿对局大卡片。
  - `.deskQuickGrid`：四个小圆角功能图标的排列（快速匹配等）。
  - `.deskHomeThreeCol`：底部对局模式、赛事推荐、排行榜三栏的黄金分割比例与金线卡片效果。
- 新增或重写对局大厅样式：
  - `.lobbySidebar`：左侧纵向侧边栏布局，包含带有段位/Lv标识的用户头像区、以及带有圆角红底激活效果的纵向导航链接。
  - `.lobbyMain` & `.lobbyAside`：重新布局，使搜索栏、房间卡片、最近对局以及右栏完美贴合设计图。
- 新增或重写棋盘对局页样式：
  - `.boardDesk--game` & `.boardDesk--practice`：设置水墨山水与翠绿竹林背景墙，左右各带有垂钓舟船和棋子石罐。
  - `.boardRail`：左栏精美头像与大字计时器的卡片式展示（`.boardPlayerCard` 在轮到该玩家时呈现精美的金边框高亮）。
  - `.boardHost`：使棋盘带上逼真的木质纹理与阴影，重绘“楚河 汉界”文字。
  - 控制按钮重绘为精致的木纹按钮组。
  - `.recordPane`：右栏棋谱的 Tab 头（棋谱/聊天/分析）与底部木纹微调按钮栏。

### 2. 重构 JS 视图渲染逻辑 (JS View Reconstruction)
编辑 [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js)：
- **`renderHomePageGuofeng()`**:
  - 重构为居中大标题，两侧渲染浮动的象棋（左）与五子棋（右）大圆形图景，并排象棋对局和五子棋对局大按钮。
  - 下方输出四个功能圆圈。
  - 底部输出对局模式卡片组、赛事推荐列表（动态拉取 rooms 和 presets 数据）、以及排行榜小组件。
- **`renderPlayLobbyDesk()`**:
  - 修改为左侧纵向侧边栏导航 + 中间主干区域 (搜索框/大卡片/最近对局) + 右侧辅助栏 (建房/排行榜周榜) 的左右分栏大布局。
- **`renderOnlineGameView()` & `renderPracticeView()`**:
  - 将对局和练习棋盘页输出改写为：左侧头像时钟栏 + 中间木纹棋盘与底部木纹操作按钮 + 右侧带 Tab 的棋谱记录栏。
  - 根据 `game.gameType` 是 `XIANGQI` 还是 `GOMOKU` 分别在外层容器加挂 `xiangqiTheme` 或 `gomokuTheme` 以激活专属水墨背景（如山峦或竹林）。

### 3. 构建与验证流程 (Build & Verification)
- 运行 `mvn -q -DskipTests compile` 验证编译。
- 使用 `screenshot.py` 自动化测试脚本截取全部 6 个核心路由的最终页面，输出并保存为：
  - `screenshot-home.png` (首页)
  - `screenshot-play.png` (大厅)
  - `screenshot-xiangqi.png` (象棋详情)
  - `screenshot-gomoku.png` (五子棋详情)
  - `screenshot-practice.png` (对弈练习)
  - `screenshot-analysis.png` (复盘分析)
- 在仓库中创建评审报告 `docs/superpowers/reviews/2026-06-04-guofeng-complete-replica-review.md` 记录重构与图片证据。
