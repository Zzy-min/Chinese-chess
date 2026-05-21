# Online 棋盘二次回归修复设计（practice 裁切再现）

日期：2026-05-21

## 背景

线上再次出现 `#/practice/*` 棋盘底部裁切。当前页面表现是：

1. 棋盘主体可见但底部被截断。
2. 走子记录区正常。
3. 路由属于 `practice`，且存在 meta/status/buttons 顶底信息行。

## 根因

当前统一缩放逻辑以 `boardHost.clientHeight` 作为可用高度。`practice` 布局下这会触发循环依赖：

1. `boardHost` 的最终高度会受棋盘内容尺寸反向影响。
2. 缩放逻辑再读取该高度，会高估可用空间。
3. 棋盘虽然“适配了 host”，但加上同 pane 内其它行后，总高度超过 pane，最终被外层 `overflow:hidden` 裁切。

## 目标

1. 继续保持全棋盘页统一缩放（`practice/game/analysis`）。
2. 棋盘 pane 无内滚动；记录区可滚动。
3. 在存在 meta/status/actions 行时，棋盘按 pane 剩余空间缩放，不再底部裁切。
4. 保持最小格子策略：象棋 12px，五子棋 10px。

## 方案

### 1. 可用空间测量改为 pane 驱动

新增通用测量函数：

1. 以 `boardPane.clientHeight` 为总高。
2. 扣除 `boardHost` 兄弟元素高度。
3. 扣除 grid `row-gap` 总和。
4. 得到 `boardHost` 行理论可用高度，替代单纯 `host.clientHeight`。

宽度仍优先取 `host.clientWidth`，异常时回退到 pane 宽度。

### 2. 通用缩放保持不变，仅替换输入

`fitBoardToHost` 继续：

1. 读棋盘外框开销（padding+border）。
2. 按 `min(base, byWidth, byHeight)` 计算目标格子。
3. 按 1px 递减收敛，直到容纳或到最小值。

### 3. 触发策略不变

仍在以下时机重算：

1. render 后。
2. resize。
3. 棋盘局部重绘后（practice/game 实时刷新）。
4. 移动端棋盘/记录页签切换后的 render。

## 非目标

1. 不改后端接口/协议。
2. 不改走子规则与棋盘语义层（河界、宫线、标记等）。
3. 不改首页或非棋盘路由布局。

## 验收

1. `practice/game/analysis` × `XIANGQI/GOMOKU` × 4 视口下：
   - `boardFitsWidth=true`
   - `boardFitsHeight=true`
   - `paneScrollableY=false`
2. 重点确认 `practice` 大屏和移动屏不再底部裁切。
