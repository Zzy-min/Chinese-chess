# Online 棋盘样式与学习内容充实实施计划

**Goal:** 将五子棋/象棋在线棋盘视觉对齐真实棋盘风格，并把学习页从“摘要列表”升级为“可阅读的知识与残局内容”。

**Architecture:** 仅改 `online` 前端资源与学习种子 JSON；保留既有 API 与认证交互。

**Tech Stack:** vanilla JS, CSS, JSON resources, Maven/JUnit

---

### Task 1: 棋盘样式增强

**Files**
- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`

- [ ] 调整象棋对局棋盘渲染结构（楚河汉界、九宫、圆形棋子）
- [ ] 调整五子棋棋盘样式（交叉线、星位、棋子质感）
- [ ] 保持棋种分发与未知棋种保护逻辑不变

### Task 2: 学习内容具体化

**Files**
- Modify: `src/main/resources/online/learn-content.seed.json`
- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`

- [ ] 为教程添加目标/要点/示例走法/练习清单
- [ ] 为残局添加局面/目标/提示/参考解法
- [ ] Learn 页面渲染上述详情内容并保持完成按钮可用

### Task 3: 验证

**Files**
- No new test files expected

- [ ] 运行 `mvn -q test`
- [ ] 手动检查 `/online#/learn`、`/online#/practice/...` 棋盘与学习内容展示
