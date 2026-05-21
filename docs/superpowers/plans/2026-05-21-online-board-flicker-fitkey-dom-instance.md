# Plan: Online Board Flicker Fix - FitKey DOM Instance

1. 修改 `fitBoardToViewport`：加入棋盘实例级 `data-fit-key` 判定与写入。
2. 将延迟重算触发改为“仅异常测量时触发”，移除每次 render 的无条件延迟重算。
3. 本地检查：
   - `node --check src/main/resources/online/app.js`
   - `mvn -q -DskipTests compile`
4. 线上验收：
   - Playwright 登录态创建 practice 局后连续观察 3-5s，确认无明显闪动。
   - 同时验证 `boardFitsHeight=true`、`paneScrollableY=false`。
5. 提交、推送、部署。
