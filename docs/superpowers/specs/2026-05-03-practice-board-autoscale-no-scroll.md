# Practice 棋盘无内滚动与自动缩放设计

日期：2026-05-03

## 问题
`#/practice/*` 页面在部分桌面分辨率下棋盘会被裁切，且当前修复依赖内部滚动查看剩余区域，不符合“完整显示棋盘且不滚动”的目标。

## 目标
1. `practice` 页棋盘区域不出现内部滚动条。
2. 棋盘（象棋/五子棋）始终完整显示在可视区域内。
3. 视口变化或布局变化后自动重新缩放。
4. 仅改 `practice` 路由，不影响在线对局 `#/game/*` 的既有体验。

## 根因
1. 旧逻辑仅在 `<=980px` 时执行 `fitPracticeBoardToViewport`，桌面端不缩放。
2. `boardPane` 通用规则带来滚动语义，`practice` 未建立“棋盘承载区占剩余空间”的稳定布局约束。

## 方案
1. `practice` 棋盘 pane 改为 5 行网格：`meta / info / status / board-host / actions`，其中 `board-host` 为 `minmax(0,1fr)`。
2. 禁用 `practice` 棋盘 pane 内部滚动，保留触摸限制（防误触滚动）。
3. `fitPracticeBoardToViewport` 改为全视口生效（含桌面），基于 `board-host` 的实时可用宽高计算目标单元格尺寸：
   - 象棋：`target = min(base, byWidth, byHeight)`，并限制最小值。
   - 五子棋：同上，使用各自外框常量。
4. fit key 增加 `board-host` 实际尺寸，避免窗口尺寸不变但布局变化时漏算。

## 验收标准
1. `practice` 页不存在棋盘区内部滚动条。
2. 棋盘始终完整可见，无底部裁切。
3. 浏览器窗口缩放、横竖切换后棋盘自动调整且仍完整。
4. `mvn -q -DskipTests compile` 通过，`node --check src/main/resources/online/app.js` 通过。

