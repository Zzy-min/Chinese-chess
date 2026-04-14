# Implementation Plan: Endgame Puzzle Enhancement — Phase 3

**Date:** 2026-04-14
**Spec:** `docs/superpowers/specs/endgame-enhancement-phase3.md`

## Step 1: Schema + OnlineStore — Puzzle Completions

**File:** `src/main/resources/online/schema.sql`

Add after existing tables:
```sql
create table if not exists puzzle_completions (
  id identity primary key,
  user_id varchar(64) not null,
  endgame_id varchar(128) not null,
  move_count int,
  hints_used int default 0,
  solved_at timestamp default current_timestamp,
  constraint uk_puzzle_user_endgame unique (user_id, endgame_id)
);
create index if not exists idx_puzzle_completions_user on puzzle_completions(user_id);
```

**File:** `src/main/java/com/xiangqi/online/server/OnlineStore.java`

Add methods:
- `recordPuzzleCompletion(String userId, String endgameId, int moveCount, int hintsUsed)`
- `getPuzzleCompletionStatus(String userId, List<String> endgameIds)` → `Set<String>` of solved IDs
- `getUserPuzzleStats(String userId)` → `Map<String, Object>` with totalSolved, byDifficulty

## Step 2: PracticeGameHub — Track Hints + Record Completions

**File:** `src/main/java/com/xiangqi/online/practice/PracticeGameHub.java`

2a. Add `hintUsed` field to `ActivePracticeGame` (init to 0)

2b. Add `getHint(String gameId, AuthUser user)` method:
  - Validate game belongs to user + is endgame + is playing
  - Get human's color from board
  - Call `xiangqiEngine.findBestMove(board, humanColor, difficulty)`
  - Increment `game.hintUsed`
  - Return `{from: {row, col}, to: {row, col}, notation: "..."}`

2c. In `finalizeGame()`:
  - If game has `endgameName` AND human won → `store.recordPuzzleCompletion(userId, endgameId, moveCount, hintUsed)`

2d. Include `hintUsed` in snapshot

## Step 3: OnlineSiteServer — Hint API + Solved Status

**File:** `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`

3a. Add route: `POST /online/api/learn/practice-games/{gameId}/hint`

3b. Update `handleListEndgames`:
  - If user is logged in, pass userId to endgameCatalog or query store
  - Include `solved: true/false` per endgame in response

3c. Add user puzzle stats to profile response (or a new endpoint)

## Step 4: Frontend — Hint Button + Solved Indicators

**File:** `src/main/resources/online/app.js`

4a. In `renderPracticeView()`:
  - If endgame puzzle AND playing: show "提示" button
  - Show hint count: "已用 N 次提示"
  - On hint: call API, highlight source square with CSS class

4b. In `renderLearn()`:
  - After loading endgames, also load solved status
  - Show green "已破解" badge on solved endgame cards

4c. Enhance endgame result:
  - If puzzle + human won: "残局破解成功！" special banner
  - Show move count and hint count in result

**File:** `src/main/resources/online/app.css`

4d. Add styles for hint highlight, solved badge, puzzle result banner

## Step 5: Testing + Integration

1. Unit test for OnlineStore puzzle methods
2. Integration test for hint API
3. Verify existing tests still pass

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| AI engine returns null for hint | Return error "无法提供提示" |
| Puzzle completion race condition | UNIQUE constraint on (user_id, endgame_id) with MERGE/INSERT IGNORE |
| Hint highlights wrong square | Return full {from, to} from AI engine |
| Schema migration on existing DB | `CREATE TABLE IF NOT EXISTS` is safe |
