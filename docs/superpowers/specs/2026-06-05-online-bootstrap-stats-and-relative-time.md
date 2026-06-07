# Spec: Online Bootstrap Stats and Relative Time Completion

## Background
- 现有前端仍有几处明显的假数据或前端硬算：
  - 五子棋模式页的 `4.8分`、`1023人`
  - 大厅最近对局的固定 `8分钟前`
  - 个人页“积分”由前端公式硬算
  - 个人页“最近活跃”没有真实时间依据

## Goal
- 让首页/大厅/模式页/个人页这批统计展示有真实后端数据来源。
- 保持浏览器端路由和交互不变，改为消费真实响应字段。

## Scope
1. 扩展 `bootstrap`
- 增加 `gameStats`
- 维度：
  - `XIANGQI`
  - `GOMOKU`
- 字段：
  - `totalGames`
  - `completedGames`
  - `trainingGames`
  - `distinctPlayers`
  - `activePublicRooms`

2. 扩展 `recentGames`
- 返回 `startedAt`
- 返回 `updatedAt`
- 前端据此计算相对时间，不再硬编码

3. 扩展 `profileSummary`
- 增加 `ratingScore`
- 增加 `lastGameAt`
- 前端不再自行推导“积分”

4. 前端改造
- 五子棋模式页统计格改用真实 `gameStats`
- 大厅“最近对局”改用相对时间函数
- 个人页“积分”与“最近活跃”改用后端字段

## Non-Goals
- 本次不实现复杂 Elo/MMR 排名系统
- 本次不做在线人数 websocket 级实时统计

## Acceptance
1. 模式页不再展示硬编码评分/在线人数。
2. 大厅最近对局时间文案基于真实时间字段变化。
3. 个人页“积分”来自后端摘要字段，不再由前端公式硬算。
4. 相关字段有测试与浏览器验收。
