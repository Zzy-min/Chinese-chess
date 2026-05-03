# Online 象棋棋盘线稿化与 AI 练习直达实施计划

## Goal

修复象棋棋局显示异常并将首页 AI 练习改为一键直达对局。

## Scope

- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`
- Modify: `src/main/resources/online/index.html`
- Modify: `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

## Tasks

### Task 1: 象棋棋盘结构与字符规范化

- [ ] 在 `renderXiangqiBoard/renderStaticXiangqiBoard` 增加线段类与标记点类
- [ ] 新增棋子字符规范化函数，替换直接渲染 `cell` 的逻辑
- [ ] 保持 `gameType` 分发逻辑不回退

### Task 2: 象棋棋盘样式改为线稿图

- [ ] 将 `xiangqiBoard/xiangqiCell` 样式替换为黑白线稿风格
- [ ] 补充标记点、九宫线与河界样式
- [ ] 保留移动端可读性（响应式字号与格距）

### Task 3: 首页 AI 练习直达

- [ ] 首页卡片按钮改为“直接进入 AI 对局”
- [ ] 新增 `quick-start-ai-practice` 事件绑定与处理逻辑
- [ ] 登录态直开局，未登录弹登录层

### Task 4: 验证与缓存刷新

- [ ] 更新 `index.html` 静态资源版本号
- [ ] 更新资源契约测试断言
- [ ] 运行 `mvn -q test`
- [ ] 使用 Playwright 产出首页与象棋对局截图
