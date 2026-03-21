# 轻棋局 Online（XiangqiGame）

一个基于 Java 的三棋项目，当前提供三条主要使用路径：
- 桌面版（Swing）
- 本地浏览器版（旧单页模式）
- 公共在线站点（房间邀请制在线对战）

## 在线地址

- Render：`https://xiangqi-web.onrender.com/`

## 功能概览

- 中国象棋 / 五子棋 / 围棋统一站点结构
- 在线双人对战（房间邀请制）
- 基础注册登录
- 人机对战（PVC）
- 残局练习与复盘
- 外部引擎接入（按配置启用）
- 新公共站点首页 / 大厅 / 房间 / 对局 / 分析页

## 网站功能介绍

- 新公共站点：`PublicWebMain` 现在启动在线站点，支持注册、建房、邀请码加入、在线对局与局后分析。
- 旧本地浏览器模式：`BrowserModeMain` 仍保留原有本地单页对局能力，适合本机体验和 AI/残局能力。
- 首页与大厅拆分：首页负责分发，大厅负责建房/加房，对局页只负责棋局本身。
- 首版在线链路：当前已打通中国象棋与五子棋的房间邀请制结构，围棋在站点结构中保留入口并显式标记未开放。

## 网站优势

- 前后端一体化：Java 服务直接提供站点壳、API 与 WebSocket。
- 多棋统一：三棋共用一套站点导航和产品结构。
- 在线优先：双人对战不再局限于同一终端。
- 部署链路完整：仓库已内置数据库蓝图、Dockerfile 与运行依赖复制流程。

## 核心算法

### 象棋 AI

- 搜索框架：迭代加深 + Negamax + Alpha-Beta 剪枝。
- 性能优化：置换表（Transposition Table）、历史启发、杀手着法、静态搜索（Quiescence）。
- 高级剪枝：空着剪枝（Null Move）、LMR（后期减深）、Futility Pruning。
- 策略增强：开局库（OpeningBook）与难度分级时间预算。

### 五子棋 AI

- 候选点生成：基于已有棋子邻域收集候选落点，减少无效搜索空间。
- 战术优先：先做“立即胜利/立即防守”判断，再进入深度搜索。
- 搜索框架：Alpha-Beta + Negamax，并按难度控制搜索深度与候选宽度。
- 排序评估：先做快速排序评分，再对高价值候选进行精评分。

### 五子棋规则算法（禁手）

- 黑方禁手判定：内置长连、四四、三三检测（Renju 风格）。
- 落子校验：禁手点会被拦截并返回具体原因，前端可直接提示玩家。

## 本地运行

要求：Java 11+、Maven 3.9+

### 1) 构建项目

```bash
mvn -DskipTests clean package
```

### 2) 启动桌面版

```bash
java -jar target/XiangqiGame-1.0.0.jar
```

### 3) 启动公共在线站点

```bash
java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain
```

可选环境变量：
- `PORT`（默认 `18388`）
- `BIND_HOST`（默认 `0.0.0.0`）
- `XQ_DATABASE_URL`（未设置时，本地默认回退到 H2 文件数据库）

示例（PowerShell）：

```powershell
$env:PORT = "18388"
$env:BIND_HOST = "0.0.0.0"
java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain
```

如果要启动旧的本地浏览器模式：

```powershell
java -cp "target/classes;target/dependency/*" com.xiangqi.web.BrowserModeMain
```

## Docker 运行

```bash
docker build -t xiangqi-web .
docker run --rm -p 18388:18388 -e PORT=18388 -e BIND_HOST=0.0.0.0 xiangqi-web
```

## Render 部署

仓库已包含 `render.yaml` 与 `Dockerfile`：
- 服务类型：`web`
- 运行时：`docker`
- 自动部署：`autoDeploy: true`
- 启动入口：`com.xiangqi.web.PublicWebMain`

将代码推送到 `main` 后，Render 会自动触发新部署，并同时挂接 `xiangqi-db` 数据库。

## 文档导航

- 英文文档：[`README.en.md`](./README.en.md)
- 仓库入口页：[`README.md`](./README.md)

## 仓库地址

- `https://github.com/Zzy-min/Chinese-chess`
