# Online 音效二进制资源修复实施计划

关联设计：`docs/superpowers/specs/2026-05-15-online-audio-binary-resource-fix.md`

## 1. 先补后端回归测试

1. 在 `PublicSiteServerTest` 中新增 `/assets/audio/move.wav` 测试
2. 用 `BodyHandlers.ofByteArray()` 读取响应
3. 断言响应头和响应体前缀与类路径源文件一致

## 2. 修正资源发送链

1. 修改 `PublicSiteServer.sendResource()`
2. 改成直接发送原始字节
3. 不改现有前端音效逻辑

## 3. 本地验证

1. 运行 `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest" test`
2. 必要时跑 `mvn -q test`
3. 本地或线上浏览器验证音频对象不再报解码错误

## 4. 部署与复测

1. bump 在线资源版本
2. 提交、推送、部署
3. 线上 practice 实际落子复测音效
