# Phase3/4 补齐设计：stateId 同步 + 服务端安全接入

日期：2026-05-03

## 背景
当前主线代码中，Phase1/2 已基本落地；Phase3/4 存在“文件在但接线不完整”的状态：
- `stateId()` 在接口层存在，但快照与前端 WS 消息未形成完整校验闭环；
- `GameClock` 文件存在，但 `XiangqiMatch` 未集成；
- `RateLimiter`、`RoomVisibility`、`EventBus` 等文件存在，但认证限流与 WS 订阅授权未完整接入。

## 目标
1. 完成 stateId 版本同步链路：引擎 -> 快照 -> 前端 WS 校验与丢包全量回补。
2. 完成 `XiangqiMatch` 的 `GameClock` 与超时判负闭环。
3. 在 `OnlineSiteServer` / `PublicSiteServer` 接入：
   - 登录/注册/建房限流
   - WebSocket 鉴权
   - 房间订阅授权（公共房间放行，私有仅房主/客方）。

## 方案
1. 对战框架（Phase3）
- `XiangqiMatch` 新增 `stateId` 字段并在成功落子后自增；覆写 `stateId()`。
- `XiangqiMatch` 集成 `GameClock`，在 `finished/winnerSide/resultText` 中纳入 timeout 判定。
- `OnlineRoomHub.gameSnapshot` 输出 `stateId`。
- `online/app.js` WS 收包时按 `stateId` 执行：忽略旧包；检测跳号时拉全量 `/games/{id}` 快照回补。

2. 安全与运维（Phase4）
- 在两套 Server（online/public）接入 `RateLimiter`：
  - register/login
  - create-room
- WS `onConnect` 基于 cookie token 鉴权；未登录直接 close。
- `subscribe(roomId)` 增加授权：
  - public 房间：允许
  - private/invite-only：仅 host/guest userId 允许

## 验收
1. `mvn -q -DskipTests compile` 通过。
2. `PublicSiteServerTest` 与 `OnlineSiteServer` 关键路径可运行。
3. 线上 `/online` 保持可用，`board.js` 合约不回退。
4. 代码证据可检索：`stateId`、`RateLimiter`、`subscribe` 权限校验、`GameClock` 集成。
