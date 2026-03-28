# Online Home Entry And Review Fix Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/online#/home` 设为主入口，同时修复首页 AI 对局反馈和 Online 中国象棋回顾棋盘的错误观感。

**Architecture:** 通过 `PublicSiteServer` 切换根路径路由，把旧首页 AI 保留在新兼容路径；通过首页旧棋盘前端状态与 practice 侧日志补强“一步一响应”感知；通过 Online CSS/模板拆分出中国象棋专用静态回顾棋盘，避免继续使用通用方格样式。

**Tech Stack:** Undertow, Java 17, vanilla JS, CSS, Maven, JUnit 5

---

### Task 1: 固定入口路由

**Files:**
- Modify: `src/main/java/com/xiangqi/web/PublicSiteServer.java`
- Modify: `src/test/java/com/xiangqi/web/PublicSiteServerTest.java`
- Modify: `src/main/resources/online/app.js`

- [ ] 把 `/` 改成重定向到 `/online#/home`
- [ ] 给旧首页增加 `/home-ai` 入口
- [ ] 把 Online 页中 `go-home-ai` 的跳转从 `/` 改成 `/home-ai`
- [ ] 更新 `PublicSiteServerTest` 断言新入口行为

### Task 2: 修复首页 AI 对局反馈

**Files:**
- Modify: `src/main/resources/web/app.js`
- Modify: `src/main/java/com/xiangqi/online/practice/PracticeGameHub.java`
- Modify: `src/test/java/com/xiangqi/web/LegacyHomeSessionHubTest.java`

- [ ] 在首页旧棋盘前端增加动作进行中状态
- [ ] 为中国象棋首页 AI 对局增加本地即时落子回显
- [ ] 人机走子请求发出后显示 “AI 思考中...”
- [ ] 请求完成后恢复棋盘交互
- [ ] 在 `PracticeGameHub` 增加开局、落子、AI 应手耗时和终局日志
- [ ] 补测试，确认人机一步后仍在一次状态里返回 AI 应手结果

### Task 3: 修复 Online 中国象棋回顾棋盘样式

**Files:**
- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/app.css`

- [ ] 为中国象棋静态回顾棋盘拆出专用渲染函数 / 类名
- [ ] 增加河界、九宫、木色底板和圆形棋子样式
- [ ] 保持五子棋静态回顾棋盘不变
- [ ] 确保分析页步进按钮与棋盘联动不变

### Task 4: 验证

**Files:**
- Test: `src/test/java/com/xiangqi/web/PublicSiteServerTest.java`
- Test: `src/test/java/com/xiangqi/web/LegacyHomeSessionHubTest.java`

- [ ] 运行定向测试
- [ ] 运行 `mvn -q test`
- [ ] 启动本地或服务器实例验证 `/`、`/home-ai`、`/online#/home`
- [ ] 检查服务器日志中新增的人机对局记录
