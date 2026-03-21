# Qing Qiju Online

A Java-based multi-board-game project with three main runtime paths:
- Desktop edition (Swing)
- Legacy local browser mode
- Public online site with invite-room multiplayer

## Live URL

- Render: `https://xiangqi-web.onrender.com/`

## Features

- Unified site structure for Xiangqi, Gomoku, and Go
- Online PvP via invite rooms
- Basic sign-up and sign-in
- Player vs Computer (PVC)
- Endgame practice and review mode
- External engine integration (when configured)
- Public pages for home, lobby, room, game, and analysis

## Website Functionality

- Split game entry: select Xiangqi or Gomoku on the landing view, then enter a dedicated game view.
- Full game controls: new game, undo, resign, draw, review, and endgame practice workflows.
- Real-time state sync: the browser continuously pulls board state, turn, clocks, result, and review progress.
- Configurable engines: built-in AI works out of the box, with optional external engines (Pikafish, Rapfi, AlphaGomoku).
- Mobile-ready UX: touch targets, board visibility, and information density are tuned for phones.

## Key Advantages

- Unified architecture: Java server provides both APIs and web assets in one service.
- Board-first UI: interaction hierarchy is centered on the board and move flow.
- Multi-game consistency: Xiangqi and Gomoku share stable controls and state handling patterns.
- Deployment-friendly: `render.yaml` + `Dockerfile` are included, and pushes to `main` can auto-deploy.

## Algorithms

### Xiangqi AI

- Search core: Iterative Deepening + Negamax + Alpha-Beta pruning.
- Speedups: Transposition Table, history heuristic, killer moves, and quiescence search.
- Advanced pruning: Null Move pruning, LMR (Late Move Reductions), and Futility pruning.
- Strategy layer: OpeningBook support and difficulty-based time/depth budgeting.

### Gomoku AI

- Candidate generation: builds move candidates from neighborhoods around existing stones.
- Tactical first: checks immediate win and immediate block before deep search.
- Search core: Alpha-Beta + Negamax, with depth/width tuned by difficulty.
- Move ordering: uses quick ordering scores and deeper scoring for top candidates.

### Gomoku Forbidden-Move Rules

- Black forbidden move detection is built in: overline, double-four, and double-three.
- Illegal black forbidden moves are rejected with explicit reason strings for UI feedback.

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

### 3) Start the public online site

```bash
java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain
```

Optional environment variables:
- `PORT` (default: `18388`)
- `BIND_HOST` (default: `0.0.0.0`)
- `XQ_DATABASE_URL` (falls back to a local H2 file database when omitted)

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
