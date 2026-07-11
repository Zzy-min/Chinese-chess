# 设计规约：Online 前后端功能接线完整性

## 1. 背景

国风 UI 回滚后，部分后端能力（`quick-match`、`profile/dashboard`、`profile/preferences`）仍在 `PublicSiteServer` 中可用，但前端入口错绑到 AI 练习、假数据或死按钮，造成「看起来有功能、点了却不对」的体验。

## 2. 目标

在不重做国风视觉的前提下：

1. 所有「匹配 / 实时匹配 / 智能匹配」走 `POST /online/api/rooms/quick-match`，禁止伪装成 AI。
2. 个人中心 `#/me/*` 使用 `GET /online/api/profile/dashboard` 真数据。
3. 偏好（音效 / 主题 / 翻转）登录后经 `profile/preferences` 持久化。
4. 观战列表区分实时观战与复盘分析。
5. 大厅邀请码可输入并 `join-by-code`。
6. 无后端能力的 UI（签到 / 好友 / 俱乐部实体）诚实隐藏或标注未上线。

## 3. 非目标

- 不上线围棋 Online。
- 不实现签到、好友、段位 ELO、俱乐部实体。
- 不做引擎局面评估 API。
- 不合并 `OnlineSiteServer`（生产以 `PublicSiteServer` 为准）。

## 4. API 契约（已存在，前端须消费）

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/online/api/rooms/quick-match` | `{ gameType, initialTimeSeconds }` → `{ matched, room, game? }` |
| POST | `/online/api/rooms/join-by-code` | `{ roomCode }` → room |
| GET | `/online/api/profile/dashboard` | summary / recentGames / achievements / notifications / preferences / learnProgress |
| GET/POST | `/online/api/profile/preferences` | soundEnabled / boardTheme / boardFlipped |

## 5. 文案诚实规则

- 「匹配」≠ AI；AI 入口文案必须含「人机 / AI / 练习」。
- 无后端能力：隐藏，或 `disabled` +「即将开放 / 未上线」。
- 排行榜无数据时空态文案，禁止假名兜底。

## 6. 验收矩阵

见实施计划 Wave 4 十二项浏览器场景；自动化：`node --check` + `PublicSiteServerTest` 等既有测试。
