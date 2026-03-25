# 轻·棋局

当前仓库同时包含两条产品线：

- 现有 Java Web 版本：三棋站点，提供中国象棋、五子棋与围棋体验。
- 新的 Node/TypeScript workspace：四棋综合棋站，覆盖中国象棋、五子棋、围棋、国际象棋，并提供在线对局、AI 练习、账号、历史、回顾、排行榜等统一产品链路。

- 中文文档: [README.zh-CN.md](README.zh-CN.md)
- English docs: [README.en.md](README.en.md)

当前仓库已移除 Swing 桌面端，只保留 Web 入口与部署链路。

## 新 workspace

新增目录：

- `apps/web`: Next.js 四棋主站，包含首页、登录/注册、对局、练习、历史、回顾、个人页、排行榜
- `apps/server`: Fastify API + WebSocket 服务，负责认证、房间、练习、归档、排行榜
- `packages/core`: 四棋共享目录、类型和棋盘主题
- `packages/engines-chess`: 基于 `chess.js` 的国际象棋规则适配器

当前已落地：

- 四棋在线对局与 AI 练习
- 中国象棋与国际象棋差异化棋盘表现
- WebSocket 房间同步
- 站内注册、登录、登出和 cookie 会话
- 本地文件数据库持久化用户、会话、活动房间、活动练习、归档与排行榜统计
- 历史页、回顾页、个人页、排行榜页

说明：

- 当前本地验证使用文件数据库持久化，接口边界按后续迁移 PostgreSQL 组织。
- 当前回顾页提供高质量回放与轻量标签，不是深度引擎分析。

常用 workspace 命令：

```powershell
corepack pnpm install
corepack pnpm test
corepack pnpm build
```

默认本地服务：

- Node API: `http://127.0.0.1:4310`
- Next.js Web: 通过 `next dev` 或 `next start` 启动

## 现有 Java 版本

- 主入口: `com.xiangqi.web.PublicWebMain`
- 本地启动脚本: `run_web.bat` / `运行游戏.bat`
- 围棋子服务脚本: `run_go_engine.bat`
- 默认本地地址: `http://127.0.0.1:18388/`

核心变化:

- 中国象棋默认优先 `AUTO` 引擎策略，已接入 `Pikafish` 自动优先级
- 五子棋保留内置 AI，并支持 `Rapfi / AlphaGomoku`
- 围棋新增 19 路、中国规则、提子、打劫、停一手、双停计分、题库与复盘
- 围棋 PvE 通过外部 `go-engine` 服务接入，主站读取 `XQ_GO_ENGINE_URL`
- 仓库内已新增 `services/go-engine`，可直接作为第二个 Render 服务部署
- 前端切换到三棋种注册表驱动，切到五子棋/围棋时会立即清空预览棋盘与状态提示
- `run_go_engine.bat` 会优先从 `%USERPROFILE%\tools\katago` 自动识别本地 KataGo，优先尝试 CUDA 版，缺少运行库时回退到 OpenCL
- `run_web.bat` 本地默认把 `XQ_GO_ENGINE_URL` 指向 `http://127.0.0.1:2718`

常用命令:

```powershell
mvn -q -DskipTests compile
mvn -q test
run_web.bat
```

部署说明、引擎配置与详细使用方式见对应语言 README。
