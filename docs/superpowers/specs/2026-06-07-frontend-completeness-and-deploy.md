# 前端完整性校验、功能补齐与部署 Spec

经过对 `/online` 前端模块（[app.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.js)）的全面审查，我们发现了三个显著的功能完整性缺失和不一致问题。本 Spec 旨在定义这三个核心前端功能的补齐和对齐方案，并进行测试与服务重新部署。

---

## 1. 核心需求分析

### 1.1 社区观战房“实时观战”与“复盘分析”区分
* **现状**：在社区观战页面（`#/watch`）的“公开房间”列表中，无论对局是正在进行（`PLAYING`）还是已结束，只要对局 ID（`gameId`）存在，按钮全部硬编码显示为“分析”并导航至 `analysis/{gameId}`。
* **问题**：玩家在对局进行中更渴望进行实时同步观战。后端与前端 WebSocket 均完全支持 `/game/{gameId}` 路由作为实时观战室（观战者无法落子但可通过 WebSocket 实时接收落子推送）。
* **方案**：根据公开房间的状态（`room.status`）进行区分：
  - 若为 `PLAYING`，按钮显示“实时观战”，导航至 `#/game/{gameId}`。
  - 若为 `FINISHED`，按钮显示“复盘分析”，导航至 `#/analysis/{gameId}`。
  - 若还在等待，显示“等待开局”。

### 1.2 排行榜（社区榜单）页面的游戏类型切换
* **现状**：当玩家进入排行榜页面（`#/community`）时，界面直接展示全量混合的 `board.winBoard` 或 `board.activityBoard`，并且页面中没有任何允许用户在“中国象棋”与“五子棋”之间进行选择和切换的 Tab/按钮。
* **问题**：排行榜后端其实有按游戏类型划分的明细（`byGameType`）。
* **方案**：在排行榜页面排头加入像大厅一样的 Tab 切换（象棋/五子棋）。根据当前选中的 `state.leaderboardGameType`，使用 `leaderboardItems(leaderboardGameType, boardType)` 检索对应棋种的榜单列表，并在 `bindCommon()` 中绑定类型切换事件。

### 1.3 学习（练习与棋谱）页面子 Tab 逻辑打通
* **现状**：大厅“残局练习”卡片会引导玩家跳转到 `#/learn/puzzles/ALL`。然而现在的 `renderLearnPage(route)` 完全忽略了 `route.learnTab` 及已有的 `renderLearnTabContent`，只返回了一个包含“总览”、“残局题库”、“教程复盘”卡片，但实质内容完全固定的静态总览卡片网格。无论玩家点击什么 Tab，都无法进入实际的“题库列表”或“教程列表”进行学习。
* **问题**：学习模块已被拦腰截断，题库和教程逻辑成为空壳。
* **方案**：重写 `renderLearnPage` 方法，在开始时增加 `state.learnContent` 自动加载检测。根据当前子路由：
  - 若为总览（`isOverview`），渲染四大总览卡片网格。
  - 若为题库（`puzzles`）、教程（`tutorials`）、练习（`practice`），高亮当前 Tab 项并调用 `renderLearnTabContent()` 显示内容。
  - 同时将 Tab 的切换按钮改用路由链接 `<a>` 或绑定点击事件来引发路由跳变。

---

## 2. 验证与部署方案

1. **Java 单元测试校验**：通过运行 `mvn test` 确认代码更改未破坏任何现有单元测试契约。
2. **网站部署重启**：对于 Undertow 本地服务，进行 Maven 重新构建并重启后台服务，使前端代码和逻辑完全在本地 `http://localhost:18388` 实效部署。
