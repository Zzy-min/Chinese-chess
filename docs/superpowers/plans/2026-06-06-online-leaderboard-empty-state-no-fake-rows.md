# 在线前端榜单空态真实化实施计划

## 步骤

1. 前端渲染调整
   - 提取统一榜单行渲染函数。
   - 提取统一榜单空态函数。
   - 替换首页和大厅中硬编码榜单 fallback。

2. 契约测试
   - 在在线资源测试中断言不再包含假榜单用户名。
   - 断言空态文案存在。

3. 验证
   - `node --check src/main/resources/online/app.js`
   - `mvn -q "-Dtest=OnlineSiteResourceContractTest,PublicSiteServerTest" test`
   - 浏览器打开首页/大厅并检查假榜单文字不存在。

## 回滚点

如果空态样式或榜单显示异常，可以只回滚前端渲染函数与资源契约测试，不影响后端接口和数据。
