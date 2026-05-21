# Plan: Online Board Clip Third Recurrence - Viewport-Bound Fit

1. 修改 `measureBoardHostSpace`：引入可见区域下边界约束并收敛到较小高度。
2. 在 `render` 之后追加一次短延时 `fitBoardToViewport(..., true)` 重算。
3. 本地验证：
   - `node --check src/main/resources/online/app.js`
   - `mvn -q -DskipTests compile`
4. 线上验证：
   - 资源版本命中最新。
   - Playwright 登录后在 `#/practice/{gameId}` 验证 `boardFitsHeight=true`、`paneScrollableY=false`。
5. 提交、推送、部署并二次验收。

Rollback
- 回退到 `b35ce55` 并重新部署。
