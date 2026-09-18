# Pi brief：轻棋局深改 · 后端 / 架构 / WS / 安全 / 持久化（2026-09-17）

- **仓**：`/workspace/Chinese-chess`
- **约束**：**禁止 git push / 部署 / 改生产**。本 brief 为下一执行波次说明；落地时只改 Java/测试/相关文档。
- **对照 backlog**：`docs/diag/2026-09-17-deep-improve-BACKLOG.md`
- **已结案（勿重做）**：`028165b` — WS 心跳+死链清理、`FileRoomPersistence`、REST `canReadRoom`≡`canSubscribeRoom`、路由 404 友好态（前端协同）。契约见 `docs/ws-reconnect-contract.md`。Toast/WS QA（`voonie-align/xiangqi-qa-2026-09-17-toast-ws`）已 ✅。

## 目标（本波次，详细升级非微补丁）

把「可玩」抬到「可信任运营」基线：错误契约可机读、会话与安全头达标、身份与限流不易被滥用、`/online/` 可进、持久化可运维。不重做已完成的心跳/鉴权对齐/落盘最小方案。

## Must ship

### P1-1 · 统一 HTTP 错误契约（BE-01）
**问题**：`GET /online/api/auth/me`（及 `/api/auth/me`）未登录仍 `200` + JSON `null`（`PublicSiteServer.handleMe` → `orElse(null)`）。历史「不存在」路径曾 `200+{}` 与 `404` 混用；业务失败大量 `400` + 英文内部字符串（`invalid credentials`、`room not found` 等）。

**做法**：
1. 定义稳定错误体：`{"code":"AUTH_REQUIRED|FORBIDDEN|NOT_FOUND|CONFLICT|RATE_LIMITED|BAD_REQUEST","message":"<中文或可映射 key>"}`。
2. `me`：二选一写进契约并改前端——**(A)** 未登录 `401` + `AUTH_REQUIRED`，或 **(B)** 保持 200 但改为 `{"user":null}`（禁止裸 `null`）。推荐 (A) 并同步 agy。
3. 房间/对局缺失统一 `404 NOT_FOUND`；无权限 `403`；限流 `429`。
4. 登录/注册失败不要直吐内部英文：映射 `INVALID_CREDENTIALS` 等 code；message 给中文默认值。
5. 单测覆盖 me / 缺房间 / 缺对局 / 私房越权矩阵。

**验收**：
- 未登录 `curl -s -o /dev/null -w '%{http_code}' /online/api/auth/me` 符合选定契约（非「200 + null」）。
- 不存在 game/room → 404 + `code=NOT_FOUND`。
- 私房非成员 → 403（登录后）。
- 相关 `mvn test` 绿（可只跑 `PublicSiteServer*` / auth 相关，避免无意义全仓超长跑）。

### P1-2 · Cookie 与安全响应头（BE-02）
**问题**：`setAuthCookie` 仅 `Path=/` + `HttpOnly` + `Max-Age`，无 `Secure` / `SameSite`；未见 CSP / HSTS / `X-Content-Type-Options` / `X-Frame-Options` / `Referrer-Policy`。

**做法**：
1. Cookie：`SameSite=Lax`（或 Strict，若与分享链接兼容）；HTTPS 环境 `Secure=true`（可用配置开关，本地 http 关闭）。
2. 在公共响应路径加安全头基线（至少 `X-Content-Type-Options=nosniff`、`X-Frame-Options=DENY` 或 `SAMEORIGIN`、`Referrer-Policy=strict-origin-when-cross-origin`；HSTS 仅在确认全站 HTTPS 时启用）。
3. 文档注明与 Cloudflare 头的职责边界（源站仍要有底线）。

**验收**：登录响应 `Set-Cookie` 含 `HttpOnly` + `SameSite`（及生产 `Secure`）；`curl -I` 关键页/API 可见安全头。

### P1-3 · 用户枚举与昵称治理（BE-03）
**问题**：`AuthService.normalizeUsername` 仅 trim+lower，无长度/字符集/敏感词；lobby 搜索与排行返回完整 user UUID（diag-PI PI-06；agy ISSUE-009）。

**做法**：
1. 注册：长度上下限、允许字符（字母数字中文与有限符号）、拒绝空白/控制符；敏感词黑名单（可配置文件）。
2. 公开 API：排行榜/搜索不要暴露稳定内部 UUID，或改短公开 id / 哈希；内部 id 仅己方会话可见。
3. 与 agy 约定展示截断（前端 FE-09），后端仍要做写入侧拦截。

**验收**：含辱骂词/超长/非法字符注册失败；公开 search/leaderboard JSON 无完整内部 UUID（或仅有不可逆公开 id）；单测覆盖。

### P2-1 · 密码与限流加固（BE-04）
**问题**：密码仅 `length >= 8`；`authLimiter.allow(ip + ":login")` 按 IP（8/min），NAT 误伤、无账号锁定。

