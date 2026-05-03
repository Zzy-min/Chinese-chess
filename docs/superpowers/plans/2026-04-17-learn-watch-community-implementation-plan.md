# Learn / Watch / Community 功能完善实施计划

**Goal:** 将 Online 的 `learn/watch/community` 从壳层升级为真实可用能力，支持游客浏览与登录互动，并保持现有对局主链路稳定。

**Architecture:** 基于 `PublicSiteServer + OnlineStore` 扩展只读与登录态接口，前端通过页面注册表与独立 loader 接入新数据；学习内容使用资源种子，进度持久化到 `learn_progress`。

**Tech Stack:** Undertow, Java, H2/PostgreSQL-compatible SQL, vanilla JS, Maven, JUnit 5

---

### Task 1: 数据层与资源

**Files**
- Modify: `src/main/resources/online/schema.sql`
- Add: `src/main/resources/online/learn-content.seed.json`
- Modify: `src/main/java/com/xiangqi/online/server/OnlineStore.java`

- [ ] 新增 `learn_progress` 表并确保幂等初始化
- [ ] 增加学习种子内容加载能力
- [ ] 增加学习进度读取与完成写入能力
- [ ] 增加观战归档列表与社区榜单聚合查询

### Task 2: API 扩展

**Files**
- Modify: `src/main/java/com/xiangqi/web/PublicSiteServer.java`
- Modify: `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`

- [ ] 新增 `learn/content`、`watch/overview`、`community/leaderboard` 公开接口
- [ ] 新增 `learn/progress` 与完成写入接口并保持登录校验
- [ ] 保持错误响应语义与既有接口一致

### Task 3: 前端页面层重构

**Files**
- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`

- [ ] 建立页面注册表和独立数据 loader
- [ ] Learn 页接入教程/题库/推荐练习与进度交互
- [ ] Watch 页接入列表展示、过滤与分析跳转，并启用 10 秒轮询
- [ ] Community 页接入胜局榜/活跃榜切换与登录定位
- [ ] 保留现有对局主流程行为

### Task 4: 测试与回归

**Files**
- Modify: `src/test/java/com/xiangqi/web/PublicSiteServerTest.java`
- Modify: `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

- [ ] 增加接口契约测试（游客可读、登录可写）
- [ ] 增加学习进度读写回归测试
- [ ] 增加前端资源契约断言（新页面 renderer/loader）
- [ ] 运行 `mvn -q test` 并验证主链路不回退
