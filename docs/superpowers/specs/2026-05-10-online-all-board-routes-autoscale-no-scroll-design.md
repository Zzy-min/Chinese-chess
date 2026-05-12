# Online 全棋盘页裁切修复设计

日期：2026-05-10

## 背景与问题

当前 `#/practice/*` 已有棋盘自适应逻辑，但 `#/game/*`、`#/analysis/*` 没有同等的可测量棋盘承载区与统一缩放入口。在部分桌面/移动视口下，棋盘会出现底部裁切或局部超出。

## 目标

1. 统一修复 `practice`、`game`、`analysis` 三类棋盘页的底部裁切问题。
2. 棋盘容器不出现内部滚动条；走子记录区保持可滚动。
3. 以“完整显示优先”为准则自动缩放棋盘。
4. 不改后端协议与棋局规则，不改朝向/河界/落子语义。

## 约束与范围

1. 棋盘最小单元格：
   - 象棋：12px
   - 五子棋：10px
2. 只调整在线页渲染容器与前端缩放算法。
3. 不扩展到首页静态展示棋盘。

## 设计方案

### 1. 统一棋盘承载布局契约

1. 为三类棋盘 pane 使用显式语义类：
   - `boardPane--game`
   - `boardPane--practice`
   - `boardPane--analysis`
2. 三类 pane 均使用 grid 行模板，棋盘承载行为 `minmax(0, 1fr)`。
3. 三类棋盘 host 统一使用 `.boardHost`，作为缩放测量基准。
4. `boardPane` 禁内滚动，记录 pane 的 `moves` 继续滚动。

### 2. 缩放逻辑泛化

1. 将 `fitPracticeBoardToViewport` 泛化为 `fitBoardToViewport`，覆盖 `game/practice/analysis`。
2. 使用 `boardHost.clientWidth/clientHeight` 作为可用空间。
3. 棋盘外框开销通过 `getComputedStyle` 动态计算（padding + border）。
4. 目标格子尺寸：
   - `target = min(baseCell, byWidth, byHeight)`
   - 再套最小值下限（象棋 12 / 五子棋 10）
5. 安全收敛：若目标值应用后仍超出 host，则按 1px 递减直到容纳或到最小值。
6. 对 `host` 为隐藏态或零尺寸时跳过本轮，等待下一次渲染/resize 重算。

### 3. 触发时机

1. 首次渲染后触发。
2. `resize` 触发。
3. 路由切换触发。
4. 移动端棋盘/记录切页签导致布局变化后触发（经重渲染）。
5. 分析步进重渲染后触发。

## 验收标准

1. 三类棋盘页均无底部裁切。
2. 棋盘 pane 无滚动条，记录列表可滚动。
3. 缩放后棋子、河界、宫线、落子标记层级与位置不异常。
4. 在窗口缩放与页签切换后持续稳定，不出现“首次正常、再次裁切”。

