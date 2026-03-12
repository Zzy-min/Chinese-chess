# 中国象棋（XiangqiGame）

一个基于 Java 的中国象棋项目，提供两种使用方式：
- 桌面版（Swing）
- 浏览器版（本地 Web 服务）

## 在线地址

- Render：`https://xiangqi-web.onrender.com/`

## 功能概览

- 中国象棋基础规则与对弈
- 双人对战（PVP）
- 人机对战（PVC）
- 残局练习与复盘
- 外部引擎接入（按配置启用）
- 浏览器版主题切换与移动端适配

## 网站功能介绍

- 棋种入口分离：首屏先选择中国象棋或五子棋，再进入对应对局界面。
- 对局控制完整：支持开局、悔棋、认输、和棋、复盘、残局练习等常用流程。
- 实时状态同步：页面会持续获取当前局面、回合、步时/总时、结果与复盘进度。
- 引擎可配置：内置 AI 可直接使用，也支持接入外部引擎（如 Pikafish、Rapfi、AlphaGomoku）。
- 移动端可用：针对手机做了按钮触达、棋盘可视面积和信息密度优化。

## 网站优势

- 前后端一体化：Java 服务直接提供 API 与静态页面，部署路径简单，维护成本低。
- 棋盘优先设计：界面层级以“棋盘与落子”为中心，减少非必要干扰信息。
- 多玩法统一：同一站点内同时支持象棋与五子棋，且规则与操作一致性高。
- 线上部署稳定：仓库内置 `render.yaml` + `Dockerfile`，推送主分支即可自动部署。

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

### 3) 启动浏览器版

```bash
java -cp target/classes com.xiangqi.web.PublicWebMain
```

可选环境变量：
- `PORT`（默认 `18388`）
- `BIND_HOST`（默认 `0.0.0.0`）

示例（PowerShell）：

```powershell
$env:PORT = "18388"
$env:BIND_HOST = "0.0.0.0"
java -cp target/classes com.xiangqi.web.PublicWebMain
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

将代码推送到 `main` 后，Render 会自动触发新部署。

## 文档导航

- 英文文档：[`README.en.md`](./README.en.md)
- 仓库入口页：[`README.md`](./README.md)

## 仓库地址

- `https://github.com/Zzy-min/Chinese-chess`
