# 轻棋局前后端功能接线修复 — 执行计划

## Context

对 `Chinese-chess`（轻棋局 / XiangqiArena）的深度审查表明：

- **后端核心能力已基本齐备**（`PublicSiteServer`）：房间、走子、AI 练习、悔棋、排行榜、观战列表、大厅搜索、`quick-match`、`profile/dashboard`、`profile/preferences` 均已实现，且有对应 Java 测试。
- **前端国风 UI 在回滚后出现接线回潮**：大量按钮文案承诺了真人匹配 / 个人中心分区 / 签到俱乐部等，实际错绑 AI、假数据或死链接。
- **用户感知问题优先于新功能**：修「假按钮」比加围棋 Online 更能立刻提升产品完整性。

本计划目标：**在不重做国风视觉的前提下，把前后端能力一一接回，消灭假落地与错绑，使可点击入口与真实行为一致。**

**非目标（本轮不做）：**

- 不上线围棋 Online 对战/练习（保持 `GO.enabled=false`）
- 不实现真实签到、好友系统、段位 ELO、俱乐部实体
- 不重写棋盘渲染 / 不做引擎局面评估 API
- 不合并 `OnlineSiteServer`（可列为后续债务，本轮仅文档标注主入口）

---

## Recommended Approach

**前端接线优先 + 后端零改或微改。**

后端 `PublicSiteServer` / `OnlineRoomHub` / `OnlineStore` 已具备所需 API；历史 plan（`docs/superpowers/plans/2026-06-05-online-profile-dashboard-real-sections.md`、`2026-06-06-online-quick-match-real-backend.md`）与 browser artifacts 证明能力曾验收通过。本轮以 **恢复前端接线** 为主，只在缺表单/缺状态时补最小 UI，不重造后端。

分 4 个交付波次（Wave），每波可独立验证、可独立回滚。

---

## Critical Files

| 文件 | 角色 |
|------|------|
| `src/main/resources/online/app.js` | 主改动：动作绑定、个人中心、匹配、观战、偏好 |
| `src/main/resources/online/app.css` | 仅在 join 弹层 / me 子页布局缺样式时最小补丁 |
| `src/main/java/com/xiangqi/web/PublicSiteServer.java` | 原则上不改；仅当响应字段与前端契约不一致时微调 |
| `src/main/resources/online/index.html` | 若有 cache-bust 版本号则 bump |
| `docs/superpowers/specs/2026-07-11-online-fe-be-wiring-completeness.md` | 本轮 design/spec 落盘 |
| `docs/superpowers/plans/2026-07-11-online-fe-be-wiring-completeness.md` | 仓库内 plan 副本 |
| `src/test/java/com/xiangqi/web/PublicSiteServerTest.java` | 复用已有 quickMatch / dashboard / preferences / lobbySearch 测试 |

---

## Existing Code to Reuse

### 后端（勿重写）

| 能力 | 位置 |
|------|------|
| `POST /online/api/rooms/quick-match` → `{ matched, room, game? }` | `PublicSiteServer.handleQuickMatch` + `OnlineRoomHub.quickMatch` |
| `GET /online/api/profile/dashboard` | `PublicSiteServer.handleProfileDashboard` / `buildProfileDashboard` |
| `GET/POST /online/api/profile/preferences` | `handleProfilePreferences` / `handleSaveProfilePreferences` + `OnlineStore.profilePreferences` |
| `POST /online/api/rooms/join-by-code` | `handleJoinByCode` |
| 观战数据 | `handleWatchOverview` → `publicRooms` + `archivedGames` |
| 排行榜 `byGameType` | `OnlineStore.communityLeaderboard` |

### 前端（扩展现有模式）

| 模式 | 位置 |
|------|------|
| `fetchJson` + `API_BASE` | `app.js` ~3898 / 70 |
| 预设建房 `createRoomWithPreset` | `app.js` ~2676 |
| 登录门闸 `state.showAuthModal` | 各 quickStart* |
| `currentRoute` / `navTo` / `data-nav` | 路由与导航 |
| `persistOnlineSoundEnabled` / `toggleOnlineSound` | 本地音效，扩展为服务端同步 |
| `renderProfileGameCard` | 个人对局卡片（已存在） |
| `bindCommon` 的 `on('[data-action=...]')` | 统一绑事件 |

### 历史参考（接线语义）

