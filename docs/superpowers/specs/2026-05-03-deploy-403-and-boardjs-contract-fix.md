# Deploy 403 + Online board.js 404 资源契约修复设计

日期：2026-05-03
范围：`tools/deploy_production_vps.py`、`PublicSiteServer`、`OnlineSiteServer`、`online/index.html`

## 背景
1. 部署脚本在源站健康时仍可能因 Cloudflare 对默认 Python UA 返回 403，导致“假失败”。
2. 线上 `/online` 页面不再引用 `board.js`，且 `/online/assets/site/board.js` 返回 404，资源契约断裂。

## 问题定义
- `public_check()` 使用 `urllib.urlopen(url)`，请求头过于机器化，易被 WAF 拦截。
- 资源链路缺失：
  - `online/index.html` 缺少 `board.js` 引用；
  - 两个服务器入口（`PublicSiteServer` / `OnlineSiteServer`）未暴露 `board.js` 资源路由。

## 目标
1. 部署脚本的公网回检更接近真实浏览器请求，减少 403 误报。
2. 恢复 `board.js` 资源契约：页面引用 + 路由可达。
3. 保持改动最小，不触及业务玩法逻辑。

## 非目标
1. 不在本次直接修改 Cloudflare Worker 控制台变量（需要外部凭据）。
2. 不重构前端棋盘逻辑，仅恢复资源可加载。

## 方案
1. `deploy_production_vps.py`
   - 为 `public_check` 增加浏览器样式请求头（UA/Accept/Accept-Language/Cache-Control）。
   - 输出 `status` 与 `final_url` 便于诊断。
2. `PublicSiteServer` 与 `OnlineSiteServer`
   - 增加 `GET /assets/site/board.js` 路由与 `handleBoardJs()`。
3. `src/main/resources/online/index.html`
   - 在 `app.js` 前增加 `board.js` 脚本引用（保持同版本 query 参数）。

## 验收标准
1. 本地编译通过。
2. 本地资源契约检查：`/online/assets/site/board.js` 可返回 200（通过本地服务或集成测试链验证）。
3. 部署脚本 `public_check` 在 Cloudflare 下不再稳定 403（以浏览器 UA 请求策略验证）。
