# Implementation Plan: Online Bootstrap Stats and Relative Time Completion

## 1. Backend
- `OnlineStore`
  - 扩展 `recentGames(...)` 返回 `startedAt` 与 `updatedAt`
  - 扩展 `profileSummary(...)` 返回 `ratingScore` 与 `lastGameAt`
  - 新增 `gameTypeStats()` 聚合
- `PublicSiteServer`
  - `handleBootstrap()` 返回 `gameStats`
  - `activePublicRooms` 按 `roomHub.publicRoomSummaries()` 分游戏统计

## 2. Frontend
- `app.js`
  - 增加 `relativeTimeLabel(...)`
  - 大厅最近对局使用真实时间字段
  - 五子棋模式页统计卡改读 `bootstrap.gameStats.GOMOKU`
  - 个人页积分与最近活跃改读 `profile.summary`

## 3. Tests
1. `OnlineStoreTest`
- `profileSummary` 新字段
- `gameTypeStats` 聚合正确

2. `PublicSiteServerTest`
- `bootstrap` 包含 `gameStats`

## 4. Browser Verification
1. 打开大厅，确认最近对局不是固定 `8分钟前`
2. 打开五子棋模式页，确认统计值来自真实数据
3. 登录个人页，确认积分与最近活跃来自后端摘要