- `docs/superpowers/specs/2026-06-08-online-complete-guofeng-features.md`
- `docs/superpowers/plans/2026-06-05-online-profile-dashboard-real-sections.md`
- `docs/superpowers/plans/2026-06-06-online-quick-match-real-backend.md`
- `artifacts/audit-20260605-profile-dashboard-browser-v3/verification-summary.json`
- `artifacts/audit-20260606-quick-match-browser/verification-summary.json`

---

## Wave 0 — 文档落盘（开工前）

1. 写入 design/spec：`docs/superpowers/specs/2026-07-11-online-fe-be-wiring-completeness.md`
   - 问题清单（P0/P1）
   - API 契约表
   - 文案诚实规则（匹配 ≠ AI；无后端能力必须隐藏或标「即将开放」）
   - 验收矩阵
2. 写入 plan 副本：`docs/superpowers/plans/2026-07-11-online-fe-be-wiring-completeness.md`（本文件精简版）

---

## Wave 1 — P0 信任修复（必须先做）

### 1.1 真人快速匹配接线

**新增函数** `quickStartPublicMatch(gameType, initialTimeSeconds = 300)`：

```
1. 未登录 → showAuthModal + 文案「请先登录再匹配」
2. POST /online/api/rooms/quick-match { gameType, initialTimeSeconds }
3. 取 result.room
4. state.room = room; refreshBootstrapAndProfile()
5. if room.gameId → navTo(`game/${gameId}`)
   else → navTo(`room/${room.roomId}`)
6. status 文案：
   - matched=true → 「已匹配到对手」
   - matched=false → 「已创建公开候场房，等待棋友加入」
```

**改绑（禁止再绑 AI）：**

| UI 位置 | 当前 | 目标 |
|---------|------|------|
| 首页「快速匹配」 | `quick-start-ai-practice` | `quick-start-public-match` + `data-game-type="XIANGQI"` |
| 象棋模式「实时匹配」 | `quick-start-ai-practice` | 同上 |
| 象棋模式「立即开局」 | `start-xiangqi-game`（仅建公开房） | `quick-start-public-match` |
| 大厅侧栏「快速匹配」 | `data-nav="play"` | `quick-start-public-match` |

**保留 AI 入口**（文案必须含「人机 / AI / 练习」）：

- `quick-start-ai-practice` / `quick-start-gomoku-practice`
- 学习页 AI 练习 Tab

**大厅五子棋卡片「进入大厅」**：

- 当前：`quick-start-gomoku-practice`（错）
- 改为：`data-nav="play/gomoku"` 或 `start-gomoku-game`（真人公开房）
- 人机入口单独保留在五子模式页

### 1.2 加入房间（邀请码）

现状：`joinByCode()` 读 `document.getElementById('joinCode')`，DOM **不存在**。

方案（最小侵入）：

1. 大厅「加入房间」改为打开简单 prompt / 内联输入条：
   - 优先：侧栏在按钮下增加 `<input id="joinCode">` + 确认按钮
   - 或：`window.prompt('请输入房间码')` 后调用 API（更快，体验稍差）
2. **推荐**：侧栏内联 input + `data-action="join-by-code"`，避免依赖不存在的 form
3. 成功后 `navTo(room/${roomId})`（已有逻辑）

### 1.3 主题切换 Bug

```javascript
// 错误
state.boardTheme = state.boardTheme === 'ink' ? 'ink' : 'wood';
// 正确
state.boardTheme = state.boardTheme === 'ink' ? 'wood' : 'ink';
```

并在 Wave 2 接到 preferences 持久化。

### 1.4 观战按钮语义

修改 `renderWatchRooms` / `renderWatchGames`：

| 状态 | 按钮文案 | 导航 |
|------|----------|------|
| `PLAYING` 且有 `gameId` | 实时观战 | `game/${gameId}` |
| `FINISHED` 且有 `gameId` | 复盘分析 | `analysis/${gameId}` |
| 无 `gameId` / 候场 | 等待开局 / 进入房间 | `room/${roomId}` 或 pill |

---

## Wave 2 — 个人中心 + 偏好同步

### 2.1 路由

扩展 `currentRoute()`：

- 解析 `#/me`、`#/me/records|study|inbox|achievements|settings|help`
- 返回 `meTab`（默认 `overview`）
- **勿破坏** `learn` 子路由特判

### 2.2 数据加载

```javascript
async function loadProfileDashboard() {
  state.profileDashboard = await fetchJson(`${API_BASE}/profile/dashboard`);
}
// loadProfile() 改为 dashboard；失败可降级 summary
```

登录成功 / `boot` 已登录时：并行 `loadProfileDashboard` + `loadProfilePreferences`。