**做法**：
1. 密码：最小长度 + 复杂度或常见密码黑名单；错误 code 与中文 message。
2. 限流键改为 `username+ip`（登录）/ `ip`（注册防刷）分层；连续失败指数退避或短时锁。
3. 文档说明 429 行为。

**验收**：弱密码注册失败；同 IP 不同用户不互相误杀到不可用；单测模拟限流。

### P2-2 · `/online/` 尾斜杠入口（BE-06）
**问题**：只注册了 `/online` 与 `/online/index.html`，裸 `/online/` → 404（deep-explore D11）。

**做法**：注册 `GET/HEAD /online/` → 同 `handleOnlineIndex`，或 `301` → `/online`（或 `/online#/home`）。优先 200 同源资源，少一次跳转。

**验收**：`curl -s -o /dev/null -w '%{http_code}' https://…/online/`（或本地）为 200 或预期 301，不再 404。

### P2-3 · 走子后冗余刷新契约（BE-05，联合 agy）
**问题**：每步 move 成功后前端 `refreshBootstrapAndProfile()` 等再打 bootstrap+dashboard（diag-PI PI-07）。

**做法（后端侧）**：
1. 确保 `POST …/move` 响应已含客户端所需权威快照（board/turn/clock/stateId/result）。
2. 可选：WS `room_state` 已带够字段则文档声明「move 后不必拉 bootstrap」。
3. 不要为了兼容再增加更重的全量端点。

**验收**：契约注释/简短 docs；agy 删逐步 bootstrap 后对局仍一致（联调）。

### P2-4 · WS 握手失败要干净（BE-07）
**问题**：无鉴权/异常握手曾 HTTP 500（PI-09）。

**做法**：握手失败返回 401/403 或可预期关闭码；禁止未捕获异常变 500；补测。

**验收**：无 cookie 握手不再 500；日志无栈刷屏。

### P2-5 · 持久化运维化（BE-08）
**问题**：`FileRoomPersistence`（`XQ_ROOM_STATE_FILE`，默认 `./data/room-state.json`）已能重启恢复，但生产挂载、备份、多实例未产品化。

**做法**：
1. `docs/deployment/`（或现有 deployment 文档）写清：数据卷路径、权限、重启验证步骤。
2. 评估是否升级到 SQLite/DB（SPI 已在）；单机可先文件+卷。
3. 多实例：明确「需粘性会话或外置状态」，避免静默双脑。

**验收**：文档可照做；本地模拟「杀进程再起」对局可续（已有测试则引用）；不在本波次强上多活集群除非明确要求。

### P2-6 · 结算文案权威中文（BE-12，联合 agy）
**问题**：`resultText` 仍可能出现 `resigned` 等英文与中文「认输」并排（dual-ui / D08）。

**做法**：服务端生成面向用户的中文 `resultText`；保留枚举字段 `terminationReason=RESIGN|CHECKMATE|TIMEOUT|…` 给前端。不要只靠前端猜译。

**验收**：认输/将死/超时结算 API 与 WS 快照中 `resultText` 为中文；枚举仍稳定。

## Later（本波次可不做，已挂 backlog）
- BE-09 viewerSide 观战语义、BE-10 analysis stateId 防御、BE-11 孤儿 PLAYING 练习局清理。
- BE-99：**WAITING_FOR_EXPLORATION_LIST** — 等云电脑补齐大厅/匹配/观战/排行/个人中心后再开 API 专项。

## 明确不要做
- 不要重做 WS ping/重连订阅契约（已有且 QA ✅）。
- 不要重做 REST/WS 读鉴权对齐（已有）。
- 不要重做 `FileRoomPersistence` 最小落盘（已有）；只做运维化与可选背板。
- 不要改 agy 负责的 Toast/横幅/记谱 UI（可留接口字段）。
- 不要 push / 部署。

## 主文件
- `src/main/java/com/xiangqi/web/PublicSiteServer.java` — 路由、me、cookie、安全头、WS 握手、`/online/`
- `src/main/java/com/xiangqi/online/auth/AuthService.java` — 用户名/密码校验
- `src/main/java/com/xiangqi/online/server/RateLimiter.java` — 限流键
- `src/main/java/com/xiangqi/online/server/FileRoomPersistence.java` / `OnlineRoomHub.java` — 持久化运维
- 测试：`src/test/java/com/xiangqi/web/PublicSiteServerTest.java` 等

## 交付
1. 代码 + 单测。
2. `docs/fix-briefs/2026-09-17-deep-improve-pi-RESULT.md`（已做/验证/未做）。
3. 若改 `me` 或错误体，在 RESULT 里写明给 agy 的破坏性变更说明。

## Explore v1.1 addendum
- **D23/BE-13**：未知 hash 路由勿静默回首页 → 友好 404
- **D22/BE-14**：me/bootstrap 当前对局过滤 FINISHED
- **D08/D14/D24**：resultText / 时长 / 棋种枚举中文化
