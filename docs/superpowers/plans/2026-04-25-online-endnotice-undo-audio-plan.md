# Online 终局提示 + AI 悔棋认输 + 音效修复计划（2026-04-25）

## Steps
1. 后端新增 practice undo 能力
- 新增 `POST /online/api/learn/practice-games/{gameId}/undo`。
- 在 `PracticeGameHub` 实现“回合悔棋”并重建对局状态。
- 在 `OnlineStore` 增加原子化持久化（重写走子记录 + 更新 games 快照）。

2. 前端补齐 AI 练习动作组
- 练习页加入 `悔棋` 按钮并接入新接口。
- `认输`加入确认提示并保持高可见危险态。
- 不可悔棋时明确禁用并给出原因文案。

3. 前端新增终局强提示弹层
- 统一 `game/practice` 终局弹层渲染与关闭/跳转动作。
- 加入同局面去重逻辑，避免重复弹窗。

4. 前端接入在线音效
- 加入音效开关（默认开，持久化）。
- 首次手势解锁音频播放。
- 新增走子与终局音效触发并去重。

5. 自动化回归
- 运行：
  - `mvn -q "-Dtest=PublicSiteServerTest,PracticeGameHubTest,LegacyHomepageResourceContractTest" test`
  - `mvn -q test`
- 增加对应断言：practice undo、终局弹层函数、在线音效函数/开关入口。

## Risks
- undo 回放与持久化若不同步，可能导致分析页与实时页不一致。
- 音频自动播放策略可能导致首次无声，需要手势解锁兜底。

## Rollback
- 后端回滚点：undo 路由与 hub/store 新增方法。
- 前端回滚点：终局弹层渲染块、practice 悔棋按钮与音效管理函数。
