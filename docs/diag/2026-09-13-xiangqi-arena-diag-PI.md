# 轻棋局 Public Product Diagnostic Report — Pi（逻辑 / 后端 / 架构）专项

- 诊断日期：2026-09-13
- 诊断者：Pi（逻辑/后端/架构/数据与规则）
- 产品：轻棋局 XiangqiArena — https://www.xiangqiarena.com
- 关联仓库：https://github.com/Zzy-min/Chinese-chess
- 交付文件：`/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-PI.md`

> 本报告为 **Pi 专项**（Logic / Backend / Architecture / Data & Rules）。前端/UX/Mobile/A11y 结论由 agy 专项覆盖（见 agy 报告）。交叉问题已按 brief 标注「需 agy 联合」。

---

## 1. Executive Summary（Pi 视角）

本轮以真实用户旅程驱动，我实际访问了公开站点并阅读了其**已确认关联的公开仓库**（`Zzy-min/Chinese-chess`，README 明确声明生产域名 `https://www.xiangqiarena.com/`，路由/接口/资源路径与线上完全一致），对规则引擎、状态机、房间并发、鉴权、WebSocket、持久化、错误模型进行了交叉分析。

核心对局链路：**可正常完成**。服务端是权威源，中国象棋规则引擎内置且规则齐全，房间/落子在单进程内用 `synchronized` 串行化防竞态，走子由服务端校验（回合、合法性、将死、超时），客户端只做展示与格式校验——架构正确。

发现统计：
- P0：0
- P1：3
- P2：5
- P3：5

最高优先级（Pi 视角）：
1. `GAME`/`ROOM` 只读接口鉴权缺口（信息泄露，虽被不可猜 UUID 缓解）——修 REST 与 WS 鉴权不对称。
2. 活动对局/房间仅存内存，Java 源站重启即丢失在局状态；WS 无心跳/自动重连，网络抖动或代理断链后对局实时同步静默失效。
3. 后端错误契约不标准（未授权/不存在返回 200 + null/{}/404 混杂），导致客户端无法区分“未登录/不存在/服务异常”。

最值得保留：
1. 服务端权威 + 完整且正确的中象规则引擎；`isValidMove` 用试下法阻止“将被将把己方/暴露”与送将。
2. 房间/关联机的并发控制（`synchronized(room)`、`synchronized(game)`、双检 quick-match）与 `stateId` 版本去重。

关键限制：所有本次诊断均为静态源码 + 只读 HTTP 观测；未真机下载对局、未注册账号联机下棋（避免扰线），因此在局时/规则正确性的最终用户体验需下一轮真机回归补充。

---

## 2. 测试范围与环境

| 项 | 内容 |
|---|---|
| 站点访问 | `GET https://xiangqiarena.com` → 301 → `https://www.xiangqiarena.com/` → 302 → `/online#/home` |
| 读取对象 | `/online/index.html`、`/online/assets/site/app.js`(228KB)、`app.css`(215KB)、`board.js`、PUBLIC API 若干 |
| 线上只读 API | `/online/api/site/bootstrap`、`/community/leaderboard`、`/lobby/overview`、`/lobby/search?q=z`、`/auth/me`、`/rooms/{id}`、`/games/{id}`、`/online/ws`(无鉴权握手) |
| 源码 | GitHub Zzy-min/Chinese-chess（Java），`src/main/java/com/xiangqi/**`，资源 `schema.sql`；共 509 文件，通过 raw/API 读取，**未 git clone** |
| 环境 | 无法纳入生产系统的压力测试；只允许普通 Web 只读观察 |

---

## 3. 事实边界与限制

- 仓库关联程度：**已确认**（README 显式声明生产域名与部署路径一致；线上 `/online/assets/site/*`、`/online/api/*`、`/online/ws` 与 `PublicSiteServer.java` 路由一一对应；线上接口返回 JSON 结构与源码一致）。
- 但我未将“仓库内容”逐字节对照“线上运行版本”，可能存在**轻微版本漂移**；对已上线行为，我以线上 API 实测为准，对规则/内部实现以源码为准并标“已验证/推断”。
- 我**未注册/登录、未创建/加入房间、未实际下棋**（避免写入生产数据），因此部分仅能靠源码 + 只读端点推断。
- 未做真机浏览器测试、未做被量、未做多标签实测。

**是否区分“已验证 / 合理推断 / 未知 / 未验证”**：本报告的每条结论都严格带上该标注。

