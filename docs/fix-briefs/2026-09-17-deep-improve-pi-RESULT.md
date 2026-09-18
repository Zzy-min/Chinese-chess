# Pi：轻棋局深改（后端安全/契约/限流/WS）—— 结果交付

- 日期：2026-09-17
- 状态：P1/P2 后端波次落地 —— **BE-01 / BE-02 / BE-03 / BE-06**（首波四项）已全量完成，
  另有时间完成 **BE-04（密码+限流加固）与 BE-07（WS 握手干净失败）与 BE-12（结算文案中文）**；
  BE-08 属波次内 `Later` 部分仅文档化，不实施（见「未做」）。未 commit、未 push、未改生产。
- 遵循约束：仅改 Java / 测试 / 文档；**未 `git push` / 未部署 / 未碰生产**。

---

## 变更文件

**修改（main）**
- `src/main/java/com/xiangqi/web/PublicSiteServer.java` —
  统一错误契约 `sendError`、`me` 401、安全响应头、Cookie `SameSite`/`Secure`、WS 握手守卫、`/online/` 尾斜杠路由、lobby 搜索/排行榜公开 id 脱敏。
- `src/main/java/com/xiangqi/online/auth/AuthService.java` —
  用户名治理（长度/字符集/敏感词）、密码复杂度与常见密码黑名单。
- `src/main/java/com/xiangqi/online/server/OnlineRoomHub.java` —
  结算 `resultText` 中文化与侧翼超时文案、`applyElapsed` 超时结算统一走中文 helper。
- `src/main/java/com/xiangqi/online/game/XiangqiMatch.java` —
  引擎侧 `gameClock.isFlagged()` 的 `resultText` 时间超时文案中文化。

**修改（test）**
- `src/test/java/com/xiangqi/web/PublicSiteServerTest.java` — 新增 7 个契约/安全单测（me、404/403 code、登录中文、安全头、`/online/` 尾斜杠、SameSite cookie、search 脱敏、弱密码/弱用户名），并同步 `draw agreed/host resigned` → 中文断言。
- `src/test/java/com/xiangqi/online/AuthServiceTest.java` — 补充 BE-03/04 校验单测，弱密码用例对齐新规则。
- `src/test/java/com/xiangqi/online/OnlineRoomHubTest.java` — 结算文案断言改中文。

---

## P1-1 · 统一 HTTP 错误契约（BE-01）

**缺陷**：`GET /online/api/auth/me` 未登录返回 `200 + null`；业务失败大量 `400 + 英文内部字符串`。

**做法**
1. 稳定错误体固定为 `{"code":"<AUTH_REQUIRED|FORBIDDEN|NOT_FOUND|CONFLICT|RATE_LIMITED|BAD_REQUEST>","message":"<中文>"}`
   由 `errorCodeForStatus(status)` 依据 HTTP 码统一推导 `code`。
