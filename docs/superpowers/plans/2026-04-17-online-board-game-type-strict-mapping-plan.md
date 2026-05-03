# Online 棋盘与棋种严格对应修复计划

**Goal:** 在不改后端 API 与 AI 算法的前提下，保证所有页面棋盘渲染严格匹配 `gameType`，并补齐象棋练习局的即时反馈体验。

**Architecture:** 在 `online/app.js` 建立统一棋盘分发与 payload 校验，象棋练习局走乐观更新临时态，复盘棋盘继续保持象棋/五子棋样式隔离。

**Tech Stack:** vanilla JS, CSS, Maven, JUnit 5

---

### Task 1: 文档与约束落地

**Files:**
- Added: `docs/superpowers/specs/2026-04-17-online-board-game-type-strict-mapping-design.md`
- Added: `docs/superpowers/plans/2026-04-17-online-board-game-type-strict-mapping-plan.md`

- [ ] 固化“棋盘与棋种严格对应”的设计边界
- [ ] 固化未知棋种安全失败策略

### Task 2: Online 统一棋盘分发与未知棋种保护

**Files:**
- Modify: `src/main/resources/online/app.js`

- [ ] 新增对局页棋盘分发函数，按 `gameType` 路由到对应棋盘
- [ ] 新增分析页棋盘分发函数，按 `gameType` 路由到对应复盘棋盘
- [ ] 新增未知棋种占位 UI，并禁用交互
- [ ] 将对局页、练习页、分析页改为统一分发入口，移除分散三元判断

### Task 3: 象棋练习局乐观更新与 payload 校验

**Files:**
- Modify: `src/main/resources/online/app.js`

- [ ] 增加前端临时态用于象棋练习局本地落子回显
- [ ] 仅在 `isTraining + XIANGQI` 条件下启用乐观更新
- [ ] 增加 payload 形状校验，棋种不匹配直接拒绝
- [ ] 请求成功覆盖临时态，请求失败回滚并提示错误
- [ ] 临时态期间禁用棋盘交互，避免重复提交

### Task 4: 复盘样式隔离与契约测试

**Files:**
- Modify: `src/main/resources/online/app.css`
- Modify: `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

- [ ] 确认象棋复盘样式仅作用在象棋类名
- [ ] 确认五子棋复盘样式路径不受影响
- [ ] 扩展资源契约测试，断言：
  - `online/app.js` 含显式 `XIANGQI/GOMOKU` 分发逻辑
  - `online/app.js` 含未知棋种保护分支

### Task 5: 验证

**Commands:**
- `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomeSessionHubTest,LegacyHomepageResourceContractTest" test`

- [ ] 跑定向测试并记录结果
- [ ] 检查改动未影响入口与会话基础流程
