# XiangqiGame 全仓库四维体检报告（2026-04-25）

## 1. 审计范围与基线
- 仓库路径：`D:\claude项目\XiangqiGame`
- 覆盖维度：功能性、交互性、逻辑性、外观
- 覆盖模块：`web`、`online`、Java 后端、`services/go-engine`、部署脚本、Tunnel 公网链路
- 工作区状态：脏工作区（本报告不回滚现有改动）

### 基线证据
- 自动化：
  - `mvn -q test`：通过
  - `python -m pytest -q services/go-engine/tests`：`21 passed`
- 本地可达：
  - `http://127.0.0.1:18388/online`：`200`
- 静态资源版本：
  - `/online/assets/site/app.js?v=20260422a`
- 视觉证据：
  - `artifacts/audit-20260425/desktop-home.png`
  - `artifacts/audit-20260425/desktop-practice-before-move.png`
  - `artifacts/audit-20260425/desktop-practice-after-move.png`
  - `artifacts/audit-20260425/desktop-game-u1.png`
  - `artifacts/audit-20260425/desktop-game-u2.png`
  - `artifacts/audit-20260425/mobile-practice.png`

## 2. 结论概览
- 主要功能链路可用：注册/登录、建房/入房、开局、落子、分析、练习、学习进度写入均可跑通。
- 在线对局朝向与河界文案在 `#/game/*` 生效：红黑视角映射与“楚河/汉界”方向符合预期。
- 发现 5 条问题（`P0:1, P1:1, P2:2, P3:1`），其中 2 条会直接影响可用性与稳定性。

## 3. 问题清单（P0-P3）

### AUD-P0-001
- 严重级别：`P0 阻断`
- 维度：功能性 / 运维可用性
- 问题：公网出现 Cloudflare Tunnel 1033（站点不可用）
- 复现步骤：
  1. 确认本地源站 `127.0.0.1:18388` 正常；
  2. 访问 `https://xiangqiarena.com/online`。
- 实际结果：
  - 公网返回 `530`，正文包含 `error code: 1033`；
  - 同时本地 `/online` 返回 `200`；
  - 现场时段无 `cloudflared` 进程。
- 期望结果：公网与本地同时可达（`200`）。
- 证据：
  - 命令输出：`public_online_code=530`、`local_online_code=200`
  - `curl https://xiangqiarena.com/` 返回 `error code: 1033`
  - 进程快照无 cloudflared；手动执行 `tools/ensure_tunnel.ps1` 后恢复 `200`
- 影响面：所有公网用户无法访问主站。
- 修复建议：
  - 启用并常驻隧道自愈守护（定时确保 `cloudflared` 存活）；
  - 增加公网探活告警（530/1033 触发自动拉起）。
- 回归用例：
  - 人工结束 `cloudflared` 后，60s 内自动恢复；
  - `https://xiangqiarena.com/online` 与 `/online/api/site/bootstrap` 持续 `200`。

### AUD-P1-001
- 严重级别：`P1 高风险`
- 维度：交互性 / 外观可用性（移动端）
- 问题：AI 练习页锁滚动后，移动端棋盘底部被裁切
- 复现步骤：
  1. 390x844 视口登录；
  2. 进入 `#/practice/{gameId}`。
- 实际结果：
  - 页面滚动锁生效（`scrollY=0` 且不可滚）；
  - 但棋盘底部超出可视区：`boardBottom=903 > viewportHeight=844`；
  - `boardPane` 为 `overflow:hidden`，导致底部棋格不可见不可点。
- 期望结果：移动端 AI 对局单屏可完整操作（至少底线棋格全部可触达）。
- 证据：
  - `artifacts/audit-20260425/mobile-practice.png`
  - 浏览器测量：`paneOverflow=hidden`、`canScrollPage=false`、`boardBottom=903`
- 影响面：手机端 AI 对局选子/落子稳定性下降，底部交互受阻。
- 修复建议：
  - 以“可用高度”驱动棋盘动态缩放（而非仅按 viewport 宽度）；
  - 移动端压缩顶部信息行高度；
  - 仅棋盘容器允许受控内部滚动或在超小屏降阶布局（棋盘优先）。
