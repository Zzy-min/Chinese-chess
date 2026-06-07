# 在线快速匹配真实后端实施计划

## 步骤

1. 后端接口
   - 在 `PublicSiteServer` 增加 `POST /online/api/rooms/quick-match` 路由。
   - 复用登录校验和创建房间限流。
   - 返回 `{ matched, room, game? }`。

2. 房间匹配逻辑
   - 在 `OnlineRoomHub` 增加 `quickMatch`。
   - 优先匹配同棋种、公开、未开局、未满员、非本人创建的候场房。
   - 若找到候场房，加入用户并自动置为已准备；若双方已准备则启动游戏。
   - 若找不到，创建公开房并自动置发起者为已准备。

3. 前端入口
   - 增加 `quickStartPublicMatch`。
   - 将首页与象棋模式页的“快速匹配/实时匹配/立即开局”接到 quick-match。
   - 保留明确的 AI 练习入口继续使用 practice 接口。

4. 验证
   - `node --check src/main/resources/online/app.js`
   - `mvn -q "-Dtest=OnlineSiteResourceContractTest,PublicSiteServerTest" test`
   - 浏览器登录后点击快速匹配，确认进入真实房间或对局页。

## 回滚点

如快速匹配流程异常，可回滚 `quick-match` 路由、`OnlineRoomHub.quickMatch` 和前端 `quickStartPublicMatch` 绑定；现有创建房间、加入、准备、AI 练习接口不受影响。
