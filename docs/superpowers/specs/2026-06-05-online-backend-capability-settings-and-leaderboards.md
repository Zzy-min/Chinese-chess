# Design Spec: Online Backend Capability Parity for Settings and Segmented Leaderboards

## Summary
- 目标：继续把当前网页端已经呈现出来的功能，补成真实后端能力，而不是仅靠前端本地状态或混合假数据。
- 本轮聚焦两个当前最明确的缺口：
  1. 棋桌设置的用户级持久化能力
  2. 排行榜按棋种分榜的数据能力

## Current-State Gap

### 1. 棋桌设置仍是本地状态
- 当前前端在棋桌右栏提供：
  - 音效开关
  - 视觉背景主题
  - 翻转棋盘
- 现状问题：
  - `toggle-theme` 只是本地变量，且现有逻辑存在切换错误。
  - `flip-board` 只是当前页内状态，没有用户级持久化。
  - `soundEnabled` 仅依赖本地 `localStorage`，没有账号级同步。
- 结果：这些是当前前端确实呈现出来的功能，但后端还没有对应能力。

### 2. 排行榜页面与首页/大厅的棋种分榜缺少真实后端数据
- 当前前端结构明确区分了象棋与五子棋榜单语义。
- 现状问题：
  - 现有 `communityLeaderboard` 只返回混合榜单 `winBoard` / `activityBoard`。
  - 首页、大厅右栏虽然呈现棋种分区或分栏语义，但后端没有提供真正按 `game_type` 分段的榜单数据。
- 结果：页面有分榜表达，但后端尚未提供与之对应的数据能力。

## Requirements

### A. 用户设置持久化
- 新增用户偏好存储：
  - `soundEnabled`
  - `boardTheme`
  - `boardFlipped`
- 只对已登录用户提供后端持久化；未登录时允许继续本地默认行为。
- 采用加法式接口，不破坏现有 API。
- 偏好字段要做白名单校验：
  - `boardTheme` 仅允许 `wood` / `ink`
  - 其余为布尔值

### B. 分榜数据能力
- 扩展现有社区榜单响应，增加按棋种拆分的数据。
- 至少覆盖：
  - 象棋胜局榜
  - 五子棋胜局榜
  - 象棋活跃榜
  - 五子棋活跃榜
- 保持现有 `winBoard` / `activityBoard` 字段不变，避免现有页面回退。

## API Design

### Preferences
- `GET /online/api/profile/preferences`
  - 需登录
  - 返回当前用户偏好快照
- `POST /online/api/profile/preferences`
  - 需登录
  - 接收部分字段更新
  - 返回更新后的完整偏好快照

### Community Leaderboard
- 保留：
  - `winBoard`
  - `activityBoard`
- 新增：
  - `byGameType.XIANGQI.winBoard`
  - `byGameType.XIANGQI.activityBoard`
  - `byGameType.GOMOKU.winBoard`
  - `byGameType.GOMOKU.activityBoard`

## Storage Design
- 在 `schema.sql` 增加 `user_preferences` 表：
  - `user_id` 主键
  - `sound_enabled`
  - `board_theme`
  - `board_flipped`
  - `updated_at`
- 默认值：
  - `sound_enabled = true`
  - `board_theme = 'wood'`
  - `board_flipped = false`

## Frontend Integration
- `boot()` 完成登录态检查后，若已登录则拉取偏好。
- 棋桌设置切换时：
  - 先更新本地状态，保证交互立即响应
  - 再异步提交到偏好接口
- 首页/大厅排行榜优先消费按棋种分榜数据；无数据时回退到现有榜单字段。

## Verification
- 编译：
  - `node --check src/main/resources/online/app.js`
  - `mvn -q -DskipTests compile`
- 接口：
  - 偏好接口读写往返验证
  - 社区榜单返回 `byGameType` 结构验证
- 浏览器：
  - 登录后切换音效/主题/翻转，刷新后状态保持
  - 首页/大厅排行榜可消费真实分榜数据
