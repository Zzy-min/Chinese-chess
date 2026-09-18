# 轻棋局深改 Backlog（2026-09-17）

- **产品**：轻棋局 / XiangqiArena（`xiangqiarena.com`）
- **代码仓**：`/workspace/Chinese-chess`（`main` ahead 1：`0245638`）
- **性质**：分析合并 + 改进 backlog；**不实现、不 push、不部署**
- **合并来源**（均已读）：
  1. `docs/diag/2026-09-13-xiangqi-arena-diag-{SUMMARY,PI,AGY}.md`（自 `/workspace/voonie/docs/diag/` 同步副本）
  2. `docs/fix-briefs/2026-09-13-{pi,agy}-*-RESULT.md` + `2026-09-17-agy-game-ui-RESULT.md`
  3. `/workspace/voonie-align/xiangqi-qa-2026-09-17-toast-ws/report.md`（Toast/WS QA ✅）
  4. `/workspace/voonie-align/xiangqi-qa-2026-09-17/report-dual-ui.md`（历史 UI；部分已由 `0245638` 消化）
  5. `docs/ws-reconnect-contract.md` + 2026-09-13 起 fix-briefs
  6. `/workspace/voonie-align/xiangqi-deep-explore-2026-09-17/report.md`（云电脑网页全站深挖；**v1 已合并 D01–D21**）
  7. 代码深潜：`PublicSiteServer` / `OnlineRoomHub` / `FileRoomPersistence` / `AuthService` / `RateLimiter` + `src/main/resources/online/{app.js,app.css,mobile.css,board.js}`

## 已结案（勿重做）

| 主题 | Commit / 证据 | 说明 |
|---|---|---|
| 异常路由死循环 + 友好 404 | `028165b` + agy 09-13 RESULT | `routeLoadError` 短路；hash 坏链只打 1 次请求 |
| 胜方结算弹窗（认输路径） | `028165b` + dual-ui / toast-ws 续测 | FINISHED 绕过局部 patch；双端出 Modal |
| 非法走子 Toast 不被心跳冲刷 | `028165b` + **toast-ws QA ✅** | `interactionError` 锁 ≥2.6s；状态栏/Toast 双通道 |
| WS 心跳 + 自动重连 + snapshot 对账 | `028165b` + `docs/ws-reconnect-contract.md` + **toast-ws QA ✅** | 服务端 25s ping / 50s 死链；客户端 pong + 1s 重连 + REST 对账 |
| 房间/对局文件持久化 | `028165b` + pi RESULT | `FileRoomPersistence` + moves 回放重建 |
| REST 只读鉴权与 WS 对齐 | `028165b` + pi RESULT | `canReadRoom` ≡ `canSubscribeRoom`；401/403/404 |
| 对局页 UI 五连打磨 | **`0245638`**（agy-game-ui RESULT；用户口中的 3a8acc0 在本仓不存在） | 顶栏减重、去掉误导「等待中」、时钟可读、棋谱红黑用字、三栏 minmax、认输降权 |

> **残留注意**：WS 功能已通，但断线**横幅 UI**仍缺（见 FE-07）。移动端 ~375 对局页仍标「待验」（见 FE-08）。

## 探索清单状态

- **v1 已合并**：`/workspace/voonie-align/xiangqi-deep-explore-2026-09-17/report.md` 已提供 D01–D21；首页 / 大厅 / 创建房间 / 登录注册已有覆盖，不再以「未探索」阻塞 backlog。
- **既有映射保持**：D01–D06 继续按 `0245638` 标记 fixed；D07→FE-01，D08→BE-12/FE-02，D09→FE-02，D10→FE-07，D11→BE-06，D12→FE-08。
- **剩余覆盖缺口**：观战截图、个人中心、匹配进行中、异常路由截图、悔棋/求和全流程、移动端对局页仍待补（若对应项尚未验收）。
- **WAITING 收窄**：FE-99 / BE-99 仅保留上述剩余覆盖缺口，不再等待首页 / 大厅 / 创建房间 / 登录注册探索清单。

---

## Master backlog

