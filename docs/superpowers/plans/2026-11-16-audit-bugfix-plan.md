# 轻棋局（XiangqiArena）质量审计缺陷修复实施计划 (Plan)

## 1. 修改清单与步骤
1. **后端配置与路由修复**
   - `src/main/java/com/xiangqi/online/server/OnlineStore.java`: 修复 `createDefaultDataSource()`，识别 `jdbc:h2:` 前缀并选用 `org.h2.Driver` 与默认 H2 凭据（BUG-003）。
   - `src/main/java/com/xiangqi/web/PublicSiteServer.java`: 注册 `/assets/audio/capture.wav` 与 `/assets/audio/check.wav` 静态路由（BUG-006）。
2. **前端逻辑与交互修复** (`src/main/resources/online/app.js`)
   - `fetchJson`: 附加 `error.status` 与 `error.code`。
   - `init()`: 优先复用 `state.bootstrap.me`，消除匿名访客 `/online/api/auth/me` 401 噪音（BUG-009）。
   - `loadRoom()` & `renderRoom()`: 403 时若已登录自动尝试 `POST /rooms/{id}/join`，401 时提示登录，区分真实错误（BUG-001）。
   - `render()`, `loadGame()`, `loadPractice()`, `renderGameEndModal()`: 路由切换/对局 ID 变更时清理并校验 `state.endGameModal`（BUG-002）。
   - `renderPlayLobbyDesk()`: 移除虚假在线人数 `12564`/`9642`，去除重复 `已完赛` 标签（BUG-005）。
   - `practiceDifficultyLabel()`, `renderPracticeView()`, `renderPlayerClockCard()`: 难度中文化，移除矛盾 `步时 01:00` 与写死假时钟（BUG-007）。
   - `renderLearnPracticeTab()`: 为 4 个 `<select>` 补全 `id`、`for` 与 `aria-label`（BUG-010）。
3. **前端样式修复**
   - `src/main/resources/online/app.css`: 修复 `.actionBtn--join` 白色文字对比度（BUG-004）；修复 `.deskLobbyAside` 标题折行、排行榜 4 列对齐与右边距（BUG-005）。
   - `src/main/resources/online/mobile.css`: 隐藏 `.mobileLobbyCta--bamboo::after` 与 `.mobileDisclosureDeco` 模糊灰块（BUG-008）。
4. **缓存版本号同步**
   - `src/main/resources/online/index.html`: 将 `app.css`、`mobile.css`、`board.js`、`app.js` 的 `?v=` 统一更新为 `20261116r1`。
5. **单元测试补充**
   - 在 `src/test/java/com/xiangqi/web/PublicSiteServerTest.java` 或新测试中验证 `/assets/audio/capture.wav`、`/assets/audio/check.wav` 返回 200，以及 `OnlineStore` 对 `XQ_DATABASE_URL=jdbc:h2:mem:...` 的支持。

## 2. 验证计划
1. 运行 `mvn test` 验证全部 Java 单元/集成测试通过。
2. 在本地隔离端口启动 `PublicWebMain`（`XQ_DATABASE_URL=jdbc:h2:file:...` 直接验证 BUG-003！），运行既有 Playwright / Node 自动化脚本（`audit_api_and_rules.mjs`、`audit_ui_and_interactions.cjs`、`audit_multiplayer_and_load.cjs`、`audit_deep_verify.mjs`）以及针对 BUG-001~010 的专项回归断言。
3. 清理临时测试进程，确认 `git diff` 干净聚焦，不触碰生产环境。
