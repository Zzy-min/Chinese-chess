# 轻·象棋（XiangqiGame）

一个基于 Java 的中国象棋项目，提供 **桌面版（Swing）** 与 **浏览器版（本地 Web）** 两套对局模式。  
项目重点是：规则完整、对局流畅、AI 可分级、残局训练与术语提示。

GitHub 项目地址：`https://github.com/Zzy-min/turbo-octo-lamp`

## 简介

轻·象棋，一款开箱即玩的中国象棋对弈程序。  
支持双人同屏、人机三档难度、残局练习与棋局回顾。  
你可自由选择先后手，执黑时棋盘自动翻转到底侧视角。  
桌面与浏览器双端可用，浏览器模式可独立运行不中断。  
每一步更清晰，每一局更流畅，专注纯粹对弈体验。

## 最新版本特性

- 双人对战（PVP）与人机对战（PVC）
- 人机难度三档：`简单 / 中等 / 困难`
- 难度标定（2026-02）：中等≈业余棋手对弈强度，困难为高压进攻型（面向业余上限）
- 人机可选先后手：`我先手(红)` 或 `我后手(黑)`
- 双人同屏模式：每步落子后自动换向，当前行棋方始终在下（桌面 + 浏览器）
- 人机模式：玩家执黑时自动翻转为黑方在下（桌面 + 浏览器）
- 双人对战计时：
  - `10分钟`：步时 1 分钟（前三步 30 秒）
  - `20分钟`：步时 1 分钟（前三步 30 秒）
  - `无限`：不计时
- 网页版双人对战默认 `无限时`（避免长局被计时中断）
- 超时自动判负，结束后封盘（禁止继续落子）
- 认输功能（桌面 + 浏览器）
- 棋盘回顾模式（逐步前进/后退）
- 最近两步落点标记（先后次序）
- 杀招术语居中闪现（0.5s），支持扩展术语判定
- 落子音效与绝杀音效（`move.wav` / `mate.wav`，桌面 + 浏览器）
- 浏览器模式性能优化：一步一刷新、移动动画、抗缓存请求
- 浏览器模式独立进程启动：不受桌面程序关闭影响
- 新增 `run_web.bat`：可直接启动网页服务并自动打开浏览器（无需先打开桌面端）
- 多终端会话隔离：每个终端/标签页独立对局，互不影响
- 手机浏览器适配：小屏布局优化 + 触控操作增强
- 点击与落子链路提速：`pointerdown` 即时反馈、动作队列防丢点击、低重绘轮询
- 静态棋盘缓存分层 + 棋子精灵缓存复用，显著降低每帧绘制开销
- 服务端状态 `seq` 防乱序：多请求并发时自动丢弃过期响应，减少“点了没反应”错觉
- 新增性能观测接口：`/api/perf`、`/api/perf/reset`、`/api/perf/event`（会话级 p50/p95/p99）
- 浏览器模式可信地址：`http://127.0.0.1:18388/`

## AI 与算法

- 迭代加深搜索（Iterative Deepening）
- Alpha-Beta 剪枝
- 置换表（Transposition Table）
- Killer Moves + History Heuristic 走法排序
- 将军局面延伸，降低浅层漏算
- 开局库（OpeningBook）
- 残局学习集成：
  - `EndgameStudySet`：174 个残局局面，按 `初/中/高` 三档权重
  - `XqipuLearnedSet`：来自 xqipu 的学习局面集合
- 赛事学习集成：
  - `EventLearnedSet`：来自 `xqipu.com/eventlist` 的实战局面学习集（当前采样 2384 局面）
- 残局专用难度曲线：
  - 初级更快应手
  - 中级速度与稳定平衡
  - 高级偏稳求解（更深/更久）
- 赛事局面搜索增强：
  - 命中赛事学习局面后提高搜索预算（中高难度更明显）
- 赛事局面快速通道：
  - 第 10 步后命中赛事局面时，按“安全优先 + 向前压进”快速选点（尤其减少中盘长考）
- 前十步开局质量优化：
  - 前十步禁用随机走子（避免中等/简单难度开局漂移）
  - 后手（黑方）前十步预算增强 + 开局原则评分（发展/控中/安全）
- AI 结果缓存质量门槛：仅在搜索深度达标时写入缓存，降低浅层误缓存
- 动态搜索预算 2.0：结合局面分支、设备核数与近期“时间压力”自适应调节深度/时限

### 赛事学习数据更新（xqipu）

新增脚本：`tools/update_event_fens.ps1`

