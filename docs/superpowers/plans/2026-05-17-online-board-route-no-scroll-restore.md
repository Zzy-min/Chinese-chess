# Online 全棋盘页无内滚动恢复实施计划

关联设计：`docs/superpowers/specs/2026-05-17-online-board-route-no-scroll-restore.md`

## 1. CSS 恢复统一策略

1. 将 `.site.is-board-route .boardPane` 从 `overflow:auto` 改为 `overflow:hidden`。
2. 增加/校正：
   - `boardPane--game` 行模板：`auto auto auto auto minmax(0,1fr) auto`
   - `boardPane--practice` 行模板：`auto auto minmax(0,1fr) auto`
   - `boardPane--analysis` 行模板：`auto auto minmax(0,1fr) auto`
3. 增加通用 `.site.is-board-route .boardHost`，保证棋盘居中且可测量。
4. 保持 `.recordPane .moves` 可滚动，不改记录体验。

## 2. JS 恢复通用适配入口

1. 引入 `fitBoardToViewport(route, force)`，替换 `fitPracticeBoardToViewport`。
2. 引入通用子函数：
   - `fitXiangqiBoardToHost`
   - `fitGomokuBoardToHost`
   - `fitBoardToHost`
   - `shrinkBoardToHost`
3. 计算逻辑：
   - 基于 host 可用空间 + 棋盘动态外框
   - 最小格子：象棋 12、五子棋 10
4. 清理与统一策略冲突的分析页“静态尺寸重置”调用路径。
5. 在 render、resize、棋盘局部刷新后统一触发 `fitBoardToViewport`。

## 3. 验证

1. 静态检查：
   - `node --check src/main/resources/online/app.js`
   - `mvn -q -DskipTests compile`
2. Playwright 真后端矩阵复测：
   - 路由：`practice/game/analysis`
   - 棋种：`XIANGQI/GOMOKU`
   - 视口：`1366x768`, `1536x864`, `1920x1080`, `390x844`
3. 通过条件：
   - `boardFitsWidth=true`
   - `boardFitsHeight=true`
   - `paneScrollableY=false`
   - `authOverlay=false`

## 4. 回滚点

1. `src/main/resources/online/app.css`
2. `src/main/resources/online/app.js`
3. 新增 docs 文件可单独回滚，不影响运行逻辑
