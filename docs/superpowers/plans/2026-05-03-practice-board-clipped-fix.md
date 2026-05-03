# Practice 棋盘裁切修复计划

关联设计：`docs/superpowers/specs/2026-05-03-practice-board-clipped-fix.md`

## 实施步骤
1. 修改 `src/main/resources/online/app.css`：
   - 桌面默认：`.site.route-practice-locked .boardPane--practice { overflow:auto; }`
   - 移动端（`max-width:980px`）：恢复 `overflow:hidden/touch-action/overscroll`。
2. 运行基础验证：
   - `mvn -q -DskipTests compile`
   - 检查 CSS 规则是否生效。
3. 线上部署后做视觉验收：
   - 桌面端 `#/practice/*` 棋盘完整。
   - 移动端无回归。

## 回滚
单文件回滚 `src/main/resources/online/app.css`。
