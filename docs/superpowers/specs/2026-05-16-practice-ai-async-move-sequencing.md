# Practice AI 异步落子时序修复设计

日期：2026-05-16

## 背景

用户反馈 AI 练习局的落子表现不符合“人类落子 -> AI 思考 -> AI 落子”的异步体验。当前 practice 页面里，人类提交走子后，界面几乎立刻同时出现 AI 应手，视觉上像两步一起发生。

## 已确认事实

1. 后端 `PracticeGameHub.applyMove()` 在人类走子后只会：
   1. 更新当前局面
   2. `scheduleAiMove(game)`
   3. 返回带 `aiPending=true` 的 snapshot
2. 后端并不会在 `/move` 响应里同步 `drainPendingAiIfReady()`，所以问题不在“后端把 AI 落子直接塞进 move 响应”。
3. 前端 `sendMove()` 在收到 practice `/move` 响应后，如果 `aiPending=true`，会调用：
   1. `startPracticePolling(state.game.gameId, true)`
4. `startPracticePolling(..., true)` 会让 `schedulePracticePoll()` 用 `delay=0`，立即请求 `GET /online/api/learn/practice-games/{gameId}`。
5. 当 AI 计算很快时，这次 0ms 轮询会马上拿到 AI 已落子的 snapshot，于是用户感受到“人类落子和 AI 应手同时出现”。

## 根因

practice 前端的人类落子后首轮轮询过于激进。虽然服务端已经是异步 AI，但前端用 `immediate=true` 在同一交互链路里立刻抓取下一帧快照，把原本应当独立呈现的 “AI 思考中” 状态跳过去了。

## 方案

### 1. 去掉人类落子后的 0ms 首轮轮询

在 `sendMove()` 中，当 practice 返回 `aiPending=true` 时，不再使用 `startPracticePolling(gameId, true)`，改为按正常节奏启动轮询。

### 2. 保留快速轮询，但让首轮至少经过一个可见思考窗口

继续保留现有快速轮询节奏，让 AI 快时仍然响应灵敏；但首轮不能是 0ms，至少要经历一次前端已渲染的人类落子状态与 `AI 思考中...` 状态。

### 3. 同步修正文案

practice 默认状态文案从“后端会立刻返回 AI 应手”改为更符合异步行为的描述，例如“你落子后，AI 会思考并自动应手”。

## 测试策略

### 1. 前端合同回归

增加一个只读资源合同测试，约束 `online/app.js`：

1. 不再包含 `startPracticePolling(state.game.gameId, true)`
2. 包含 `startPracticePolling(state.game.gameId, false)` 或等价的非立即轮询调用

### 2. 现有后端行为保持

保留并复用 `PublicSiteServerTest` 的 practice 流程测试，确保：

1. 人类 `/move` 响应仍是 `aiPending=true`
2. 后续轮询快照仍能拿到 `moveCount=2`

## 验收标准

1. practice 页人类落子后，先看到人类落子结果与 `AI 思考中...`。
2. AI 应手在后续轮询中单独出现，不再与人类落子同时刷出。
3. `PublicSiteServerTest` 仍通过。
4. 新增前端合同测试通过。
5. 线上用浏览器真实落子时，AI 应手出现时间晚于人类落子渲染。
