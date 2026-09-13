# agy 前端修复交付报告：轻棋局前端（P0 死循环 + 结算/Toast + 移动端视口）

> **交付分支/工作区**：`/workspace/Chinese-chess`  
> **关联诊断**：  
> - `/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-SUMMARY.md`  
> - `/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-AGY.md`  
> **任务要求**：  
> - `/workspace/Chinese-chess/docs/fix-briefs/2026-09-13-agy-frontend-p0p1.md`  
> **约束遵守**：严格禁止 `git push`；严格只修改前端资源文件；诚实报告不虚标。

---

## 一、修改文件清单

| 文件路径 | 变更行数 | 说明 |
| :--- | :--- | :--- |
| `src/main/resources/online/app.js` | +481, -78 | P0 路由死循环拦截与404错误面板、P1 胜方结算弹窗修复、P1 走子交互错误保护与防心跳冲刷、P1 移动端空间测量优化、WS Ping/Pong/断线快照对齐 |
| `src/main/resources/online/app.css` | +50, -1 | `.status--error` 警示样式及抖动动效、`.toast--warning` 警告框、`.routeErrorPanel` / `.routeErrorCard` 国风404异常面板样式 |
| `src/main/resources/online/mobile.css` | +79, -23 | 移动端 390×844 等视口棋盘紧凑排布、人机/在线对局时钟双列并排（`practiceGrid`）、压缩操作条与卡片尺寸、对局页 Toast 顶部避让 |

---

## 二、详细修复内容

### 1. 【P0】异常房间/对局路由死循环修复

#### 现象与根因
- 用户直接访问或刷新不存在、已过期或格式错误的路由（如 `#/room/non-existent-id`、`#/game/invalid-id`、`#/practice/bad-id`、`#/analysis/bad-id`）时，前端路由系统触发 `render()`。
- `renderRoom` 等函数在检查到 `!state.room` 时发起 HTTP 请求 `loadRoom()`。
- 接口返回 404 或 400 时，原代码在 `catch` 块中将状态置空并再次调用 `render()`。
- 再次 `render()` 又进入 `renderRoom()`，再次发现 `!state.room`，再次发起请求——形成**每秒 5~10 次的无限递归请求与重复渲染**，导致浏览器控制台红屏刷崩，严重消耗服务器资源与客户端 CPU。

#### 落地解决方案
1. **全局路由加载与错误隔离状态**：
   - 在 `state` 中加入 `routeLoading: {}` 和 `routeLoadError: {}`，按 `{type}:{id}` 精确加锁与记录错误。
   - 增加辅助函数 `getRouteError(type, id)` 与 `clearRouteError(type, id)`。
2. **渲染入口短路与友好 404 面板**：
   - 在 `renderRoom`、`renderGame`、`renderPractice`、`renderAnalysis` 的最前置检查：
     - 若当前路由对应资源存在未清除的错误状态，**立刻中止数据加载**，直接调用 `renderRouteErrorPanel(...)` 渲染友好错误卡片。
     - 若当前路由对应资源正在加载中，直接展示加载占位骨架，**严禁并发重复调用**。
3. **加载函数全链路保护**：
   - `loadRoom`、`loadGame`、`loadPractice`、`loadAnalysis` 在发起网络请求前锁定 `routeLoading[key] = true`；
   - 捕获 400/404 等网络异常或后端空响应对象 `{}` 时，将错误消息写入 `routeLoadError[key]`，解除加载锁并执行且仅执行一次 `render()`；
   - 界面上展示国风雅致 404 卡片，包含：
     - 棋子徽标与清晰的错误提示信息（如「房间未找到或已解散 (404)」、「对局数据不存在或已失效」）；
     - 「返回对局大厅」按钮（跳转至 `#/play`，自动重置错误态）；
     - 「新建房间/对局」快捷按钮；
     - 「重新加载」按钮（提供 `retry-route` 交互，点击后清除错误锁并重试一次）。
4. **WebSocket 连接防骚扰**：
   - 在 `syncRealtime` 中加入前置拦截：若目标房间/对局已处于路由错误状态，立即调用 `closeSocket()`，不再向后端无意义建立 WS 连接。

