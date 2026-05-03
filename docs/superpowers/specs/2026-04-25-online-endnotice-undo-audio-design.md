# Online 终局提示 + AI 悔棋认输 + 音效修复设计（2026-04-25）

## Summary
- 目标：一次性修复 3 个核心体验问题：终局提示不明显、AI 练习缺少悔棋体验、在线页无音效反馈。
- 范围：`/online` 前后端协同修复；不改棋规与既有房间对局 API 语义。

## Design Decisions
1. 终局提示增强
- 为 `#/game/*` 与 `#/practice/*` 增加统一终局弹层（遮罩 + 居中卡片）。
- 仅在同局面首次进入 `FINISHED` 时弹出，避免轮询/WS 重复打断。

2. AI 练习“回合悔棋”
- 新增 practice undo 接口，仅登录用户可调用。
- 悔棋语义：回退到“人类最近一步之前”的局面；若其后已有 AI 应手则一并回退。
- 前置条件：人类参与者、`status=PLAYING`、`aiPending=false`、至少存在 1 步人类落子。

3. 认输可见性
- 保留现有认输能力，前端改为更清晰的危险按钮并加入二次确认，避免误触。

4. 在线音效系统
- 复用现有 `/assets/audio/move.wav` 与 `/assets/audio/mate.wav`。
- 默认开启并写入 `localStorage` 持久化。
- 增加首次交互解锁机制，兼容浏览器自动播放策略。
- 落子音/终局音分别按“新增走子”与“状态进入 FINISHED”触发，并去重。

## Backend Notes
- 新增接口：`POST /online/api/learn/practice-games/{gameId}/undo`
- `PracticeGameHub` 新增 undo 入口与重放回退逻辑。
- `OnlineStore` 新增“重写 game_moves + 更新 games 快照”的原子方法，保证分析与持久化一致。

## Frontend Notes
- AI 练习操作区固定为：`悔棋`、`认输`、`进入分析`、`返回学习页`。
- 悔棋不可用时显示明确禁用原因（例如 AI 思考中）。
- 终局弹层在练习局提供 `再开一局/返回学习`，在在线局提供 `回到房间`，两者都提供 `进入分析`。
