# 首页 AI 与旧棋盘回迁实施计划

## 阶段 1：测试先行

- 新增 legacy 首页兼容层测试：
  - 已登录用户可创建象棋 AI 对局
  - 返回 JSON 含 `started=true`、`mode=PVC`、`pvcHumanColor`
  - 首页点击选子与落子后，AI 自动回应
  - 复盘开始 / 前进 / 后退 / 退出可用
- 新增统一公开服务器路由测试：
  - `/` 返回旧首页
  - `/online` 返回在线站点
  - `/online/api/auth/me` 与 `/online/api/site/bootstrap` 前缀有效

## 阶段 2：后端兼容层

- 新增 legacy 首页会话运行时，保存首页所需 UI 状态。
- 将旧接口 `/api/*` 映射到 `PracticeGameHub`。
- 把 `PracticeGameHub` 快照转换成旧首页状态格式。
- 登录接口复用现有 `AuthService`，允许首页直接登录 / 注册。

## 阶段 3：统一公开服务器

- 重构当前 `OnlineSiteServer` 或新增统一服务器承载两套路由。
- 在线大厅所有资源、API、WebSocket 挂到 `/online` 前缀。
- `PublicWebMain` 改为启动新的统一入口。

## 阶段 4：旧首页前端调整

- 修改 `web/index.html`：
  - 首页加入 AI 入口与在线对战入口
  - 删除实施说明文案
  - 补登录区域
- 修改 `web/app.js`：
  - 接首页登录接口
  - 将旧的本地 PVP 行为改成 AI / 在线分流
  - 兼容 `/online` 跳转
- 按需修改 `web/app.css`，但不重做一套新视觉。

## 阶段 5：在线站点路径迁移

- 修改 `online/index.html` / `online/app.js`
- 所有请求从 `/api/...` 切换到 `/online/api/...`
- WebSocket 从 `/ws` 切换到 `/online/ws`

## 阶段 6：验证与交付

- 运行新增测试，确认先红后绿
- 运行 `mvn -q test`
- 运行 `mvn -q -DskipTests package`
- 做根路径与 `/online` 的基础浏览器检查
- 创建新分支并推送到 `origin`
