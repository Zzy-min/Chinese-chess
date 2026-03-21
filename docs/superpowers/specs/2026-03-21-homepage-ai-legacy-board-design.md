# 首页 AI 与旧棋盘回迁设计

## 目标

把公开站点重新分成两条清晰路径：

- `/`：首页与 AI 对局入口，继续使用 `src/main/resources/web/*` 的旧棋盘与交互语言。
- `/online`：在线房间站点，继续承载双人在线对战、房间、分析与个人页。

本轮实现解决以下问题：

- AI 对局入口从学习页回到首页。
- 首页上的 AI 按钮与开局交互必须可用，不再停留在无效按钮或错误模式。
- 首页不再展示实施说明、架构说明一类文案。
- 棋盘回到原有 `web/*` 画布棋盘，不再用 `online` 里的简化棋盘。
- 双人对战不再表示“同终端 PVP”，而是显式跳转到在线大厅。

## 维持与突破的边界

本轮仍要保留的边界：

- 围棋在线仍是占位。
- `online` 里的观战、社区、学习页仍是壳层或导航占位。
- 活动房间仍是内存态，服务重启后会丢失。
- 棋钟深化 UI、深度分析 UI 仍不在本轮范围。

本轮要突破的不是这些边界，而是“首页与 AI 入口割裂、旧棋盘未接入当前持久化与登录体系”的边界。

## 产品设计

### 根路径 `/`

首页保留旧棋盘风格，但产品语义调整为：

- 默认主链路是 AI 对局。
- “在线对战”作为明确入口，跳转到 `/online`。
- 如果用户未登录，首页需要提供登录 / 注册能力，并在开始 AI 对局前完成鉴权。
- 中国象棋与五子棋都可以在首页直接发起 AI 对局。
- 首页继续支持残局、复盘、认输等旧棋盘侧边能力，但这些能力都要走当前的练习局持久化体系。

首页不再暴露“本地双人同屏对战”语义。若保留旧的模式控件，也只能把 PVP 语义替换成跳转在线大厅，不能继续启动本地 PVP 状态机。

### 在线站点 `/online`

`online` 保持现有信息架构与房间体验，只做路径迁移：

- HTML、CSS、JS 静态资源都挂到 `/online/...`
- 所有 HTTP API 改到 `/online/api/...`
- WebSocket 改到 `/online/ws`

这样可以避免与首页旧接口 `/api/*` 冲突。

## 技术设计

### 统一公开服务器

当前 `PublicWebMain` 直接启动 `OnlineSiteServer`，无法同时承载旧首页接口与在线大厅接口。本轮改成统一公开服务器，内部共享同一套依赖：

- `OnlineStore`
- `AuthService`
- `OnlineRoomHub`
- `PracticeGameHub`

路由规划：

- `/` -> 旧首页 `web/index.html`
- `/assets/ui/*` -> 旧首页资源 `web/*`
- `/api/*` -> 首页旧接口兼容层
- `/api/auth/*` -> 首页登录鉴权接口
- `/online` -> 在线站点首页
- `/online/assets/site/*` -> 在线站点资源
- `/online/api/*` -> 在线大厅接口
- `/online/ws` -> 在线站点 WebSocket

### Legacy 首页兼容层

新增一个专门的首页兼容层，负责把旧前端期望的接口和状态，翻译成 `PracticeGameHub` 当前的运行时与归档结构。

职责：

- 维护浏览器会话级状态：
  - 当前 `gameId`
  - 当前选中格 `selectedRow/selectedCol`
  - 复盘开关与步数
  - 当前棋种、难度、先后手、引擎偏好
- 首页 AI 对局必须要求登录用户
- 将旧接口映射到当前练习局接口：
  - `/api/state`
  - `/api/new`
  - `/api/endgame`
  - `/api/click`
  - `/api/surrender`
  - `/api/review/start|prev|next|exit`
- 输出与旧 `web/app.js` 兼容的状态 JSON

不复用 `WebXiangqiServer` 里的旧 Session 与旧落子状态机，避免引入第二套未持久化逻辑。

### 兼容状态格式

兼容层要输出旧首页已依赖的字段，包括但不限于：

- `started`
- `mode` 固定为 `PVC`
- `difficulty` / `difficultyText`
- `pvcHumanColor`
- `currentTurn`
- `gameOver`
- `result`
- `selectedRow` / `selectedCol`
- `reviewMode` / `reviewMoveIndex` / `reviewMaxMove`
- `recentMoves`
- `board`
- 象棋与五子棋的引擎可用性字段

其中：

- `board` 必须回到旧格式：
  - 象棋：`{ name, color }`
  - 五子棋：`{ name, color }`
- `recentMoves` 也必须兼容旧画布动画依赖的字段名。

### 旧前端调整

对 `src/main/resources/web/*` 的改动保持最小化，重点放在三件事：

1. 首页增加登录 / 注册入口与未登录提示。
2. 把“双人对战”文案与按钮替换为“在线对战”，跳转 `/online`。
3. 删除讲实施过程的说明文案，改成面向用户的入口说明。

棋盘绘制、画布交互、残局抽屉、复盘抽屉尽量保留原实现，避免再做一套新 UI。

## 测试设计

先加失败测试锁定以下行为：

- legacy 首页兼容层能为已登录用户创建 AI 对局，并返回旧状态格式。
- 首页落子后，AI 会自动回应，状态仍是旧前端可消费格式。
- 复盘接口能基于活动练习局与归档练习局前后翻步。
- `/online` 路径下的在线站点静态资源与 API 前缀仍可正常访问。

## 非目标

- 不恢复本地同屏双人对战。
- 不为围棋补全在线或 AI 对局。
- 不持久化活动房间本身。
- 不对 `online` 壳层页面做内容扩展。
