# Online 棋盘与 AI 练习修复实施计划

**Goal:** 一次性修复棋盘几何、学习题库独立视图、AI 练习交互稳定性三条链路，恢复可正常在线对弈。

**Architecture:** 前端 `online` 单页应用重构（路由子视图 + 棋盘渲染层 + 练习状态机）；后端 API 保持不变。

**Tech Stack:** Vanilla JS, CSS, Maven/JUnit

---

### Task 1: 象棋棋盘几何重做（交点模型）

**Files**
- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`

- [ ] 改造象棋格点绘制为交点中心半段线模型
- [ ] 河界仅断中间 7 列竖线，保留边线连续
- [ ] 九宫斜线与炮兵角标独立图层化
- [ ] 棋子严格居中对齐交点

### Task 2: 学习页二级标签与深链接

**Files**
- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`

- [ ] 增加 `#/learn/tutorials|puzzles|practice` 子路由分发
- [ ] `#/learn` 默认落到 `puzzles` 视图
- [ ] 学习页改为标签切换，避免三块堆叠
- [ ] 保留“按此题开局”与推荐练习入口

### Task 3: AI 练习交互状态机修复

**Files**
- Modify: `src/main/resources/online/app.js`

- [ ] 引入统一 `moveInFlight` 锁，防连点重复提交
- [ ] 移除练习局乐观更新，改为服务端确认渲染
- [ ] 新增 practice 轮询器并绑定 `aiPending`
- [ ] 明确并实现轮询停止条件（结束/离页/登出/认输）
- [ ] 服务端快照刷新时清理旧 `selectedFrom`

### Task 4: 资源契约测试扩展

**Files**
- Modify: `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

- [ ] 断言学习二级标签路由函数存在
- [ ] 断言 `moveInFlight` 与 practice 轮询函数存在

### Task 5: 验证与发布检查

- [ ] 运行 `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest,PracticeGameHubTest" test`
- [ ] 运行 `mvn -q test`
- [ ] 验证 `/online` 静态资源版本更新
- [ ] 手工回归 `/online#/learn`、`/online#/practice/{id}`、`/online#/analysis/{id}`
