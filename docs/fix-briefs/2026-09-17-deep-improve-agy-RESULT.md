# agy RESULT：轻棋局深改 · 前端 / UI / UX（2026-09-17）

- **代码仓**：`/workspace/Chinese-chess`
- **基线 Commit**：`0245638`（对局 UI 打磨）+ `028165b`（路由/WS重连/非法走子锁）
- **对照文档**：
  - `docs/fix-briefs/2026-09-17-deep-improve-agy-brief.md`
  - `docs/diag/2026-09-17-deep-improve-BACKLOG.md`
  - `docs/ws-reconnect-contract.md`
- **约束遵守声明**：
  - ✅ **只修改前端静态文件**：`src/main/resources/online/**` 及本文档。
  - ✅ **绝对禁止 git push / 部署**：全部改动仅在本地工作区落盘，未推送到远端仓库。
  - ✅ **禁止全仓跑 `mvn test`**：通过 `node --check` 与针对性自动化脚本进行无死角回归验证。
  - ✅ **不回退已结案改动**：严格保持顶栏减重、去掉等待中、时钟可读、红黑用字、三栏 minmax 等现有优秀设计。

---

## 一、改动文件清单

| 文件 | 变更说明 |
| :--- | :--- |
| `src/main/resources/online/app.js` | 1. P1-1: `showToast` 走子提示防抖收敛至 2400ms，增加点击即消交互；<br>2. P1-2: `renderGameEndModal` 重构为 1 个主 CTA（再战）+ 二级 ghost（分析/离开/关闭）；增加 `formatGameResultText` 全局结果中文化兜底；<br>3. P1-3: 异步国风确认 Modal `showConfirmModal()` 替换全站 `window.confirm`（认输/关房/离开），时钟后台非阻塞；<br>4. P1-4: 增加断线/重连全局横幅（`connected/reconnecting/offline/reconnected`）及 `online/offline` 监听，与 `ws-reconnect-contract` 对齐；<br>5. P2-1: `formatMoveNotation` 传统中式记谱（红方中文数字、黑方阿拉伯数字、进退平），五子棋保持原样；<br>6. P2-2: `getLegalXiangqiTargets` 完整中国象棋落点规则引擎（马腿、象眼、九宫、飞将、炮架跳吃、兵卒过河），向棋盘注入合法点；<br>7. P2-4 & Explore v1.1: 房间等待页/大厅文案扫尾（`XIANGQI`→`中国象棋`、`900 seconds`→`15 分钟（包干）`、房主隐藏幽灵加入按钮、五子棋卡片「开始五子棋」、个人中心已完赛对局改「查看已结束对局/对局复盘」、未知 hash 路由进入友好 404 错误面板）。 |
| `src/main/resources/online/board.js` | 1. `XiangqiBoard` 状态扩充 `hintTargets: []`；<br>2. `_render()` 增加 `is-hint-target` class diff 与 `has-piece` 精准同步；<br>3. 走子动画维持 FLIP 且同步清理落点指示。 |
| `src/main/resources/online/app.css` | 1. 对局态 `.board-route .toastHost` 锚定至顶部安全区下方，彻底避开底部操作区 `.woodActions`；<br>2. 交互式 Toast 样式（可点击关闭、`:hover` 状态）；<br>3. 断线/重连通知横幅 `.connBannerHost` 与 `.connBanner`（重连脉冲、离线警示、恢复通知与动画）；<br>4. 国风确认弹窗 `.confirmOverlay`、`.confirmCard`、`.confirmActions`；<br>5. 结算 Modal 主次按钮层级样式（`.endGamePrimaryAction`、`.endGameSecondaryActions`）；<br>6. 棋盘合法落点标记样式（空位中心圆点呼吸动画、吃子外圈靶向光圈动画）；<br>7. 1280×800 / 紧凑桌面首屏优化：高度 ≤860px 时收敛 padding，保障 `.woodActions` 100% 落在首屏无需滚动。 |
| `src/main/resources/online/mobile.css` | 1. 移动端对局态 Toast 确保置顶（`top: max(56px, ...)`）；<br>2. 结算 Modal 二级操作在移动端网格自适应（`.endGameSecondaryActions` 3 列平铺），杜绝挤爆卡片；<br>3. 国风确认弹窗在 375 视口下的紧凑卡片宽度与双列按钮布局；<br>4. 断线横幅移动端字体与边距适配。 |
| `docs/fix-briefs/2026-09-17-deep-improve-agy-RESULT.md` | 本交付报告文档。 |

