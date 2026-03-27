# PublicSiteServer Auth Bootstrap Schema Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `PublicSiteServer` 未初始化在线站点 schema 导致 bootstrap、注册和登录交互异常的问题，并用回归测试兜住默认公共站点启动路径。

**Architecture:** 保持现有认证与在线站点架构不变，只在 `PublicSiteServer` 的 store 注入边界补上 schema 初始化。测试层新增“未手动初始化 store 也能正常启动公共站点”的回归用例，确保问题被固定在启动边界上，而不是靠外部调用者记忆补救。

**Tech Stack:** Java 17, Maven, JUnit 5, H2, Undertow

---

## File Structure

### Existing files to modify
- `src/main/java/com/xiangqi/web/PublicSiteServer.java`: 在构造时初始化 schema，并对失败场景抛出明确异常。
- `src/test/java/com/xiangqi/web/PublicSiteServerTest.java`: 新增未手动初始化 store 的回归测试，并保持现有在线首页断言。

---

## Chunk 1: 回归测试先行

### Task 1: 为未初始化 store 的公共站点启动路径写失败测试

**Files:**
- Modify: `src/test/java/com/xiangqi/web/PublicSiteServerTest.java`

- [ ] **Step 1: 新增一个不调用 `store.initSchema()` 的 store 工厂**

```java
private OnlineStore newUninitializedStore() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    return new OnlineStore(dataSource);
}
```

- [ ] **Step 2: 写失败测试，断言 bootstrap 在空数据库首次启动时仍返回 200**

```java
@Test
void initializesSchemaForPublicSiteStoreAutomatically() throws Exception {
    OnlineStore store = newUninitializedStore();
    PublicSiteServer server = new PublicSiteServer(store);
    int port = findFreePort();
    try {
        server.start("127.0.0.1", port);
        HttpResponse<String> bootstrap = HttpClient.newHttpClient()
            .send(request(port, "/online/api/site/bootstrap"), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bootstrap.statusCode());
    } finally {
        server.stop();
    }
}
```

- [ ] **Step 3: 运行测试确认当前失败**

Run: `mvn -q -Dtest=PublicSiteServerTest test`
Expected: FAIL，当前构造路径未建表时 bootstrap 返回 500

---

## Chunk 2: 最小实现

### Task 2: 在 `PublicSiteServer` 注入边界统一初始化 schema

**Files:**
- Modify: `src/main/java/com/xiangqi/web/PublicSiteServer.java`

- [ ] **Step 1: 将 schema 初始化收敛到 `PublicSiteServer(OnlineStore store)` 构造路径**

```java
public PublicSiteServer(OnlineStore store) {
    this.store = requireInitializedStore(store);
    ...
}
```

- [ ] **Step 2: 实现最小 helper，统一调用 `store.initSchema()` 并包装异常**

```java
private static OnlineStore requireInitializedStore(OnlineStore store) {
    try {
        store.initSchema();
        return store;
    } catch (Exception ex) {
        throw new IllegalStateException("failed to initialize public site schema", ex);
    }
}
```

- [ ] **Step 3: 保持默认构造路径不变，由注入构造统一兜底**

Run: file edit only
Expected: `PublicSiteServer()` 不需要额外显式调用第二次初始化

---

## Chunk 3: 验证

### Task 3: 运行回归测试和本地接口验证

**Files:**
- Modify: `src/test/java/com/xiangqi/web/PublicSiteServerTest.java`

- [ ] **Step 1: 运行目标测试确认通过**

Run: `mvn -q -Dtest=PublicSiteServerTest test`
Expected: PASS

- [ ] **Step 2: 运行完整测试集确认没有引入回归**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 3: 重启本地站点并验证 bootstrap**

Run: `cmd /c "set NO_BROWSER=1 && cd /d D:\claude项目\XiangqiGame && run_web.bat --rebuild"`
Expected: 站点启动成功

Run: `curl.exe -i http://127.0.0.1:18388/online/api/site/bootstrap`
Expected: `HTTP/1.1 200`

- [ ] **Step 4: 验证注册接口**

Run: use PowerShell `Invoke-WebRequest` with JSON body to `/online/api/auth/register`
Expected: `200` and session payload

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/web/PublicSiteServer.java src/test/java/com/xiangqi/web/PublicSiteServerTest.java docs/superpowers/specs/2026-03-27-auth-bootstrap-schema-design.md docs/superpowers/plans/2026-03-27-auth-bootstrap-schema-fix-plan.md
git commit -m "fix(web): initialize online schema for public site auth"
```
