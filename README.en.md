# XiangqiGame

**Language / 语言:** [中文](README.zh-CN.md) | [English](README.en.md)

A Java-based Xiangqi project with both **desktop (Swing)** and **browser (local web)** play modes.  
It focuses on complete rules, smooth gameplay, adjustable AI difficulty, endgame training, tactical prompts, and a continuously improved browser UI.

GitHub Repository: `https://github.com/Zzy-min/turbo-octo-lamp`

## Overview

XiangqiGame is a ready-to-play Chinese chess experience built for both quick local games and long-form practice.  
It supports local PvP, three AI difficulty levels, endgame training, and move review.  
You can freely choose who moves first, and the board auto-flips when the player takes black.  
Both desktop and browser versions are available, and the browser mode runs independently.  
The browser UI now includes a lighter default theme, alternative skins, clearer onboarding for new players, and a board-centered layout.

## Browser UI Refresh (2026-03)

- The old “board + fixed sidebar” layout has been redesigned into a board-centered stage with a settings drawer.
- Status, clocks, and primary actions are more visible on the first screen.
- A lighter minimal theme is now the default, while elegant oriental and imperial themes remain available.
- Before starting a game, players can directly see and switch game type, mode, first-move choice, and theme.
- Web assets now live under `src/main/resources/web`, making future UI work much easier.
- See `docs/web-ui-refresh-2026-03.md` for the detailed refresh notes.

## Latest Features

- Supports both **PvP** and **PvE** play modes.
- Three AI difficulty levels: `Easy / Medium / Hard`.
- Medium difficulty is tuned for amateur-level play; Hard is more aggressive and demanding.
- In PvE, the player can choose to move first or second.
- In PvP, the board auto-rotates after each move so the current player always appears at the bottom.
- In PvE, the board auto-flips when the human player takes black.
- Timed PvP modes include:
  - `10 minutes`: 1 minute per move, first 3 moves capped at 30 seconds
  - `20 minutes`: 1 minute per move, first 3 moves capped at 30 seconds
  - `Unlimited`: no clock
- Browser PvP defaults to unlimited time to avoid accidental interruption in long games.
- Timeouts result in an automatic loss, and the board is locked after game over.
- Surrender is supported on both desktop and browser.
- Move review mode supports step-by-step forward/backward navigation.
- The last two moves are highlighted with ordering markers.
- Tactical prompts such as checks and kill patterns are flashed on screen.
- Move and mate sound effects are available in both desktop and browser versions.
- Browser mode runs independently and can be launched directly with `run_web.bat`.
- Each browser tab/session keeps its own isolated game state.
- Mobile browser interaction has been optimized for small screens and touch input.
- The browser frontend now uses a board-centered UI with stronger discoverability for new players.
- Frontend resources are split into standalone `index.html / app.css / app.js` assets.
- Request sequencing with `seq` helps ignore stale responses in concurrent browser requests.
- Performance observation endpoints are available: `/api/perf`, `/api/perf/reset`, `/api/perf/event`.
- The browser mode local URL is `http://127.0.0.1:18388/`.
- AI response time has been improved with SEE caching, fast-path logic, and lighter capture analysis.
- Browser rendering includes enhanced recent-move signals, stronger selection highlights, and richer piece materials.
- PixiJS is used as the main browser rendering path, with Canvas fallback preserved.
- GSAP-based opening / check / kill transitions remain available.

## AI & Search

The AI stack is centered around classic game-tree search, with ongoing engineering work focused on stronger tactical stability and faster practical response time.

- Iterative Deepening
- Alpha-Beta pruning
- Transposition Tables
- Killer Moves + History Heuristic move ordering
- SEE (Static Exchange Evaluation) for move ordering and quiescence filtering
- SEE acceleration via hashing and depth-adaptive simplification under load
- Check extensions to reduce shallow tactical misses
- Quiescence Search + Delta Pruning
- Null Move Pruning + LMR + Futility Pruning
- Adaptive Aspiration Windows
- Repetition-aware evaluation to reduce pointless loops
- Difficulty-specific tuning for speed vs depth
- Opening Book support
- Improved opening selection with safety checks and near-best weighted choices
- Endgame learning sets:
  - `EndgameStudySet`: 174 endgame positions with weighted tiers
  - `XqipuLearnedSet`: learned positions from xqipu
