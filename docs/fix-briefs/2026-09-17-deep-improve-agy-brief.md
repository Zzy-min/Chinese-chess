# agy brief：轻棋局深改 · 前端 / UI / UX（2026-09-17）

- **仓**：`/workspace/Chinese-chess`
- **约束**：**只改** `src/main/resources/online/**` 与本 brief/RESULT 文档；**禁止 git push / 部署**。不要开全仓 `mvn test`（易超时）；用 `node --check` / 静态自测 / 窄范围手测。
- **对照 backlog**：`docs/diag/2026-09-17-deep-improve-BACKLOG.md`
- **已结案（勿重做）**：
  - `028165b`：路由死循环 404 面板、胜方结算 Modal、非法走子 `interactionError`+Toast 抗心跳、WS pong+1s 重连+snapshot 对账、移动端 390 视口预算。
  - **`0245638`**（agy-game-ui RESULT）：顶栏减重、去掉 PLAYING「等待中」、时钟可读、棋谱红黑用字、`formatMoveNotation`、三栏 `minmax`、认输 `ghost danger`。
  - Toast/WS QA：`voonie-align/xiangqi-qa-2026-09-17-toast-ws/report.md` ✅（断线横幅仍缺 → 本波次做）。

> 用户口头「3a8acc0」在本仓不存在；对局 UI 打磨 commit 以 **`0245638`** 为准。

## 目标（本波次，详细升级非微补丁）

在 `0245638` 之后清掉仍阻碍「职业对局感」的 UX 债：Toast 挡操作、结算层层次乱、原生 confirm、断线无感知、375 待验、记谱专业化、合法点提示。不回退已打磨布局。

## Must ship

### P1-1 · 对局 Toast 避开操作条（FE-01）
**问题**：`showToast(..., 'move')`（「对手已落子…」）走 `.toastHost` 贴底（`app.css`），压住 `.woodActions`（悔棋/求和/认输/离开）。证据：dual-ui §7、deep-explore D07。

**做法**：
1. 对局/练习路由（`mobile-board-route` 或桌面 board route）把 Toast 锚到**顶部安全区下方**或棋盘上方状态带，禁止盖住 `.woodActions`。
2. `toast--move` 可缩短时长或可点击关闭；不要同时堆多条挡按钮。
3. 保持非法走子 warning Toast 与 `interactionError` 锁逻辑（勿破坏 toast-ws ✅）。

**验收**：双人对局中对方落子 → Toast 可见且操作条可点；非法走子提示仍 ≥2s 不被时钟刷掉。

### P1-2 · 结算弹窗层次与中文（FE-02）
**问题**：四按钮两红两白略挤；`resultText` 可能英文 `resigned` 与「认输」并排（dual-ui、D08/D09）。`renderEndGameModal` / `.endGameActions`。

**做法**：
1. 主次 CTA：主按钮 1 个（再战/请求再战），次要 ghost（分析、离开、关闭）；避免四个同权实心。
2. 展示层对 `resultText` / `terminationReason` 再做中文词典兜底（即使 Pi 尚未改服务端）。
3. 不重做结算出现逻辑（胜方已能出窗）。

**验收**：认输后双端弹窗；无裸英文枚举；窄宽下按钮不挤爆卡片。

### P1-3 · 国风确认框替换 `window.confirm`（FE-03）
**问题**：认输/关房/离开等仍 `window.confirm`，阻塞主线程、打断动画与观感（ISSUE-008）。

**做法**：
1. 轻量国风 Modal（复用 endGame 遮罩视觉语言）：标题、说明、取消/确认。
2. 认输、离开房间、关闭房间等破坏性操作全切过去。
3. 时钟：确认打开期间不要「假死」；若必须暂停 UI，恢复后时间展示与服务器权威一致（以服务端 clock 为准）。

**验收**：全站对局路径无 `window.confirm`；认输确认样式与站点一致；确认后结算仍正常。

### P1-4 · 断线/重连醒目横幅（FE-07）
**问题**：功能可自动重连（toast-ws ✅），但 Offline→Online 过程无横幅（D10）。`socket.onclose` → `setTimeout(syncRealtime, 1000)` 无 UI。

