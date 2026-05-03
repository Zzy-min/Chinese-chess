# Phase3/4 补齐实施计划

关联设计：`docs/superpowers/specs/2026-05-03-phase3-phase4-integration-completion.md`

## 步骤
1. `XiangqiMatch`：接入 `stateId` 与 `GameClock`，并补充 timeout 判定。
2. `OnlineRoomHub`：快照输出 `stateId`，构造 `XiangqiMatch` 时传入初始时长与时钟。
3. `online/app.js`：WS `onmessage` 增加 stateId 判定与跳号回补。
4. `OnlineSiteServer` 与 `PublicSiteServer`：
   - register/login/create-room 限流
   - WebSocket cookie 鉴权
   - subscribe 权限校验（isPublic 或房间成员）
5. 编译 + 目标测试 + 线上只读验证。

## 验证命令
- `mvn -q -DskipTests compile`
- `mvn -q "-Dtest=PublicSiteServerTest" test`
- 线上检查：`/online`、`/online/assets/site/board.js?v=...`

## 回滚
- 如发现回归，按文件粒度回滚：
  - `XiangqiMatch` / `OnlineRoomHub` / `app.js`
  - `OnlineSiteServer` / `PublicSiteServer`
- 回滚后重跑编译与 smoke 检查。
