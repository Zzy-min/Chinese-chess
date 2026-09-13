# Pi：轻棋局后端修 —— 结果交付（WS 心跳/持久化/只读鉴权）

- 日期：2026-09-13
- 状态：3/3 项落地，全部相关 `mvn test` 通过（97 pass, 0 fail）。
- 遵循约束：仅改 Java / 测试 / 少量文档；未 commit、未 push。

---

## 变更文件

**新增：**
- `src/main/java/com/xiangqi/online/server/RoomPersistence.java` — 持久化 SPI。
- `src/main/java/com/xiangqi/online/server/NoopRoomPersistence.java` — 空实现（无副作用，供单测/内嵌场景）。
- `src/main/java/com/xiangqi/online/server/FileRoomPersistence.java` — JSON 文件落盘（原子写 `.tmp`→rename；路径默认 `XQ_ROOM_STATE_FILE` 或 `./data/room-state.json`）。
- `src/test/java/com/xiangqi/online/RoomPersistenceTest.java` — 持久化 round-trip 单测（3 用例，全绿）。
- `docs/ws-reconnect-contract.md` — WS 心跳/重连契约说明。

**修改：**
- `src/main/java/com/xiangqi/online/server/OnlineRoomHub.java` — 房间/对局快照持久化 + 启动恢复。
- `src/main/java/com/xiangqi/web/PublicSiteServer.java` — WS 心跳清理、只读 REST 鉴权对齐。
- `src/test/java/com/xiangqi/web/PublicSiteServerTest.java` — 新增 REST 鉴权与 WS 心跳单测。

---

## 1) WebSocket 心跳 + 重连策略

**服务端 ping/heartbeat**（`PublicSiteServer.WsHub`，`/online/ws`）
- 握手时从会话 cookie 解析并写入 channel attribute（`userId`/`username`）；未登录连接仅不授权，不影响建连。
- 周期性（`ScheduledThreadPoolExecutor` 守护线程，间隔 25s）对每个活跃连接推送：
  ```json
  { "type": "ping" }
  ```
- 活性以 `lastSeen` 时间戳维持：收到**任何文本帧**即续命（客户端不必须回 pong）。
- 死连接清理：某连接 **>50s（2 个周期）** 无任何入站帧 → `sendClose(1001,"heartbeat timeout")` 并移除其 room/lobby 订阅（`unsubscribe`）。
- 各 `onClose`/`onError` 均清理 `lastSeen`，避免泄漏。

**重连契约**（详见 `docs/ws-reconnect-contract.md`）
- 断线后重连 `/online/ws`；重发 `{ "type":"subscribe","roomId":"<uuid>" }` 或
  `{ "type":"subscribe_lobby" }` 即可重获授权与最新全量快照。
  - 单连接仅一个订阅目标：再次订阅会先取消旧订阅再建新订阅。
  - `subscribe` 立即回推 `room_state`（`room` + 进行中 `game`）。
  - 拿 `event.game.stateId`（`game.engine.stateId()`）作为基线做对账；大厅回 `lobby` 全量。
- REST 与 WS 共用同一套 `canSubscribeRoom`/`canReadRoom` 判定，避免“订阅鉴权”与“REST 鉴权”漂移。

**验证**：`PublicSiteServerTest.reconnectedLobbyWebSocketReceivesHeartbeatPing` 实测
订 lobby → 40s 内收到 `{"type":"ping"}`；初次订阅即收到带最新快照的 lobby 事件。

---

## 2) 对局持久化（重启不丢盘面）

**方案**：RoomPersistence SPI + FileRoomPersistence（JSON 落盘）；引擎通过 **DB 回放 `game_moves`** 精确重建。

**落盘内容**（`OnlineRoomHub`，每次变更入口调用 `persistAll()`）：
- 每个活跃房间：`roomId/roomCode/gameType/status/isPublic/host/guest/hostReady/guestReady/gameId/系列比分`。
- 房间挂历对局 `gameId` 对齐活动 hub；需重建时：
  - 房间与对局元数据从持久化文件恢复；
  - `OnlineMatchEngine` 状态改为启动时从 `store.loadGameAnalysis(gameId)`，迭代 `game_moves` 记录逐手回放重建 `board / currentTurn / stateId / clock`。
  - 因为**着法历来留在 DB（`game_moves` 表）**，所以无需自行保存全量 FEN 即可还原盘面；`board_json` 亦已被完整性作为校验。

