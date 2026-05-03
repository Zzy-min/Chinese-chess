# XiangqiGame 四维体检修复批次计划（2026-04-25）

## 1. 目标
- 依据体检报告（`docs/superpowers/reviews/2026-04-25-xiangqigame-4d-health-audit.md`）按风险优先级修复。
- 先消除 `P0/P1` 可用性风险，再处理 `P2/P3` 体验与一致性问题。
- 修复后保证：公网稳定可达、移动端 AI 对局可完整操作、执棋信息清晰、交互反馈更灵敏。

## 2. 批次拆分与依赖

## Batch-0（P0）公网可用性止血
- 范围：
  - 启用隧道守护任务（`XiangqiArena-EnsureTunnel-Loop`、`XiangqiArena-EnsureWeb-Loop`）。
  - 增加“源站 200 + 公网 200”双探活脚本与失败自动拉起。
  - 记录并告警 Cloudflare 530/1033。
- 依赖：无
- 风险：低（运维层）
- 验收：
  - 主动 kill `cloudflared`，60 秒内自动恢复；
  - `https://xiangqiarena.com/online`、`/online/api/site/bootstrap` 连续探活为 `200`。

## Batch-1（P1）移动端 AI 对局可用性修复
- 范围（仅 `#/practice/*`）：
  - 以“可用高度”驱动象棋/五子棋棋盘缩放，确保棋盘完整进入视口。
  - 压缩 AI 页顶部信息占高（pill 行数与间距）。
  - 保留防误滚动策略，但允许受控容器级滚动兜底（仅在超小屏触发）。
- 重点文件：
  - `src/main/resources/online/app.css`
  - `src/main/resources/online/app.js`（必要时增加动态缩放计算）
- 依赖：Batch-0 可并行
- 验收：
  - 390x844 / 375x812 / 360x780 下，棋盘底部可见且可点击；
  - 页面级滚动仍锁定，误触滚动不复现。

## Batch-2（P2）分棋清晰度 + 体感性能
- 范围：
  - `watch/overview` 返回公开房间时补充明确 side 信息；
  - AI 练习落子增加“待确认落点”即时反馈（不改规则结算，不做乐观落子）。
- 重点文件：
  - `src/main/java/com/xiangqi/web/PublicSiteServer.java`
  - `src/main/resources/online/app.js`
  - `src/main/resources/online/app.css`
- 依赖：Batch-1
- 验收：
  - 观战列表中 `players.first.side/players.second.side` 非空且与对局一致；
  - 用户点击落子后 200ms 内出现可感知反馈，且无重复提交/回合错乱。

## Batch-3（P3）接口语义与探活兼容收尾
- 范围：
  - `POST /online/api/rooms` 缺失 `gameType` 时返回可读错误语义；
  - 根路径补充 HEAD 兼容。
- 重点文件：
  - `src/main/java/com/xiangqi/web/PublicSiteServer.java`
- 依赖：Batch-2
- 验收：
  - 缺参返回 `400 + 业务语义消息`；
  - `curl -I /` 返回 200/302。

## 3. 统一测试计划

### 自动化
- `mvn -q test`
- `python -m pytest -q services/go-engine/tests`
- 新增/扩展契约测试：
  - `watch/overview` side 非空断言（公开对局场景）
  - `rooms` 缺失 gameType 错误语义断言
  - `practice` 移动端布局关键 class 存在断言（如动态缩放类）

### 手动
- 公网：
  - `https://xiangqiarena.com/online` 连续访问 30 分钟；
  - 人工重启 tunnel 进程，验证自动恢复。
- 对局：
  - 在线局红黑双方各 1 局（朝向+分棋+落子）
  - AI 练习象棋/五子棋各 1 局（标注+响应）
- 移动端：
  - 390x844、375x812、360x780 三档验证棋盘完整可用。

## 4. 风险与回滚
- 风险点：
  - Batch-1 涉及布局约束，可能影响桌面端密度；
  - Batch-2 涉及状态反馈节奏，需避免与 `moveInFlight` 冲突。
- 回滚策略：
  - 每批次独立提交并可单独回滚；
  - 保留当前 `app.js?v=20260422a` 对照基线，发布时逐批递增版本号。

## 5. 建议执行顺序
1. 先执行 Batch-0（保障公网稳定）
2. 再执行 Batch-1（恢复移动端可对弈）
3. 执行 Batch-2（信息清晰与体感）
4. 最后 Batch-3（语义与兼容收尾）