---

## 3. 事实边界与限制（补充）

（上节已含；此处补充）：

- 已读取并分析的关键文件：
  - `web/PublicWebMain.java`、`web/PublicSiteServer.java`（线上后端，63582B）
  - `online/server/OnlineRoomHub.java`、`online/server/OnlineStore.java`、`online/server/RateLimiter.java`
  - `online/game/XiangqiMatch.java`、`OnlineMatchEngine.java`、`GameClock.java`
  - `online/room/RoomService.java`、`InMemoryRoomRepository.java`
  - `online/auth/AuthService.java`、`PasswordHasher.java`、`AuthSessionRepository.java`
  - `model/Board.java`（规则引擎，23.6KB）、`Piece.java`、`Move.java`
  - `AUTH_BUG_ANALYSIS.md`（2026-04-14 历史文档）
  - 线上 `app.js`（客户端，用于后端契约对照）

---

## 4. 用户旅程结论（Pi 视角）

| Journey | Result | 逻辑/后端摩擦 | 证据 | Confidence |
|---|---|---|---|---|
| 首访理解 | Pass | 说明：SPA `#/home` 首屏中文“轻棋局 Online”+“开始对局”（参见 agy）。后端打通 | bootstrap 200 | High |
| 登录 | Pass* | `auth/me` 未登录返回 `200 + null`（非 401）；客户端容忍。注册/登录 API 态 400（见 ISSUE）| `auth/me` 实测 | High |
| 创建对局 | Pass（需登录）| create 需登录（401 未登录）；限速 12/min | 源码 | High |
| 加入对局 | Pass | join 需登录；`synchronized(room)` 防满员竞态 | 源码 | High |
| 完成一盘棋 | 可分步骤，但未实测一盘 | 移动→服务端校验→权威快照→WS 推送，链路成立 | 源码+实点 | Medium |
| 异常恢复 | Partial | WS 无心跳/重连、模块重启失在局、错误契约含混 | 源码 | High |
| Mobile | 未测（agy 主责）| — | — | — |

---

## 5. Severity Summary

| Severity | Count | 核心影响 |
|---|---:|---|
| P0 | 0 | — |
| P1 | 3 | REST 只读鉴权缺口、在局状态非持久/重启丢局、WS 无心跳重连 |
| P2 | 5 | 错误契约、Cookie/安全头加固、用户搜索暴露 user 枚举、each move 冗余请求、弱用户名校验 |
| P3 | 5 | 不存在的 game 返回 200+{}、ws 无鉴权握手返回 500、可访问性/结果、小交互边界、文案自动化 |

---

## 6. Issue Overview（总表）

| ID | 严重度 | Journey | 问题 | Owner | 状态 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| PI-01 | P1 | 对局/联机 | `/online/api/games/{id}`、`/rooms/{id}` 只读接口未鉴权，泄露私房/在局盘面 | Pi | 已验证 | Fix Now |
| PI-02 | P1 | 对局/联机 | 活动房间/对局仅存内存，Java 源站重启即全部丢失导致在局无法继续 | Pi | 已验证 | Fix Now |
| PI-03 | P1 | 对局/联机 | WS 无心跳/自动重连，网络/代理断线后对局实时更新静默失效 | Pi | 已验证 | Fix Now |
| PI-04 | P2 | 认证 | `auth/me` 未登录返回 200+`null`（应 401）；不存在 game 返回 200+`{}`；错误语义不统一 | Pi | 已实测 | Next |
| PI-05 | P2 | 安全 | 鉴权 Cookie 缺 Secure/SameSite；无 CSP/HSTS 等安全响应头 | Pi | 已验证 | Next |
| PI-06 | P2 | 首访/信任 | 公开 lobby 搜索与排行榜暴露用户 UUID + 用户名；无用户名内容校验（可含辱骂词） | 联合 | 已实测 | Next |
| PI-07 | P2 | 性能 | 每次走子后客户端额外拉取 bootstrap+dashboard（每步约 3 个请求）| 联合 | 源码 | Later |
| PI-08 | P2 | 认证 | 密码最小长度仅 8 且无强度校验；无封禁/锁定；限流以 IP 维度 | Pi | 源码 | Later |
| PI-09 | P3 | 错误 | 无鉴权 WS 握手返回 HTTP 500 而非干净拒绝 | Pi | 已实测 | Backlog |
| PI-10 | P3 | 错误 | 房间不存在 GET 返回 404；game 不存在返回 200 {}，不规范 | Pi | 已实测 | Backlog |
| PI-11 | P3 | 对局 | `game/viewerSide` 未登录观察者推断为空，棋盘翻牌逻辑不透明 | 联合 | 源码 | Backlog |
| PI-12 | P3 | 对局 | 复盘/analysis 构造的 move index 依赖 replay 连打，未对 `stateId` 校验缝隙做防御 | Pi | 源码 | Backlog |
| PI-13 | P3 | 对局 | 训练 AI 局在 bootstrap recentGames 中出现长期 PLAYING 状态的孤立记录 | 联合 | 已实测 | Backlog |

