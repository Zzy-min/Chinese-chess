# XiangqiGame

A Java-based Xiangqi project with two playable modes:
- Desktop edition (Swing)
- Browser edition (local web server)

## Live URL

- Render: `https://xiangqi-web.onrender.com/`

## Features

- Core Xiangqi gameplay
- Player vs Player (PVP)
- Player vs Computer (PVC)
- Endgame practice and review mode
- External engine integration (when configured)
- Theme switching and mobile-friendly browser UI

## Run Locally

Requirements: Java 11+, Maven 3.9+

### 1) Build

```bash
mvn -DskipTests clean package
```

### 2) Start Desktop Edition

```bash
java -jar target/XiangqiGame-1.0.0.jar
```

### 3) Start Browser Edition

```bash
java -cp target/classes com.xiangqi.web.PublicWebMain
```

Optional environment variables:
- `PORT` (default: `18388`)
- `BIND_HOST` (default: `0.0.0.0`)

Example (PowerShell):

```powershell
$env:PORT = "18388"
$env:BIND_HOST = "0.0.0.0"
java -cp target/classes com.xiangqi.web.PublicWebMain
```

## Run with Docker

```bash
docker build -t xiangqi-web .
docker run --rm -p 18388:18388 -e PORT=18388 -e BIND_HOST=0.0.0.0 xiangqi-web
```

## Deploy to Render

This repository already includes `render.yaml` and `Dockerfile`:
- Service type: `web`
- Runtime: `docker`
- Auto deploy: `autoDeploy: true`
- Startup entry: `com.xiangqi.web.PublicWebMain`

Every push to `main` triggers a new Render deployment automatically.

## Documentation

- Chinese docs: [`README.zh-CN.md`](./README.zh-CN.md)
- Landing page: [`README.md`](./README.md)

## Repository

- `https://github.com/Zzy-min/Chinese-chess`
