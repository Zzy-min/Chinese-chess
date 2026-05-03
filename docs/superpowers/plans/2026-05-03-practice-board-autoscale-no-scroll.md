# Practice 棋盘无内滚动与自动缩放实施计划

关联设计：`docs/superpowers/specs/2026-05-03-practice-board-autoscale-no-scroll.md`

## 实施步骤
1. 调整 `src/main/resources/online/app.css`
   - `practice` 棋盘 pane 固定为 5 行结构，棋盘承载区占剩余空间。
   - `practice` 棋盘 pane 关闭内部滚动。
   - 保留移动端触摸防误滚策略。
2. 调整 `src/main/resources/online/app.js`
   - 移除 `fitPracticeBoardToViewport` 的桌面早退条件。
   - 基于 `board-host` 可用宽高计算象棋/五子棋目标单元格。
   - fit key 纳入容器宽高，保证布局变化时重新计算。
3. 验证
   - `node --check src/main/resources/online/app.js`
   - `mvn -q -DskipTests compile`
   - 静态检查 CSS 规则与 `fitPracticeBoardToViewport` 逻辑。

## 回滚
仅回滚 `app.css` 与 `app.js` 两个文件。

