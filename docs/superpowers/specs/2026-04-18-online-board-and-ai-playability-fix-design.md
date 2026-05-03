# Online 棋盘与 AI 练习可对弈性修复设计

## Summary

本设计聚焦四个线上可用性问题并一次性修复：

1. 象棋棋子未严格落在交点中心。
2. 象棋棋盘线条存在缺段与结构不完整。
3. 学习页信息堆叠，题库缺乏独立视图入口。
4. AI 练习对局交互不稳定（重复提交、状态错乱、AI 应手不同步）。

## Design

### 1) 象棋棋盘几何改造（交点模型）

- 棋盘改为“交点中心绘制”：
  - 每个交点按方向绘制半段线（上/下/左/右），边界只向内绘制。
  - 河界仅断开中间 7 列竖线连接，保留左右边线连续。
- 九宫斜线与炮兵角标使用独立图层精确绘制，不再使用近似十字占位。
- 棋子视觉保持当前线稿风格，但以“交点居中”作为第一优先级。

### 2) 学习页二级视图与深链接

- 在 `#/learn` 下引入二级视图：
  - `#/learn/tutorials`
  - `#/learn/puzzles`
  - `#/learn/practice`
- 默认视图为 `puzzles`，满足“题库单独页面”诉求，同时保持学习模块内聚合。
- 学习页主结构改为标签切换，不再一次性堆叠三大块。

### 3) AI 练习稳定性优先策略

- 引入统一 `moveInFlight` 锁，阻止连点重复提交。
- 关闭练习局乐观更新（尤其象棋），只按服务端确认结果渲染。
- 增加练习局轮询器：
  - 人类落子且 `aiPending=true` 时轮询 `/online/api/learn/practice-games/{id}`。
  - 命中停止条件后立即停轮询。
- 停止条件：
  - `aiPending=false`
  - 对局结束
  - 离开练习页
  - 登出
  - 认输
- 任一服务端新快照落地时，清理 `selectedFrom`，避免旧选点污染新局面。

## Scope

- 仅改前端资源：`src/main/resources/online/app.js`、`src/main/resources/online/app.css`。
- 不改后端 API，不新增协议。
- 保持 `XIANGQI/GOMOKU` 严格按 `gameType` 渲染与落子校验。

## Verification

- 自动化：
  - `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest,PracticeGameHubTest" test`
  - `mvn -q test`
- 手动：
  - 象棋交点对齐、河界/九宫/角标完整。
  - `#/learn` 默认题库，`#/learn/puzzles` 可直达。
  - AI 练习可稳定收到应手，不再卡死在“AI 思考中...”。
