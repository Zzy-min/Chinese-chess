# 轻棋局（XiangqiArena）质量审计缺陷修复设计规格 (Spec)

## 1. 背景与目标
针对 `tests/reports/full-quality-audit/BUG_REPORT.md` 与 `FINAL_TEST_REPORT.md` 中确认的 10 个缺陷（BUG-001 至 BUG-010），在不破坏现有后端安全契约（`BE-01`、`BE-03`、私房 403 访问控制）且不触碰生产服务器的前提下，完成全部 10 项缺陷的修复与本地隔离回归验证。

## 2. 缺陷修复方案设计

### BUG-001 (P1): 直接打开房间分享链接 `#room/{roomId}` 报错 403
- **约束**: `PublicSiteServerTest` 明确要求私房对非成员 `GET /online/api/rooms/{roomId}` 返回 `403 FORBIDDEN`，未登录返回 `401`。因此不放宽 `GET /online/api/rooms/{roomId}` 的权限校验。
- **方案**:
  1. 在 `app.js` `fetchJson` 中将 HTTP 状态码 `response.status` 与错误码 `data.code` 挂载到抛出的 `Error` 对象（`err.status`、`err.code`）。
  2. 在 `app.js` `loadRoom(roomId)` 中：
     - 若 `GET /rooms/${roomId}` 返回 `403` 且当前用户已登录（`state.me` 存在），自动尝试调用 `POST ${API_BASE}/rooms/${roomId}/join`；若加入成功，将返回的房间快照写入 `state.room` 并刷新渲染；若加入失败（如房间已满/已开局/已关闭），展示真实错误信息而非误导性的“该房间不存在或已关闭”。
     - 若 `GET /rooms/${roomId}` 返回 `401`，记录明确错误态引导用户登录后加入（并在 `renderRoom` 错误面板或提示中提供登录入口/清晰文案）。
     - 若 `GET /rooms/${roomId}` 返回 `200` 且为公开房间、`!data.guest && state.me && data.host?.id !== state.me.id && data.status === 'WAITING'`，保持可立即点击加入或自动加入，确保“打开链接即可加入”语义一致。

### BUG-002 (P1): 对局结束弹窗 (`endGameOverlay`) 跨局/切换路由后残留
- **方案**:
  1. 在 `app.js` `render()` 入口检测当前路由：若 `state.endGameModal` 存在且 `(route.page !== 'game' && route.page !== 'practice')` 或 `state.endGameModal.game?.gameId !== route.id`，立即清空 `state.endGameModal = null`。
  2. 在 `loadGame(gameId)` 与 `loadPractice(gameId)` 加载新对局时，若新 `gameId` 与当前 `state.endGameModal?.game?.gameId` 不一致，清空 `state.endGameModal = null`。
  3. 在 `renderGameEndModal()` 中增加校验：仅当 `modal.game && modal.game.gameId === route.id` 时才渲染弹窗。

### BUG-003 (P1): `XQ_DATABASE_URL` 传入 H2 JDBC URL 时驱动绑定失败
- **方案**:
  - 修改 `OnlineStore.createDefaultDataSource()`：在 `rawUrl` 非空分支中，先计算 `String jdbcUrl = toJdbcUrl(rawUrl)`，根据 `jdbcUrl.startsWith("jdbc:h2:")` 自动选择 `org.h2.Driver`（否则 `org.postgresql.Driver`）；若为 H2 且未显式提供 `XQ_DATABASE_USER` / `XQ_DATABASE_PASSWORD`，分别默认 `"sa"` 与 `""`。

### BUG-004 (P2): `#/play` 右侧“加入房间”按钮对比度不足
- **方案**:
  - `app.css` 中 `.lobbyActionGrid .actionBtn .actionText strong` 与 `span` 覆盖了 `.actionBtn--join` 的白色文字继承。为 `.actionBtn--join` 及其子元素（`.actionText strong`、`.actionText span`、`.actionIcon`）显式设置 `color: #FFFFFF !important;`，确保对比度 >= 4.5:1。