- Event learning set:
  - `EventLearnedSet`: practical game positions from `xqipu.com/eventlist`
- Event-hit search budget scaling for medium and hard levels
- Fast-path handling for event-learned positions in later opening stages
- Higher-quality first 10 moves through stricter anti-randomization and safer opening principles
- Cache write thresholds to reduce shallow-result pollution
- Dynamic search-budget tuning based on branching factor, CPU, and time pressure
- Dynamic quiescence budget for faster practical responsiveness
- Controlled check extensions for stronger tactical consistency on medium/hard

### External Xiangqi Engine (Pikafish / UCI)

The project supports runtime switching for Xiangqi AI engines:

- Default: `Built-in AI`
- Optional: `Pikafish` (via UCI)
- If the external engine fails, it falls back to the built-in AI
- The browser control panel can switch between `Built-in / Pikafish / Auto`

Startup parameters (choose one):

```powershell
# Option 1: JVM parameters
java -Dxq.xiangqi.engine=PIKAFISH `
     -Dxq.xiangqi.pikafish.cmd="D:	ools\pikafish\pikafish.exe" `
     -cp target/classes com.xiangqi.web.BrowserModeMain
```

```powershell
# Option 2: Environment variables
$env:XQ_XIANGQI_ENGINE="PIKAFISH"
$env:XQ_XIANGQI_PIKAFISH_CMD="D:	ools\pikafish\pikafish.exe"
java -cp target/classes com.xiangqi.web.BrowserModeMain
```

Supported values:

- `XQ_XIANGQI_ENGINE=BUILTIN`
- `XQ_XIANGQI_ENGINE=PIKAFISH`
- `XQ_XIANGQI_ENGINE=AUTO`

Command path variables:

- `XQ_XIANGQI_PIKAFISH_CMD`: path to Pikafish
- Legacy compatibility: `XQ_XIANGQI_UCI_CMD`

### External Gomoku Engine (Piskvork)

The project supports runtime switching for Gomoku AI engines:

- Default: `Built-in AI`
- Optional: `Rapfi / AlphaGomoku` (through Piskvork protocol)
- If the external engine fails, it falls back to the built-in AI
- The browser control panel can switch directly between `Built-in / Rapfi / AlphaGomoku`

Startup parameters (choose one):

```powershell
# Option 1: JVM parameters (Rapfi)
java -Dxq.gomoku.engine=RAPFI `
     -Dxq.gomoku.rapfi.cmd="D:	oolsapfiapfi.exe" `
     -cp target/classes com.xiangqi.web.BrowserModeMain
```

```powershell
# Option 2: Environment variables (Rapfi)
$env:XQ_GOMOKU_ENGINE="RAPFI"
$env:XQ_GOMOKU_RAPFI_CMD="D:	oolsapfiapfi.exe"
java -cp target/classes com.xiangqi.web.BrowserModeMain
```

Supported values:

- `XQ_GOMOKU_ENGINE=BUILTIN`
- `XQ_GOMOKU_ENGINE=RAPFI`
- `XQ_GOMOKU_ENGINE=ALPHAGOMOKU`
- `XQ_GOMOKU_ENGINE=AUTO`

Command path variables:

- `XQ_GOMOKU_RAPFI_CMD`: path to Rapfi
- `XQ_GOMOKU_ALPHAGOMOKU_CMD`: path to AlphaGomoku
- Legacy compatibility: `XQ_GOMOKU_PISKVORK_CMD`

### Updating Event Learning Data (xqipu)

New script: `tools/update_event_fens.ps1`

Purpose: batch-fetch `data-fen` values from `eventlist -> eventqipu` pages and generate a deduplicated FEN list for expanding the event-based learning set.

Example:

```powershell
pwsh -File tools/update_event_fens.ps1 -StartPage 0 -EndPage 10 -OutFile data/event_fens.txt
```

Generate and publish the event learning set in one command:

```powershell
pwsh -File tools/update_event_learnedset.ps1 -StartPage 0 -EndPage 10 -Compile -Publish
```