---

## 7. P0 Issues

当前未发现真正 P0：核心对局能在服务端权威下完成，规则正确，无高危可利用（UUID 不可枚举），无会阻塞多数用户且无绕过的问题。若存在「多实例/横向扩容」导致的房间状态跨实例不一致风险，本部无法从公开信息确认（见 §19），不作为 P0。

---

## 8. P1 Issues

### ISSUE PI-01 — 只读对局/房间接口未登录即读，私房在局状态可被读取（REST 与 WS 鉴权不对称）

- 严重度：P1
- Owner：Pi（后端鉴权）
- 状态：已验证事实
- 用户旅程：对局 / 信任 / 联机
- 影响平台：全部
- 影响用户：所有玩家（任何知道 UUID 者）

**问题描述**
`GET /online/api/games/{gameId}` 与 `GET /online/api/rooms/{roomId}` 不要求登录/不校验成员资格即可返回完整对局（含实时盘面 `board`、双方用户名、当前回合、剩余时间）与私房信息（host/guest 用户名、状态、房间码）。而同一服务的 **WS subscribe** 却通过 `canSubscribeRoom` 仅允许房主/双方/公开房订阅——形成「HTTP 只读 vs WS 实时」鉴权不对称。理论上任何人只要拿到 gameId/roomId 就能读私房与在局状态。

- 前置条件：
  - 未登录 curl 直接 GET
- 可复现步骤：
  1. `curl 'https://www.xiangqiarena.com/online/api/games/<某真实gameId>'`（无 Cookie）
  2. 观察到返回完整 `board`/`players`。
  3. 注意公开 bootstrap/lobby 已把 UUID 等 ID 公开展示（见 PI-06），ID 可能通过分享、复盘、观战被传播。
- 实际结果：无鉴权可达完整盘面/成员/时钟。
- 期望结果：游戏 GET 至少要求登录；私房与在局对局应仅对参与者 + 授权的观战者可见（与 WS `canSubscribeRoom` 保持一致）。
- 用户影响：私房对战、私人信息（用户名、实时盘面）有可能被第三方读取；降低了用户“房间是否公开”的心理预期与信任。
- 影响范围：全部用户；尤其使用「非公开房间」的用户。
- 证据：`web_PublicSiteServer.java` 中 `handleGameById`/`handleRoomById` 未校验 auth；线上实测 GET 任意真实在局 gameId 200 返回盘面（产物 `EVID-DIAG-01`）。
- 技术分析：
  - 已验证：`handleGameById` 传入 `currentUser(...).orElse(null)`，仅用于 `viewerSide`，不做授权判断；`handleRoomById` 直接用 `roomSnapshotById`，不校验。
  - 推断：gameId 为 UUIDv4 不可枚举，故“实际被读取”的门槛较高；但「可读私房/在局盘面」本身违反最小权限和用户预期。
  - 未知：生产是否对 `Cloudflare` 缓存/限流了这些 GET（实测未缓存，DYNAMIC）。
- 修复方向：抽一个 `requireJoinRoom`/`requirePlayer`（网上已有 `ensureParticipant`，GET 端复用）；matches the code path that already exists in WS layer. 对公开/在局观战走单独 `?`。
- 依赖：后端 GET handler 鉴权；观战路径（watch public）与私有路径拆分。
- 验证方式：未登录 GET 私房/在局 game → 期望 401；登录且参与 → 200；观战对公开房 → 200。

---

### ISSUE PI-02 — 活动房间/对局仅内存持有，Java 源站重启即在局丢失

- 严重度：P1
- Owner：后端/架构
- 优先级：Fix Now
- 状态：已验证事实
- 用户旅程：对局 / 异常恢复
- 影响平台：全部

