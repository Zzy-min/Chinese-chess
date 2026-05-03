# Practice 棋盘被裁切修复设计

日期：2026-05-03

## 问题
在 `#/practice/*` 页面，桌面端出现“棋盘不完整（下半截被截断）”。

## 复现线索
- 路由：`practice`
- 现象：棋盘容器高度不足时底部不可见，且无法滚动看到剩余区域。
- 样式证据：
  - `.site.route-practice-locked .shell { overflow:hidden; }`
  - `.site.route-practice-locked .boardPane--practice { overflow:hidden; }`

## 根因
`practice` 路由把容器滚动能力在所有视口下都锁死；桌面端并未启用移动端缩放策略（`fitPracticeBoardToViewport` 只在 `<=980px` 生效），导致棋盘高度超过可视 pane 后直接被裁切。

## 目标
1. 桌面端保证棋盘完整可见（至少可滚动查看完整棋盘）。
2. 保留移动端防误触/锁滚动策略。
3. 最小改动，不改变棋盘渲染逻辑。

## 方案
1. 将 `.site.route-practice-locked .boardPane--practice` 默认改为 `overflow:auto`。
2. 将 `overflow:hidden + touch-action:none + overscroll-behavior:contain` 限定到 `@media (max-width:980px)`。
3. 保持现有 `fitPracticeBoardToViewport` 逻辑不变。

## 验收
1. 桌面端 `practice` 页棋盘不再被截断。
2. 移动端仍保持锁滚动与触摸行为。
3. 关键样式查询可见规则分层（桌面 auto、移动 hidden）。
