# 轻·棋局

纯网页版三棋项目，基于 Java Web 服务提供中国象棋、五子棋与围棋对局体验。

- 中文文档: [README.zh-CN.md](README.zh-CN.md)
- English docs: [README.en.md](README.en.md)

当前仓库已移除 Swing 桌面端，只保留 Web 入口与部署链路。

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
