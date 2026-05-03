# Learn FEN 练习入口实施计划（2026-04-18）

## Phase 1 - 后端能力
1. 扩展 `CreatePracticeGameRequest` 增加 `initialFen`。
2. 新增 `XiangqiFenParser`（严格解析 + 统一报错语义）。
3. `XiangqiMatch` 增加支持初始 `Board` 的构造函数。
4. `PracticeGameHub` 接入 `initialFen`：
   - `XIANGQI` 使用解析后的初始棋盘；
   - `GOMOKU` 拒绝 `initialFen`；
   - 在快照输出 `initialFen`；
   - 复盘历史从初始棋盘重放。
5. `OnlineSiteServer` 与 `PublicSiteServer` 创建练习局接口透传 `initialFen`。

## Phase 2 - 前端交互
1. 学习页题目卡增加“按此题开局”按钮。
2. 增加前端 FEN 结构校验，仅对结构有效题目启用按钮。
3. 新增 `startPracticeFromPuzzle` 事件处理，提交 `initialFen` 并跳转练习页。
4. 增补样式（动作区按钮纵向排列与禁用态提示）。

## Phase 3 - 测试与回归
1. 更新受影响构造调用与测试编译。
2. 新增 `PracticeGameHubTest`：
   - 自定义 FEN 创建成功；
   - 无效 FEN 拒绝。
3. 扩展 `LegacyHomepageResourceContractTest`：
   - 断言学习题目开局入口函数/动作存在。
4. 运行：
   - `mvn -q "-Dtest=PracticeGameHubTest,LegacyHomepageResourceContractTest,PublicSiteServerTest" test`
   - `mvn -q test`

## 风险与对策
- 风险：导入题库中多数 FEN 不完整，用户误以为都可开局。
- 对策：前端显式禁用无效项，并在后端二次校验兜底，确保安全失败。
