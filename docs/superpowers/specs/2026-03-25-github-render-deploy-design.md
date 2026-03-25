# GitHub 上传与新主站 Render 部署设计

## 概要

本轮目标不是继续扩产品功能，而是把当前已经通过验证的 Node/TS 四棋主站上传到现有 GitHub 仓库，并补齐可用于 Render 的新部署蓝图。部署目标是新主站，不再沿用仓库中仅适用于旧 Java Web 版本的根级 `render.yaml`。

## 目标

- 将当前工作树以独立分支推送到现有 GitHub 仓库。
- 为新的 Node/TS 主站生成可部署的 Render Blueprint。
- 保留旧 Java 版 Render 蓝图作为单独文件，不让它和新主站部署配置混用。
- 解决 Web 与 API 分离部署时的 cookie 和 API 调用路径问题。

## 非目标

- 本轮不把部署平台切到 Vercel、Netlify 或其他平台。
- 本轮不重构业务功能，只做部署适配所需的最小代码修改。
- 本轮不伪称已经完成 Render 上线，除非出现新鲜的成功部署证据。

## 设计

### GitHub

- 现有远端 `origin` 已指向 `https://github.com/Zzy-min/Chinese-chess.git`。
- 当前本地工作应推送到独立分支 `deploy/main-sync`，避免在本地 `main` 落后远端 `main` 的情况下直接覆盖主分支。

### Render 拓扑

新主站采用双服务：

- `qiju-api`
  - Fastify API + WebSocket
  - 持有 SQLite 文件数据库
- `qiju-web`
  - Next.js Web 前端
  - 通过同域 `/backend/*` 重写代理到 API

### 为什么要做同域代理

当前前端依赖 cookie 会话。若 Web 与 API 分别部署在两个独立域名上，浏览器会把它们视为跨站请求，现有 cookie 行为会变复杂并且容易失效。

因此部署时改为：

- 浏览器只请求 Web 服务自己的 `/backend/*`
- Next.js 服务端把 `/backend/*` 代理到 API 内网地址
- 浏览器看来仍是同源请求，cookie 可以按主站域名工作

### 数据持久化边界

- 当前运行时持久化是真实 SQLite 文件数据库，不是 PostgreSQL。
- Render 上如果需要真正持久化，就必须给 API 服务挂持久磁盘。
- 这也是为什么新蓝图把 API 服务计划设为可挂盘形态，而不是继续沿用旧的纯无状态配置。

## 风险

- 当前 Render CLI 未认证，自动校验和自动创建服务都会被 401 阻塞。
- 新蓝图是否最终能在你的 Render 账号里成功创建，还取决于 Render 侧认证和计划可用性。
- 本轮只能诚实保证“仓库已上传、蓝图已准备、部署链路已补齐到可导入状态”，不能在没有 Render 认证成功证据时宣称已经上线。
