# agy：轻棋局前端开修（P0 死循环 + 结算/Toast + 移动端）

工作区：`/workspace/Chinese-chess`。前端主要在 `src/main/resources/online/`（`app.js` / `app.css` / `mobile.css` / `index.html` / `board.js`）。
**禁止 git push。只改与下列相关的前端资源。**

诊断：
- `/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-SUMMARY.md`
- `/workspace/voonie/docs/diag/2026-09-13-xiangqi-arena-diag-AGY.md`

## 必须修

### P0 — 异常房间路由死循环
- 错误/过期 `#room/...` 不得每秒多次递归请求。
- `loadRoom`/`loadGame` 失败后：置错误状态、停止 render 循环、展示友好 404/错误面板 +「返回大厅」按钮。

### P1 — 胜方结算弹窗
- WS/状态进入 `FINISHED`（认输/将死等）时，**胜方与负方**都应唤起结算 Modal（不要只更新局部 DOM 后 return）。

### P1 — 走子错误提示不被心跳刷掉
- 非法走子/点敌子等错误：用 Toast 或独立错误区，停留 ≥2s，不被 250ms 心跳状态栏覆盖。

### P1 — 移动端对局裁切
- 390×844 一类视口：玩家卡/棋盘/操作区尽量单屏可用，减少底部被裁；改 `mobile.css` / 布局，避免破坏桌面。

## 可选协同
若 Pi 已加 WS ping/pong 事件，接上客户端心跳与断线重连+重新 subscribe（有则做，无则在 RESULT 标待对接）。

## 交付
- `docs/fix-briefs/2026-09-13-agy-frontend-RESULT.md`（已改项、如何手测、仍待）
- 诚实不虚报

Explore→Plan→Execute。