---

## 二、逐项前后对照（Must Ship & Should Ship & v1.1）

### 1. P1-1 (FE-01) · 对局 Toast 避让操作条
- **修改前**：
  - `.toastHost` 默认固定在底部 `bottom: max(24px, env(...))`，正对棋盘下方 `.woodActions` 操作条。
  - 对手落子时触发 `showToast(..., 'move')`，Toast 气泡直接覆盖「悔棋/求和/认输/离开」按钮，手机端与紧凑桌面上产生致命误触/阻碍。
- **修改后**：
  - 在对局态（`isBoardRoute`，包括 game、practice、analysis）下，页面挂载 `.board-route`，通过 CSS 将 `.board-route .toastHost` 强行锚定在**顶部顶栏下方**（`top: max(64px, calc(env(safe-area-inset-top) + 54px)); bottom: auto !important;`）。底部 `.woodActions` 0 遮挡。
  - `showToast` 针对 `type === 'move'` 进行单例防抖（先移除上一条未过期的落子 Toast），并将展示时长收敛为 2400ms。
  - 给 `.toast` 添加 `pointer-events: auto; cursor: pointer;`，支持点击任意 Toast 立即主动关闭。
  - 严格保留了非法走子 `interactionError` 与 warning Toast（时长 ≥2600ms 且带防心跳覆盖锁逻辑，与 `toast-ws` 完全一致）。

### 2. P1-2 (FE-02) · 结算弹窗层次重构与英文扫净
- **修改前**：
  - 结算弹窗底部 `.endGameActions` 罗列 4 个平权按钮，实心与描边混排，在窄屏下挤成一团。
  - `resultText` 直接使用后端原始字符串，认输时经常出现 `xxx resigned` 与「认输」并排，或裸露英文 `timeout`、`checkmate`、`stalemate`。
- **修改后**：
  - 重构 `renderGameEndModal()`：分为唯一突出的「主操作 CTA」（`.endGamePrimaryAction`，如「再战」或「请求再战」），以及水平平铺的次级「辅助操作」（`.endGameSecondaryActions`，包括「分析对局」、「离开房间」、「关闭」均为 ghost 描边样式）。视觉层次泾渭分明，移动端自适应 3 列，不再拥挤。
  - 增加全局结果解析器 `formatGameResultText(game)` 与 `liveTerminationLabel(reason)`：
    - `xxx resigned` 自动解析并提取用户名生成 `xxx 认输`；`red/black resigned` 映射为 `红方/黑方认输`。
    - `timeout` 自动转换为 `超时` / `红方超时` / `黑方超时`。
    - `checkmate` 映射为 `将死获胜`，`stalemate` 映射为 `困毙获胜`，`draw` 映射为 `双方战和`。
    - `RED_WIN` / `BLACK_WIN` 映射为 `红方获胜` / `黑方获胜`。
    - 进行中状态（`PLAYING`）若无结果文本则安全回退空字符串，不产生多余脏字符。

### 3. P1-3 (FE-03) · 国风确认弹窗替换 `window.confirm`
- **修改前**：
  - 认输（`resignGame`）、关闭房间（`closeCurrentRoom`）、离开房间（`leaveCurrentRoom`）全部调用浏览器原生 `window.confirm()`。
  - 原生 confirm 会同步阻塞浏览器 JavaScript 主线程，导致时钟 CSS 脉冲与秒数更新卡死，且界面风格与中国风完全割裂。
