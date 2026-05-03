# Online 首页与 AI 棋桌入口恢复实施计划

## Goal

修复 Online 首页把 AI 对局导回旧链路的回退行为，恢复“Online 内一体化练习入口”。

## Scope

- Modify: `src/main/resources/online/app.js`
- Modify: `src/main/resources/online/index.html`

## Tasks

### Task 1: 移除 Online 首页的旧链路回退

- [ ] 更新首页文案，去掉“AI 回到站点首页/旧棋盘”描述
- [ ] 将“首页 AI 对局”卡片改为“在线 AI 练习”，按钮改为 `data-nav="learn"`
- [ ] 删除 `go-home-ai` 事件绑定
- [ ] 首页空状态文案统一为“大厅或学习页”

### Task 2: 缓存刷新与验证

- [ ] 更新 `online/index.html` 版本号
- [ ] 运行 `mvn -q test`
- [ ] 本地文本断言：`app.js` 不含 `go-home-ai` 与“旧棋盘”关键词

## Acceptance

1. `/online#/home` 不再出现跳转 `/home-ai` 的按钮和提示。
2. 用户可从首页直接进入 Online 学习/AI 练习链路。
3. 自动化测试通过，无既有链路回归。