### 2.3 渲染分区（全部真数据或真空态）

| meTab | 数据源 | UI |
|-------|--------|-----|
| overview | user + summary + activity | 用户名、真实对局数/胜率、活动入口；**删除** 10086/业余1级/假勋章数 |
| records | `recentGames` | 复用 `renderProfileGameCard`，空态「暂无对局」 |
| study | `learnProgress` | 已完成教程/题数；链到 learn |
| inbox | `notifications` | 列表 + path 跳转；空态 |
| achievements | `achievements` | 进度条 current/target；earned 标记 |
| settings | preferences | 音效 / 主题 wood\|ink / 翻转；保存调 POST |
| help | 静态 | 可内嵌 `renderHelpPage` 摘要或 `navTo help` |

侧栏高亮当前 tab，全部 `data-nav="me/..."`，**禁止**再指向 learn/watch/community 冒充。

### 2.4 偏好持久化

| 动作 | 行为 |
|------|------|
| `loadProfilePreferences` | GET → 应用 `soundEnabled` / `boardTheme` / `boardFlipped` 到 state；音效仍写 localStorage 作离线兜底 |
| `toggleOnlineSound` | 改 state + localStorage + **若已登录** POST preferences |
| `toggle-theme` | 切换 wood↔ink + 登录则 POST |
| `flip-board` | 切换 flipped + 登录则 POST |
| settings 页 | 显式开关，与对局内设置共用同一函数 |

### 2.5 删除 / 降级假 UI

- 勋章「12枚/36局/24项/58局」写死块：改为 achievements 摘要或移除
- 积分伪公式：改为只显示 wins/losses/totalGames，或标注「非段位积分」
- 「编辑资料」：无后端则隐藏或 disabled + title「暂未开放」

---

## Wave 3 — 诚实呈现与死按钮清理

### 3.1 文案与导航对齐

| 入口 | 处理 |
|------|------|
| 顶栏「俱乐部」 | 改名「观战」或保持文案但 `title` 说明；链接仍 `#/watch` |
| 大厅双「俱乐部/观战大厅」 | 合并为一个「公开观战」 |
| 「好友」Tab | 隐藏或 `disabled` +「即将开放」 |
| 每日签到 / 每日任务积分 | 隐藏整块，或统一标「装饰预览 · 未上线」且按钮 disabled |
| LV.6 棋圣 | 改为真实用户名 + 简单徽章（如「棋友」）或登录状态 |
| 局势 Tab 局时 15:00 / 01:30 | 改读 `game.initialTimeSeconds` / clock 字段；没有则显示 `-` |
| 分析 Tab 50% 评估条 | 改为说明「复盘回放可用；引擎评估尚未接入」或隐藏评估条 |

### 3.2 教程详情

`data-action="view-tutorial-detail"`：

- 绑定 handler：在卡片内展开 `keyPoints` / `exampleLine` / `objective`（数据已在 `learnContent.tutorials`）
- 或简单 `alert`/modal 展示 summary+keyPoints（优先内联展开，更贴合 SPA）

### 3.3 死 API 调用清理

- `createRoom()` 依赖不存在的 `#createGameType` 等：大厅若不用通用创建表单，可保留函数但仅在存在 DOM 时调用；UI 统一走 `createRoomWithPreset`
- 确认无残留 `data-action="create-room"` 指向空表单

### 3.4 首页排行榜

- 象棋列：`byGameType.XIANGQI.winBoard`（或 winBoard 过滤）
- 五子列：`byGameType.GOMOKU.winBoard`（**禁止**用全局 `activityBoard` 冒充五子榜）
- 无数据时：空态文案，**禁止**「棋圣无名」等假名兜底（对齐 `2026-06-06-online-leaderboard-empty-state-no-fake-rows`）

---

## Wave 4 — 验证、契约与收尾

### 4.1 自动化

```powershell
# 语法
node --check src/main/resources/online/app.js

# 后端契约（已覆盖本轮依赖 API）
mvn -q "-Dtest=PublicSiteServerTest,OnlineStoreTest,OnlineRoomHubTest,PracticeGameHubTest" test
```

必要时在 `PublicSiteServerTest` **不增新业务**，仅在前端契约依赖新字段时补断言。

### 4.2 手工 / 浏览器验收矩阵

