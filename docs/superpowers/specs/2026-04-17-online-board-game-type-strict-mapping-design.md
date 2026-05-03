# Online 棋盘与棋种严格对应修复设计

## Summary

本轮目标是在 Online 前端中建立“棋盘与棋种强绑定”的硬约束，同时补齐练习局落子反馈，避免出现“棋种 A 却渲染棋盘 B”的错配体验。

仅做最小行为修复，不改后端 API，不改 AI 算法。

## Design

### 1. 棋盘渲染统一分发

当前对局页与分析页使用分散的条件判断，存在未来回归时误接默认棋盘的风险。

本轮引入统一分发入口：

- `XIANGQI` 仅使用象棋棋盘渲染函数
- `GOMOKU` 仅使用五子棋棋盘渲染函数
- 未知 `gameType` 返回安全失败占位，不做棋盘兜底

这样可以确保所有页面都由同一逻辑决定棋盘类型，减少错配路径。

### 2. 练习局乐观更新只允许象棋

练习局当前请求返回前用户看不到自己的落子，体感“点击后没反应”。

本轮新增前端临时态，仅在 `isTraining + XIANGQI` 下启用：

- 第二次点击提交走法时，先本地渲染玩家落子结果
- 状态文案切到“AI 思考中...”
- 请求完成后由服务端快照覆盖并清理临时态
- 请求失败时回滚临时态并展示错误

同时对 payload 做棋种校验：

- `XIANGQI` 仅接受 `{fromRow, fromCol, toRow, toCol}`
- `GOMOKU` 仅接受 `{row, col}`
- 与棋种不匹配直接拒绝并提示

### 3. 象棋复盘棋盘继续独立样式

保留已有象棋复盘专用样式方向，仅约束作用范围：

- 只影响象棋复盘类名
- 不影响五子棋复盘棋盘样式与结构

## Public Interfaces

- 不新增后端 API
- 不调整现有 JSON 协议字段
- 仅增加前端内部状态（非对外接口）

## Test Plan

- 自动化：
  - 运行 `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomeSessionHubTest,LegacyHomepageResourceContractTest" test`
  - 扩展 `LegacyHomepageResourceContractTest`，断言 `online/app.js` 含有：
    - 显式棋种分发函数
    - 未知棋种保护分支
- 手动：
  - 象棋练习局：立即回显玩家落子并显示“AI 思考中...”
  - 五子棋练习局：仍为 15x15 五子棋棋盘，不出现象棋元素
  - 分析页：棋种与复盘棋盘始终对应

## Assumptions

- 后端 `gameType` 当前稳定使用 `XIANGQI` / `GOMOKU`
- 本轮不新增第三棋种在线渲染能力
