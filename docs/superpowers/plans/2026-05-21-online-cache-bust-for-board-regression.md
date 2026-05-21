# Plan: Online Static Asset Cache-Bust For Board Regression

1. 将 `index.html` 中 `app.css`、`board.js`、`app.js` 的 `?v=` 从 `20260516c` 升级到 `20260521a`。
2. 本地校验：`node --check src/main/resources/online/app.js`（确保脚本无语法问题）。
3. 提交并部署到 VPS。
4. 线上验收：
   - `/online` 返回新版本号。
   - 新版 `app.js` 包含 `measureBoardHostSpace`。
   - Playwright 关键视口检查 practice 页棋盘完整显示、无 pane 内滚动。

Rollback
- 若异常，可将 `?v=` 回退并重新部署上一稳定 commit。