| # | 场景 | 期望 |
|---|------|------|
| 1 | 未登录点快速匹配 | 登录门闸，不进 AI |
| 2 | 用户 A 快速匹配象棋 | 公开候场房 + hostReady |
| 3 | 用户 B 同棋种快速匹配 | matched → 进 game 或 FULL 房 |
| 4 | 大厅输入房间码加入 | 成功进 room |
| 5 | 观战 PLAYING 房 | 「实时观战」→ `#/game/...` 只读看盘 |
| 6 | 观战 FINISHED | 「复盘分析」→ analysis |
| 7 | `#/me` 各子 Tab | 真数据/真空态，无假勋章 |
| 8 | settings 改音效主题翻转后刷新 | 保持 |
| 9 | 对局内主题按钮 | wood↔ink 可切换 |
| 10 | AI 入口 | 仅进入 practice，文案含人机 |
| 11 | 学习教程「查看详情」 | 展开内容 |
| 12 | 排行榜空数据 | 无假名 |

### 4.3 回归保护

- 在线走子 / 求和 / 认输 / 练习悔棋 不改后端协议
- WebSocket subscribe 行为不变
- `learn` 路由与 puzzle theme 不变

### 4.4 文档

- README.zh-CN：功能概览改为「Online 主路径：象棋 + 五子；围棋入口预留」
- 可选：`OnlineSiteServer` 注释标明「精简/遗留，生产以 PublicSiteServer 为准」

---

## Implementation Order（建议提交粒度）

| Commit | 内容 |
|--------|------|
| 1 | docs: specs + plans 落盘 |
| 2 | fix: quick-match 前端接线 + 入口改绑 + 五子大厅纠错 |
| 3 | fix: join-by-code UI + theme toggle + watch 按钮 |
| 4 | feat: me dashboard 分区 + preferences 同步 |
| 5 | chore: 假 UI 诚实化 + 教程详情 + 排行榜空态 |
| 6 | test/docs: 验收记录 + README 叙事修正 |

每 commit 后至少跑：`node --check` + 相关 `mvn test`。

---

## Risk & Rollback

| 风险 | 缓解 |
|------|------|
| me 子路由破坏 learn | `currentRoute` 分 page 解析，单测式手工回归 learn/puzzles |
| quick-match 被限流 | 复用已有 createRoom limiter；错误展示 status |
| preferences 写失败 | 本地 state 仍生效，toast/status 提示同步失败 |
| 国风布局回归 | 只改 data-action/文案/侧栏内容，不改大布局 class |
| 回滚 | 按 commit 逆序；Wave 1–3 互不强制依赖 Wave 3 |

---

## Success Criteria

1. **无一键假匹配**：所有「匹配/实时/智能匹配」走 `quick-match` 或明确是建房，不进 AI。  
2. **无一键假个人中心**：`#/me/*` 数据来自 dashboard，无写死勋章/ID。  
3. **偏好可持久化**：登录用户刷新后音效/主题/翻转一致。  
4. **观战语义正确**：进行中实时观战，结束复盘。  
5. **邀请码可加入**：大厅有输入并能 join-by-code。  
6. **空态诚实**：排行榜/成就/通知无假数据填充。  
7. **既有 Java 测试全绿**；`node --check` 通过。

---

## Out of Scope Follow-ups（登记，不在本轮实施）

1. 围棋 Online 全链路（房间 + practice + 前端棋盘）
2. 统一废弃 `OnlineSiteServer` 或让其代理完整路由
3. 对局内真实引擎评估 / 形势分
4. 签到、好友、俱乐部、段位系统
5. Android 端与 Web 能力对齐审计

---

## Execution Checklist（实施时逐项勾选）

### Wave 0
- [ ] 写 spec
- [ ] 写 plan 副本到 `docs/superpowers/plans/`

### Wave 1
- [ ] `quickStartPublicMatch` + bind
- [ ] 首页/象棋/大厅入口改绑
- [ ] 五子「进入大厅」纠错
- [ ] joinCode UI + joinByCode 稳健化
- [ ] theme toggle 修复
- [ ] watch 按钮语义

### Wave 2
- [ ] `meTab` 路由
- [ ] `loadProfileDashboard` / preferences
- [ ] 七区渲染
- [ ] 设置与对局内设置共用写回
- [ ] 去除假勋章/假 ID

### Wave 3
- [ ] 俱乐部/签到/好友诚实化
- [ ] 教程详情
- [ ] 排行榜空态 + byGameType
- [ ] 局势/分析 Tab 去硬编码假数

### Wave 4
- [ ] `node --check` + `mvn test`
- [ ] 浏览器 12 项矩阵
- [ ] README 叙事修正
- [ ] 可选：artifacts 新一轮 verification-summary