- 回归用例：
  - 390x844、375x812、360x780 下，棋盘 bottom 不越界；
  - 快速点按底线棋子不再出现误触/不可点。

### AUD-P2-001
- 严重级别：`P2 功能缺陷`
- 维度：逻辑性 / 交互信息清晰度
- 问题：观战房间快照中双方执棋方为空，分棋信息不清晰
- 复现步骤：
  1. 调用 `GET /online/api/watch/overview`；
  2. 查看 `publicRooms[*].players.first.side/second.side`。
- 实际结果：处于 `PLAYING` 的房间仍返回空字符串 side。
- 期望结果：返回明确 side（象棋 `RED/BLACK`，五子棋 `BLACK/WHITE`）。
- 证据：
  - 接口样例：`"players":{"first":{"username":"...","side":""},"second":{"username":"...","side":""}}`
  - 代码：`src/main/java/com/xiangqi/web/PublicSiteServer.java` 的 `watchPublicRooms()` 将 `side` 写死为空字符串
- 影响面：观战列表与状态理解成本高，易引发“执棋方不清楚”反馈。
- 修复建议：
  - `watchPublicRooms` 在 `PLAYING` 时优先读取 game 快照 side；
  - 至少按房主/客座与 gameType 填充稳定 side。
- 回归用例：
  - 进行中的公开房间，列表 side 非空且与对局页一致。

### AUD-P2-002
- 严重级别：`P2 功能缺陷`
- 维度：交互性 / 体感性能
- 问题：AI 练习“人类落子反馈”仍有可感知延迟
- 复现步骤：
  1. 桌面端进入 `#/practice/{gameId}`；
  2. 点击一步合法走子，记录到走子记录首条出现耗时。
- 实际结果：本地 headless 实测人类落子反馈约 `497.9ms`，AI 应手约 `1447.4ms`。
- 期望结果：用户点击后有更即时确认反馈（<200ms 可感知反馈）。
- 证据：
  - 浏览器自动化结果：`humanMoveLatencyMs=497.9`，`aiResponseMs=1447.4`
  - 现实现状：`sendMove` 在服务端确认后才写入新局面（无本地局面预提交）
- 影响面：用户感知“落子不灵敏/卡顿”。
- 修复建议：
  - 保持“非乐观落子”前提下，加入即时“待确认落点”视觉反馈；
  - `sendMove` 提交后先局部更新标注与状态文案，再等待服务端快照覆盖。
- 回归用例：
  - 连续 20 步点击中，点击反馈可见延迟控制在 200ms 内（视觉确认，不要求规则落定）。

### AUD-P3-001
- 严重级别：`P3 体验优化`
- 维度：功能性 / 运维兼容
- 问题：根路径 HEAD 请求返回 405，不利于通用探活器兼容
- 复现步骤：`curl -I http://127.0.0.1:18388/`
- 实际结果：`405 Method Not Allowed`
- 期望结果：HEAD 返回与 GET 一致的 200/302（轻量探活友好）。
- 证据：本地命令返回头部 `HTTP/1.1 405 Method Not Allowed`
- 影响面：部分探活系统误判不可用。
- 修复建议：为 `/` 增加 HEAD 路由或通用 HEAD 回退策略。
- 回归用例：GET/HEAD `/` 均可作为健康检查。

## 4. 通过项（关键能力）
- 棋盘棋种映射：`XIANGQI` / `GOMOKU` 渲染分发正常，未知棋种安全失败。
- 在线局朝向：红黑双方视角正确；黑方视角河界文案变为“汉界 楚河”。
- 河界层级：`river z-index=1`，`piece z-index=4`，棋子层级高于河界文字。
- 象棋几何：
  - 线段计数符合预期：`up=74 down=74 left=80 right=80`
  - 棋子中心偏移样本：`dx=0 dy=0`（6 个采样点）
- AI 落子提示：状态行有“AI 已落子：...”文案，终点/起点高亮可见。

## 5. 审计附注
- 审计过程中已执行一次环境恢复：`tools/ensure_tunnel.ps1`，随后公网 `https://xiangqiarena.com/online` 恢复 `200`。
- 此恢复动作仅用于继续审计并验证链路，不等同于根治（守护任务仍需修复/启用）。
