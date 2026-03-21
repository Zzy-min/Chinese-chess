# 学习页 AI 对局设计

## 目标

将在线站点的“学习”页从壳层升级为真实可用入口，首个落地能力为 AI 对局练习。

本次只突破以下边界：

- 学习页从占位变为可操作页面。
- 中国象棋与五子棋都支持单人对 AI 练习。
- AI 对局会归档到现有 `games` / `game_moves`，可在个人页与分析页查看和回放。

以下边界保持不变：

- 围棋在线仍是占位。
- 观战、社区仍是壳层。
- 活动房间仍是内存态，服务重启后丢失。
- 不改动 `src/main/resources/web/*`，继续只维护独立 `online` 前端。

## 产品设计

学习页新增“AI 对局”模块，包含：

- 棋种：`XIANGQI` / `GOMOKU`
- 难度：`EASY` / `MEDIUM` / `HARD`
- 先后手：玩家先手 / AI 先手
- 引擎偏好：按棋种分别提供内置与外部偏好选项，但允许后端自动回退到内置引擎

用户流程：

1. 登录用户进入学习页。
2. 选择棋种、难度、先后手和引擎偏好。
3. 创建练习对局后直接进入独立的 practice 对局页。
4. 玩家落子后，后端同步完成 AI 回应并返回最新局面。
5. 对局结束或认输后，归档对局可在个人页最近对局和分析页查看。

页面要求：

- 学习页不出现房间码、邀请、准备状态。
- Practice 对局页保留棋盘、走子列表、AI 元信息、认输、分析入口。
- Profile 最近对局和首页最近对局要能标识“AI 练习”。
- Analysis 页继续复用已有回放能力，但需要能显示训练元数据。

## 技术设计

新增独立运行时 `PracticeGameHub`，不复用 `OnlineRoomHub` 的房间状态机。

职责划分：

- `OnlineRoomHub`：只处理双人在线房间对局。
- `PracticeGameHub`：只处理单人对 AI 练习对局。
- `OnlineStore`：统一持久化在线对局与 AI 练习对局。

练习对局模型：

- 没有 `roomId` 语义，持久化时允许使用空值或专用占位值。
- `first` / `second` 仍保留双边玩家结构，AI 作为合成对手写入归档。
- 额外记录：
  - `is_training`
  - `opponent_type`
  - `ai_engine`
  - `difficulty`

AI 引擎复用：

- 象棋：`ConfigurableXiangqiEngine`
- 五子棋：`ConfigurableGomokuEngine`
- 外部引擎不可用时，仍允许创建练习局，并显式回退为内置 AI

API 设计：

- `POST /api/learn/practice-games`
- `GET /api/learn/practice-games/{gameId}`
- `POST /api/learn/practice-games/{gameId}/move`
- `POST /api/learn/practice-games/{gameId}/resign`

Practice 对局不接入 WebSocket，不共享房间实时协议。

## 数据与兼容性

归档兼容要求：

- 训练局必须继续满足现有分析页回放结构。
- `recentGames` / `recentGamesForUser` 需要返回训练标记和 AI 对手元信息。
- `loadGameAnalysis` 需要返回训练元信息，以便前端区分展示。

为减少前端分叉，practice 对局快照尽量沿用现有 game snapshot 结构，新增字段只做补充，不破坏已有字段。