---

### 2. 【P1】胜方结算弹窗修复（对齐胜负双端弹窗展示）

#### 现象与根因
- 在对局结束时（对方认输、将死判负、超时等），服务端向 WebSocket 广播 `game` 终态（`status: "FINISHED"`）。
- 接收端调用 `applyServerGameSnapshot(incomingGame)`，该函数内部正确设置了 `state.endGameModal`。
- 但是随后原代码执行了 `if (patchOnlineGameRealtimeView()) return;`，该局部 DOM 补丁函数成功更新了棋盘和状态栏并返回 `true`，触发了 `return;`。
- 这导致 `socket.onmessage` **直接提前退出，跳过了全量 `render()`**！
- 结果：负方（主动认输方）因点击按钮触发过全量渲染弹出了结算窗，但**胜方（接收 WS 消息方）界面停留，无法挂载 `.endGameOverlay` 弹窗**，呈现出“静默假死”体验。

#### 落地解决方案
1. **终态绕过局部补丁，确保全量渲染**：
   - 在 `socket.onmessage` 中，接收到 `data.game` 后检测 `state.game.status === 'FINISHED'`，主动调用 `maybeOpenEndGameModal(state.game)`；
   - 在 `patchOnlineGameRealtimeView()` 的前置守卫中增加条件：
     ```javascript
     if (previousGame
       && state.game
       && previousGame.gameId === state.game.gameId
       && routeNow.page === 'game'
       && !state.game.isTraining
       && state.game.status !== 'FINISHED' // 对局终态绝不走局部退出
       && !state.endGameModal             // 存在待展示弹窗绝不走局部退出
       && patchOnlineGameRealtimeView()) {
       return;
     }
     ```
   - 确保对局结束那一刻必然执行完整 `render()`，使 `.endGameOverlay` 完整挂载至 `#app`。
2. **结算弹窗文案中文化翻译**：
   - 完善 `renderGameEndModal` 内的结算理由解析，统一映射 `liveTerminationLabel(game.terminationReason)`（如 `RESIGN` $\to$「对方认输」/「主动认输」，`CHECKMATE` $\to$「绝杀将死」，`TIMEOUT` $\to$「超时判负」等），告别纯英文枚举展示。

---

### 3. 【P1】走子交互错误反馈（停留 ≥2s，不被 250ms 时钟心跳冲刷）

#### 现象与根因
- 用户在棋盘上下出非法步（如别马腿、未应将、走子不合法）或误点对方棋子时，代码原本会将错误写入 `state.status`。
- 但页面每 250ms 都会执行一次 `tickLiveGameClock()`，其内部会调用 `refreshLiveStatusLine()`，无条件用 `onlineGameStatusText(state.game)`（如「红方思考中 (29:45)」）覆盖当前状态栏。
- 导致走子错误提示在 **0~250 毫秒内瞬间被冲刷抹去**，玩家完全看不到错误反馈，误以为操作无响应。

#### 落地解决方案
1. **交互错误独立时间锁与状态机制**：
   - 在 `state` 中新增 `interactionError: ''` 与 `interactionErrorExpireAt: 0`；
   - 新增 `setBoardInteractionError(message, durationMs = 2600)` 方法，每次设置错误时锁定 2.6 秒；
   - 增加交互错误智能翻译器 `translateInteractionError(raw)`，将各种后端与核心库走子异常（如 `invalid move`、`illegal coordinate`）转化为清晰的中文走子规则提示（如「走子不符合象棋规则，请核对走法」）。
2. **心跳状态栏智能让道**：
   - 改造 `refreshLiveStatusLine()`：
     - 若当前处于交互错误锁定期内（`Date.now() < state.interactionErrorExpireAt`），保留错误提示并添加 CSS 高亮类名 `.status--error`；
     - 只有在锁定期结束后，才自动平滑恢复为对局回合与倒计时文字。
3. **双重提示保障（状态栏动效 + Warning Toast）**：
   - 在 `app.css` 中增加 `.status--error` 样式（醒目红底边框 + 呼吸抖动动效 `shakeX`）；
   - 增加 `.toast--warning` 样式；在触发非法操作时，除锁定状态栏提示外，同步派发 `showToast(msg, 'warning')`，即使玩家视线未停留在状态栏也能一目了然。
