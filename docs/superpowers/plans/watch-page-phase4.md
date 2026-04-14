# Implementation Plan: Live Watch Page Enhancement — Phase 4

**Date:** 2026-04-14
**Spec:** `docs/superpowers/specs/watch-page-phase4.md`

## Step 1: Backend Watch Summary API

**Target files:**
- `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`
- `src/main/java/com/xiangqi/online/server/OnlineRoomHub.java` if needed

Add a read-only endpoint such as:
- `GET /online/api/watch`

The endpoint should return a list of watchable items built from existing live room/game data. Keep the payload compact but useful:
- `roomId`
- `gameId`
- `gameType`
- `status`
- `moveCount`
- `playerA`, `playerB`
- `currentTurn`
- `createdAt`, `startedAt`
- optionally `analysisUrl` or `gameUrl`

Prefer reusing existing room summaries and game snapshots rather than introducing new storage.

## Step 2: Frontend Watch Page

**Target file:** `src/main/resources/online/app.js`

Replace the placeholder `watch` route rendering with a real page:
- a hero section explaining what watch mode is
- a grid/list of live and recent games
- action buttons:
  - `观战` / `进入`
  - `复盘分析`
- if the backend returns no live items, show a friendly empty state

## Step 3: Spectator-Safe Game View

**Target file:** `src/main/resources/online/app.js`

Update game/room rendering so spectators are clearly read-only:
- show a spectator badge when the viewer is not a participant
- hide move/ready/resign controls for spectators
- preserve the board and analysis links
- keep current player interactions unchanged for actual participants

## Step 4: Realtime Hookup

**Target file:** `src/main/resources/online/app.js`

If the user opens a live watch target, subscribe to websocket updates when possible and refresh snapshot data on change.

If the existing websocket room subscription is sufficient, reuse it. Otherwise add a minimal watch refresh path that simply reloads the watched game snapshot when the websocket indicates a state change.

## Step 5: Styling

**Target file:** `src/main/resources/online/app.css`

Add styles for:
- watch cards
- spectator badge
- empty state
- subtle status pills for live / finished games

## Step 6: Verification

1. Run `mvn compile`
2. Run `mvn test`
3. Open the watch route in browser and confirm it no longer shows a placeholder
4. Verify read-only behavior when not a participant
5. Commit when green