**做法**：
1. 状态：`connected | reconnecting | offline`；断线立刻顶栏/棋盘条提示「连接中断，正在重连…」；恢复「已恢复连接」数秒后消隐。
2. 对齐 `docs/ws-reconnect-contract.md`：重连后依赖现有 subscribe + REST 对账，不必重造协议。
3. 不要在重连风暴时刷 Toast 盖住操作条（与 FE-01 一致）。

**验收**：DevTools Offline→Online：有明显断线态与恢复态；盘面可续走；无请求死循环。

### P1-5 · 移动端对局 ~375 验收与补洞（FE-08）
**问题**：09-13 已做 390 预算；deep-explore D12 / dual-ui 项 4 仍标对局页 375 待验。

**做法**：
1. 在 375×667 与 390×844 仿真下打开真实对局页：棋盘、双方时钟、操作条一屏可见、无关键裁切。
2. 仅改 `mobile.css` / 测量函数；不破坏 `0245638` 桌面三栏。
3. 在 RESULT 贴测量结论（通过/仍缺什么）。

**验收**：375 对局页无需滚动即可完成「选子→走子→看钟→点求和」；Toast 不挡操作（与 FE-01 联动）。

## Should ship（同波次有余力）

### P2-1 · 传统中式记谱（FE-05）
**现状**：`formatMoveNotation` 只统一红黑兵卒/仕士等用字，仍非「炮二平五」。

**做法**：坐标→中式记谱（红用中文数字路、黑用阿拉伯路等常规约定）；列表与复盘共用；五子棋保持原样。

**验收**：若干典型着法快照对照正确；红黑用字不回退。

### P2-2 · 合法落点提示（FE-06）
**做法**：选中己子后展示可走点（`board.js` 已有 hint class 可扩展）；非法目标仍走现有错误通道。

**验收**：选马/炮/车时落点直观；不影响对手视角与观战。

### P2-3 · 1280×800 操作条回归（FE-14）
**验收**：1280×800 / 1366×768 下 `.woodActions` 完整在首屏（`0245638` 后回归）。

### P2-4 · 文案扫尾（FE-04）
登录/大厅等残留英文走统一 `errorMap`；未知后端字符串兜底中文。与 Pi 错误码联调优先。

## Nice / Later
- FE-09 榜单昵称截断、FE-10 横幅平铺缝、FE-12 a11y、FE-13 导航用词统一；FE-11 已因 D15 提升为 P2。
- FE-99：**WAITING_FOR_EXPLORATION_LIST** — 仅保留观战截图、个人中心、匹配进行中、异常路由截图、悔棋/求和全流程、移动端对局页等剩余覆盖缺口。

## 明确不要做
- 不要重做：路由 404 面板、胜方 Modal 出现条件、非法 Toast 抗心跳、WS ping/pong/1s 重连、`0245638` 五连 UI（只允许回归修复）。
- 不要改 Java / 不要 push。
- 不要为了「传统记谱」破坏红黑用字已修结果。

## 主文件
- `src/main/resources/online/app.js` — Toast、confirm、endGameModal、syncRealtime、formatMoveNotation
- `src/main/resources/online/app.css` — toastHost、endGameActions、woodActions、三栏 grid
- `src/main/resources/online/mobile.css` — 375/390、toast 避让
- `src/main/resources/online/board.js` — 合法点 hint

## 手测清单
1. 双人对局：对方落子 Toast 不挡按钮；非法走子提示 ≥2s。
2. 认输：国风确认 → 双端结算；无英文 `resigned`（有兜底）。
3. DevTools Offline→Online：横幅出现并恢复。
4. 375 与 390 对局页一屏可操作。
5. （可选）记谱「炮二平五」样例；合法点可见。
6. `node --check src/main/resources/online/app.js` 通过。

## 交付
写 `docs/fix-briefs/2026-09-17-deep-improve-agy-RESULT.md`：改动文件、逐项前后对照、手测结果、明确未做项、未 push。


## v1 explore addendum

- 若尚未开始，优先本波处理 **D15 / FE-11（P2）**：房主等待室隐藏「加入当前房间」幽灵按钮，避免把房主误导为仍可加入。

## Explore v1.1 addendum（务必本波覆盖）
- **D15/FE-11**：房主隐藏「加入当前房间」
- **D22/FE-20**：`#/me` 勿对 FINISHED 显示「继续当前对局」
- **D23**：配合 Pi，未知路由友好 404（勿静默回首页）
- **D13/D14/D24**：XIANGQI/FINISHED/900 seconds/resigned 中文化