Generate and publish the combined learning set (`qipus` + `canjugupu`):

```powershell
pwsh -File tools/update_xqipu_learnedset.ps1 -QipusStartPage 0 -QipusEndPage 49 -Compile -Publish
```

## Rules & Win Conditions

Core Xiangqi rules and common game-ending conditions are fully implemented.

- Full movement rules for king/general, advisors, elephants, horses, rooks, cannons, and pawns/soldiers
- Flying-general restriction
- Check, checkmate, and stalemate detection
- Surrender loss
- Timeout loss in timed PvP mode

## Main Modules

Below is a quick map of the main code modules and entry points.

- `src/main/java/com/xiangqi/model`: board, pieces, movement, tactical detection
- `src/main/java/com/xiangqi/ai`: AI search, opening book, endgame learning sets
- `src/main/java/com/xiangqi/ai/EventLearnedSet.java`: event-based learned positions
- `src/main/java/com/xiangqi/controller`: game flow and time control
- `src/main/java/com/xiangqi/ui`: desktop Swing UI
- `src/main/java/com/xiangqi/web`: browser-side services and page logic

Main entry points:

- Desktop: `com.xiangqi.ui.XiangqiFrame`
- Browser standalone service: `com.xiangqi.web.BrowserModeMain` (default port `18388`)

## How to Run

You can launch the project with Windows scripts, direct `java` commands, or Maven depending on your environment.

### 0) One-click Windows launch (Recommended)

```bat
:: Desktop version
run_game.bat

:: Browser version (opens directly in browser, no desktop dependency)
run_web.bat

:: Browser version + Pikafish external engine (Xiangqi)
run_web_pikafish.bat "D:	ools\pikafish\pikafish.exe"

:: Browser version + Rapfi external engine (Gomoku)
run_web_rapfi.bat "D:	oolsapfiapfi.exe"

:: Force rebuild before launching the browser version (optional)
run_web.bat --rebuild
```

### 1) Using javac / java

```powershell
# Run from the project root
$files = Get-ChildItem -Path src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d target/classes $files

# Launch desktop version
java -cp target/classes com.xiangqi.ui.XiangqiFrame

# Launch standalone browser version (optional)
java -cp target/classes com.xiangqi.web.BrowserModeMain
```

If using raw `javac`, also copy resource files manually:

```powershell
Copy-Item -Path src/main/resources/* -Destination target/classes -Recurse -Force
```

After starting browser standalone mode, visit:

- `http://127.0.0.1:18388/`

### 2) Maven

Recommended if you already use Maven locally.

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.xiangqi.ui.XiangqiFrame"
```

## Browser Mode Notes

- You can run `run_web.bat` directly to start the browser version as a standalone service.
- If port `18388` is already occupied, `run_web.bat` opens the browser without starting a duplicate service.
- You can also enter the browser mode from the desktop menu.
- Closing the desktop window does not end browser-side games.

Notes:

- `http://127.0.0.1:18388/` is a local address and requires the local service process to be running.
- If you want a “click and play” public URL, you need to deploy the service to a public server.

### Public Deployment

- New entry class: `com.xiangqi.web.PublicWebMain`
- Default bind address: `0.0.0.0`
- Reads the `PORT` environment variable, defaulting to `18388`
- Startup example:

```bash
mvn -DskipTests clean package
java -cp target/classes com.xiangqi.web.PublicWebMain
```

### Deploy to Render (Blueprint)

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/Zzy-min/turbo-octo-lamp)

- The repository already includes `render.yaml` and `Dockerfile`
- The Render Blueprint uses `runtime: docker`
- The container entry point is `com.xiangqi.web.PublicWebMain`
- After deployment, use the Render-assigned public URL directly

## Replacing Sound Effects

- Default sound files:
  - `src/main/resources/audio/move.wav`
  - `src/main/resources/audio/mate.wav`
- Default sound source: Kenney Interface Sounds (`CC0 1.0`)
- You can replace the files directly without changing the code
- Recommended format: `WAV / PCM / 44.1kHz / 16-bit / mono`
- License/source record: `docs/audio-license.md`

## System Requirements

- Java 11+
- Windows (current scripts and examples are Windows-oriented)

## License

For learning and communication purposes only.
