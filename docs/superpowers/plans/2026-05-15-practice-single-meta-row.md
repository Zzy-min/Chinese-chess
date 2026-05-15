# Practice 单行状态条实施计划

关联设计：`docs/superpowers/specs/2026-05-15-practice-single-meta-row.md`

## 1. 改 practice 视图结构

1. 合并 `gameMetaRow` 与 `practiceInfoLine`
2. 改成单个 `practiceMetaLine`

## 2. 改 practice CSS

1. `practiceMetaLine` 禁止换行
2. 加横向滚动兜底
3. practice grid 行数从 5 行收成 4 行

## 3. 验证

1. 本地语法与 `PublicSiteServerTest`
2. Playwright 宽屏和较矮视口进入 practice
3. 验证顶栏单行、棋盘完整、AI 应手后尺寸不回退

## 4. 部署

1. bump 资源版本
2. 提交、推送、部署
3. 线上复测
