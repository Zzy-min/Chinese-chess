# 轻棋局 Online WebSocket 心跳与重连契约

> 范围：`PublicSiteServer` 的 `/online/ws` 端点（ws://…/online/ws）。
> 角色：服务端（PublicSiteServer 内 `WsHub`）；客户端按本文档配合即可。
> 关联交付：`docs/fix-briefs/2026-09-13-pi-backend-RESULT.md`。

## 1. 连接与鉴权

- 握手时浏览器会自动携带 cookie（本应用会话 cookie）。
- 服务端在 **握手阶段** 即从 session 读取 `userId` 并以 channel attribute（`userId`/`username`）保存。
  未登录的 WebSocket 连接也能建立，但任何订阅都会因 `canSubscribeRoom` / `canReadRoom`
  权限校验失败而**静默不推送**（即无法拉取私房盘面）。
- 服务端依据 `channel` 上已记录的 `userId` + 房间快照做 `canSubscribeRoom` 判定，与
  `GET /online/api/rooms/{id}`、`GET /online/api/games/{id}` 的只读鉴权**完全一致**。

## 2. 订阅帧（入站）

| 帧 | 载荷 | 效果 |
| --- | --- | --- |
| `subscribe` | `{ "type":"subscribe", "roomId":"<uuid>" }` | 订阅单个房间；**当前无权限则忽略**，有权限则立即推送一条 `room_state`（含 `room` + 进行中的 `game`）。 |
| `subscribe_lobby` | `{ "type":"subscribe_lobby" }` | 订阅公开大厅；立即推送一条 `lobby` 快照。 |

一次连接**只能有一个订阅目标**：再次发送 `subscribe` / `subscribe_lobby` 会先取消旧订阅再建立新订阅，因此「重连补订阅」只需重发对应帧即可。

## 3. 心跳（服务端 ping）

- 服务端周期（约 **25 秒**）对每个活跃连接推送一帧：
  ```json
  { "type": "ping" }
  ```
- 客户端**无需应答**；服务端用「收到任意文本帧」自动续命（`lastSeen` 时间戳）。
- 若某连接 **>50 秒（约 2 个心跳周期）** 仍无任何入站帧，服务端判定其为死连接，
  发送 `1001 going-away` 关闭帧并**清理该通道的订阅**（room/lobby 均移除）。
- 客户端若希望延长保活，可在空闲时主动发送任何文本帧（例如 `{ "type":"pong" }`）
  都会刷新 `lastSeen`，等价于「客户端 ping / 服务端 pong」的镜像约定。

> 依赖建议：浏览器侧可用原生 `WebSocket`，无需额外依赖；若自有转发层需要，
> 客户端再额外回 `{ "type":"pong" }` 即可（服务端兼容）。

## 4. 断线重连 / 补快照（客户端步骤）

1. 重连到 `/online/ws`（握手完成即鉴权）。
2. 若原本订阅房间：重发 `{ "type":"subscribe", "roomId":"<uuid>" }`。
   - 服务端会立刻回送最新 `room_state`：`event.room`（房间维度）+ `event.game`（对局维度）。
   - `event.game.stateId`（`game.engine.stateId()`）为对局逻辑状态指纹。**重连后以本次
     推送的 `stateId` 为基线**，忽略重连期间可能错过的中间事件（可选对账）。
3. 若原本订阅大厅：重发 `{ "type":"subscribe_lobby" }`，拿到最新 `lobby` 全量快照。
4. 之后按增量事件继续更新（`room_state` / `lobby` 推送）。

## 5. 客户端配合字段（对齐异常显示）

- 服务端 push 事件：
  - 房间/对局：`{ "type":"room_state", "room":{…}, "game":{…}? }`
  - 大厅：`{ "type":"lobby", "lobby":{ "games":[…], "rooms":[…] } }`
  - ping：`{ "type":"ping" }`
- 客户端上送：
  - `subscribe` / `subscribe_lobby`（见上表）
  - 可选的 `{ "type":"pong" }`（非必须）。

## 6. 与 REST 只读鉴权的一致性

订阅房间/对局与下述接口**共用同一套判定**：
- `canSubscribeRoom(userId, room)`：未登录 → 拒；私房 → 仅房间参与者/创建者；公开 → 已登录可读。
- `GET /online/api/rooms/{id}`（读房间快照）
- `GET /online/api/games/{id}`（进行中对局读活动 hub；已归档对局走 `store` 参与者校验）

HTTP 状态语义：未登录 `401`；已登录但无权限 `403`；`null`/不存在路径 `404`。
```