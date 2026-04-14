# Implementation Plan: Authentication Bug Fix — Phase 1

**Date:** 2026-04-14  
**Spec:** `docs/superpowers/specs/auth-bugfix-phase1.md`

## Overview
Fix 11 bugs in 4 ordered steps. Each step is independently testable.

---

## Step 1: Frontend Cookie & Auth Fix (BUG-001, BUG-002, BUG-009)

**Files:**
- `src/main/resources/web/app.js`

**Changes:**
1. **BUG-001**: Replace `fetchJson('/api/auth/me')` with:
   ```javascript
   fetch('/api/auth/me', { credentials: 'same-origin' })
     .then(r => r.ok ? r.json() : null)
   ```

2. **BUG-002**: In `api()` function, add `credentials: 'same-origin'`:
   ```javascript
   res = await fetch(url, { cache: 'no-store', credentials: 'same-origin' });
   ```

3. **BUG-009**: In both server login handlers, differentiate auth errors:
   ```java
   if (ex.getMessage() != null && ex.getMessage().contains("invalid credentials")) {
       sendError(exchange, StatusCodes.UNAUTHORIZED, ex.getMessage());
   } else {
       sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage());
   }
   ```
   **Files:** `OnlineSiteServer.java`, `PublicSiteServer.java`

**Verification:** `mvn test` passes. Manual: login → refresh → still logged in.

---

## Step 2: Session Repository Fix (BUG-005, BUG-006)

**Files:**
- `src/main/java/com/xiangqi/online/auth/AuthSessionRepository.java`
- `src/main/java/com/xiangqi/online/auth/InMemoryAuthSessionRepository.java`

**Changes:**
1. **BUG-006**: Remove default no-op from `deleteByToken()`, make it abstract:
   ```java
   void deleteByToken(String token);
   ```

2. **BUG-005**: Add expiration check in `InMemoryAuthSessionRepository.findByToken()`:
   ```java
   public Optional<UserSession> findByToken(String token) {
       UserSession session = sessions.get(token);
       if (session != null && session.expiresAt().isAfter(Instant.now())) {
           return Optional.of(session);
       }
       if (session != null) sessions.remove(token);
       return Optional.empty();
   }
   ```

**Verification:** Existing tests pass. New test: create session → advance clock past expiry → findByToken returns empty.

---

## Step 3: Cookie Security Attributes (BUG-007, BUG-008)

**Files:**
- `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`
- `src/main/java/com/xiangqi/web/PublicSiteServer.java`

**Changes:**
1. **BUG-008**: Add `SameSite=Lax` to both `setAuthCookie()` methods:
   ```java
   cookie.setSameSiteMode("Lax");
   ```

2. **BUG-007**: Add conditional `Secure` flag. Use Undertow's `SecureCookieHandler` or check if the request was made over HTTPS:
   ```java
   if (exchange.getRequestScheme().equalsIgnoreCase("https")) {
       cookie.setSecure(true);
   }
   ```

**Verification:** Login → inspect Set-Cookie header → contains `SameSite=Lax` and `Secure` (when HTTPS).

---

## Step 4: WebSocket Authentication (BUG-003, BUG-004)

**Files:**
- `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`
- `src/main/java/com/xiangqi/web/PublicSiteServer.java`

**Changes:**
1. **BUG-003**: In `onConnect()`, extract auth cookie and validate:
   ```java
   public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
       String token = extractCookie(exchange, AUTH_COOKIE);
       if (token == null || store.sessions().findByToken(token).isEmpty()) {
           // Reject connection
           exchange.setStatusCode(401);
           exchange.endExchange();
           return;
       }
       // Store user info on channel for later authorization
       channel.setAttribute("userId", store.sessions().findByToken(token).get().userId());
       wsHub.onConnect(channel);
   }
   ```

2. **BUG-004**: In `subscribe()`, check room access:
   ```java
   private void subscribe(WebSocketChannel channel, String roomId) {
       String userId = (String) channel.getAttribute("userId");
       // Check room exists and user has access (participant or public room)
       // ... existing room check logic ...
   }
   ```

**Verification:** 
- WebSocket connection without cookie → rejected (401)
- WebSocket subscribe to private room you're not in → rejected

---

## Step 5: Database Schema (BUG-010, BUG-011)

**Files:**
- `src/main/resources/online/schema.sql`

**Changes:**
1. **BUG-010**: Add indexes:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id ON auth_sessions(user_id);
   CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions(expires_at);
   ```

2. **BUG-011**: Add FK constraint (H2 compatible):
   ```sql
   ALTER TABLE auth_sessions ADD CONSTRAINT IF NOT EXISTS fk_auth_sessions_user 
       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
   ```

**Verification:** Schema migration runs without error. `OnlineStoreTest` passes.

---

## Risk Assessment
| Risk | Mitigation |
|------|-----------|
| Breaking existing sessions | Cookie changes are additive (SameSite/Secure), don't invalidate existing tokens |
| WebSocket auth breaks browser clients | Cookie extraction from upgrade request is standard; browsers always send cookies |
| FK constraint fails on existing data | Use `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS` (H2 supports this) |
| Test failures | Run tests after each step, fix immediately |