- 问题描述：`ActiveRoom`（roomsById/roomsByCode）与 `ActiveGame`（gamesById）仅存于 JVM 堆内存 `ConcurrentHashMap`；`games`/`game_moves` 表只持久化对局与走子历史。一旦 Java 源重启/滚动更新，所有**进行中**的房间、对局、时钟、ready/席位即从内存清零：
  - `room()`/`game()` 找不到 → `throw IllegalStateException("room/game not found")`
  - 用户刷新后 `gameSnapshotById` 回到 `store.loadGameAnalysis` 只能重建**已经落盘的招式/状态**，但 `applyMove` 对不在内存的 game 抛 “game not found”，**在局用户无法再走子**；房间也无法再继续。
- 前置条件：Java 进程重启；或运维发布、崩溃、单机冷启动。
- 实际结果：所有在局对局与等待/进行中房间失效；用户刷新后得到历史的复盘片段但无法续战；WS 也断。
- 期望结果：在局对局具备可恢复路径（持久化的 `games`/`game_moves` 可重建 `ActiveGame` 并以 `stateIn` 续走）或在生存期内部控制更新窗口；且需提供 reconnect 恢复机制。
- 用户影响：任何登录玩家处于「进行中对局」时，源站重启即丢局，需重开；若频繁部署/崩溃则影响大。
- 影响范围：对局中的用户；在等待/等待加入的房间成员也会失效（room 丢失 join 后再进不行）。
- 证据：源码 `OnlineRoomHub.game()` 直接抛 “game not found”；`gameSnapshotById` 对缺失 game 走 `store.loadGameAnalysis`（历史旧副本）。线上复现需写入在局+重启，未执行（避免破坏生产），基于源码充分确认 root cause。
- 技术分析：
  - 已验证：`gamesById`/`roomsById` 为内存 map；`store` 只存储 `games`/`game_moves` 记录。
  - 推断：生产若单机源 + CF 前置，源重启即可触发；若源站常活则间隔长。
  - 未知：生产 CF 前置多副本/会话粘性如何实现、是否有热旧常驻保持内存。
- 修复方向：提供「从 `games`+`game_moves` 重建 `ActiveGame`」的 recovery（replay move list 到 `XiangqiMatch` 并重建时钟）；或在启动后把 status=PLAYING 的存档角色切到“复盘”并引导新局；配合 WS 自动重连恢复。
- 依赖：持久化恢复；计时器重建；房间是否重建。
- 验证：重启源站后仍能对同一 gameId 走子（重建成功），或明确回退（不更糟）。

---

### ISSUE PI-03 — WS 无心跳 / 无自动重连

- 严重度：P1
- Owner：后端 + 前端（裸牵手）
- 优先级：Fix Now
- 状态：已验证事实
- 用户旅程：实时同步 / 断线恢复
- 影响平台：所有在联机 rooms 的用户

- 问题描述：
  - 客户端 `state.ws.onclose = () => { state.ws = null; }`，无 backoff 自动重连。
  - `syncRealtime` 仅在路由切换（进入/回到对局页）时被调用；若用户停留对局页且 WS 被远端（Cloudflare/代理空闲超时、弱网、网络切换）断开，则不会重建连接。
  - 服务端/客户端均无 heartbeat/ping-pong/keepalive。
- 复现场景：用户在对局页持续超过代理空闲窗口；切换网络 Wi→LTE；后台切回（部分移动浏览器冻结）。
- 实际结果：对手走子不再实时出现；对局停止更新（只能靠刷新/REST，或对手打出移动时间点才更新）。
- 期望结果：WS 断线后自动重连（带退避），并按 `stateIn`（已有版本号机制）+恢复公告工作；或提供 HTTP fallback 轮询。
- 用户影响：弱网、移动端、长时间挂机用户易出现「卡住不动、实际对手已走」的不一致感；是决定性恢复关键路径缺陷。
- 证据：`app.js` `state.ws.onclose` 空处理；无 `ping/pong/heartbeat` 代码（前端 `rg` 无结果）；`PublicSiteServer` 无 `PingFrame`。
- 技术分析：
  - 已验证：无重连定时器；无心跳。
  - 推断：上面机制一旦断会更重依赖 REST 走路 + 每步刷新 bootstrap 来间接恢复同步，但不可靠。