用途：批量抓取 `eventlist -> eventqipu` 页面中的 `data-fen`，生成去重 FEN 清单，供赛事学习集扩展使用。

示例：

```powershell
pwsh -File tools/update_event_fens.ps1 -StartPage 0 -EndPage 10 -OutFile data/event_fens.txt
```

一键生成并发布赛事学习库（抓取 -> 生成 `EventLearnedSet.java` -> 编译 -> push）：

```powershell
pwsh -File tools/update_event_learnedset.ps1 -StartPage 0 -EndPage 10 -Compile -Publish
```

一键生成并发布综合学习库（`qipus` + `canjugupu`）：

```powershell
pwsh -File tools/update_xqipu_learnedset.ps1 -QipusStartPage 0 -QipusEndPage 49 -Compile -Publish
```

## 规则与胜负

完整实现中国象棋基本与关键特殊规则，包括：

- 将/帅、士/仕、象/相、马、车、炮、兵/卒全部走法
- 将帅照面限制
- 将军、将死、困毙判定
- 认输判负
- 计时超时判负（双人模式）

## 主要模块

- `src/main/java/com/xiangqi/model`：棋盘、棋子、走法、术语检测
- `src/main/java/com/xiangqi/ai`：AI 搜索、开局库、残局学习集
- `src/main/java/com/xiangqi/ai/EventLearnedSet.java`：赛事局面学习库
- `src/main/java/com/xiangqi/controller`：对局流程与计时控制
- `src/main/java/com/xiangqi/ui`：桌面端界面（Swing）
- `src/main/java/com/xiangqi/web`：浏览器端服务与页面

关键入口：

- 桌面：`com.xiangqi.ui.XiangqiFrame`
- 浏览器独立服务：`com.xiangqi.web.BrowserModeMain`（默认端口 `18388`）

## 运行方式

### 0) Windows 一键启动（推荐）

```bat
:: 桌面版
run_game.bat

:: 网页版（直接浏览器打开，不依赖桌面端）
run_web.bat

:: 强制重编译后再启动网页版（可选）
run_web.bat --rebuild
```

### 1) 使用 javac / java（推荐与你当前环境一致）

```powershell
# 在项目根目录执行
$files = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d target/classes $files

# 启动桌面版
java -cp target/classes com.xiangqi.ui.XiangqiFrame

# 启动浏览器独立版（可选）
java -cp target/classes com.xiangqi.web.BrowserModeMain
```

若使用 `javac` 方式，请额外复制资源文件（音效等）：

```powershell
Copy-Item -Path src/main/resources/* -Destination target/classes -Recurse -Force
```

浏览器独立版启动后访问：

- `http://127.0.0.1:18388/`

### 2) Maven

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.xiangqi.ui.XiangqiFrame"
```

## 浏览器模式说明

- 可直接运行 `run_web.bat` 独立启动网页版（推荐，响应更快）
- 若 `18388` 端口已有服务，`run_web.bat` 会直接打开浏览器，不重复启动
- 也可从桌面菜单“在浏览器打开”进入网页版
- 关闭桌面窗口后，浏览器对局仍可继续

注意：
- `http://127.0.0.1:18388/` 属于本机地址，必须先有本机服务进程在运行
- 若希望“点开网址即可玩（不依赖本地启动）”，需要部署到公网服务器

### 公网部署（直开网址）

- 新增入口类：`com.xiangqi.web.PublicWebMain`
- 默认绑定：`0.0.0.0`，读取 `PORT` 环境变量（未提供时用 `18388`）
- 启动命令（示例）：

```bash
mvn -DskipTests clean package
java -cp target/classes com.xiangqi.web.PublicWebMain
```

### Render 一键部署（Blueprint）

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Zzy-min/turbo-octo-lamp)

- 仓库根目录已提供 `render.yaml` + `Dockerfile`
- Blueprint 使用 `runtime: docker`（已适配 Render 当前校验规则）
- 容器启动入口：`com.xiangqi.web.PublicWebMain`
- 部署成功后，直接使用 Render 分配的公网 URL 访问即可

## 音效替换说明

- 默认音效文件：
  - `src/main/resources/audio/move.wav`
  - `src/main/resources/audio/mate.wav`
- 默认音效来源：Kenney Interface Sounds（`CC0 1.0`）
- 你可直接替换同名文件，无需改代码
- 建议格式：`WAV / PCM / 44.1kHz / 16-bit / mono`
- 许可与来源记录：`docs/audio-license.md`

## 系统要求

- Java 11+
- Windows（当前脚本与路径示例基于 Windows）

## 许可证

仅供学习与交流使用。
