# Implementation Plan: Learning Page + Endgame Practice — Phase 2

**Date:** 2026-04-14  
**Spec:** `docs/superpowers/specs/learn-endgame-phase2.md`

## Step 1: Endgame Data Bundle

Create a JSON resource file with endgame puzzles.

**File:** `src/main/resources/online/endgames.json`

**Format:**
```json
[
  {
    "id": "qixing_juhui",
    "name": "七星聚会",
    "fen": "3k5/4a4/4b4/9/9/9/9/4B4/4A4/2b1K3 w - - 0 1",
    "difficulty": "advanced",
    "category": "classic",
    "description": "经典残局，红方先行，双方各有兵卒互相牵制",
    "source": "百局象棋谱"
  },
  ...
]
```

Include all 10 existing endgames from `EndgameLoader` + add ~20 more from curated sources.

**Also:** Add a `EndgameCatalog` Java class to load and serve this data.

---

## Step 2: Backend — Endgame API

**Files:**
- `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`
- `src/main/java/com/xiangqi/online/practice/CreatePracticeGameRequest.java`
- `src/main/java/com/xiangqi/online/practice/PracticeGameHub.java`

**Changes:**

1. Add `GET /online/api/learn/endgames` endpoint:
   - Returns all endgames from catalog
   - Optional `?difficulty=beginner|intermediate|advanced` filter

2. Extend `CreatePracticeGameRequest`:
   - Add `String fen` field (nullable — if null, use standard starting position)

3. Extend `PracticeGameHub.createGame()`:
   - If `fen` is provided, create match from that position instead of standard
   - Store the endgame reference (id/name) in the game metadata

---

## Step 3: Frontend — Learn Page

**File:** `src/main/resources/online/app.js`

**Changes:**

1. Replace `renderLearn()` placeholder with real content:
   - Fetch endgame list from API
   - Render endgame cards grouped by difficulty
   - Each card shows: name, difficulty badge, category, description
   - Click card → navigate to endgame detail/start practice

2. Add `renderEndgameDetail(id)`:
   - Fetch single endgame details
   - Show position preview (render FEN on a mini-board)
   - Show description, difficulty, source
   - "开始练习" button → POST to create practice game with FEN → navigate to practice view

3. Style updates in `app.css` for endgame cards and grid layout

---

## Step 4: Frontend — Practice View Enhancement

**File:** `src/main/resources/online/app.js`

**Changes:**

1. In `renderPracticeView()`, if game has endgame reference:
   - Show endgame name banner
   - Show "返回残局列表" button
   - Show puzzle description

2. Navigation: back button returns to `#/learn`

---

## Step 5: Testing + Integration

1. Add unit test for `EndgameCatalog` loading
2. Add integration test for endgame API endpoint
3. Add test for practice game creation with FEN
4. Verify all existing tests still pass

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| FEN parsing from custom positions fails | Reuse EndgameLoader's proven FEN parser |
| Large endgame JSON slows startup | Bundle as resource, load lazily on first request |
| Mobile layout breaks | Use responsive grid (CSS grid/flexbox) |
| Practice view breaks with custom FEN | XiangqiMatch/GomokuMatch should handle any valid position |