- 修复方向：接入心跳 + 指数退避重连（复用已有 `state&gt;` 与 `stateId` 校验），重连后 `syncRealtime` 重新 subscribe 并 `fetchJson(games/{id})` 对账；给用户明确「重连中/已断线」状态。
- 依赖：已有 `stateId` 排序逻辑可复用。
- 验证：取消网络 10s 再恢复，对局仍自动续同步，无 UI 冻结。

---

## 9. P2 Issues

### ISSUE PI-04 — 错误/鉴权契约不统一

- 严重度：P2；Owner：后端；优先级：Next；状态：已实测
- 现象：
  - `/online/api/auth/me` 未登录返回 **HTTP 200 + body `null`**（应为 401）
  - `/online/api/games/<不存在>` 返回 **200 + `{}`**（应为 404）
  - 房间不存在返回 404（两点不一致）
  - 登录失败 / 非法走子统一返回 **400 BAD_REQUEST** 且把内部异常消息直接透出（如 "room is full"、"game already finished"）
- 用户影响：客户端无法区分未登录/不存在/服务错误；错误文案可能暴露内部状态；自动化（前端行为判断）不可靠。
- 技术：客户端已在 `fetchJson` 中 `throw new Error(data.error)`，能显示给用户；但状态 200/400 不可靠。
- 修复方向：错误模型——401 未登录、403 无权限、404 不存在、409 冲突、429 限流、400 参数，并将内部 error 映射为面向用户的 message（不泄露 stack/实现）。
- 验证：未登录 `me`→401；不存在 game→404；未参与方对局→403。

### ISSUE PI-05 — Cookie 缺 Secure/SameSite；安全响应头缺失

- 严重度：P2；Owner：后端；状态：已验证源码
- 事实：`setAuthCookie` 设 `HttpOnly` + `maxAge`，**未设 `Secure`、`SameSite`**；`PublicSiteServer` 无 CSP、HSTS、X-Frame-Options、X-Content-Type-Options、Referrer-Policy。线上实测响应头无此类安全头。
- 风险：整站 HTTPS（CF），Secure 缺失风险较轻；无 SameSite 默认值未必等 Lax（Undertow 默认不加），存在 CSRF 隐患面（基于 Cookie 的会话）；无 HSTS 增加用户首次降级面。属加固级基础问题。
- 修复方向：`cookie.setSameSiteMode(SameSiteMode.LAX)`（或 Strict）+ 条件 `setSecure(true)`；在起点加安全响应头（至少 HSTS + X-Content-Type-Options + frame）。
- 验证：`curl -I` 可见相应头。

### ISSUE PI-06 — 公开用户枚举 + 弱用户名合法校验

- 严重度：P2；Owner：联合（后端 API + agy 文案/校验）
- 状态：已实测；优先级 Next
- 事实：
  - `/online/api/lobby/search?q=z` 未登录返回一批 `{id(UUID), username}`。
  - `LOADING_LEADERBOARD`（公放）也包含 `userId` 完整 UUID。
  - 用户名允许任意字符串包含侮辱、无白名单/长度上限约束（线上已见含辱骂实例）。
- 影响：爬可枚举、可收集用户 UUID+昵称；低信任/冒犯昵称伤害社区与网络安全页观感。
- 修复方向：对外 API 去 `userId` 或暴露短暂哈希；注册时校验昵称内容/长度/字符集；提供昵称修改 & 上报。
- 验证：搜索/排行榜不再返回明文 UUID；含辱骂字符注册被拒绝。

### ISSUE PI-07 — 每步走子后额外 3 次请求

- 严重度：P2（性能）；Owner：联合；状态：源码推断
- 事实：`sendMove` 成功到 `refreshBootstrapAndProfile()` → 再发 `/site/bootstrap` + `/profile/dashboard`；加上 move POST，即每一步至少 3 个 HTTP。
- 影响：快速连走时对移动/弱网更明显；浪费带宽、增加服务端。
- 修复方向：move 后按需刷新（仅在确实影响全局统计时）；合并 bootstrap/dashboard 为一次 `/profile/dashboard`；或 WS 推送带概览增量。属优化向。
- 验证：DevTools Network 对移动逐步提交应只有 1 个 move 请求。

### ISSUE PI-08 — 密码/账号安全基线较弱（P2）

