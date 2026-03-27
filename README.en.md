# Qing Qiju Online (XiangqiArena)

A Java web project for Xiangqi, Gomoku, and Go, centered on a public online site, room-based multiplayer, AI play, and review workflows.

## Live Entry

- Production domain: `https://www.xiangqiarena.com/`
- Cloudflare deployment guide: [`docs/deployment/cloudflare-tunnel.md`](docs/deployment/cloudflare-tunnel.md)
- Repository landing page: [`README.md`](./README.md)

This repository is web-first. `PublicWebMain` is the primary production entry, while `BrowserModeMain` remains as a legacy local-only mode for debugging and standalone play.

## Features

- Unified site structure for Xiangqi, Gomoku, and Go
- Public online rooms for multiplayer matches
- Basic sign-up and sign-in
- Player vs Computer (PVC)
- Review, analysis, and endgame practice
- External engine integration for Xiangqi, Gomoku, and Go

## Deployment Positioning

- Primary runtime entry: `com.xiangqi.web.PublicWebMain`
- Local startup script: `run_web.bat`
- Go engine helper: `run_go_engine.bat`
- Historical `render.yaml` and `Dockerfile` remain in the repository, but the current public publishing path is `Cloudflare DNS + Cloudflare Tunnel + local source machine`

## Run Locally

Requirements: Java 11+, Maven 3.9+

### 1) Build

```bash
mvn -q -DskipTests package
```

### 2) Start the public online site

```powershell
run_web.bat
```

Or run it manually:

```powershell
$env:PORT = "18388"
$env:BIND_HOST = "127.0.0.1"
java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain
```

### 3) Start the legacy local browser mode

```powershell
java -cp "target/classes;target/dependency/*" com.xiangqi.web.BrowserModeMain
```

### 4) Start the Go engine sidecar (optional)

```powershell
run_go_engine.bat
```

## Common Environment Variables

- `PORT`: main site port, default `18388`
- `BIND_HOST`: bind address for the main site; `127.0.0.1` is recommended for Tunnel-backed local hosting
- `XQ_DATABASE_URL`: database connection string; falls back to a local H2 file database when omitted
- `XQ_GO_ENGINE_URL`: Go sidecar endpoint; `run_web.bat` defaults to `http://127.0.0.1:2718`

## Algorithms

### Xiangqi AI

- Iterative Deepening + Negamax + Alpha-Beta pruning
- Transposition Table, history heuristic, killer moves, and quiescence search
- Null Move pruning, LMR, and Futility pruning
- Opening book and difficulty-based search budgeting

### Gomoku AI

- Neighborhood-based candidate generation
- Immediate win / immediate block tactical checks
- Alpha-Beta + Negamax search
- Forbidden-move rules for overline, double-four, and double-three

## Documentation

- Chinese docs: [`README.zh-CN.md`](./README.zh-CN.md)
- Cloudflare deployment guide: [`docs/deployment/cloudflare-tunnel.md`](docs/deployment/cloudflare-tunnel.md)