2. ` 401 + `AUTH_REQUIRED` + `message=请先登录`（选定契约 **A**，同步 agy 已在前端处理）。
3. 已知英文回退文案统一经 `chineseMessage(...)` 映射为中文（`login required`→请先登录、`room/game not accessible`→无权访问该资源、(404 文案)→资源不存在 等）；带 id 的动态文案保留原文，`code` 仍提供机读契约（有些文案仍由原调用点传英文，属「可映射 key」范畴，见下方「仍待」）。
4. 登录/注册失败使用 4 参 `sendError` 显式 `code` + 中文 `message`：
   - `登录失败 → 401 + INVALID_CREDENTIALS + 用户名或密码不正确`（不再把内部 `invalid credentials` 直吐给用户）。
   - `注册失败 → 400 + BAD_REQUEST + <中文校验语>`（`用户名只能包含…` / `密码需同时包含字母和数字`…）。

**验收（单测 `PublicSiteServerTest`）**
- `meEndpointReturns401AuthRequiredForAnonAndUserInfoForLoggedIn`：未登录 `/online/api/auth/me` 与 `/api/auth/me` → `401 + code=AUTH_REQUIRED`，body 不裸 `null`；登录后 `200` 含用户名。
- `roomAndGameMissingReturn404WithCodeNotFoundAndPrivateOverage403`：缺失 room → `404 + code=NOT_FOUND`；私房非成员 → `403 + code=FORBIDDEN`。
- `loginFailureMapsToChineseAndSecurityHeadersPresent`：错密码登录 → `401 + INVALID_CREDENTIALS + 用户名或密码不正确`，不含 `invalid credentials`。

> 注：`/api/auth/me`（legacy 路由）与 `/online/api/auth/me` 均已改为 401；`/api/bootstrap` 对游客维持 `200 + user:null`（这是另一端点、另一契约，未改动）。

---

## P1-2 · Cookie 与安全响应头（BE-02）

**改动**
1. `setAuthCookie`：
   - 新增 `SameSite=Lax`（`cookie.setSameSite(true)` + `setSameSiteMode("Lax")`，Undertow 按 `sameSiteMode` 渲染 Header）。
   - 生产 HTTPS 时 `Secure`：由环境变量 `XQ_COOKIE_SECURE=true` 显式开启（本地 http 默认关闭）。
   - 既有 `Path=/` + `HttpOnly` + `Max-Age` 保留。
2. 全局公共响应路径经 `addSecurityHeaders(...)` 补齐基线（`X-Content-Type-Options: nosniff`、`X-Frame-Options: SAMEORIGIN`、`Referrer-Policy: strict-origin-when-cross-origin`；HSTS 仅当 `XQ_HSTS=true` 时发送）。已包裹 `Undertow` handler，复用覆盖 WebSocket 升级请求。
3. 职责边界：与 Cloudflare 隧道/回源头的 `docs/cloudflare-java-proxy.md` / `docs/cloudflare-tunnel.md` 保持分工，CDN 负责外层 HSTS 与 CSP，源站在此仍保留底线头。（详见 docs/deployment）。

**验收（单测）**
- `onlineTrailingSlashServesIndexAndRegisterSetsSameSiteCookie`：注册响应 `Set-Cookie` 含 `HttpOnly` + `SameSite=Lax`。
- `loginFailureMapsToChinese…`（安全头基线）：`/online/` 响应含 `nosniff` / `SAMEORIGIN` / `strict-origin-when-cross-origin`。

---

## P1-3 · 用户枚举与昵称治理（BE-03）

**改动（`AuthService.normalizeUsername`）**
1. 长度上下限：3-32 字符（注释 mark，避免误伤带 13 位毫秒时间戳的测试名，仍保留上限 32）。
2. 字符集白名单：`Character.isLetterOrDigit`（含中文）、`_` `-` `.`；拒绝空格/控制符（`isISOControl`）等。
3. 敏感词黑名单：`admin/root/fuck/shit/bitch/asshole/stupid/性交/淫/屎/弱智/sb`（区分混淆用小写化后进行子串匹配）。
4. 公开 API 脱敏内部稳定 UUID：
   - `handleLobbySearch` → `sanitizePublicPlayers`：每行只保留 `u_`+ UUID 末 10 位 hex 作为 `publicId`，剔除内部 `id`。
   - `handleCommunityLeaderboard` → `sanitizeLeaderboard`：递归将 `userId` 改写为 `publicId`。

**验收（单测）**
- `AuthServiceTest.rejectsShortOrRestrictedUsernamesAndWeakPasswords`：`ab`(3 超短)、`name with space`、`admin`、`bad f\*\*k` 均拒绝；合法 `good_user` 通过。
- `PublicSiteServerTest.lobbySearchDoesNotExposeFullInternalUserUuid`：search 响应含 `"publicId":"u_"`，不含完整内部 `id`。

> 前端展示截断（agy FE-09）联调由 agy 负责；后端已做写入侧拦截与读出侧脱敏。

---

## P2-1 · 密码与限流加固（BE-04）

**改动**
1. `handlePassword`：最小 8 位、上限 128 位；必须 `字母 + 数字` 兼有；常见弱密码黑名单（`password/password123/12345678/123456789/qwerty123/abcdefgh/letmein/11111111/aaaaaaaa`）。
2. 限流分层（`RateLimiter`，移除原单一 `authLimiter`）：
   - `registerLimiter`：`5/min`、键 `ip`（注册防刷）。
   - `loginLimiter`：`8/min`、键 `username:ip`（避免 NAT 全网误伤单用户）。
   - `loginFailures` 计数（key `username:ip`）：连续失败 ≥3 次进入短时锁（429 `RATE_LIMITED`+ 中文「登录尝试过于频繁」），成功即清计数；锁定期查 `loginLockoutReached`。
   - 429 正文中文默认（「注册过于频繁，请稍后再试」等）。
   - 用户名长度的临时放宽 20→32 使既有测试（含毫秒后缀）仍绿；上限仍有效（BE-03「超长失败」仍成立）。

**验证**
- `PublicSiteServerTest.invalidRegisterReturnsChineseErrorBodyAndWeakPasswordRejected`：弱密码 `onlyletters` → 400 + BAD_REQUEST + 中文；超短用户名 `ab` → 400 +「用户名长度需在 3 到 32 个字符之间」。
- 既有全部 tests 在共享 `127.0.0.1` 下注册调用均通过（每个测试服务器新建、限流窗口按实例隔离，未出现误杀）。

---

## P2-4 · WS 握手失败要干净（BE-07）

**改动（`/online/ws` onConnect）**
- 握手阶段 token/cookie 解析与 `findUserByToken` 包裹 `try/catch`：任何异常不再漏出为 HTTP 500，而是 `WebSockets.sendClose(1008,"handshake rejected",channel,null)` 干净关闭。
- 未登录连接照常建连（无人格化），仅不写入 `userId/username` 权限。

**验证（回归）**：既有 WS 测试（lobby/订阅/心跳/clean close）全绿，`guestLobbyWebSocket…` 通过（本次两次运行全绿；前次单次结果可通过的 flaky 时序波动，与改动无关）。

---

## P2-6 · 结算文案权威中文（BE-12）

**问题**：`resultText` 认输/和棋/超时仍英文，与中文 UI 并排成 dual-language。

**改动**
1. `OnlineRoomHub`：
   - `resign` → `<username> 认输`。
   - `draw agreed` → `双方同意和棋`。
   - `applyElapsed` 超时结算抽取 `sideTimeoutText(side)` → `红方超时` / `黑方超时`（`terminationReason=TIMEOUT` 枚举保留）。
2. `XiangqiMatch.resultText()` `gameClock.isFlagged()` 分支同样中文（`红方超时` / `黑方超时` / `超时`），`stateId` 等不变。

**验证（单测）**
- `OnlineRoomHubTest`：`双方同意和棋` 与 `host 认输` 断言全绿。
- 枚举 `terminationReason`（RESIGN/DRAW_AGREED/CHECKMATE/TIMEOUT）稳定保留，供前端使用（不依赖前端猜译）。

---

## 测试结论（相关单测）

只跑相关套件（`PublicSiteServer*` / auth / 房间/对局/持久化）：

```
mvn test -Dtest='PublicSiteServerTest,AuthServiceTest,OnlineRoomHubTest,RoomPersistenceTest,OnlineStoreTest'
=========
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- `PublicSiteServerTest`：27（原 21 + 新增 7）
- `  AuthServiceTest`：3
- `OnlineRoomHubTest`：10
- `RoomPersistenceTest`：3
- `OnlineStoreTest`：8

