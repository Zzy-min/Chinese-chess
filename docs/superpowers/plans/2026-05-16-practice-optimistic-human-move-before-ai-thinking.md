# Practice 人类落子先于 AI 思考实施计划

关联设计：`docs/superpowers/specs/2026-05-16-practice-optimistic-human-move-before-ai-thinking.md`

## 1. 先扩展前端合同测试

1. 断言 `online/app.js` 存在 `applyOptimisticPracticeMove`
2. 继续约束 practice 非 immediate 首轮 polling
3. 继续约束默认异步思考文案

## 2. 实现 optimistic practice snapshot

1. 为 Xiangqi / Gomoku 各自实现本地一步落子
2. 封装到 `applyOptimisticPracticeMove()`
3. 不在 optimistic 阶段提前开启 practice polling

## 3. 接入 sendMove()

1. 请求发出前用 optimistic snapshot 更新 `state.game`
2. 成功后用服务端 snapshot 覆盖
3. 失败时回退原 snapshot

## 4. 验证

1. `node --check src/main/resources/online/app.js`
2. `mvn -q "-Dtest=OnlinePracticeAsyncMoveContractTest,PublicSiteServerTest" test`
3. 浏览器里验证：
   1. `sendMove()` 发出后 50-100ms 内棋盘已显示人类新局面
   2. 服务端响应后再进入 `AI 思考中...`
   3. 后续轮询再出现 AI 应手

## 5. 部署与复测

1. bump 在线资源版本
2. 提交、推送、部署
3. 线上 practice 再做真实时序复测
