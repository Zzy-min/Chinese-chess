# 轻·棋局（Web Only）

`轻·棋局` 是一个纯网页版三棋对局项目，当前支持：

- 中国象棋
- 五子棋
- 围棋（19 路，中国规则，贴目 7.5）

仓库已经移除 Swing 桌面端，只保留 Java Web 服务、前端静态资源与浏览器启动脚本。

## 当前能力

### 中国象棋

- PvP / PvE
- 残局练习
- 复盘
- 最近两步标记、术语闪屏、音效
- 默认引擎策略为 `AUTO`
- 已支持 `Pikafish` 接入，配置后优先使用外部引擎，失败时回退内置 AI

### 五子棋

- PvP / PvE
- 15x15 棋盘
- 黑方禁手（三三 / 四四 / 长连）
- 复盘与悔棋
- 内置 AI + `Rapfi / AlphaGomoku`

### 围棋

- 19x19 棋盘
- 真围棋规则：提子、自杀判定、打劫 / 超劫、停一手、认输
- 双停自动计分，支持恢复继续下
- 题库 / 死活题（JSON 场景）
- 复盘与悔棋
- PvP 始终可用
- PvE 依赖外部 `go-engine` 服务；未配置时前端会自动禁用围棋人机模式

## Web 交互更新

- 前端改成三棋种统一注册表驱动，不再只围绕“象棋 / 五子棋”二分支
- 切换到五子棋或围棋时，棋盘会立即清空，不再残留上一个棋种的棋子、最近两步、选中态或残局标签
- 对局页控件按棋种收口：
  - 象棋显示残局与象棋引擎
  - 五子棋显示五子棋引擎
  - 围棋显示围棋引擎状态、停一手、题库 / 死活题与计分信息
- 围棋引擎不可用时，UI 会自动把 `PvE` 切回 `PvP`

## 运行方式

### 1. 本地脚本

```powershell
run_web.bat
```

或：

```powershell
运行游戏.bat
```

围棋引擎子服务：

```powershell
run_go_engine.bat
```

Windows 本地开发默认约定：

- 官方 KataGo 安装在 `%USERPROFILE%\tools\katago`
- `run_go_engine.bat` 会优先尝试 `cuda12.8` 版，缺少 CUDA / cuDNN 运行库时自动回退 `opencl`
- `run_web.bat` 若未显式设置 `XQ_GO_ENGINE_URL`，会默认使用 `http://127.0.0.1:2718`

默认会在本地启动 Web 服务并打开浏览器：

- `http://127.0.0.1:18388/`

### 2. Maven / Java

```powershell
mvn -q -DskipTests compile
java -cp target/classes com.xiangqi.web.PublicWebMain
```

本地开发时也可继续使用：

```powershell
java -cp target/classes com.xiangqi.web.BrowserModeMain
```

## 环境变量与引擎

### 中国象棋引擎

- `XQ_XIANGQI_ENGINE=BUILTIN|PIKAFISH|AUTO`
- `XQ_XIANGQI_PIKAFISH_CMD=<pikafish 可执行文件>`

示例：

```powershell
$env:XQ_XIANGQI_ENGINE="AUTO"
$env:XQ_XIANGQI_PIKAFISH_CMD="D:\tools\pikafish\pikafish.exe"
run_web.bat
```

### 五子棋引擎

- `XQ_GOMOKU_ENGINE=BUILTIN|RAPFI|ALPHAGOMOKU|AUTO`
- `XQ_GOMOKU_RAPFI_CMD=<rapfi 可执行文件>`
- `XQ_GOMOKU_ALPHAGOMOKU_CMD=<AlphaGomoku 可执行文件>`

示例：

```powershell
$env:XQ_GOMOKU_ENGINE="RAPFI"
$env:XQ_GOMOKU_RAPFI_CMD="D:\tools\rapfi\rapfi.exe"
run_web.bat
```

### 围棋引擎

围棋不在主进程内直接跑 KataGo，而是通过仓库内置的独立 HTTP 服务 `services/go-engine` 接入。

- `XQ_GO_ENGINE=AUTO|REMOTE|DISABLED`
- `XQ_GO_ENGINE_URL=<go-engine 服务地址>`

本地脚本默认读取：

- `%USERPROFILE%\tools\katago\engines\...`
- `%USERPROFILE%\tools\katago\models\*.bin.gz`

示例：

```powershell
$env:XQ_GO_ENGINE="AUTO"
$env:XQ_GO_ENGINE_URL="https://your-go-engine.onrender.com"
run_web.bat
```

当 `XQ_GO_ENGINE_URL` 未配置或服务不可达时：

- 围棋 PvP 可正常使用
- 围棋题库可正常使用
- 围棋 PvE 会在前端自动置灰

## go-engine HTTP 协议

主站默认调用以下接口：

- `GET /health`
- `POST /genmove`
- `POST /score`

详细字段约定见 [docs/go-engine-api.md](docs/go-engine-api.md)。

## 部署

### 主站

- Docker 入口：`com.xiangqi.web.PublicWebMain`
- Render 蓝图：`render.yaml`
- 围棋子服务目录：`services/go-engine`

如果部署到 Render：

- 主站默认可直接运行
- 若要启用围棋 PvE，请在主站服务中配置 `XQ_GO_ENGINE_URL`
- `go-engine` 已包含在同一个 `render.yaml` 里，可直接作为第二个 Web Service 部署
- `go-engine` 仍需你提供 KataGo 可执行文件、模型与配置路径

## 目录结构

- `src/main/java/com/xiangqi/ai`：象棋 / 五子棋 AI 与引擎桥接
- `src/main/java/com/xiangqi/model`：中国象棋与五子棋模型
- `src/main/java/com/xiangqi/model/go`：围棋模型、规则、计分、场景与远程引擎客户端
- `src/main/java/com/xiangqi/web`：Web 服务入口与 API
- `src/main/resources/web`：前端 `index.html / app.css / app.js`
- `src/main/resources/go-scenarios.json`：围棋题库
- `services/go-engine`：围棋 HTTP 引擎服务

## 测试

```powershell
mvn -q -DskipTests compile
mvn -q test
```

围棋新增测试覆盖：

- 提子
- 自杀判定
- 打劫 / 超劫
- 双停计分
- 题库加载
- 运行时状态序列化
