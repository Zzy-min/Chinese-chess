# Practice 人类落子音效先于 AI 思考实施计划

关联设计：`docs/superpowers/specs/2026-05-16-practice-human-move-sound-then-ai-thinking.md`

## 1. 扩展合同测试

1. 约束 optimistic practice move 仍存在
2. 约束非 immediate 首轮 polling
3. 新增对 optimistic 人类落子显式播音的合同断言

## 2. 接入 optimistic 人类落子音效

1. 在 optimistic practice move 生效后立即播放 `onlineMoveAudio`
2. 同步推进 `lastMoveSound*` 去重状态

## 3. 调整 practice 提示文案

1. `moveInFlight` 阶段不再笼统显示 `正在提交走子...`
2. 改成更符合“人类已落子，等待进入 AI 阶段”的提示

## 4. 验证

1. `node --check src/main/resources/online/app.js`
2. `mvn -q "-Dtest=OnlinePracticeAsyncMoveContractTest,PublicSiteServerTest" test`
3. 浏览器中打点确认两次独立 `move.wav` 播放和阶段顺序

## 5. 部署与复测

1. bump 在线资源版本
2. 提交、推送、部署
3. 线上用真实 practice 落子复测
