# Online AI 防滑动 + 题库方块平铺实施计划（Rev3）

## Implementation

1. `app.js` 路由渲染层
- `practice` 路由增加 `route-practice-locked` 专用类；
- AI 练习页信息区由两张卡片改为紧凑信息行；
- 题库三级渲染函数改为方块网格实现（`renderPuzzleThemeTiles`）；
- 题目卡片改为核心信息版，移除默认详情块。

2. `app.css` 样式层
- 仅对 `route-practice-locked` 生效的锁滚动规则；
- AI 棋盘触控防滚动样式（`touch-action` + `overscroll-behavior`）；
- 新增 `learnThemeTiles/learnThemeTile` 方块网格样式（桌面 5 列、移动 3 列）；
- 新增单行摘要样式与练习页紧凑信息行样式。

3. 资源契约与版本
- 扩展 `LegacyHomepageResourceContractTest`：
  - 校验 AI 锁滚动相关类/标识；
  - 校验三级方块函数与样式标识。
- 更新 `/online` 静态资源版本号，确保线上缓存刷新。

## Verification

- `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest,PracticeGameHubTest" test`
- `mvn -q test`

## Defaults

- 锁滚动仅作用于 `practice` 页面，不改变 `game/analysis`。
- 题目详情默认收起，不提供展开交互（后续如需可增量补充）。
