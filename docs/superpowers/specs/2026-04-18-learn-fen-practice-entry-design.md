# Learn FEN 练习入口设计（2026-04-18）

## 背景与问题
- 学习页已展示大量残局 FEN，但用户无法“从该题局面直接进入在线 AI 练习”。
- 当前练习局后端仅支持标准开局，未支持 `initialFen`。
- 已导入题库中存在大量不完整 FEN，若不加校验会导致创建练习局失败或局面异常。

## 目标
- 支持在学习页对“有效象棋 FEN”一键开局，直接进入 `/online` 练习链路。
- 无效 FEN 明确禁用入口并给出可理解反馈，不影响现有推荐练习与标准开局。
- 保持“棋种与棋盘严格对应”，不引入五子棋 FEN 开局。

## 设计方案
### 1) 后端：练习创建支持 `initialFen`
- 扩展 `CreatePracticeGameRequest` 增加 `initialFen` 字段。
- `PracticeGameHub.createGame`：
  - `XIANGQI`：若 `initialFen` 非空，先严格解析并构建棋盘，再创建 `XiangqiMatch`。
  - `GOMOKU`：若传入 `initialFen`，直接拒绝（400）。
- 新增象棋 FEN 解析器（严格版）：
  - 仅接受 10 行 x 9 列棋盘；
  - 仅接受合法棋子字符（`k/a/b/n/r/c/p` 大小写）与数字空格；
  - 必须同时存在红帅与黑将各 1 枚；
  - 走棋方仅允许 `w` 或 `b`（缺省按 `w`）。

### 2) 后端：复盘一致性
- `XiangqiMatch` 增加“自定义初始棋盘”构造入口。
- `PracticeGameHub` 保存初始棋盘快照，`historyBoards` 重放从该初始局面开始，避免复盘误回标准开局。
- 快照增加 `initialFen` 便于前端标识来源。

### 3) 前端：学习页一键开局
- 学习题目卡片新增“按此题开局”动作：
  - 仅 `XIANGQI` 且 FEN 结构有效时启用；
  - 点击后调用 `POST /online/api/learn/practice-games` 并携带 `initialFen`；
  - 成功直接跳转练习对局页。
- 对无效 FEN 显示禁用状态（如“FEN 待补全”），避免无意义请求。

## 兼容性与边界
- 不改动既有房间对局链路（`/rooms`、`/games`）。
- 不改动 AI 算法，仅改开局局面来源。
- 本轮只支持 `XIANGQI/GOMOKU`；`GO` 保持禁用。

## 验证策略
- 单元/集成测试覆盖：
  - 有效 FEN 可创建练习局并正确反映先手方与棋盘；
  - 无效 FEN 创建失败（400 语义）；
  - `historyBoards` 初始帧与自定义局面一致。
- 资源契约测试补充：
  - 学习页 JS 包含“题目开局入口”与对应处理函数。