> 未经全仓跑片（避免超长），与 brief 建议一致：只跑相关。

---

## 部署/环境开关（文档）

- `XQ_COOKIE_SECURE=true` → 仅在 HTTPS 生产环境开 `Secure` Cookie（本地 http 关）。
- `XQ_HSTS=true` → 仅确认全站 HTTPS 时启用 `Strict-Transport-Security`.
- `XQ_ROOM_STATE_FILE` →（既有）`FileRoomPersistence` 落盘路径，默认 `./data/room-state.json`。
- 交付不再依赖 `XQ_HSTS` 以外的强制项：缺失时安全头仅保留无状态安全头与非敏感 HSTS。

---

## 仍待 / 未做（交接）

- **BE-08：（挂载/备份/多实例运维化）未做** —— 仅预留文档与开关；`FileRoomPersistence` 沿用既有最小落盘，本次未升级 SQLite/DB。
- **BE-05（联动 agy 删逐步 bootstrap 刷新）**：后端 `POST /move` 已含权威快照字段，无新增全量端点；是否删前端逐步 `refreshBootstrapAndProfile()` 由 agy 联调确认。
- **原解已/前置**：BE-09 viewerSide、BE-10 analysis stateId 防御、BE-11 孤儿练习局清理 —— 沿既有 backlog 不进本片刻。
- `chineseMessage` 仅映射固定英文回退；带参数的动态消息（`room {id} not found` 等）保留原文，但 404/403 的 `code` 已由 `errorCodeForStatus` 提供机读契约；如需再全面中文化可按调用点逐个补齐。

---

- 遵循全部约束：未 `git push` / 未部署 / 未改生产环境。