- 严重度：P2；Owner：后端；状态：已验证源码；优先级 Later
- 事实：`validatePassword` 仅长度≥8；`authLimiter` 8 次/分钟/IP，**按 IP 而非按账号**（NAT/CGN 会误伤同公网多用户）；无账号锁定/重试爆仓。
- 影响：密码猜测受限（8/min/IP），但共享 IP 的校园/企业网会被限流造成误伤（429）；密码强度低。
- 修复方向：按 username+IP 分组限流；加密码复杂度与常见密码黑名单；可叠加机会量 hcaptcha。
- 验证：同 IP 下不同用户重复登录不互相触发。

---

## 10. P3 Issues

- **PI-09**：无鉴权 WS 握手 curl 返回 HTTP 500（应干净拒绝）。已实测 2026-09-13。Owner：后端/CF 接线。
- **PI-10**：`/games` 不存在返回 200+{}（同 P4 一并归一）。
- **PI-11**：未登录观察者 `viewerSide` 推断为空、翻转逻辑不确定（后端契约 + 前端展示，联合）。
- **PI-12**：`analysis`/复盘用 `replay` 连续 `applyMove` 重建棋盘，无对 `stateId`/用途数量约束，防御弱、静态可演化失真（低风险）。
- **PI-13**：bootstrap recentGames 中出现长期 `PLAYING` 的 AI 训练局（在线实测发现数日前 “PLAYING” 训练对局），说明训练局没有会被清理的机制，长期堆积游数据。

---

## 11. 产品亮点（有依据）

### HIGHLIGHT-001 — 服务端权威 + 完整且正确的中国象棋规则引擎
- 事实状态：已验证（源码 `Board.java`）
- 用户价值：不信任客户端，规则正确可移植；防止非法走子、蹩马腿、塞象眼、将帅照面、自照将（`isValidMove` 用试下盘面 `isInCheck` 阻断自己进入被将军）全部在服务端校验；回合由服务端强制。
- 证据：`XiangqiMatch.previewMove`→`applyMove`（先预览后提交）；`Board.isValidMove` 含 `testBoard.isInCheck`；`src/test/com/xiangqi/online/XiangqiMatchTest`（示例可见）。规则正确性还经 `Board` 内 `countPiecesBetween/isValidPaoMove/isValidZuMove` 一一实现。
- 为什么值得保留：这是全产品最值得保护的核心资产；对局可信度高。

### HIGHL-002 — 房间/对局单机内并发控制到位
- 事实状态：已验证
- 价值：防重复加入、防满员、防 rematch 同时接受；quick-match 双检（锁内再校验）。
- 证据：`OnlineRoomHub` 中大量 `synchronized(room)`/`synchronized(game)`；quick-match `canQuickMatch` 锁内重检。

### HIGHLIGHT-003 — `stateId` / 对局版本号
- 事实状态：已验证
- 价值：客户端 WS 收到多个快照时按 `stateId` 排序 / 丢弃旧 —— 提供天然的重复消息与乱序恢复基础（虽然重连不自动）。
- 证据：`XiangqiMatch.stateId` 自增；客户端 `state` WS 处理 `incomingStateId<=currentStateId` skip。

### HIGHLIGHT-004 — 分权限观战与私房（WS 层做对）
- 事实：WS `canSubscribeRoom`：公开房任意登录者可看，私房仅参与者可订阅。
- 价值：给“直播/观战”与“私密”两者提供正确边界（虽然对 REST 未对齐，见 P-01）。

### HIGHLIGHT-005 — 轻量加载
- 事实状态：已验证线上
- 证据：app.js gzip 后约 52KB compress，CSS 215KB，initial App 3/3 single-page；bootstrap TTFB ~0.2s；静态资源 TTFB ~0.04s（CF + Gzip）。
- 用户价值：首屏《首访 5 次要》足够不必担心。

---

## 12. Pi — Logic / Backend / Architecture findings（专项）

### 系统状态模型（已验证 / 推断）

```
未登录（可浏览 public lobby/bootstrap/排行榜/观战）
  ↓ login/register（cookie XQ_AUTH 14 天）
已登录（Auth）
  ↓ 创建(REST) / 快速匹配 / 房间码加入
Room（WAITING/FULL/PLAYING/CLOSED；host/guest）   -- 内存
  ↓ 双方 ready（Quick match 自动 Ready）
Game（IDLE→PLAYING→FINISHED）                    -- ActiveGame 内存，(存 DB 记录)
  -- 权威状态：服务端
  -- 走子：同步 + synchronized
  ↓ FINISHED（将死/超时/认输/法国 ai / rematch→下一局）
Watch（公开房实时 / archived 复盘）
```

