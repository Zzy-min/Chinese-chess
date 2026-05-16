# Practice 人类落子先于 AI 思考设计

日期：2026-05-16

## 背景

用户继续纠正了 practice 异步落子体验目标：不只是“AI 应手不要和人类应手同帧出现”，而是“人类落子之后，棋子要先真实落到新位置上，然后再进入 AI 思考”。  
上一轮修复只去掉了 0ms 首轮轮询，确保 AI 应手不会和人类落子同帧刷出，但 practice 仍然要等 `/move` 响应回来后，才同时显示：

1. 人类这一步已经落子
2. `AI 思考中...`

因此视觉上仍不满足“人类先走完，再开始 AI 思考”。

## 已确认事实

1. 当前 `sendMove()` 在请求发出前只设置了：
   1. `moveInFlight`
   2. `pendingMoveMarker`
2. `pendingMoveMarker` 只影响起终点高亮，不会把 `state.game.board` 更新成“人类新走完的一步”。
3. practice 棋盘、状态 pill、走子记录等主要渲染都依赖 `state.game`，而不是 `pendingMoveMarker`。
4. 所以在 `/move` 返回前，用户看到的仍是旧局面；等响应回来后，才第一次看到“人类已落子”的新局面，并同时进入 `AI 思考中...`。

## 根因

practice 前端缺少真正的 optimistic human move。当前只做了“待提交标记”，没有在本地先把人类这一步应用到 practice snapshot 上。

## 方案

### 1. 新增 optimistic practice snapshot

为 practice 落子新增 `applyOptimisticPracticeMove()`：

1. 基于当前 `state.game` 克隆一个临时 snapshot
2. 在临时 snapshot 上直接应用人类这一步
3. 让棋盘先显示人类新局面

### 2. 请求飞行期间使用 optimistic snapshot

在 `sendMove()` 里：

1. 发请求前把 `state.game` 切到 optimistic snapshot
2. 保持 `moveInFlight=true`
3. 在服务端响应成功后，再用服务器 snapshot 覆盖
4. 如果请求失败，则回退到原始 snapshot

### 3. optimistic 阶段不提前进入 AI pending 轮询

optimistic snapshot 的作用是“先显示人类已落子”，不是伪造 AI 已开始思考。  
因此 optimistic snapshot 不应直接启动 practice polling，也不应提前把前端带入下一帧 AI 应手流程；等服务端 `/move` 响应确认后，再按真实 `aiPending` 状态进入 `AI 思考中...`。

### 4. 覆盖象棋与五子棋

practice 支持两种棋种，因此 optimistic 应用需要覆盖：

1. Xiangqi：源格清空、目标格落子
2. Gomoku：目标点落子

## 测试策略

### 1. 前端合同测试

增加只读合同测试约束：

1. `online/app.js` 包含 `function applyOptimisticPracticeMove`
2. 仍保持非 immediate 首轮 polling
3. 默认文案仍为异步思考语义

### 2. 浏览器时序验证

真实页面中验证：

1. 调用 `sendMove()` 后，在请求返回前，`state.game.board` 已是人类新局面
2. 此时 `moveInFlight=true`
3. 服务端响应返回后，再进入 `AI 思考中...`
4. 后续轮询再拿到 AI 应手

## 验收标准

1. 人类点击落点后，棋盘先显示人类这一步。
2. 人类这一步显示出来后，才出现 `AI 思考中...`。
3. AI 应手仍在后续独立轮询中出现。
4. 请求失败时，棋盘会回退到原局面。
5. 合同测试、`PublicSiteServerTest`、浏览器真实时序验证通过。
