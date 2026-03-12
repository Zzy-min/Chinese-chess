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