明确标注：
- 已验证：创建/加入/就绪/开始/走子/结束/复盘、房间状态枚举、对局状态（PLAYING/FINISHED）。
- 推断：跨实例/网点仅单一源站（见 19）。
- 未知：多标签页（tab）创建重复房间是否会自动防（我看创建有 8 位 codes、`roomCode `存在，但多 tab 各自创建不同房间，不冲突，但没有“快速复用现有房间”逻辑，可能造成多个顿 room）。

### 走移动闭环
```
用户点击 → POST /games/{id}/move (fromRow/Col,toRow/Col)
  → 服务端 sure && 回合 && previewMove(合法) && engineer → stateId++
  → in-memory 状态更新 + store.appendMove
  → wsHub.broadcastRoom(roomEvent) → 对手 + 自己实时收到
  → 客户端以 HTTP 快照断言 + stateId 去重
```
风险节点：room vs ws 并发发布、stateId 参数化误差、重启（见 P-02）、重连（P-03）。

### 数据权威性分析（17.3）
| 问题 | 结论 | 标注 |
|---|---|---|
| 谁背书状态 | **服务端**（ActiveGame.engine/Board；DB games）| 已验证 |
| 当前回合由谁决定 | **服务端**（`engine.currentTurnKey`）| 已验证 |
| 合法走子由谁判断 | **服务端**（`XiangqiMatch.preview`→`engine.apply`）+ `Board.isValidMove`| 已验证 |
| 胜负由谁 | 服务端 engine.finished/winnerSide/resultText | 已验证 |
| 客户端持有多少可信逻辑 | 仅展示+列表校验/偏好，无权威规则 | 已验证 |
| reconnect 恢复 | 走 HTTP 重建（复盘），但步进无法续写（P2-P1）| 身 |
| 版本/自增 | **有** `stateId`；加 ws 去重 | 已验证 |
| 多实例一致 | 否（单内存源）| 未知/高风险 |

### API / 错误观测（17.4）
| Purpose | Endpoint | 结果 | 备注 |
|---|---|---|---|
| bootstrap | GET /site/bootstrap | 200 | 公开 |
| me | GET /auth/me | 200+null（未登录）| — |
| register/login/logout | POST | 400/401/200 | 限流 429 |
| room 快照 | GET /rooms/{id} | 200（未登录存在）| 潜在鉴权缺口 |
| game 快照 | GET /games/{id} | 200 无需鉴权 | 潜在鉴权缺口 |
| move | POST /games/{id}/move | 200（走就返回快照）| 需登录 |
| ws | /online/ws | （登录/AUTH）| 无心跳 |

- 错误：login Failure 400；room full 400；`game not found` 400/404 不等瓦尔。

## 17.5 可靠性风险 Top 5（有依据）

1. `Proces restart → 所有在局丢`（PI-02）
2. `WebSocket 无心跳/重连 → 断线后对局停更`（PI-03）
3. `只有幂 3 个请求/走子 + 全页刷新`（PI-07 可导致……不完全)
4. `子主端缓存/私有 GET 可读`（PI-01）
5. `Game/rooms ID 不可猜测（UUIDv4）` → 实际路径真，优先级 p1 降级为 P1 而非可自动利用 v2.

---

## 18. 修复优先级矩阵

| Issue | 修复方向 | Owner | 依赖 | 工作量 | 风险 | 验证 |
|---|---|---|---|---|---|---|
| PI-01 | GET 端复用 `canSubscribe/参与者` 鉴权 | 后端 | — | S | 低 | 未登录 GET 361；参与人 200 |
| PI-02 | 从 games+moves 重建 ActiveGame；续走 | 后端 | recovery | L | 中 | 重启后可行棋 |
| PI-03 | 心跳+指数退避重连+对账 | 后台+前台 | stateId 现有 | M | 中 | 断线自动恢复 |
| PI-04 | 统一错误码/映射 | 后端+前端(文案) | — | S | 低 | 各错误返回正确码 |
| PI-05 | Cookie Secure/SameSite；HSTS | 后端 | CF | S | 低 | header 检查 |
| PI-06 | 屏蔽 userId；昵称校验 | 后端+前端 | — | S | 低 | 对应 UI 不再返回 id |
| PI-07 | 合并刷新 | 前端+后端 | — | S | 低 | 每走 1 请求 |
| PI-08 | 按账号限流；强密码 | 后端 | — | M | 低 | 爆卷/锁定测试 |

