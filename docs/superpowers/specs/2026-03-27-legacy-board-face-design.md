# Legacy 首页棋盘空白缺陷设计

## 1. 背景与问题

用户反馈：首页旧棋盘进入 AI 对局后，中间棋盘区域为空白，但状态条、按钮和 `/api/state` 数据都正常。

已确认根因：

- `web/index.html` 中两个 `.boardFace` 容器缺少 `data-board-face` 属性
- `web/app.js` 中 `syncGamePanels(...)` 没有给 `#flipStage` 写入 `data-game`
- `web/app.css` 仅在 `flipStage[data-game="..."] [data-board-face="..."]` 匹配时才显示对应画布

因此首页旧棋盘的两个 canvas 一直处于 `display:none`，导致页面看起来像“棋盘没渲染”。

## 2. 目标

- 修复首页旧棋盘视图的画布显示条件
- 确保中国象棋和五子棋在首页切换时都能显示正确棋盘面
- 增加资源契约测试，防止 HTML 和 JS 再次脱节

## 3. 范围与非目标

### 3.1 本期范围

- 修改 `src/main/resources/web/index.html`
- 修改 `src/main/resources/web/app.js`
- 新增资源契约测试
- 做首页旧棋盘的真实浏览器验证

### 3.2 非目标

- 不改首页登录流程
- 不改 `/api/state` 协议
- 不重做首页视觉布局

## 4. 方案

最小修复：

1. 在 `index.html` 的两个 `.boardFace` 节点上补 `data-board-face="XIANGQI"` 与 `data-board-face="GOMOKU"`
2. 在 `syncGamePanels(...)` 中同步写入 `flipStage.dataset.game = g`
3. 新增一个资源契约测试，断言 HTML 与 JS 同时包含该机制需要的关键标记

## 5. 验收

- 资源契约测试先失败后通过
- `mvn -q test` 通过
- 浏览器中点击首页“中国象棋”入口后，旧棋盘不再空白
- Playwright/DOM 验证显示当前激活的 `.boardFace` 为 `display:flex`
