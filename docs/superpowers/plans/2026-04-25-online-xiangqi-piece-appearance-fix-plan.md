# Online 象棋棋子外观修复计划（2026-04-25）

## Steps
1. 调整棋子本体样式
- 文件：`src/main/resources/online/app.css`
- 内容：提高棋子尺寸占比、强化边框与阴影、优化字体比例与颜色。

2. 兼容选中态与高亮
- 文件：`src/main/resources/online/app.css`
- 内容：同步更新 `is-selected` 的阴影参数，确保视觉一致。

3. 回归验证
- 资源契约测试：`mvn -q "-Dtest=LegacyHomepageResourceContractTest" test`
- 手动视觉验证：生成 `practice` 页截图，对比修复前后棋子观感。

## Risks
- 过大尺寸可能与边线/角标冲突。
- 字号调整不当会导致小屏可读性下降。

## Rollback
- 仅单文件样式改动，可直接回退 `app.css` 对应片段。