---

## 19. 未验证 / 需要权限或后续验证

| 项目 | 当前状态 | 无法验证原因 | 所需条件 |
|---|---|---|---|
| 多标签页（同账号两标签同时下同局）——最终一致性 | 未验证 | 未下真实此局 | Staging | 真实两客户端 |
| 在局中重启后能否恢复（真实操作） | 未验证（源码推断可丢） | 不可破坏生产 | 后台 verify |
| 服务端对“多 IP 相同账号/多实例”一致性 | 未知 | 不可压测 | 架构确认 |
| 完整规则达人：真实对局将帅照面/蹩马/象眼 | 未验证 | 未下完整一盘 | 真实对局 |
| 移动真机体验 | 未验证（服务器/界面） | 专 agy | 真机 |
| 生产部署形态（单机/多副本） | 未知 | 需内部 | 运维 |
| CSRF 实际利用面 | 未验证 | 不做攻击性测试 | 需安全专项 |
| copy Cookie 的强度（HttpOnly 已设；`Secure` 缺失）| 未验证| 未做攻击 | 需专项 |
| CPF代理对 WS / 长连接的保持时长 | 未验证 | 需真实 hold | staging |

**已遵循 强制停止条件**：未发现需要输入他人密码 / 绕过 / 利用漏洞的场景；只在只读 GET 上验证，未写生产数据。未在报告粘贴任何 token/密码/cookie 值。

---

## 20. 下一步建议（Pi 建议序列）

### Phase A — 阻塞/一致性
- PI-01（后端鉴权收敛到 WS 一致）、CI-02（在局恢复）。

### Phase B — 可靠性
- PI-03（WS 心跳+重连+with 对账）、PI-04（错误模型）。

### Phase C — 安全/隐私基线
- PI-05（Cookie/安全头）、PI-06（去 UUID、昵称校验）、PI-08（账号级限流+认证强度）。

### Phase D — 性能与工程
- PI-07（合并刷新）、PI-12/13（复盘数据防性、废弃训练局清理）。

### Phase E — Quality Gate（回归清单建议）
Golden Path：
1. 未登录 → 浏览 public → login/register → 创建房间 → 第二账号加入 → 开始 → 中断 20+ 合法走子 → 结束（将死）→ 结果正确、复盘一致。
Bad Path：
2. 非法走子（蹩/象眼/翻将/超时）→ 被拒且 UI 恢复。
3. 断线/重连；刷新；对手退出；重开。
4. 未被邀请者访私房 → WS 不允许 + REST 401/403。
5. 慢网络连走 10 步 → 对局一致。

---

## 附：证据快照（脱敏，不含敏感值）
- `WEB-001` 首页 HTML：`<title>轻棋局 Online</title>`，`lang=zh-CN`，`/online/#/home`。
- `NET-001` bootstrap：`{"activeRooms":24,"totalUsers":97,"totalGames":513,...}`（仅用统计）。
- `NET-002` `GET /online/api/games/<id>`（无 Cookie）→ HTTP 200，body 含完整 `board`/`players`（已确认；不贴原始盘面）。
- `NET-003` `GET /online/api/auth/me` → 200 `null`。
- `NET-004` `GET /online/api/lobby/search?q=z` → 返回 user UUID+username 列表。
- `WS-001` `curl` 无授权 WS 握手 → HTTP 500。
- `PERF-001` app.js gzip 52KB、TTFB ≈0.04s；bootstrap TTFB ≈0.2s。
- `CODE-001` `PublicSiteServer`：`handleGameById`/`handleRoomById` 无鉴权。
- `CODE-002` `OnlineRoomHub`：rooms/games 仅内存 map；`game()` 抛 “game not found”。
- `CODE-003` 客户端 `ws.onclose` 空处理，无重连/心跳。
- `CODE-004` `Board.java` 完整中象规则 + `isInCheck` 预检。
- `CODE-005` `setAuthCookie` 无 `Secure`/`SameSite`。
- 附：仓库根存在历史 `AUTH_BUG_ANALYSIS.md`（2026-04）记录的旧 WS/认证 bug，**已在当前 main 段修复**（WS 已加 token 校验），故不作为当前问题重复上报。