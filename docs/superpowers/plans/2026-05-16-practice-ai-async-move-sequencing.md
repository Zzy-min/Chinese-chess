# Practice AI 异步落子时序修复实施计划

关联设计：`docs/superpowers/specs/2026-05-16-practice-ai-async-move-sequencing.md`

## 1. 先补前端合同测试

1. 新增针对 `online/app.js` 的只读合同测试
2. 先断言 practice 人类落子后不应再触发 `immediate=true` 的首轮轮询

## 2. 修改 practice 首轮轮询时序

1. 调整 `sendMove()` 中 practice 的轮询启动方式
2. 保留后续快速轮询节奏，不改后端 AI 异步计算模型

## 3. 修正文案

1. 更新 practice 默认状态提示
2. 避免继续暗示“后端会立刻返回 AI 应手”

## 4. 验证

1. 运行新增合同测试和 `PublicSiteServerTest`
2. 用浏览器真实创建 AI 练习并落子
3. 验证先出现人类落子，再出现 `AI 思考中...`，最后 AI 应手

## 5. 部署与复测

1. bump 在线资源版本
2. 提交、推送、部署
3. 线上再次做真实落子复测
