# 轻·棋局 Online（XiangqiArena）

一个基于 Java 的三棋 Web 项目，当前主路径是公共在线站点，覆盖中国象棋、五子棋与围棋的统一站点结构、房间对局、AI 对弈与复盘分析。

## 在线入口

- 正式域名：`https://www.xiangqiarena.com/`
- Cloudflare 部署说明：[`docs/deployment/cloudflare-tunnel.md`](docs/deployment/cloudflare-tunnel.md)
- 仓库首页：[`README.md`](./README.md)

当前仓库以 Web 为主，不再把 Swing 桌面端作为主叙事。主站入口为 `PublicWebMain`，旧本地浏览器模式 `BrowserModeMain` 仅保留为本机体验与调试路径。

## 功能概览

- 中国象棋 / 五子棋 / 围棋统一站点结构
- 在线双人对战（房间邀请制）
- 基础注册登录
- 人机对战（PVC）
- 残局练习、复盘与分析页
- 外部引擎接入（按配置启用）
- 围棋外部 `go-engine` 子服务接入

## 当前部署定位

- 主站运行入口：`com.xiangqi.web.PublicWebMain`
- 本地启动脚本：`run_web.bat` / `运行游戏.bat`
- 围棋引擎脚本：`run_go_engine.bat`
- 历史 `render.yaml` / `Dockerfile` 仍保留在仓库中，但当前主要公开发布路径改为 `Cloudflare DNS + Cloudflare Tunnel + 本机源站`

## 本地运行

要求：Java 11+、Maven 3.9+

### 1) 构建项目

```bash
mvn -q -DskipTests package
```

### 2) 启动公共在线站点

```powershell
run_web.bat
```

或手动执行：

```powershell
$env:PORT = "18388"
$env:BIND_HOST = "127.0.0.1"
java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain
```

### 3) 启动旧的本地浏览器模式

```powershell
java -cp "target/classes;target/dependency/*" com.xiangqi.web.BrowserModeMain
```

### 4) 启动围棋引擎子服务（可选）

```powershell
run_go_engine.bat
```

## 常用环境变量

- `PORT`：主站端口，默认 `18388`
- `BIND_HOST`：主站绑定地址，推荐本机运行时使用 `127.0.0.1`
- `XQ_DATABASE_URL`：数据库连接串；未设置时默认回退到本地 H2 文件数据库
- `XQ_GO_ENGINE_URL`：围棋子服务地址，`run_web.bat` 默认指向 `http://127.0.0.1:2718`

## 核心算法

### 象棋 AI

- 搜索框架：迭代加深 + Negamax + Alpha-Beta 剪枝
- 性能优化：置换表、历史启发、杀手着法、静态搜索
- 高级剪枝：空着剪枝、LMR、Futility Pruning
- 策略增强：开局库与难度分级时间预算

### 五子棋 AI

- 候选点生成：基于已有棋子邻域收集候选落点
- 战术优先：先做立即胜利/立即防守判断
- 搜索框架：Alpha-Beta + Negamax
- 规则约束：黑方长连、四四、三三禁手检测

## 文档导航

- 英文文档：[`README.en.md`](./README.en.md)
- Cloudflare 部署说明：[`docs/deployment/cloudflare-tunnel.md`](docs/deployment/cloudflare-tunnel.md)
