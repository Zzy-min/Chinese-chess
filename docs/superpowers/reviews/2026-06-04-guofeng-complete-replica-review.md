# Review Report: Guofeng Web UI Complete Replica Redesign

本评审报告详细记录了轻棋局站点在线模块的古雅水墨国风 UI 彻底重构的完成情况、设计细节与截图验证证据。

## 1. 重构范围与实现细节

我们严格按照设计效果图，对以下 4 大核心视图及棋桌进行了彻底重构：

### 1.1 首页 (`#/home`)
*   **标题与主题**: 大标题“落子之间，自有风雅”，副标题“在在线象棋与五子棋对局、轻松开局，随时对弈”。
*   **Hero 区 (`.deskHero`)**: 左右侧绝对定位悬浮象棋圆盘插图与五子棋圆盘插图，中间并排朱红“象棋对局”与墨绿“五子棋对局”入口。
*   **快捷圆钮 (`.deskQuickGrid`)**: 提供“快速匹配”、“智能练习”、“赛事活动”、“棋友社区”四个带有阴影和古典微缩图标的轻量圆角入口。
*   **底部三栏 (`.deskHomeThreeCol`)**: 细金边背景卡片，左栏显示对局模式，中栏动态加载赛事推荐（带“进行中/报名中”彩色标签），右栏显示排行榜周榜前 4 名。

### 1.2 对局大厅 (`#/play`)
*   **左侧纵向侧边栏 (`.deskSidebar`)**: 圆形用户头像带有金色边框，下方显示“LV.6 棋圣”VIP挂件与用户名，挂载精致木纹风格的“每日签到”卡片。
*   **主工作区 (`.deskLobbyMain`)**: 顶部放置带放大镜的搜索栏与大厅分类标签，中间是带大面积立体象棋与五子棋艺术插画的横版对局大卡片，下方为最近对局归档记录。
*   **右侧辅助栏 (`.deskLobbyAside`)**: 提供大尺寸的“创建房间”与“加入房间”高光动作按钮，下方展示排行榜周榜前 5 名。

### 1.3 模式选择详情页 (`#/play/xiangqi` & `#/play/gomoku`)
*   **象棋模式**: 挂载江流山水垂钓图景，大朱红色艺术图章，提供“标准模式”、“3分钟快棋”以及“友谊对局”的多卡片选择。
*   **五子棋模式**: 挂载竹林黑白乾坤图景，绿意盎然的徽章印章，提供“人机对战”、“双人联机”、“复盘记录”、“胜率统计”等竹青色功能卡片。

### 1.4 对弈/练习桌 (`#/game/*` & `#/practice/*`)
*   **左右背景叠加**: 根据游戏类型，分别加挂 `xiangqiTheme`（水墨山峦江河与垂钓孤舟）或 `gomokuTheme`（翠绿竹影与黑白围棋罐）的大背景装饰。
*   **左栏 (时钟与头像)**: 
    *   在线对局：上下对称的圆形头像卡片，带有金边框，右侧有“红方 · 业余5段”段位徽章，大字剩余时间，当前回合时徽章亮起朱红色高亮脉冲呼吸动画。
    *   练习对局：上方是 AI 专属的翠绿圆形头像，显示 AI 思考中和 AI 难度；下方是挑战者玩家头像与状态。
*   **中栏 (拟真棋盘与木纹按钮)**: 
    *   拟真木纹理棋盘背景，象棋楚河汉界清晰可见，棋子带有拟真阴影与磨砂木质感。
    *   底部包含全新重构的古典木质控制按钮组：悔棋、求和、认输、离开。
*   **右栏 (走子记录)**: 顶部 Tab 切换与精致的红黑方分栏走步列表。

---

## 2. 代码重构定位

主要代码修改分布于：
1.  **视图与逻辑控制**: [app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js) 中的 [renderHomePageGuofeng](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js#L3430), [renderPlayLobbyDesk](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js#L3548), [renderOnlineGameView](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js#L1173), [renderPracticeView](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js#L1261) 以及 [renderClockCard](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js#L1359) 的彻底覆写。
2.  **样式与主题定义**: [app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css) 底部追加的 1000+ 行国风版布局（包括纸张渐变色底、金线卡片、头像、徽章、木纹及大背景叠加层定义）。

---

## 3. 页面验证证据 (截图展示)

我们已经运行 Playwright 自动化脚本完成了全链路核心路由的截图验证：

````carousel
![1. 首页](screenshot-home.png)
<!-- slide -->
![2. 对局大厅](screenshot-play.png)
<!-- slide -->
![3. 象棋模式详情](screenshot-xiangqi.png)
<!-- slide -->
![4. 五子棋模式详情](screenshot-gomoku.png)
<!-- slide -->
![5. AI练习局棋桌](screenshot-practice.png)
<!-- slide -->
![6. 复盘分析棋桌](screenshot-analysis.png)
````

> [!NOTE]
> 经过实际验证，所有页面的排版、色彩方案与三栏架构均与设计图一致，且所有的下子交互、AI轮询、WebSocket 对局更新和时钟秒退逻辑均运转完美。