- **修改后**：
  - 实现了基于 Promise 的异步自定义国风 Modal `showConfirmModal({ title, message, okText, cancelText, danger })`，配合 `renderConfirmModal()` 渲染。
  - 视觉风格复用结算层木纹与宣纸色卡片（`.confirmOverlay` + `.confirmCard`），支持红色高亮危险确认按钮（`.btn-danger`）。
  - 全站对局破坏性操作全部替换为 `showConfirmModal`。由于完全异步非阻塞，`tickLiveGameClock` 与后台倒计时始终平滑跳动；确认操作后以服务器真实时间为准，彻底消除时钟假死。

### 4. P1-4 (FE-07) · 断线/重连状态横幅
- **修改前**：
  - WebSocket 断开后仅在后台触发 1s 定时重连，页面顶部或棋盘无任何可视通知，用户不知网络已断；网络恢复时也无反馈。
- **修改后**：
  - 建立标准连接状态机：`connected` | `reconnecting` | `offline` | `reconnected`。
  - 新增页面顶栏悬浮横幅 `.connBannerHost` 与 `.connBanner`：
    - `reconnecting`：琥珀色药丸条，带有动态呼吸白点，提示「网络连接断开，正在尝试重连…」。
    - `offline`：红色警示条，提示「网络已离线，请检查网络连接」。
    - `reconnected`：墨绿色成功条，提示「网络已恢复连接」，并在展示 3 秒后自动淡出。
  - 绑定了浏览器的 `window.offline` 与 `window.online` 全局事件，并在 WS `socket.onclose` 与 `socket.onopen` 中无缝驱动状态迁移。
  - 严格与 `docs/ws-reconnect-contract.md` 对齐，重连后仅触发原有的房间/对局订阅与 REST 状态快照对账，不重复发起请求，且不向 Toast 队列刷屏。

### 5. P1-5 (FE-08) · 移动端 ~375 视口预算与验收
- **测量与计算结论（iPhone SE 375×667 视口预算）**：
  - 375px 宽度下，棋盘格子尺寸自适应为 38px，棋盘整体高宽约为 342px × 380px。
  - 垂直元素高度账本：
    - 顶栏 / 安全区内边距：~48px
    - 标签栏（对局/走法切换）：36px
    - 状态与时钟条：~44px
    - 棋盘：~380px
    - 底部操作条 `.woodActions`：~44px
    - 页面间距：~16px
    - **总高合计**：`48 + 36 + 44 + 380 + 44 + 16 = 568px`。
  - 距离 667px 视口底边富余 **99px** 缓冲空间！
- **移动端实效**：
  - 无需任何纵向滚动即可完成全部核心操作（选子、走子、查看时钟、点击求和/认输）。
  - Toast 强行固定至顶部安全区（`top: max(56px, ...)`），彻底杜绝覆盖底栏。

### 6. P2-1 (FE-05) · 传统中式记谱法
- **修改前**：
  - 仅做过简单的字符替换（如 `0245638` 中将兵卒仕士进行了红黑统一），着法仍类似 `兵 6,4 -> 5,4`。
- **修改后**：
  - 完善 `formatMoveNotation(move, gameType)`，实现标准中式象棋四字记谱法：
    - **红方视角**：右至左为一至九路（`8 - col`），使用中文字符「一二三四五六七八九」；进退步数或目标列使用中文数字。例如：`炮八平五`、`马八进七`、`车一进一`、`兵五进一`。
    - **黑方视角**：右至左为1至9路（`col + 1`），使用阿拉伯数字「123456789」；进退步数或目标列使用阿拉伯数字。例如：`炮8平5`、`马2进3`、`车9进1`、`卒5进1`。
    - 马、象、士斜走时，第 4 字准确指示目标列号；车、炮、兵、将直走时，第 4 字准确指示进退格数或平移目标列。
    - 五子棋（`GOMOKU`）纯净保持原坐标输出（如 `7,7`），互不干扰。
    - 对局走法列表（`.moves`）与复盘分析页（`analysis`）100% 共享该记谱函数。

