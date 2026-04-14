# Spec: Learning Page + Endgame Practice — Phase 2

**Date:** 2026-04-14  
**Status:** Draft  

## 1. Problem Statement

The online chess platform's learning page (`#/learn`) is a placeholder shell with no actual content. The practice game system only supports full games from the standard starting position — there's no way to practice specific endgame puzzles. The standalone server (WebXiangqiServer) has 10 classic endgames working, but the online site has zero endgame support.

## 2. Current State

### What Exists
- 10 hardcoded classic endgames in `EndgameLoader.java` (七星聚会, etc.)
- 194 study positions in `EndgameStudySet.java` (AI-internal only)
- 4,543 + 9,286 raw FEN files in `data/` (AI-internal only)
- `PracticeGameHub` — works for full games from standard position
- Standalone server endgame endpoints work (WebXiangqiServer)

### What's Missing
1. No endgame listing API on the online site
2. No FEN/start-position support in practice game creation
3. Learn page is an empty shell
4. No puzzle/challenge UI
5. Legacy endgame endpoint is broken

## 3. Phase 2 Scope

### 2A: Endgame Data Expansion
- Keep the existing 10 classic endgames
- Add more endgame puzzles from public sources (crawl or curate)
- Organize by difficulty: Beginner / Intermediate / Advanced
- Store as JSON with metadata (name, FEN, difficulty, description, solution hints)

### 2B: Backend — Endgame Practice API
- `GET /online/api/learn/endgames` — List available endgames with metadata
- Extend `CreatePracticeGameRequest` to accept `fen` field
- Extend `PracticeGameHub` to create games from custom FEN positions
- Support endgame-specific game validation (e.g., detect if puzzle is solved)

### 2C: Frontend — Learn Page Rebuild
- Endgame browser: grid/list of endgames grouped by difficulty
- Endgame detail view: shows the position, description, difficulty
- "Start Practice" button: creates practice game from the endgame FEN
- Practice view already works — just needs to receive the gameId

### 2D: Frontend — Endgame Practice View
- Reuse existing practice view (`renderPracticeView`)
- Add endgame name/description banner
- Add "back to endgame list" navigation
- Show puzzle status (solved/unsolved)

## 4. Non-Goals
- Full puzzle system with move-by-move validation (Phase 3)
- Go tsumego/life-and-death puzzles (Phase 3)
- AI-generated puzzles
- User-submitted puzzles
- Leaderboard/scoring system

## 5. Constraints
- Must not break existing practice game flow
- Must work with existing auth system
- Endgame data should be bundled as resource files (no external DB dependency)
- Mobile-friendly UI

## 6. Verification Criteria
1. `GET /online/api/learn/endgames` returns list of endgames
2. Clicking an endgame creates a practice game from the correct FEN
3. Practice game plays correctly from the endgame position
4. Learn page renders endgame grid with difficulty grouping
5. All existing tests still pass
