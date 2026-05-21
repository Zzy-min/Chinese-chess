# Online Board Flicker Fix - FitKey Must Bind To DOM Instance

## Summary
棋盘页存在闪动：渲染链会重建棋盘 DOM，但 `boardFitKey` 仅按 route/viewport 维度缓存，导致新建棋盘首帧可能跳过 fit，随后延迟强制 fit 再次触发，表现为明显闪动。

## Goal
让每次新建棋盘 DOM 都在首帧被正确 fit，且避免无意义延迟强制 fit 造成二次重算闪动。

## Scope
- `src/main/resources/online/app.js` 适配判定与重算调度。
- 不改后端、不改棋规、不改页面结构。

## Design
1. `fitBoardToViewport` 的跳过条件改为“双条件”：
   - 全局 `state.boardFitKey` 命中；且
   - 当前棋盘元素的 `data-fit-key` 也命中。
2. fit 成功后写入 `board.dataset.fitKey = fitKey`，确保 DOM 实例级判定。
3. 去掉每次 render 后无条件 `scheduleBoardRefit`，改成只在 host/board 不可测或可用空间为 0 时补一次延迟重算。

## Acceptance
- practice/game/analysis 在持续轮询状态下不再出现棋盘闪动。
- 棋盘仍完整显示，board pane 无内滚动。