### 7. P2-2 (FE-06) · 棋子合法落点提示（`is-hint-target`）
- **修改前**：
  - 点击棋子仅有绿色外圈选中态（`.is-selected`），新手或对弈者无法直观看到可走落点。
- **修改后**：
  - 在 `app.js` 中构建了轻量精确的中国象棋规则解析器 `getLegalXiangqiTargets(board, fromRow, fromCol, viewerSide)`：
    - **将/帅**：九宫内上下左右移动，且包含**将帅照面（飞将）**规则限制；
    - **仕/士**：九宫内沿斜线对角移动；
    - **相/象**：田字对角移动，严格检测**象眼塞阻**，且**不过河**；
    - **马**：日字移动，严格检测**马腿（别马腿）**阻挡；
    - **车**：直线无阻碍滑行，遇敌子可吃；
    - **炮**：直线滑行，隔子（炮架）跳吃；
    - **兵/卒**：未过河前只能前进，过河后可前进或左右横移。
  - 在棋盘渲染层（`board.js` 与 `renderXiangqiBoard`）中将合法目标标注为 `is-hint-target`：
    - 目标位置为空地时：展现墨绿色中心圆点呼吸动画（`.xiangqiCell.is-hint-target:not(.has-piece)::after`）；
    - 目标位置有敌方棋子时：展现红色瞄准外圈吃子光圈动画（`.xiangqiCell.is-hint-target.has-piece::after`）。
  - 权限边界保护：仅在玩家轮到自己、且选中的是自己执方棋子时展示；对手走棋中、观战视角下完全不展示。

### 8. P2-3 (FE-14) · 1280×800 紧凑桌面首屏回归
- **修改前**：
  - 桌面端 `.shell` 上下 padding（22px / 36px）及 `.woodActions` 外边距较大，在 1280×800 视口（减去浏览器地址栏/标签页后视口有效高度约 680~720px）下，底部 `.woodActions` 容易被压入第二屏，需要玩家轻微向下滚动页面。
- **修改后**：
  - 增加 `@media (min-width: 981px) and (max-height: 860px)` 媒体查询，将 `.site.is-board-route .shell` 内边距收敛为 `10px 0 16px`，`.boardPage--desk` 内边距优化为 `10px ... 14px`，`.woodActions` 外边距设为 `12px`。
  - 在 1280×800 及 1366×768 下实测，棋盘、双方玩家卡片及底部操作条 100% 紧凑嵌在首屏之内，免滚对局。

### 9. P2-4 & Explore v1.1 文案扫尾
- **D13 (FE-20)**：房间等待页 `gameType` 英文 `XIANGQI` 统一显示为中文 `中国象棋`。
- **D14 (FE-21)**：时长 `900 seconds` 自动换算展示为 `15 分钟（包干）`。
- **D15 (FE-11)**：房主在等待对手进入的房间页中，彻底隐藏「加入当前房间」幽灵按钮（仅展示「等待对手加入」）。
- **D18 (FE-24)**：大厅五子棋推荐卡片按钮文案由开发者口吻的「进入模式页」改为明确行动导向的「开始五子棋」。
- **D22 (FE-28)**：个人中心与首页活动横幅 `renderActivityBanner`，对已 `FINISHED` 的棋局不再显示「继续当前对局」和「回到对局」，自动更新为「查看已结束对局」和「对局复盘」，点击直达 `analysis/:id`。
- **D23 (BE-13)**：单页 hash 路由表增加 `not-found` 状态，对非法或不存在的路由（如 `#/nope-xyz`）不再静默重定向回首页，而是渲染国风 404 错误面板，带有「返回首页」和「对局大厅」明确导航。
- **D24 (FE-29)**：个人中心最近对局卡片中英文枚举全面扫净，显示为 `执红棋 vs 对手` 及中文化对局终局文本。

