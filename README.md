# 轻·象棋（XiangqiGame）

一个基于 Java 的中国象棋项目，提供 **桌面版（Swing）** 与 **浏览器版（本地 Web）** 两套对局模式。  
项目重点是：规则完整、对局流畅、AI 可分级、残局训练与术语提示。

## 简介

轻·象棋，一款开箱即玩的中国象棋对弈程序。  
支持双人同屏、人机三档难度、残局练习与棋局回顾。  
你可自由选择先后手，执黑时棋盘自动翻转到底侧视角。  
桌面与浏览器双端可用，浏览器模式可独立运行不中断。  
每一步更清晰，每一局更流畅，专注纯粹对弈体验。

## 最新版本特性

- 双人对战（PVP）与人机对战（PVC）
- 人机难度三档：`简单 / 中等 / 困难`
- 人机可选先后手：`我先手(红)` 或 `我后手(黑)`
- 当人类执黑时，棋盘自动翻转为黑方在下（桌面 + 浏览器）
- 双人对战计时：
  - `10分钟`：步时 1 分钟（前三步 30 秒）
  - `20分钟`：步时 1 分钟（前三步 30 秒）
  - `无限`：不计时
- 超时自动判负，结束后封盘（禁止继续落子）
- 认输功能（桌面 + 浏览器）
- 棋盘回顾模式（逐步前进/后退）
- 最近两步落点标记（先后次序）
- 杀招术语居中闪现（0.5s），支持扩展术语判定
- 浏览器模式性能优化：一步一刷新、移动动画、抗缓存请求
- 浏览器模式独立进程启动：不受桌面程序关闭影响
- 浏览器模式可信地址：`http://xiangqi.localhost:18388/`

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

浏览器独立版启动后访问：

- `http://127.0.0.1:18388/`

### 2) Maven

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.xiangqi.ui.XiangqiFrame"
```

## 浏览器模式说明

- 桌面菜单“在浏览器打开”会优先连接 `18388` 端口
- 若未运行，会自动拉起浏览器独立服务进程
- 关闭桌面窗口后，浏览器对局仍可继续

## 系统要求

- Java 11+
- Windows（当前脚本与路径示例基于 Windows）

## 许可证

仅供学习与交流使用。