4. **覆盖所有交互点**：
   - 在 `onXiangqiCellClick`、`onGomokuCellClick`、`sendMove`、`undoPracticeMove` 等所有棋盘交互操作点全量接入。

---

### 4. 【P1】移动端对局单屏视口适配（390×844 等设备）

#### 现象与根因
- 在 iPhone 12/13/14 等常见移动视口（390×844）以及 375×667 设备上：
  1. `measureBoardHostSpace(host)` 算出的棋盘最大高度未扣除下方操作栏（`.woodActions`）以及页面底部的时钟/玩家卡（`.boardRail`），导致计算出的棋盘尺寸过大，将下方的操作按钮与玩家信息直接顶出视口外，产生垂直溢出和裁切；
  2. 在人机练习模式下，双方玩家卡在竖屏上垂直堆叠，占用了过多纵向高度；
  3. 底部 Toast 弹出时会遮挡底部的下棋者卡片及认输/求和按钮。

#### 落地解决方案
1. **精准扣除下方宿主元素高度**：
   - 在 `measureBoardHostSpace(host)` 中，通过 DOM 查询计算棋盘下方必须保留的可见空间 `belowHostSpace`（包括 `.woodActions`、`.boardRail`、安全边距 safe-area-inset），将其从可用视口高度中精确扣除，使动态棋盘自动收缩到能完美容纳整套界面的黄金比例。
2. **人机练习时钟双列排布对齐**：
   - 改造 `renderPractice`，将人机双方玩家卡片包装进 `<div class="clockGrid practiceGrid">`；
   - 在 `mobile.css` 中强制定义：
     ```css
     .clockGrid,
     .boardRail--practice,
     .boardRail--practice .practiceGrid {
       display: grid !important;
       grid-template-columns: 1fr 1fr !important;
       gap: 4px !important;
     }
     ```
     彻底消灭垂直堆叠，节约出 60px 以上宝贵的垂直空间。
3. **移动端组件尺寸紧凑化**：
   - `.boardPlayerCard`：padding 缩减为 `4px 6px`，头像大小优化为 `22px`，时钟字体调整为 `15px`，用户名单行超出省略；
   - `.boardStage > .status`：高度紧凑化为 `26px`，文字字号微调；
   - `.woodActions button`：最小高度收缩为 `38px`，间距优化为 `5px`；
   - `.boardMobileTabs button`：高度优化为 `36px`；
4. **移动端对局 Toast 避让**：
   - 在 `mobile.css` 中设置 `.mobile-board-route .toastHost` 的位置为顶部安全区下方（`top: max(56px, calc(env(safe-area-inset-top) + 46px)) !important; bottom: auto !important;`），防止弱网或操作提示遮挡底部的走子与操作按钮。

---

### 5. 【可选协同】WebSocket Ping/Pong 与重连快照对齐

#### 现状与落地说明
- **Ping/Pong 心跳响应**：
  在 `socket.onmessage` 中已接入对服务端心跳帧的处理：
  ```javascript
  if (data.type === 'ping' || data.type === 'heartbeat') {
    try {
      socket.send(JSON.stringify({ type: 'pong' }));
    } catch (_) {}
    return;
  }
  ```
- **断线自动重连**：
  在 `socket.onclose` 与 `socket.onerror` 中实现 1 秒自动重连退避，重新握手并执行订阅。
- **重连快照对齐（Snapshot Reconcile）**：
  在 `socket.onopen` 触发时，除补发 `{ type: 'subscribe', roomId }` 报文外，检测若当前位于在线对局路由（`#/game/{id}`），自动异步请求 `GET /online/api/games/{id}` 获取最新游戏快照并更新本地盘面与终态弹窗，解决短时网络波动重连后着法与状态未同步的问题。
- **与 Pi 后端对接状态**：
  前端契约已就绪。待后端 Pi 完善 `PublicSiteServer` WS 心跳与鉴权落盘部署后，前后端可无缝连通。

---

## 三、手动测试与验证指引

