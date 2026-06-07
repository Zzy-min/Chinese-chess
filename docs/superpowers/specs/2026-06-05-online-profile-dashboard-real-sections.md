# 个人页真实分区与聚合后端能力设计

## 背景

当前网页端 `#/me` 虽然已经接入了基础战绩摘要，但侧栏仍保留多处占位式入口：

- `我的棋谱`
- `我的收藏`
- `消息通知`
- `我的成就`
- `设置`
- `帮助与反馈`

这些入口目前只是跳去 `learn/watch/community/help` 等无关页面，前端语义与真实能力不一致。

## 目标

把个人页改成一个真实的 profile dashboard：

1. 侧栏进入的都是 `#/me/*` 子页，不再跳去别的模块页。
2. 子页内容全部来自现有或新增的后端聚合数据，不依赖硬编码占位文案。
3. 不引入会员能力，不改登录协议，不改棋局/房间接口。

## 路由与信息架构

- `#/me`：总览
- `#/me/records`：对局记录
- `#/me/study`：学习档案
- `#/me/inbox`：消息通知
- `#/me/achievements`：我的成就
- `#/me/settings`：棋桌设置
- `#/me/help`：帮助与反馈

## 后端设计

新增聚合接口：

- `GET /online/api/profile/dashboard`

返回结构：

- `user`
- `summary`
- `recentGames`
- `activity`
- `preferences`
- `learnProgress`
- `achievements`
- `notifications`

其中：

### achievements

由后端根据真实数据派生，不单独引入新表：

- 首局完成：`summary.totalGames >= 1`
- 首胜：`summary.wins >= 1`
- 学习起步：完成任意教程或题目
- 持续练习：总对局数或学习完成数达到设定阈值

每条成就包含：

- `id`
- `title`
- `description`
- `earned`
- `current`
- `target`

### notifications

由真实活动与最近行为派生：

- 当前仍在房间或对局中
- 最近有新归档对局可进入分析
- 最近已同步棋桌设置
- 最近完成教程/题目

每条通知包含：

- `id`
- `title`
- `body`
- `kind`
- `href`
- `createdAt`

## 前端设计

### 总览页

保留现有头像、摘要和统计卡，但侧栏入口改成真实子页导航。

### 对局记录

展示 `recentGames`，支持进入分析页。

### 学习档案

展示：

- 已完成教程数
- 已完成题目数
- 推荐继续入口

### 消息通知

展示后端 `notifications` 列表；空态明确说明暂无新通知。

### 我的成就

展示后端 `achievements` 列表，区分已达成与进行中。

### 设置

读取并展示真实 `preferences`，继续复用现有音效、主题、翻转能力。

### 帮助与反馈

放置真实帮助入口与常用操作入口，不再复用无关页面跳转。

## 非目标

- 不做会员体系
- 不做收藏持久化
- 不做独立消息中心推送系统
- 不改房间/对局/分析协议

## 验收标准

1. 个人页侧栏点击后都停留在 `#/me/*` 下。
2. 各子页不再出现跨模块“错跳”。
3. `notifications` 和 `achievements` 来自真实后端派生数据。
4. 浏览器实测能进入至少 `records / study / inbox / achievements / settings / help` 六个分区。
