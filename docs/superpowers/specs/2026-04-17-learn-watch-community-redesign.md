# Learn / Watch / Community 功能完善设计

## Summary

本轮把 Online 站点中的 `learn`、`watch`、`community` 从壳层升级为可用页面，目标是：

- 游客可浏览学习内容、观战入口和社区榜单
- 登录用户可进行学习进度互动（完成教程 / 题目）
- 保持现有在线对局主链路不回退

## Design

### 1. Learn：题库/残局 + 教程双线

- 新增 `GET /online/api/learn/content`，返回内置种子数据（教程、题库/残局、推荐练习配置）。
- 新增 `GET /online/api/learn/progress` 与完成接口：
  - `POST /online/api/learn/puzzles/{id}/complete`
  - `POST /online/api/learn/tutorials/{id}/complete`
- 游客可看内容，登录后可记录进度。
- 题目与教程使用资源文件种子数据，避免本轮引入复杂后台系统。

### 2. Watch：公开观战列表（先轮询）

- 新增 `GET /online/api/watch/overview`，返回：
  - 公开在线房间快照（按最近更新时间排序）
  - 可观战归档对局（含 `gameId/gameType/status/players/updatedAt`）
- 前端支持棋种/状态过滤和跳转分析页。
- 刷新策略采用 10 秒轮询，后续再升级实时推送。

### 3. Community：排行榜与活跃榜

- 新增 `GET /online/api/community/leaderboard`：
  - `winBoard`：按胜局与胜率排序
  - `activityBoard`：按近 30 天活跃局数排序
- 默认统计窗口为近 30 天；若窗口内数据为空，回退全量历史并返回回退标记。
- 游客可浏览榜单，登录用户可看到自己的榜单定位提示。

### 4. 前端页面层重构

- 将 `learn/watch/community` 的渲染改为统一页面注册表 + 独立数据加载器。
- 去掉原壳层占位文案，替换为真实数据视图。
- 保留 `play/room/game/practice/analysis` 既有主流程逻辑，新增功能为增量扩展。

## Public Interfaces

- 新增公开接口：
  - `GET /online/api/learn/content`
  - `GET /online/api/watch/overview`
  - `GET /online/api/community/leaderboard`
- 新增登录接口：
  - `GET /online/api/learn/progress`
  - `POST /online/api/learn/puzzles/{id}/complete`
  - `POST /online/api/learn/tutorials/{id}/complete`
- 新增资源与持久化：
  - `online/learn-content.seed.json`
  - `learn_progress` 表

## Test Plan

- 后端契约：
  - 游客访问 `learn/content`、`watch/overview`、`community/leaderboard` 返回 200。
  - 游客访问 `learn/progress` 或完成接口返回 401。
  - 登录后完成接口返回成功，`learn/progress` 可读回完成状态。
- 前端契约：
  - `online/app.js` 包含 `renderLearnPage`、`renderWatchPage`、`renderCommunityPage` 及对应 loader。
- 回归：
  - 全量测试 `mvn -q test` 通过。
  - 在线对局主链路不回退。

## Assumptions

- 本轮仍仅覆盖 `XIANGQI/GOMOKU`。
- 学习内容由内置种子数据驱动，后续可迁移到后台管理。
- 观战轮询间隔默认 10 秒。
