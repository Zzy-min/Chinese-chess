# go-engine

轻量级围棋 HTTP 服务，面向当前仓库主站的 `XQ_GO_ENGINE_URL` 调用约定。

## 提供接口

- `GET /health`
- `POST /genmove`
- `POST /score`

## 运行方式

### Windows 本地默认目录

如果你按当前仓库约定把官方 KataGo 安装到：

```text
%USERPROFILE%\tools\katago
```

则仓库根目录的 `run_go_engine.bat` 会自动：

- 优先尝试 `cuda12.8` 版 KataGo
- 如果缺少 CUDA / cuDNN 运行库，则自动回退到 `opencl`
- 自动读取 `%USERPROFILE%\tools\katago\models\*.bin.gz`

### 1. 直接提供完整命令

```powershell
$env:KATAGO_CMD="D:\tools\katago\katago.exe gtp -config D:\tools\katago\default_gtp.cfg -model D:\tools\katago\kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
python server.py
```

### 2. 分字段提供

```powershell
$env:KATAGO_BIN="D:\tools\katago\katago.exe"
$env:KATAGO_CONFIG="D:\tools\katago\default_gtp.cfg"
$env:KATAGO_MODEL="D:\tools\katago\kata1-b18c384nbt-s9996604416-d4316597426.bin.gz"
python server.py
```

默认端口：

- `2718`

可选环境变量：

- `PORT`
- `BIND_HOST`
- `GO_ENGINE_NAME`
- `GO_ENGINE_RULES`，默认 `chinese`
- `GO_ENGINE_VISITS_EASY`
- `GO_ENGINE_VISITS_MEDIUM`
- `GO_ENGINE_VISITS_HARD`

## 说明

- 服务会把请求中的初始 `rows` 与后续 `moves` 一起重放到 KataGo
- `score` 会用 KataGo 的 `final_score` 结果生成胜负，同时返回本地面积统计字段，方便主站 UI 统一展示
- 如果未配置 KataGo，可启动服务，但 `/health` 会返回 `503`
