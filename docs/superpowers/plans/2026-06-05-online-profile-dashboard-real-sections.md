# 个人页真实分区与聚合后端能力实施计划

## 实施步骤

1. 后端新增 `GET /online/api/profile/dashboard`
   - 聚合 summary / recentGames / activity / preferences / learnProgress
   - 增加 notifications 与 achievements 派生函数

2. 前端路由扩展
   - 解析 `#/me/*` 子路由
   - 增加当前子页状态高亮

3. 个人页 UI 重构
   - 把现有侧栏占位入口全部改成真实子页
   - 为 records / study / inbox / achievements / settings / help 增加独立渲染函数

4. 测试与验收
   - Java 测试覆盖 dashboard 聚合输出
   - `node --check`
   - `mvn -q "-Dtest=OnlineStoreTest,PublicSiteServerTest" test`
   - 浏览器逐页验证 `#/me/*`

## 风险

- 现有 `currentRoute()` 对 `learn` 做了特判，新增 `me` 子路由时要避免影响原 learn 行为。
- dashboard 聚合若直接复用多个现有接口语义，需保证无登录时仍严格返回 `401`。
- 前端不要把 `settings` 做成假表单；应直接复用已存在的真实偏好写回能力。

## 回滚点

- 若 dashboard 聚合实现出错，可暂时保留旧 `#/me` 总览页，同时下线 `#/me/*` 子页导航。

## 验证矩阵

- 未登录访问 `#/me`：看到登录提示，不泄露 dashboard 数据
- 已登录访问：
  - `#/me`
  - `#/me/records`
  - `#/me/study`
  - `#/me/inbox`
  - `#/me/achievements`
  - `#/me/settings`
  - `#/me/help`

验收点：

1. 每个分区都能渲染真实内容或真实空态
2. 不再跳去 `learn/watch/community/help` 充当个人页内容
3. 设置页修改音效/主题/翻转后刷新仍保留
