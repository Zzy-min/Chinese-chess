# Qing Qiju (Web Only)

`Qing Qiju` is a web-only Java board-game project with three playable game types:

- Xiangqi
- Gomoku
- Go (19x19, Chinese rules, 7.5 komi)

The Swing desktop client has been removed from this repository. Only the web server, browser UI, and web launch scripts remain.

## What is included

### Xiangqi

- PvP / PvE
- Endgame practice
- Move review
- Recent-move markers, tactical overlays, and audio cues
- `AUTO` is now the default engine preference
- `Pikafish` is supported and becomes the preferred engine when configured

### Gomoku

- PvP / PvE
- 15x15 board
- Renju-style forbidden black moves
- Undo and move review
- Built-in AI plus `Rapfi / AlphaGomoku`

### Go

- 19x19 board
- Real Go rules: captures, suicide prevention, ko / superko handling, pass, resign
- Double-pass scoring with resume support
- Puzzle / life-and-death scenarios from JSON
- Undo and review
- PvP always available
- PvE enabled only when a remote `go-engine` service is configured

## UI changes

- The frontend is now driven by a three-game registry instead of a hard-coded Xiangqi/Gomoku split
- Switching to Gomoku or Go clears the preview board immediately
- Stale stones, recent-move markers, selected cells, and scenario labels no longer bleed across games
- The action area is filtered by game type:
  - Xiangqi shows endgames and Xiangqi engine controls
  - Gomoku shows Gomoku engine controls
  - Go shows engine availability, pass, scoring, and puzzle controls
- If the Go engine is unavailable, the UI disables Go PvE automatically

## Running locally

### Launch script

```powershell
run_web.bat
```

Alternative alias:

```powershell
运行游戏.bat
```

Go engine sidecar:

```powershell
run_go_engine.bat
```

For local Windows development, the launch scripts now assume:

- the official KataGo binaries live under `%USERPROFILE%\tools\katago`
- `run_go_engine.bat` tries the `cuda12.8` build first and falls back to `opencl` if CUDA runtime DLLs are missing
- `run_web.bat` defaults `XQ_GO_ENGINE_URL` to `http://127.0.0.1:2718` when not explicitly set

Default local URL:

- `http://127.0.0.1:18388/`

### Maven / Java

```powershell
mvn -q -DskipTests compile
java -cp target/classes com.xiangqi.web.PublicWebMain
```

For loopback-only local development you can still use:

```powershell
java -cp target/classes com.xiangqi.web.BrowserModeMain
```

## Engine configuration

### Xiangqi

- `XQ_XIANGQI_ENGINE=BUILTIN|PIKAFISH|AUTO`
- `XQ_XIANGQI_PIKAFISH_CMD=<path to pikafish>`

Example:

```powershell
$env:XQ_XIANGQI_ENGINE="AUTO"
$env:XQ_XIANGQI_PIKAFISH_CMD="D:\tools\pikafish\pikafish.exe"
run_web.bat
```

### Gomoku

- `XQ_GOMOKU_ENGINE=BUILTIN|RAPFI|ALPHAGOMOKU|AUTO`
- `XQ_GOMOKU_RAPFI_CMD=<path to rapfi>`
- `XQ_GOMOKU_ALPHAGOMOKU_CMD=<path to AlphaGomoku>`

Example:

```powershell
$env:XQ_GOMOKU_ENGINE="RAPFI"
$env:XQ_GOMOKU_RAPFI_CMD="D:\tools\rapfi\rapfi.exe"
run_web.bat
```

### Go

Go PvE is intentionally split into the in-repo `services/go-engine` HTTP sidecar instead of embedding KataGo into the main Java process.

- `XQ_GO_ENGINE=AUTO|REMOTE|DISABLED`
- `XQ_GO_ENGINE_URL=<go-engine base URL>`

The local helper script auto-discovers:

- `%USERPROFILE%\tools\katago\engines\...`
- `%USERPROFILE%\tools\katago\models\*.bin.gz`

Example:

```powershell
$env:XQ_GO_ENGINE="AUTO"
$env:XQ_GO_ENGINE_URL="https://your-go-engine.onrender.com"
run_web.bat
```

If `XQ_GO_ENGINE_URL` is missing or unavailable:

- Go PvP still works
- Go puzzles still work
- Go PvE is disabled in the UI

## go-engine HTTP contract

The main web app calls:

- `GET /health`
- `POST /genmove`
- `POST /score`

See [docs/go-engine-api.md](docs/go-engine-api.md) for the expected request / response shape.

## Deployment

- Main service entry point: `com.xiangqi.web.PublicWebMain`
- Docker entry point: `com.xiangqi.web.PublicWebMain`
- Render blueprint: `render.yaml`
- Go sidecar directory: `services/go-engine`

To enable Go PvE in production, deploy the in-repo `go-engine` service from the same blueprint and set `XQ_GO_ENGINE_URL` on the main web service. You still need to supply the KataGo binary, model, and config paths.

## Project layout

- `src/main/java/com/xiangqi/ai`: Xiangqi / Gomoku AI and engine bridges
- `src/main/java/com/xiangqi/model`: Xiangqi and Gomoku models
- `src/main/java/com/xiangqi/model/go`: Go rules, scoring, scenarios, and remote engine client
- `src/main/java/com/xiangqi/web`: web entry points and API server
- `src/main/resources/web`: frontend assets
- `src/main/resources/go-scenarios.json`: Go puzzles
- `services/go-engine`: HTTP sidecar for KataGo-backed Go PvE

## Verification

```powershell
mvn -q -DskipTests compile
mvn -q test
```

Go-specific tests cover captures, suicide detection, ko / superko, double-pass scoring, scenario loading, and runtime serialization.
