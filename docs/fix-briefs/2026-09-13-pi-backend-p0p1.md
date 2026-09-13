# Pi：轻棋局后端开修（WS / 持久化 / 只读鉴权）

工作区：`/workspace/Chinese-chess`（已 shallow clone）。**禁止 git push / commit 除非用户另说；本轮默认只改本地文件。**

诊断依据：
- `/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-SUMMARY.md`
- `/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-PI.md`

## 必须落地（最小可用）

### 1) WebSocket 心跳 + 重连策略（服务端契约）
- 在 `PublicSiteServer` WS 层增加服务端 ping/heartbeat（或约定客户端 ping、服务端 pong），超时清理死连接。
- 在协议/事件中明确：连接恢复后如何 `subscribe` 房间并拿到最新 snapshot（`stateId`）。
- 写清客户端应配合的字段（可在注释或 `docs/` 短说明）；前端由 agy 接。

### 2) 对局持久化（重启不丢盘面）最小可用
- 现状：`InMemoryRoomRepository` / 内存 hub。落地 **文件或 SQLite/JSON 落盘** 最小方案：房间+对局快照（含 FEN/着法/时钟/状态）可在进程重启后恢复。
- 不要求完整生产级 DB，但要：重启后 `GET games/rooms` 与 WS 订阅能恢复进行中对局。
- 补/改单测证明「序列化→清空内存→加载→状态一致」。

### 3) 只读 `GET /online/api/games/{id}`、`GET /online/api/rooms/{id}` 鉴权与 WS 对齐
- 与 `canSubscribeRoom` 同等：未登录/无权限 → 401/403，禁止任意 UUID 读私房盘面。
- 公开大厅列表策略保持不变；私房/进行中对局需参与者或合法会话。
- 补单测。

## 约束
- 只改与上述相关的 Java/测试/少量文档。
- 不虚报；跑相关 `mvn test`（或项目既有测试命令）能过的写进交付说明。
- 交付：`docs/fix-briefs/2026-09-13-pi-backend-RESULT.md`（已改项、验证方式、仍待）。

开始 Explore→实现→测。
