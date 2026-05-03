# Deploy 403 + board.js 404 修复实施计划

关联设计：`docs/superpowers/specs/2026-05-03-deploy-403-and-boardjs-contract-fix.md`

## 实施步骤
1. 更新 `tools/deploy_production_vps.py` 的 `public_check()` 请求头与诊断日志。
2. 在 `src/main/java/com/xiangqi/web/PublicSiteServer.java` 添加 `board.js` 路由与处理函数。
3. 在 `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java` 添加 `board.js` 路由与处理函数。
4. 更新 `src/main/resources/online/index.html`，恢复 `board.js` 引用。

## 验证步骤
1. `mvn -q -DskipTests compile`。
2. 本地静态检查：
   - `index.html` 包含 `board.js` 与 `app.js`，且顺序正确。
   - 两个 Server 源码都存在 `/assets/site/board.js` 路由。
3. 线上只读核验：
   - `/online/assets/site/board.js` HTTP 状态。
   - `/online` HTML 片段是否包含 `board.js`。

## 回滚方案
1. 单文件回滚：`deploy_production_vps.py`。
2. 服务路由回滚：`PublicSiteServer`、`OnlineSiteServer`、`online/index.html`。
3. 如已部署异常，回退至上一稳定提交并重新执行 deploy 脚本。