| id | area | priority | status | problem | evidence | proposed owner | suggested approach |
|---|---|---|---|---|---|---|---|
| BE-01 | BE | P1 | open | HTTP 错误契约仍不统一：`GET /online/api/auth/me` 未登录 → **200 + `null`**；部分「不存在」曾 200+`{}` 与 404 混用；业务失败大量 400 + 英文内部文案 | diag-PI PI-04/10；`PublicSiteServer.handleMe` 现仍 `orElse(null)`；pi RESULT 未改 me 契约 | Pi | 统一 401/403/404/409/429；`me` 未登录改 401 或明确 `{user:null}` 契约并改前端；错误体 `{code,message}`；映射用户可读中文（前端可再翻译） |
| BE-02 | BE | P1 | open | Cookie 仅 `HttpOnly`，缺 `Secure` / `SameSite`；响应无 CSP / HSTS / X-Content-Type-Options / X-Frame-Options | diag-PI PI-05；`setAuthCookie` 仅 path+HttpOnly+maxAge | Pi | `SameSite=Lax` + 条件 `Secure`；统一安全响应头（可经 CF 叠加，源站也要设） |
| BE-03 | BE | P1 | open | 公开 lobby 搜索 / 排行榜暴露完整 user UUID；用户名几乎无校验（可辱骂/超长） | diag-PI PI-06；agy ISSUE-009；`AuthService.normalizeUsername` 仅 trim+lower；`handleLobbySearch` 返回 players | Pi（联合 agy 展示截断） | 对外 ID 哈希/省略；注册字符集+长度+敏感词；榜单/搜索脱敏 |
| BE-04 | BE | P2 | open | 密码仅 ≥8；auth 限流按 **IP**（`ip:login`），共享出口易误伤；无账号锁定 | diag-PI PI-08；`AuthService.validatePassword`；`RateLimiter` 8/min | Pi | 复杂度/常见密码黑名单；限流键 `username+IP`；失败锁定/退避 |
| BE-05 | BE | P2 | open | 每步走子后客户端仍拉 bootstrap+dashboard（约 3 请求/步） | diag-PI PI-07；`app.js` 多处 `refreshBootstrapAndProfile()` 在 move 成功路径 | Pi（契约）+ agy（调用点） | move 响应带必要增量；去掉逐步全量 bootstrap；或 WS 推送概览字段 |
| BE-06 | BE | P2 | open | 裸 `/online/`（尾斜杠）未注册 → 404；仅有 `/online` 与 `/online/index.html` | deep-explore D11；`PublicSiteServer` 路由表无 `"/online/"` | Pi | 注册 `GET/HEAD /online/` → 同 index，或 301 → `/online` / `/online#/home` |
| BE-07 | BE | P2 | open | 无鉴权/异常 WS 握手曾返回 **HTTP 500**（应干净拒绝） | diag-PI PI-09 | Pi | 握手失败 → 401/403 关闭；避免 500；补测 |
| BE-08 | BE | P2 | open | 文件持久化可用，但生产挂载/多实例/故障切换未产品化；SPI 仍偏单机 JSON | pi RESULT「仍待」；`FileRoomPersistence` + `XQ_ROOM_STATE_FILE` | Pi | 部署文档明确数据卷；评估 SQLite/DB 背板；多实例则需粘性或外置状态 |
| BE-09 | BE | P3 | open | 未登录观战 `viewerSide` 空、翻面语义不清 | diag-PI PI-11 | Pi（联合 agy） | API 明确 `viewerSide`/`perspective`；公开观战默认红下 |
| BE-10 | BE | P3 | open | analysis/replay 对 `stateId` 缝隙防御弱 | diag-PI PI-12 | Pi | replay 校验步数/stateId；失败显式 409 |
| BE-11 | BE | P3 | open | bootstrap `recentGames` 中长期 `PLAYING` 的练习/训练局堆积 | diag-PI PI-13 | Pi | 启动/定时清理孤儿 PLAYING；练习局 TTL |
| BE-12 | BE | P2 | partial | 结算 `resultText` 仍可能英文（如 `resigned`）与中文「认输」并排 | dual-ui；deep-explore D08；agy 09-13 已有 termination 映射但 resultText 源可能仍英文 | Pi（权威文案）+ agy（展示兜底） | 服务端 `resultText` 中文化；保留 `terminationReason` 枚举给前端 |

