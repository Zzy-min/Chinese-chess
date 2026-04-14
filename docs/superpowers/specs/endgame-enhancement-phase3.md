# Spec: Endgame Puzzle Enhancement — Phase 3

**Date:** 2026-04-14
**Status:** Draft

## 1. Problem Statement

Phase 2 built the endgame catalog and practice flow, but puzzles lack:
- No way to know if you've solved a puzzle before (no persistence)
- No hint system (stuck = restart or give up)
- No clear "puzzle solved" feedback distinct from regular game win
- No solve count / stats per puzzle or per user

## 2. Current Infrastructure

### What Exists
- `Board.isCheckmate(color)` / `Board.isGameOver()` / `Board.getWinner()` — full game-end detection
- `Board.getAllValidMoves(color)` — all legal moves
- `ConfigurableXiangqiEngine.findBestMove(board, color, difficulty)` — AI best move (can be used as hint)
- `PracticeGameHub.finalizeGame()` — game end handling, persists to `games` table
- `PracticeGameHub.snapshot` includes `endgameName` when set
- `OnlineStore` — no puzzle-specific tables or methods

### What's Missing
1. `puzzle_completions` table for tracking solves
2. Hint API endpoint (reuse AI engine)
3. Solved status in endgame listing API
4. Frontend hint button and solved indicators
5. Puzzle-specific result feedback ("puzzle solved" vs "you lost")

## 3. Phase 3 Scope

### 3A: Backend — Puzzle Completion Tracking
- New `puzzle_completions` table: user_id, endgame_id, solved_at, move_count
- `OnlineStore.recordPuzzleCompletion(userId, endgameId, moveCount)`
- `OnlineStore.getPuzzleCompletionStatus(userId, endgameIds)` — batch check
- `OnlineStore.getUserPuzzleStats(userId)` — total solved, by difficulty
- Integrate into `PracticeGameHub.finalizeGame()` — when endgame game ends with human win

### 3B: Backend — Hint API
- `POST /online/api/learn/practice-games/{gameId}/hint`
- Returns the AI engine's best move for the human's current turn
- Tracks hint usage: `hintUsed` field on ActivePracticeGame
- Include `hintUsed` in snapshot

### 3C: Frontend — Hint UI
- "提示" button in practice view (only for endgame puzzles)
- Shows hint as a highlighted source square on the board
- Hint count displayed ("已用 N 次提示")

### 3D: Frontend — Solved Status
- Endgame cards show solved badge if user has completed it before
- Endgame list API returns per-endgame solved status for logged-in users
- Practice result banner: "残局破解成功！" vs regular "AI 对局结束"
- User profile: show puzzle stats (total solved, by difficulty)

## 4. Non-Goals
- Multi-line solution verification (only checkmate = solved)
- Puzzle rating system
- Leaderboard
- Hint penalty / scoring

## 5. Constraints
- Hints use the same AI engine, no new dependencies
- Puzzle completions are additive (solving again doesn't duplicate)
- Anonymous users can't track completions (no user_id)

## 6. Verification Criteria
1. Solving an endgame puzzle records completion in DB
2. Revisiting the learn page shows solved badges on completed puzzles
3. Hint button returns a valid move suggestion
4. Hint usage count is tracked and displayed
5. Profile page shows puzzle stats
6. All existing tests still pass