### BUG-005 (P2): `#/play` 虚假在线人数、重复 `已完赛` 标签、标题换行与分数贴边
- **方案**:
  1. 移除 `renderPlayLobbyDesk()` 中硬编码的 `12564 + ...`、`9642 + ...` 虚假在线人数，改为基于 `state.bootstrap` 真实统计（如 `b.gameStats` 中的活跃房间数 / 累计对局数，例如 `活跃房间 X · 累计对局 Y` 或真实活跃对局数）。
  2. “最近对局”列表项中，当 `resultText === '已完赛'` 时不再重复渲染 `<span class="gameTime">已完赛</span>`；有具体胜负结果时，右侧显示状态或时间而非固定重复字样。
  3. `app.css` 中调整 `.deskLobbyAside .deskSectionHeader h3`（`font-size: 14px !important; white-space: nowrap;`）与 `.deskRankTabs`（`flex-shrink: 0; white-space: nowrap;`）防止“推荐高手（象棋）”折行；调整 `.deskRankRow` 内边距与 `.rankScore` 右边距，并修正媒体查询中 `.deskLobbyAside .deskRankRow` 的 4 列布局（`auto auto minmax(0, 1fr) auto`）。

### BUG-006 (P2): `/home-ai` 缺失 `/assets/audio/capture.wav` 与 `/assets/audio/check.wav`
- **方案**:
  - 在 `PublicSiteServer.java` 路由表中补充 `.get("/assets/audio/capture.wav", this::handleCaptureAudio)` 与 `.get("/assets/audio/check.wav", this::handleCheckAudio)`，分别返回 `/audio/capture.wav` 与 `/audio/check.wav`（`audio/wav`）。

### BUG-007 (P2): 无限步时仍显示 `步时 01:00` 及 `AI 难度: EASY` 未汉化
- **方案**:
  1. 在 `app.js` 新增 `practiceDifficultyLabel(difficulty)` 映射（`NOVICE -> 入门`、`EASY -> 简单`、`MEDIUM -> 中等`、`HARD -> 困难`、`MASTER/EXPERT -> 大师`）。
  2. 在 `renderPracticeView` 中替换 `AI 难度` 与 `.mobileDifficultyPill` 为中文难度标签，并移除硬编码的假计时 `14:32` / `步时 01:00 | 剩余 08:15`，改为真实无步时限制文案（`不限时` / `无步时限制`）。
  3. 在 `renderPlayerClockCard` 中将硬编码的 `步时 01:00 | 剩余 ...` 改为根据 `game.stepTimeSeconds` 动态渲染（无单步限时时显示 `步时 不限 | 剩余 ...`）。

### BUG-008 (P2): 移动端 `375x812` `#/play` 卡片右侧模糊灰色装饰色块
- **方案**:
  - 在 `mobile.css` 中移除/隐藏 `.mobileLobby--strict .mobileLobbyCta--bamboo::after` 与 `.mobileLobby--strict .mobileRoomDisclosure--ink .mobileDisclosureDeco` 的模糊渐变背景色块（`display: none !important;`）。

### BUG-009 (P3): 未登录访客访问站点必然触发 `/online/api/auth/me` 401 Console Error
- **约束**: 保持后端 `handleMe` 契约 `BE-01`（未登录返回 `401 + AUTH_REQUIRED`）。
- **方案**:
  - 在 `app.js` `init()` 中先等待 `loadBootstrap()` 完成；`bootstrap` 接口已包含 `me` 字段（已登录返回用户对象，未登录返回 `null`）。当 `state.bootstrap && Object.prototype.hasOwnProperty.call(state.bootstrap, 'me')` 时，直接将 `state.me = state.bootstrap.me || null`，避免匿名首次加载再请求 `/online/api/auth/me` 产生 401 控制台报错；仅在 `bootstrap` 失败时回退调用 `loadMe()`。

### BUG-010 (P3): `#/learn/practice` 4 个缺少可访问性标签的表单控件
- **方案**:
  - 在 `app.js` `renderLearnPracticeTab()` 中为 4 个 `<select>` 控件补充唯一 `id`、对应的 `<label for="...">` 以及 `aria-label`。

## 3. 缓存版本同步
- 修改 `online/app.js`、`online/app.css`、`online/mobile.css` 后，同步更新 `src/main/resources/online/index.html` 中 4 处 `?v=` 参数为同一新版本号（如 `20261116r1`），满足 `OnlineSiteResourceContractTest` 契约。