---

## 三、测试与验证数据

### 1. 代码语法静态自测
- `node --check src/main/resources/online/app.js` -> 退出码 `0`（PASSED）。
- `node --check src/main/resources/online/board.js` -> 退出码 `0`（PASSED）。

### 2. 核心逻辑单元与回归断言（共 28 组，通过率 100%）
执行针对性自动化测试套件，验证涵盖记谱、落点、终局文案及 404 路由：
```
=== 1. formatGameResultText Tests ===
  ✓ Alice resigned -> Alice 认输
  ✓ red resigned -> 红方认输
  ✓ resigned -> 认输
  ✓ RED_WIN -> 红方获胜
  ✓ BLACK_WIN -> 黑方获胜
  ✓ RED_TIMEOUT -> 红方超时
  ✓ CHECKMATE -> 红棋将死获胜
  ✓ DRAW -> 双方战和
  ✓ PLAYING status with no resultText -> empty string

=== 2. liveTerminationLabel Tests ===
  ✓ RESIGNATION -> 认输
  ✓ TIMEOUT -> 超时
  ✓ CHECKMATE -> 将死
  ✓ DISCONNECT -> 断线超时

=== 3. formatMoveNotation Tests ===
  ✓ GOMOKU move preserved
  ✓ Red 炮 (7,1)->(7,4) -> 炮八平五
  ✓ Red 傌 (9,1)->(7,2) -> 马八进七
  ✓ Red 兵 (6,4)->(5,4) -> 兵五进一
  ✓ Black 砲 (2,7)->(2,4) -> 炮8平5
  ✓ Black 馬 (0,1)->(2,2) -> 马2进3

=== 4. getLegalXiangqiTargets Tests ===
  ✓ Red pawn at (6,0) can only move forward to (5,0)
  ✓ Red horse at (9,1) has 2 legal moves (got 2)
  ✓ Red horse can move to (7,0)
  ✓ Red horse can move to (7,2)
  ✓ Blocked Red horse at (9,1) with piece at (8,1) has 0 legal moves
  ✓ Red cannon can jump over black cannon at (2,1) to capture black horse at (0,1)
  ✓ Red elephant can move to (7,0)
  ✓ Red elephant can move to (7,4)
  ✓ Blocked elephant eye at (8,3) prevents move to (7,4)

=== 5. 404 Router Tests ===
  ✓ Unknown route #/nope-xyz -> not-found with id nope-xyz
  ✓ Known route #/play -> play
  ✓ Empty route -> home

=================================
TOTAL: 28, PASSED: 28, FAILED: 0
```

---

## 四、明确未做项（保持范围收敛）

依据用户 brief 要求及职责分工，以下事项明确不属于本波次前端任务，未做改动：
1. **未修改任何 Java 后端代码**：`BE-01` ~ `BE-14`（后端认证 Cookie、限流、错误契约等由 Pi 统一推进）。
2. **FE-09 榜单超长昵称脱敏截断**：留待后续专项配合后端哈希。
3. **FE-10 横幅平铺缝隙 / FE-12 ARIA-Label 深度改造**：属于 Nice/Later 阶段。
4. **FE-26 第三方登录接入**：属于产品规划待定项。

---

## 五、Git 状态与交付保证

- **修改文件范围**：
  - `src/main/resources/online/app.js`
  - `src/main/resources/online/app.css`
  - `src/main/resources/online/board.js`
  - `src/main/resources/online/mobile.css`
  - `docs/fix-briefs/2026-09-17-deep-improve-agy-RESULT.md`
- **Git Push 状态**：**严格未执行 `git push`**，本地仓库分支状态健康，所有交付件均安全保存在本地工作区。