| BE-13 | BE | P2 | open | 未知 hash 路由（如 `#/nope-xyz`）静默回首页，未出友好 404 | deep-explore D23；`shots/bad-route.png` | Pi（路由表）+ agy（404 页） | 未知路由 → 友好 404（对齐 09-13 房间 404，勿静默吞掉） |
| BE-14 | BE | P2 | open | bootstrap/me「当前对局」可能仍返回已 FINISHED 对局 | deep-explore D22 | Pi | 过滤非活跃局；或标 `continuable:false` |
| BE-99 | BE | P2 | open | 匹配进行中/悔棋求和全流程等剩余面可能仍有 API 缺口 | deep-explore **v1.1** | Pi | 剩余缺口补齐后再专项；D23 已单列 BE-13 |
| FE-01 | FE | P1 | open | 「对手已落子」类 `toast--move` 贴底，压住棋盘下操作条（悔棋/求和/认输/离开） | dual-ui §7；deep-explore D07；`app.css` `.toastHost{bottom:…}`；`maybeNotifyOpponentMove` → `showToast(...,'move')` | agy | 对局页 Toast 改顶部/棋盘上方；或缩短 move-toast 且避开 `.woodActions`；移动端已有顶部避让可复用桌面局内 |
| FE-02 | FE | P1 | open | 结算弹窗四按钮两红两白略挤；文案层仍可能漏英文 resultText | dual-ui 结算观感；D08/D09；`renderGameEndModal` + `.endGameActions` flex wrap | agy | 主次 CTA：主按钮「再战」/「分析」，次要 ghost；`resultText` 再走一遍中文词典 |
| FE-03 | FE | P1 | open | 认输等仍用 `window.confirm`，阻塞主线程、打断时钟动画、风格割裂 | diag-AGY ISSUE-008；`app.js` 多处 `window.confirm` | agy | 国风确认 Modal；确认前冻结 UI 但时钟用 monotonic 补偿或允许继续走秒 |
| FE-04 | FE | P1 | open | 全站英文/枚举直吐未扫净（登录 `invalid credentials`、房间状态等） | diag-AGY ISSUE-007；toast-ws 非法提示已中文，但 auth/其它路径仍可能英文 | agy（联合 Pi 错误码） | 集中 `i18n`/`errorMap`；对未知后端字符串兜底「操作失败，请重试」 |
| FE-05 | FE | P2 | open | 棋谱仍非传统「炮二平五」记谱（仅做了红黑兵卒/仕士等用字统一） | diag-AGY ISSUE-005；`formatMoveNotation` 只映射首字；deep-explore D05 已由 `0245638` 修用字 | agy | 新增 ICCS/中式记谱转换（坐标→「炮二平五」）；复盘与列表共用 |
| FE-06 | FE | P2 | open | 选子无合法落点提示（仅有选中绿圈） | diag-AGY 13.3 / Journey B | agy | 向引擎或本地规则要 legal targets；`board.js` 已有 hint from/to class，可扩 multi-dot |
| FE-07 | FE | P2 | open | WS 断线/重连无醒目横幅（功能可自动恢复） | toast-ws「有保留」；deep-explore D10；`socket.onclose` 仅 1s 重连无 UI | agy | 顶栏/棋盘条：`连接中断 · 重连中…` / `已恢复`；与 `docs/ws-reconnect-contract.md` 对齐 |
| FE-08 | FE | P1 | partial | 移动端对局 ~375 裁切：09-13 已做 390 预算，对局页 375 **待验**；深挖 D12 | agy 09-13 RESULT；deep-explore D12；dual-ui 原检查项 4 | agy | 真机/仿真 375×667 对局页验收；继续压 `.woodActions`/时钟；不过度改桌面 |
| FE-09 | FE | P2 | open | 排行榜/昵称展示：前端缺省略与敏感遮罩（后端未滤时的防线） | ISSUE-009；FE 需与 BE-03 协同 | agy | CSS 截断+max-width；简单客户端屏蔽表作临时盾 |
| FE-10 | FE | P3 | open | `#play/xiangqi` 横幅背景平铺缝 | ISSUE-010 | agy | `background-size:cover; background-repeat:no-repeat` |
| FE-11 | FE | P2 | open | 房主等待室仍见「加入当前房间」幽灵按钮，易误解为房主仍可加入 | ISSUE-011；deep-explore D15 | agy | `isRoomHost` 时隐藏 join；优先处理 D15 |
| FE-12 | FE | P3 | open | 棋盘空白格缺 aria-label；首页多个「前往」同名 | ISSUE-012 | agy | 路数 aria-label；按钮补上下文名 |
| FE-13 | FE | P3 | open | 桌面「棋谱」vs 移动「学习」导航词不一致 | ISSUE-013 | agy | 统一信息架构用词 |
| FE-14 | FE | P2 | open | 1280×800 操作栏曾落入折叠线；`0245638` 三栏改善后需回归 | ISSUE-006；dual-ui 三栏；`0245638` 改了 grid 但未专项验 800 高 | agy | 1280×800 / 1366×768 回归：操作条完整在首屏 |
| FE-15 | FE | P2 | fixed | 顶栏与左栏信息重复 | dual-ui 1；D01；**`0245638` 已修** | — | 保持：顶栏只留游戏名+轮到谁 |
| FE-16 | FE | P2 | fixed | PLAYING 时卡片误显「等待中」 | dual-ui 3；D03；**`0245638`** | — | 保持「等待对方走棋」语义 |
| FE-17 | FE | P2 | fixed | 时钟难读 / 棋谱红黑用字 / 认输过重 / 三栏裁切 | dual-ui 2/4/5/6；D02/D04/D05/D06；**`0245638`** | — | 回归测试防回退 |
| FE-18 | FE | P0 | fixed | 异常房间路由死循环 | SUMMARY P0；**`028165b`** | — | 勿重做 |
| FE-19 | FE | P1 | fixed | 胜方无结算弹窗；非法 Toast 被心跳刷掉；WS 无重连 | SUMMARY/agy P1；toast-ws ✅ | — | 勿重做（横幅见 FE-07） |
| FE-20 | FE | P3 | open | 房间等待页游戏类型标签显示英文 `XIANGQI` | deep-explore D13 | agy | 本地化为「象棋」或统一中文游戏类型标签 |
| FE-21 | FE+BE | P3 | open | 房间等待页时长显示 `900 seconds`，未本地化为「15 分钟」 | deep-explore D14 | agy + Pi | 前端显示中文时长；服务端/接口枚举避免直吐英文 |
| FE-22 | FE | P3 | open | 大厅顶栏导航与左侧「首页/匹配」重复，信息架构偏叠 | deep-explore D16 | agy | 收敛重复导航，明确单一主入口 |
| FE-23 | FE | P3 | open | 大厅「快速匹配」与中央模式卡/右侧创建房并存，主路径不够单一 | deep-explore D17 | agy | 统一快速匹配主路径，弱化或重排重复入口 |
| FE-24 | FE | P3 | open | 五子棋入口「进入模式页」偏开发者口吻 | deep-explore D18 | agy | 改为面向用户的「开始五子棋」等行动文案 |
| FE-25 | FE | P3 | open | 桌面三栏/侧栏与移动底栏「开始一盘棋」的信息架构分叉较大 | deep-explore D19 | agy（产品） | 记录为产品对齐项，统一跨端主路径与导航层级 |
| FE-26 | FE | P3 | open | 登录弹层行为正确，但是否需要第三方登录仍是产品取舍 | deep-explore D20 | 产品（agy 记录） | 可选产品决策；无明确需求则保持现状，不为补功能而补功能 |
| FE-27 | FE | P3 | open | 观战公开房为空时缺少经验证的空态文案/引导 | deep-explore D21；`publicRooms=0` | agy | 为 `publicRooms=0` 增加明确空态、刷新/返回引导，并补截图验收 |
| FE-28 | FE | P2 | open | 个人中心「继续当前对局」在对局已 FINISHED 时仍出现，文案/「回到对局」误导 | deep-explore D22；`shots/me.png` | agy（联合 Pi BE-14） | 仅可续局展示；FINISHED 改「查看对局/复盘」或隐藏 |
| FE-29 | FE | P3 | open | 个人中心状态/棋种英文：`XIANGQI`/`FINISHED`/`resigned` | deep-explore D24；`shots/me.png` | agy（联合 Pi） | 与 FE-04/BE-12 同一枚举→中文映射 |
| FE-99 | FE | P2 | open | 仍待：匹配进行中 UI、悔棋/求和全流程、移动端对局页、棋谱页单截 | deep-explore **v1.1**（watch/me/bad-route 已补） | agy | 剩余缺口补齐后再专项；D22–D24 已单列 |

