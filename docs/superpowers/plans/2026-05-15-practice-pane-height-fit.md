# Practice 棋盘高度驱动适配实施计划

关联设计：`docs/superpowers/specs/2026-05-15-practice-pane-height-fit.md`

## 1. 实现测量收敛

1. 新增 practice pane 剩余空间测量函数。
2. 用 pane 总高度减去兄弟块与 row gap，得到稳定的可用棋盘高度。
3. 保留 `boardHost` 作为回退测量，不再作为唯一高度来源。

## 2. 改造 fit 逻辑

1. `fitXiangqiPracticeBoard()` 改为消费新的宽高测量。
2. `fitGomokuPracticeBoard()` 同步改造。
3. 保留现有刷新后写回旧 cell 再 refit 的路径。

## 3. CSS 补强

1. 仅对 practice 的 `data-live-board-host` 增加 `align-self:stretch`。
2. 明确 `overflow:hidden`，防止 host 本身撑出额外高度。

## 4. 验证

1. `node --check src/main/resources/online/app.js`
2. Playwright 宽屏视口进入 practice，落子并等待 AI 应手
3. Playwright 较矮视口进入 practice，确认棋盘继续收缩且完整显示

## 5. 部署

1. 本地验证通过后提交
2. 推送 `main`
3. 执行生产部署脚本
4. 部署后复查线上资源版本与 practice 行为
