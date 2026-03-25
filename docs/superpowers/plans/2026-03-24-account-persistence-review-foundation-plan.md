# Account Persistence Review Foundation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add first-party accounts, durable storage, archived history, review playback, profile pages, and basic leaderboards to the Node/TS four-game site.

**Architecture:** Introduce a durable server-side repository backed by a local file database for verifiable persistence in this environment, with clear seams to migrate to PostgreSQL later. Keep realtime room and practice runtimes in memory, but persist room/practice snapshots and archive data so user history, profile, review, and leaderboard pages all read from the same stored records.

**Tech Stack:** Fastify, WebSocket, better-sqlite3, bcryptjs, zod, React, Next.js, Vitest

---

## Chunk 1: Persistence And Auth Infrastructure

### Task 1: Add database and validation dependencies

**Files:**
- Modify: `package.json`
- Modify: `apps/server/package.json`

- [ ] **Step 1: Add failing server test coverage for auth/persistence entry points**
- [ ] **Step 2: Add `better-sqlite3`, `bcryptjs`, `zod`, and `@fastify/cookie` dependencies**
- [ ] **Step 3: Run server install/build to verify dependency resolution**

### Task 2: Create the durable repository and schema bootstrap

**Files:**
- Create: `apps/server/src/db.ts`
- Create: `apps/server/src/repository.ts`
- Modify: `apps/server/src/index.ts`
- Test: `apps/server/src/app.test.ts`

- [ ] **Step 1: Write failing tests for user/session creation and archive persistence primitives**
- [ ] **Step 2: Create SQLite schema bootstrap with tables for users, sessions, active rooms, practice sessions, archives, and leaderboard stats**
- [ ] **Step 3: Implement repository methods for auth, session lookup, room persistence, practice persistence, archive creation, and leaderboard updates**
- [ ] **Step 4: Run the targeted server tests and keep them green**

### Task 3: Introduce auth helpers and request context

**Files:**
- Create: `apps/server/src/auth.ts`
- Modify: `apps/server/src/app.ts`
- Test: `apps/server/src/app.test.ts`

- [ ] **Step 1: Write failing tests for register/login/logout/me and unauthorized write attempts**
- [ ] **Step 2: Add cookie parsing, password hashing, session cookie issuance, and current-user resolution**
- [ ] **Step 3: Guard room/practice write endpoints behind authenticated sessions**
- [ ] **Step 4: Run the auth-focused server tests and verify expected 401 behavior**

## Chunk 2: Persist Rooms, Practice Sessions, And Archives

### Task 4: Persist room and practice lifecycle state

**Files:**
- Modify: `apps/server/src/store.ts`
- Modify: `apps/server/src/app.ts`
- Modify: `packages/core/src/types.ts`
- Test: `apps/server/src/app.test.ts`

- [ ] **Step 1: Write failing tests for persisted room creation, persisted practice creation, and reload-safe retrieval**
- [ ] **Step 2: Thread repository persistence through room creation/join/ready/start/move and practice create/move**
- [ ] **Step 3: Persist enough snapshot data to rebuild active state after server restart where supported**
- [ ] **Step 4: Re-run server tests to confirm no regression in existing four-game flows**

### Task 5: Create archive and review records on match/practice completion

**Files:**
- Modify: `apps/server/src/store.ts`
- Create: `apps/server/src/review.ts`
- Modify: `packages/core/src/types.ts`
- Test: `apps/server/src/app.test.ts`

- [ ] **Step 1: Write failing tests for archive creation and review payload reads after a finished game or practice session**
- [ ] **Step 2: Add archive serialization for moves, players, snapshots, result, and review tags**
- [ ] **Step 3: Update leaderboard aggregates after finished online matches**
- [ ] **Step 4: Run archive and leaderboard tests to green**

## Chunk 3: New Server APIs For Profile, History, Leaderboard, Review

### Task 6: Add profile, history, leaderboard, and review endpoints

**Files:**
- Modify: `apps/server/src/app.ts`
- Modify: `packages/core/src/types.ts`
- Test: `apps/server/src/app.test.ts`

- [ ] **Step 1: Write failing tests for `/api/me/profile`, `/api/me/history`, `/api/leaderboard`, and `/api/reviews/:archiveId`**
- [ ] **Step 2: Implement API handlers backed by repository data**
- [ ] **Step 3: Ensure private data stays scoped to the signed-in user**
- [ ] **Step 4: Run the full server test suite**

## Chunk 4: Web Auth Shell And Navigation

### Task 7: Add shared authenticated fetch helpers and shell navigation

