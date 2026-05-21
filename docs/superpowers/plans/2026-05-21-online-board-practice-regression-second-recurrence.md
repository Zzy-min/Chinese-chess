# Online 棋盘二次回归修复实施计划

关联设计：`docs/superpowers/specs/2026-05-21-online-board-practice-regression-second-recurrence.md`

## 1. 代码改造

1. 在 `src/main/resources/online/app.js` 新增通用测量函数（pane 剩余空间）。
2. `fitBoardToViewport` 改为先计算可用空间，再生成 `fitKey`。
3. `fitBoardToHost` 增加可选 `availableWidth/availableHeight` 参数，优先使用测量值。
4. 保留现有最小格子与 1px 收敛逻辑。

## 2. 静态验证

1. `node --check src/main/resources/online/app.js`
2. `mvn -q -DskipTests compile`

## 3. 视觉验收（真后端）

1. 使用 Playwright 真实后端数据跑矩阵：
   - `practice/game/analysis`
   - `XIANGQI/GOMOKU`
   - `1366x768`, `1536x864`, `1920x1080`, `390x844`
2. 输出 `playwright-real-metrics.json`，重点检查：
   - `pane_scroll_fail == 0`
   - `fit_width_fail == 0`
   - `fit_height_fail == 0`

## 4. 部署与公网验收

1. 合并到 `main` 并推送。
2. 执行 `tools/deploy_production_vps.py`。
3. 核对公网资源版本和关键代码片段（fitBoardToViewport + boardPane hidden）。

## 5. 回滚点

1. `src/main/resources/online/app.js`
2. 新增 docs 文件可独立回滚，不影响运行逻辑
