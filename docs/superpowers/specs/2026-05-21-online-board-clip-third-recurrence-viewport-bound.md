# Online Board Clip Third Recurrence - Viewport-Bound Fit

## Summary
线上仍出现 practice 棋盘底部裁切。此前使用 `boardPane` 内测量推导 `boardHost` 可用高度，但在部分真实会话中该高度仍可能被内容自举影响，导致棋盘按过大尺寸渲染并被下方裁切。

## Goal
将棋盘可用高度改为“布局测量值 + 视口可见上限”双约束，确保棋盘最终尺寸不超过当前可见区域可承载空间，杜绝底部裁切复发。

## Scope
- `src/main/resources/online/app.js` 的 board fit 测量逻辑。
- 不改棋规、不改接口、不改记录区滚动语义。

## Design
1. 在 `measureBoardHostSpace` 中新增 viewport/shell/pane 边界约束：
   - 计算 `hostRect.top` 到可见底边（`visualViewport/innerHeight`、`.shell` 底边、`.boardPane` 底边）之间的可用高度。
2. 最终 `availableHeight` 取“pane 推导高度”与“可见高度上限”的较小值（正数优先），避免内容自举导致的虚高。
3. 保留现有 1px 递减收敛与最小格子下限。
4. 增加一次延迟重算（短延时）降低首帧布局抖动造成的误判概率。

## Acceptance
- practice/game/analysis 在桌面和移动视口下棋盘完整可见。
- board pane 无内滚动，moves 保持可滚动。