## 建议下一执行波次（仍不在本轮实现）

1. **P1/P2 联合**：FE-01 Toast + FE-02 结算 + FE-07 断线横幅 + FE-11/D15 房主幽灵加入 + FE-08 375 + **FE-28/D22 继续已结束局** + **BE-13/D23 坏路由 404**
2. **Pi 安全基线**：BE-01 错误契约 + BE-02 Cookie/头 + BE-03/04 昵称与密码限流 + BE-06 `/online/`
3. **专业度**：FE-05 传统记谱 + FE-06 合法点 + FE-03 国风 confirm
4. **v1.1 探索新增**：FE-20～FE-29（含 D22–D24）；BE-13/14 本波优先；BE-99/FE-99 仅剩匹配中/悔棋求和/移动对局页等

## 文件索引（实现时）

| 区域 | 主文件 |
|---|---|
| HTTP/WS 入口 | `src/main/java/com/xiangqi/web/PublicSiteServer.java` |
| 房间/对局枢纽 | `src/main/java/com/xiangqi/online/server/OnlineRoomHub.java` |
| 持久化 | `…/FileRoomPersistence.java`, `RoomPersistence.java` |
| 鉴权 | `…/online/auth/AuthService.java` |
| 限流 | `…/server/RateLimiter.java` |
| 前端 | `src/main/resources/online/app.js`, `app.css`, `mobile.css`, `board.js` |
| WS 契约 | `docs/ws-reconnect-contract.md` |
| Pi brief | `docs/fix-briefs/2026-09-17-deep-improve-pi-brief.md` |
| agy brief | `docs/fix-briefs/2026-09-17-deep-improve-agy-brief.md` |
