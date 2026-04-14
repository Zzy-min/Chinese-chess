# Spec: Live Watch Page Enhancement — Phase 4

**Date:** 2026-04-14
**Status:** Draft

## 1. Problem Statement

The project already has live room state, websocket broadcasts, and a real replay/analysis view, but the watch route is still a placeholder. Users cannot easily discover live games or enter a spectator mode from the frontend.

## 2. Current Infrastructure

### What Exists
- `OnlineRoomHub` broadcasts live room/game snapshots over websocket
- `OnlineSiteServer` exposes `/api/games/{gameId}` and `/api/games/{gameId}/analysis`
- `app.js` already has a `watch` route, but it renders a placeholder page
- `renderAnalysis()` already provides a usable replay UI for finished or archived games
- `publicRoomSummaries()` can expose live room metadata
- `gameSnapshot()` already becomes read-only for nonparticipants

### What’s Missing
1. A real watch page instead of a placeholder
2. A browseable list of active games/rooms
3. A clear spectator entry path into a live or finished game
4. A frontend view that stays read-only and hides participant-only controls
5. Better live updates for spectators without requiring room membership

## 3. Phase 4 Scope

### 3A: Backend — Live Watch API
- Add an API endpoint that returns public watchable game/room summaries
- Include enough metadata for discovery: roomId, gameId, gameType, player names, status, turn, move count, created/started times
- Reuse existing room snapshot / game snapshot data where possible
- Keep the endpoint read-only and safe for spectators

### 3B: Frontend — Watch Landing Page
- Replace the placeholder watch route with a real page
- Show a list/grid of live games and recently finished games
- Provide quick actions: open live view, open analysis/replay
- Add simple filters such as game type and status if easy to support

### 3C: Frontend — Spectator Game View
- Make the game page explicitly support spectator mode
- When viewer is not a participant, hide controls such as move, resign, draw, and ready actions
- Show a spectator badge or text so the page state is obvious
- Keep the board read-only and preserve analysis links

### 3D: Realtime Updates
- Spectator page should subscribe to websocket room/game updates where available
- If the game starts or changes, refresh the displayed snapshot
- Keep behavior compatible with existing room-based websocket flow

## 4. Non-Goals
- Full spectator chat
- Multi-user public lobby
- Private invite-only watch rooms
- New ranking or statistics features

## 5. Constraints
- Reuse the current websocket and snapshot architecture
- Avoid breaking the existing room/game flow
- Preserve compatibility with the analysis/replay page
- Keep the feature read-only for spectators

## 6. Verification Criteria
1. The watch route shows actual live/available items instead of a placeholder
2. Clicking an item opens a read-only spectator/game view
3. Spectator view hides participant-only controls
4. Existing room play and analysis flows still work
5. All tests pass