**恢复接入点**
- 新增构造器：`PublicSiteServer()` → `FileRoomPersistence`（默认路径 `./data/room-state.json`，可 `XQ_ROOM_STATE_FILE` 覆写）；`OnlineRoomHub` 构造最后一个可选参数 `persistence`。
- `restoreRoomState()` 在 hub 构造时调起：读完文件 → 还原房间表 + 每个进行中/待开局/局间房间的对局与引擎。
- 恢复后的房间同样参与 `GET rooms/games` 与 WS 订阅（`stateId` 一致）。

**Round-trip 单测（`RoomPersistenceTest`）**：真实文件 + H2（同一 store）。
1. 创建私房 → 双人就绪开局 → 走一手 → 落盘。
2. 用**同一个持久化文件 + 同一 DB store** 新建 `OnlineRoomHub`（模拟进程重启自动恢复）。
3. 断言重启前后 `room.status/host/guest/gameId`、`game.status/clockState/currentTurn/winner/resultText/stateId/moveCount/棋子盘面/双方剩余秒 ` 完全一致。
另 2 例：等待房、局间（BETWEEN_GAMES + FINISHED 复盘）均可重启恢复。

---

## 3) 只读 REST 鉴权与 WS 对齐

`GET /online/api/rooms/{id}`（`handleRoomById`）与 `GET /online/api/games/{id}`（`handleGameById`）
统一收敛到与 `canSubscribeRoom` 同源的判定：

| 场景 | 状态码 |
| --- | --- |
| 未登录读取任何房间/对局 | `401` |
| 私房 / 私房对局：登录但非参与者/创建者 | `403` |
| 私房/对局：参与者或创建者 | `200` |
| 公房：任意已登录用户 | `200` |
| 不存在的 room/game UUID（登录后） | `404` |
| 已归档（非活动）对局 | 走 `store.loadGameAnalysis` → 公开可读 / 私局**仅 participants**，否则 `403`；不存在 `404` |

- 判定逻辑（`canReadRoom`）与 WS `canSubscribeRoom` 同为：公开 → 已登录可见；私房 → userId ∈ {host, guest}。
- 已归档对局通过 `isArchivedGameParticipant` 校验参与者，堵住「任意 UUID 偷读私房盘面」。
- 练习局 `GET /online/api/learn/practice-games/{id}` 维持原有“登录可见”策略不变。

**验证**：`PublicSiteServerTest.readOnlyRoomSnapshotRequiresLoginAndRespectsVisibility` 与 `readOnlyGameByIdRequiresLoginAndParticipantScope` 共 3 名用户分别断言 401/403/200/404 全矩阵，全绿。

---

## 测试结论

```
mvn test
=========
Tests run: 97, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
- 新增：`RoomPersistenceTest`(3)、`PublicServerSocketTest` 新增 3 个方法（房间/对局鉴权矩阵 + WS 心跳 ping）。
- 原有用例全部保持绿色（包括已有 `PublicSiteServerTest` 21 项、`OnlineRoomHubTest` 10 项）。

---

## 仍待 / 未做（工程师交接）——不在本轮范围内
- 落盘默认目前落在**文件**（`./data/room-state.json`）；生产如需可改用挂载卷或换 SQLite 背板（SPI 已预留）。
- WS 死链主动 **重连** 由前端（agy）接 `docs/ws-reconnect-contract.md` 实现；后端已将契约与快照/`stateId` 就绪。
- `XQ_ROOM_STATE_FILE` 在生产环境由部署注入；`docs/deployment` 可补充持久化目录规划。
- 归档对局目前只读回放；如需要可审计字段（`generatedAt`）可选补齐。
- 未做 DB 迁移/升级；`schema.sql` 未改动，向后兼容。
```