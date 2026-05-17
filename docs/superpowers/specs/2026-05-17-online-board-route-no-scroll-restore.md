# Online 全棋盘页无内滚动恢复设计

日期：2026-05-17

## 背景

真实后端 Playwright 验收（`artifacts/audit-20260517-real-backend/playwright-real-metrics.json`）显示：

1. `practice` 在 `1366x768` 与 `390x844` 出现 `paneScrollableY=true`。
2. `game/analysis` 无同类失败。

当前实现与 2026-05-10 的统一策略不一致，已回退到：

1. `.site.is-board-route .boardPane{overflow:auto}`。
2. 仅 `practice` 路由执行 `fitPracticeBoardToViewport()`。

这与已确认目标冲突：棋盘 pane 禁滚、三类棋盘页统一按 host 自适应。

## 目标

1. 统一 `#/practice/*`、`#/game/*`、`#/analysis/*` 的棋盘适配行为。
2. 棋盘容器无内滚动，完整显示优先。
3. 记录区保持可滚动。
4. 最小格子下限：象棋 12px，五子棋 10px。

## 方案

### 1. 布局契约恢复为统一棋盘路由

1. 棋盘路由下 `boardPane` 一律 `overflow:hidden`。
2. 明确三类 pane 的 grid 行模板，棋盘行固定 `minmax(0,1fr)`：
   - `boardPane--game`
   - `boardPane--practice`
   - `boardPane--analysis`
3. `.boardHost` 作为统一可测量容器，承载棋盘并居中显示。

### 2. 缩放逻辑恢复为通用入口

1. `fitPracticeBoardToViewport` 升级为 `fitBoardToViewport`，覆盖 `game/practice/analysis`。
2. 通过 host 宽高与棋盘动态外框开销（padding + border）计算目标格子尺寸。
3. 目标尺寸计算：
   - `target = min(baseCell, byWidth, byHeight)`
   - 下限分别为 12 / 10。
4. 应用目标值后执行安全收敛：若仍超出 host，按 1px 递减直至容纳或到下限。
5. 若 host 尺寸为 0 或隐藏，跳过本轮，等待下次 render/resize/tab 切换重算。

### 3. 触发时机

1. 每次渲染完成后。
2. `resize`。
3. 棋盘局部重绘（如 practice/game 实时刷新棋盘 DOM）后。
4. 移动端棋盘/记录页签切换触发重渲染后。

## 非目标

1. 不改后端接口、WS 协议与数据结构。
2. 不改棋规、朝向、河界层级、起终点标记语义。
3. 不扩展到首页静态展示棋盘。

## 验收标准

1. 三路由两棋种四视口矩阵下棋盘完整可见，无底部裁切。
2. 棋盘 pane 无滚动条；记录区可滚动。
3. 缩放后棋子、河界、宫线、标记不重叠错位。
4. resize 与页签切换后不回归。