### 1. P0 异常房间路由死循环验证
- **测试步骤**：
  1. 打开浏览器并打开开发者工具（Console & Network 面板）。
  2. 故意在地址栏访问一个不存在的房间链接，例如：`http://localhost:8080/online/#/room/random-non-existent-999` 或 `http://localhost:8080/online/#/game/fake-game-id`。
- **预期结果**：
  - Network 面板中仅发出 **1 次** `GET /online/api/rooms/random-non-existent-999` 请求并返回 404；
  - **绝不出现**持续连环发请求的死循环，Network 不再持续增加请求；
  - 页面正中央展示优雅的国风 404 错误卡片，提示「房间未找到或已解散 (404)」；
  - 点击卡片上的「返回对局大厅」按钮，页面平滑切换回大厅；点击「重新加载」则清除错误尝试刷新一次。

### 2. P1 胜方结算弹窗验证
- **测试步骤**：
  1. 使用两个浏览器窗口（或一个正常窗口 + 一个无痕窗口）分别登录账号 A 和账号 B。
  2. 账号 A 创建对战房间，账号 B 加入并开始对局。
  3. 双方走动数步后，账号 B 点击「认输」并确认。
- **预期结果**：
  - 账号 B（负方）立刻弹出对局结算弹窗；
  - **账号 A（胜方）在收到 WebSocket 广播后，页面立刻同步唤起结算 Modal**，标题显示胜利，副标题清晰标明「对方认输」；
  - 胜方界面不再停留在旧状态，双端弹窗完全同步呈现「再战一局 / 返回房间 / 复盘对局」操作。

### 3. P1 走子交互错误提示停留验证
- **测试步骤**：
  1. 进入任意在线对局或人机练习（如 `#/practice`）。
  2. 在轮到我方走子时，故意进行非法操作：
     - 点击走一个被别马腿的「马」到阻挡位置；
     - 或者直接点击对方棋子尝试走子。
- **预期结果**：
  - 顶部/中间状态栏立即变红加边框抖动（`.status--error`），显示「走子不符合象棋规则，请核对走法」；
  - 同时屏幕上方弹出黄色警告 Toast；
  - **观察 2 秒以上**：尽管右上角/右侧的倒计时时钟每 250ms 刷新一次，**错误状态栏文案在 2.6 秒内持续驻留，绝不会在 250ms 内被冲刷消失**；2.6 秒后平滑恢复为正常倒计时文字。

### 4. P1 移动端视口适配验证（390×844 及 375×667）
- **测试步骤**：
  1. 打开 Chrome 开发者工具，切换至移动端仿真模式，选择 `iPhone 12/13/14 Pro`（视口 390×844）或 `iPhone SE`（视口 375×667）。
  2. 分别进入在线对局页面 `#/game/...` 与人机对战页面 `#/practice`。
- **预期结果**：
  - 棋盘自动等比例缩放到视口安全区域内，既不失真，也不撑爆视口；
  - 底部 4 个操作按钮（「认输」、「求和」等）与玩家卡片完整展示在屏幕内部，无需滚动页面即可一屏操作；
  - 人机对战模式下，双方玩家卡片呈左右并列（1fr 1fr），不再上下堆叠吃高度；
  - 弹出任何 Toast 提示时，展示在视口上方，不遮盖下方下棋手势与操作区。

---

## 四、代码语法与构建自检

- 执行 `node -c src/main/resources/online/app.js`：**语法校验通过，退出码 0，无任何语法错误**。
- `git status` 审查：本次修改仅限定在前端 3 个文件（`app.js`、`app.css`、`mobile.css`），未改动任何 Java 后端代码，严格遵守无 `git push` 规则。

---

## 五、后续协作与仍待项

1. **Pi 后端完成后的集成测试**：
   - 待 Pi 将 `PublicSiteServer` WS 心跳和对局持久化合并后，联调测试长达 60 秒以上静置对局的 WS 心跳保活与网络重连 snapshot 对齐；
   - 联调私密房间只读鉴权 401/403 返回时前端 404/403 面板的友好呈现。
2. **移动端手势优化（体验优化项）**：
   - 后续可根据移动端真实机型滑动体验，进一步引入双击放大或防误触锁盘开关。