**Files:**
- Modify: `apps/web/src/lib/api-base.ts`
- Modify: `apps/web/src/app/layout.tsx`
- Modify: `apps/web/src/app/globals.css`
- Test: `apps/web/src/components/home/home-page.test.tsx`

- [ ] **Step 1: Write failing tests for auth-aware shell navigation states**
- [ ] **Step 2: Add fetch helpers with `credentials: 'include'` and shared auth/session loading**
- [ ] **Step 3: Add top-level nav links for login, register, profile, history, leaderboard, and logout**
- [ ] **Step 4: Run affected web tests**

### Task 8: Add login and register pages

**Files:**
- Create: `apps/web/src/app/login/page.tsx`
- Create: `apps/web/src/app/register/page.tsx`
- Create: `apps/web/src/components/auth/auth-form.tsx`
- Create: `apps/web/src/components/auth/auth-form.test.tsx`

- [ ] **Step 1: Write failing tests for register/login form submission and error states**
- [ ] **Step 2: Implement the shared auth form component and pages**
- [ ] **Step 3: Redirect authenticated users toward profile or intended actions after login**
- [ ] **Step 4: Run auth component tests**

## Chunk 5: Profile, History, Review, And Leaderboard Pages

### Task 9: Add profile and history pages

**Files:**
- Create: `apps/web/src/app/me/page.tsx`
- Create: `apps/web/src/app/history/page.tsx`
- Create: `apps/web/src/components/profile/profile-page.tsx`
- Create: `apps/web/src/components/history/history-page.tsx`
- Create: `apps/web/src/components/profile/profile-page.test.tsx`
- Create: `apps/web/src/components/history/history-page.test.tsx`

- [ ] **Step 1: Write failing tests for profile summary and history filtering views**
- [ ] **Step 2: Implement server-loaded pages and focused presentational components**
- [ ] **Step 3: Add empty/error states that do not break the page shell**
- [ ] **Step 4: Run the profile/history test set**

### Task 10: Add review playback and leaderboard pages

**Files:**
- Create: `apps/web/src/app/review/[archiveId]/page.tsx`
- Create: `apps/web/src/app/leaderboard/page.tsx`
- Create: `apps/web/src/components/review/review-page.tsx`
- Create: `apps/web/src/components/leaderboard/leaderboard-page.tsx`
- Create: `apps/web/src/components/review/review-page.test.tsx`
- Create: `apps/web/src/components/leaderboard/leaderboard-page.test.tsx`

- [ ] **Step 1: Write failing tests for review move stepping and leaderboard game-type switching**
- [ ] **Step 2: Implement review playback UI using stored archive data and existing board renderers**
- [ ] **Step 3: Implement the leaderboard page with four-game tabs and stat cards**
- [ ] **Step 4: Run the review/leaderboard tests**

## Chunk 6: Integrate Existing Flows And Verify

### Task 11: Update play/practice flows to require auth and feed archived data

**Files:**
- Modify: `apps/web/src/components/play/play-page.tsx`
- Modify: `apps/web/src/components/play/room-detail-loader.tsx`
- Modify: `apps/web/src/components/play/room-detail-page.tsx`
- Modify: `apps/web/src/components/practice/practice-page.tsx`
- Modify: `apps/web/src/components/practice/practice-detail-loader.tsx`
- Modify: `apps/web/src/components/practice/practice-detail-page.tsx`
- Test: `apps/web/src/components/play/play-page.test.tsx`
- Test: `apps/web/src/components/play/room-detail-page.test.tsx`
- Test: `apps/web/src/components/practice/practice-page.test.tsx`
- Test: `apps/web/src/components/practice/practice-detail-page.test.tsx`

- [ ] **Step 1: Write failing tests for unauthenticated create/join/practice attempts and authenticated archive links**
- [ ] **Step 2: Add login prompts and authenticated flow affordances without breaking current fast interactions**
- [ ] **Step 3: Link finished sessions to history/review destinations**
- [ ] **Step 4: Run updated play/practice tests**

### Task 12: Refresh capability docs and run full verification

**Files:**
- Modify: `docs/current-node-site-matrix.md`
- Modify: `README.md`

- [ ] **Step 1: Update docs to reflect the new auth, archive, review, profile, leaderboard, and persistence capabilities**
- [ ] **Step 2: Run `corepack pnpm test`**
- [ ] **Step 3: Run `corepack pnpm build`**
- [ ] **Step 4: Run `mvn -q test`**
- [ ] **Step 5: Manually verify one end-to-end flow: register -> login -> create and finish a game or practice -> open profile/history/review**
