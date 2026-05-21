# Online Static Asset Cache-Bust For Board Regression

## Summary
近期 practice 棋盘裁切修复已上线，但 `index.html` 仍引用旧 query version（`v=20260516c`），可能导致 Cloudflare 边缘或浏览器端继续命中旧缓存，出现“部分用户复现、部分用户正常”。

## Goal
通过提升 `/online` 静态资源版本参数，强制全量客户端拉取最新 `app.js/board.js/app.css`，消除缓存不一致导致的伪回归。

## Scope
- 修改 `src/main/resources/online/index.html` 三处资源 query version。
- 不修改业务逻辑与接口。

## Non-Goals
- 不变更路由、棋盘算法、后端协议。
