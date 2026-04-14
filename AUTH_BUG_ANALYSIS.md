# Authentication Bug Analysis Report
## Chinese Chess (轻棋局) Online

**Date:** 2026-04-14  
**Files Analyzed:** 11  
**Confirmed Bugs:** 11  

---

## BUG-001: Missing `fetchJson` function in legacy `web/app.js`
- **Severity:** Critical
- **File:** `src/main/resources/web/app.js`, line 277
- **Description:** The `refreshAuth()` function calls `fetchJson('/api/auth/me')`, but `fetchJson` is never defined anywhere in this file. The legacy codebase uses `api()` (line 42) and `authRequest()` (line 278) for HTTP calls, but `fetchJson` does not exist. This means `refreshAuth()` will throw a `ReferenceError` at runtime, causing authentication state to always fail to load. Users who are already logged in (via cookie) will appear as logged out on page reload because the `/api/auth/me` call never succeeds.
- **Current code:**
```javascript
async function refreshAuth(){try{const data=await fetchJson('/api/auth/me');currentUser=data;}catch(_e){currentUser=null;}renderAuth();syncHomeChrome();}
```
- **Suggested fix:** Replace `fetchJson('/api/auth/me')` with `authRequest('/api/auth/me', null)` or use `fetch('/api/auth/me', { credentials: 'same-origin' }).then(r => r.json())`, or define a local `fetchJson` helper that includes `credentials: 'same-origin'`.

---

## BUG-002: Legacy `api()` function does not send auth cookies
- **Severity:** Critical
- **File:** `src/main/resources/web/app.js`, line 42
- **Description:** The main `api()` function used for all legacy game API calls (`/api/state`, `/api/new`, `/api/click`, `/api/surrender`, etc.) does not include `credentials: 'same-origin'` in its `fetch()` call. This means the auth cookie set during login is **never sent** with these requests. The server's `currentUser()` method reads the cookie to identify the user, so all game actions will be treated as unauthenticated. The server returns 401 for `/api/new`, `/api/click`, and `/api/surrender`, but users will be unable to play at all because the cookie is missing.
- **Current code:**
```javascript
async function api(path){...try{res=await fetch(url,{cache:'no-store'});}catch(err){...}}
```
- **Suggested fix:** Add `credentials: 'same-origin'` to the fetch options:
```javascript
res = await fetch(url, { cache: 'no-store', credentials: 'same-origin' });
```

---

## BUG-003: No authentication on WebSocket connections
- **Severity:** Critical
- **File:** `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`, lines 93-98; also `src/main/java/com/xiangqi/web/PublicSiteServer.java`, lines 134-139
- **Description:** Both the `OnlineSiteServer.WsHub.onConnect()` and `PublicSiteServer.WsHub.onConnect()` accept WebSocket connections without any authentication check. Any unauthenticated client can connect to `/ws` or `/online/ws` and subscribe to any room's real-time events. The `subscribe` message handler does not verify the user has permission to view the room. This exposes all room state (board positions, player moves, chat) to anyone.
- **Current code:**
```java
public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
    wsHub.onConnect(channel);  // no auth check
}
```
- **Suggested fix:** Extract the auth cookie from the HTTP upgrade request in `onConnect()`, validate the session token, and reject unauthenticated connections. Also verify the user has permission to view the target room when processing `subscribe` messages.

---

## BUG-004: WebSocket subscribe allows access to any room (IDOR)
- **Severity:** High
- **File:** `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`, lines 587-598; also `PublicSiteServer.java`, lines 757-769
- **Description:** Even if WebSocket auth were implemented, the `subscribe()` method in both `WsHub` classes accepts any arbitrary `roomId` string without checking if the room exists, if it is public, or if the authenticated user is a participant. A user could subscribe to private rooms they are not part of and observe all game events in real time.
- **Current code:**
```java
private void subscribe(WebSocketChannel channel, String roomId) {
    unsubscribe(channel);
    if (roomId == null || roomId.trim().isEmpty()) {
        return;
    }
    byRoom.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(channel);
    channelRooms.put(channel, roomId);
    // immediately sends room state without access check
    WebSockets.sendText(mapper.writeValueAsString(roomEvent(roomId)), channel, null);
}
```
- **Suggested fix:** Add authorization checks: verify the room exists, check whether it is public or the user is a participant, and reject unauthorized subscriptions with an error message.

---

## BUG-005: `InMemoryAuthSessionRepository` never checks session expiration
- **Severity:** High
- **File:** `src/main/java/com/xiangqi/online/auth/InMemoryAuthSessionRepository.java`, line 17-18
- **Description:** The `findByToken()` method in `InMemoryAuthSessionRepository` returns a session purely by token lookup without checking the `expiresAt` field. Expired sessions remain valid indefinitely. The `JdbcAuthSessionRepository` correctly filters by `expires_at > now()` in its SQL query, but the in-memory implementation used in tests does not. This means tests do not catch expiration-related bugs, and any deployment using the in-memory repository (e.g., single-instance dev mode) has sessions that never expire.
- **Current code:**
```java
public Optional<UserSession> findByToken(String token) {
    return Optional.ofNullable(sessions.get(token));
}
```
- **Suggested fix:** Add expiration check:
```java
public Optional<UserSession> findByToken(String token) {
    UserSession session = sessions.get(token);
    if (session != null && session.expiresAt().isAfter(Instant.now())) {
        return Optional.of(session);
    }
    if (session != null) {
        sessions.remove(token); // cleanup expired
    }
    return Optional.empty();
}
```

---

## BUG-006: `AuthSessionRepository.deleteByToken()` default is a no-op
- **Severity:** High
- **File:** `src/main/java/com/xiangqi/online/auth/AuthSessionRepository.java`, lines 10-11
- **Description:** The interface defines `deleteByToken` as a default method with an empty body. This means any implementation that does not override it (including the `InMemoryAuthSessionRepository` used in tests) will silently do nothing when logout is called. The server calls `store.sessions().deleteByToken(token)` on logout (both `OnlineSiteServer` and `PublicSiteServer`), but with the in-memory implementation, the session token remains valid and can be reused.
- **Current code:**
```java
default void deleteByToken(String token) {
}
```
- **Suggested fix:** Either remove the default implementation (make it abstract), or provide a meaningful default that throws `UnsupportedOperationException`. Ensure `InMemoryAuthSessionRepository` properly overrides it (it already does, but the interface default is still misleading and dangerous for new implementations).

---

## BUG-007: Auth cookie missing `Secure` flag
- **Severity:** High
- **File:** `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`, lines 426-432; `src/main/java/com/xiangqi/web/PublicSiteServer.java`, lines 578-584
- **Description:** Both servers set the auth cookie with `HttpOnly(true)` and `MaxAge(14 days)`, but never set the `Secure` flag. Without `Secure`, the cookie will be sent over unencrypted HTTP connections, exposing the session token to network eavesdropping (MITM attacks). When the site is served over HTTPS, the cookie should be marked `Secure` to prevent accidental transmission over HTTP.
- **Current code:**
```java
CookieImpl cookie = new CookieImpl(AUTH_COOKIE, session.token());
cookie.setPath("/");
cookie.setHttpOnly(true);
cookie.setMaxAge(14 * 24 * 3600);
// missing: cookie.setSecure(true);
```
- **Suggested fix:** Add `cookie.setSecure(true)` when the server is configured for HTTPS. This can be conditionally set based on a configuration flag or the presence of SSL.

---

## BUG-008: Auth cookie missing `SameSite` attribute
- **Severity:** Medium
- **File:** `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`, lines 426-432; `src/main/java/com/xiangqi/web/PublicSiteServer.java`, lines 578-584
- **Description:** Neither server sets a `SameSite` attribute on the auth cookie. Without `SameSite=Strict` or `SameSite=Lax`, the cookie is sent with cross-site requests, making the application vulnerable to Cross-Site Request Forgery (CSRF) attacks. An attacker could craft a malicious page that submits requests to the game API (e.g., `/api/auth/logout`, `/api/games/{gameId}/resign`) using the victim's authenticated session.
- **Current code:** (same as BUG-007)
- **Suggested fix:** Add `cookie.setSameSiteMode("Lax")` to the cookie configuration in both `setAuthCookie()` methods.

---

## BUG-009: Login/Register error returns 400 instead of 401
- **Severity:** Medium
- **File:** `src/main/java/com/xiangqi/online/server/OnlineSiteServer.java`, lines 203-211; `src/main/java/com/xiangqi/web/PublicSiteServer.java`, lines 363-371
- **Description:** The `handleLogin()` method catches all exceptions from `authService.login()` and returns HTTP 400 (Bad Request). However, `AuthService.login()` throws `IllegalArgumentException("invalid credentials")` when the username doesn't exist or the password is wrong. The correct HTTP status for authentication failure is 401 (Unauthorized). Returning 400 makes it harder for clients to distinguish between validation errors (bad request format) and authentication failures.
- **Current code:**
```java
private void handleLogin(HttpServerExchange exchange) {
    try {
        Map<String, Object> payload = readJson(exchange);
        UserSession session = authService.login(...);
        ...
    } catch (Exception ex) {
        sendError(exchange, StatusCodes.BAD_REQUEST, ex.getMessage()); // always 400
    }
}
```
- **Suggested fix:** Check if the exception message is "invalid credentials" and return 401 in that case, or have `AuthService` throw a custom exception type that the handler can distinguish.

---

## BUG-010: No database index on `auth_sessions.user_id`
- **Severity:** Medium
- **File:** `src/main/resources/online/schema.sql`, lines 8-13
- **Description:** The `auth_sessions` table has no index on `user_id`. While current queries filter by `token` (the primary key), there is no way to efficiently look up all sessions for a given user. This is needed for: (a) cleaning up all sessions when a user changes their password, (b) enforcing a maximum number of concurrent sessions per user, (c) auditing active sessions. The `game_moves` table also lacks an index on `game_id`, which would cause slow queries for loading move history.
- **Current code:**
```sql
create table if not exists auth_sessions (
  token varchar(128) primary key,
  user_id varchar(64) not null,
  expires_at timestamp not null,
  created_at timestamp not null
);
```
- **Suggested fix:** Add indexes:
```sql
create index if not exists idx_auth_sessions_user_id on auth_sessions(user_id);
create index if not exists idx_auth_sessions_expires_at on auth_sessions(expires_at);
```

---

## BUG-011: No foreign key constraint on `auth_sessions.user_id -> users.id`
- **Severity:** Medium
- **File:** `src/main/resources/online/schema.sql`, lines 8-13
- **Description:** The `auth_sessions.user_id` column has no foreign key constraint referencing `users.id`. This means: (a) sessions can reference non-existent user IDs (orphaned sessions), (b) deleting a user does not cascade-delete their sessions, leaving stale auth tokens in the database, (c) the `JdbcAuthSessionRepository.findByToken()` must do an extra query + null check to handle the case where the user was deleted. The `game_moves.game_id` also has no foreign key to `games.id`.
- **Current code:** No `REFERENCES` or `FOREIGN KEY` clause in the schema.
- **Suggested fix:** Add foreign key constraints:
```sql
create table if not exists auth_sessions (
  token varchar(128) primary key,
  user_id varchar(64) not null references users(id) on delete cascade,
  expires_at timestamp not null,
  created_at timestamp not null
);
```
Note: H2 may need `FOREIGN KEY` syntax instead of inline `REFERENCES` depending on version.

---

## BUG-012: Stale session memory leak in `InMemoryAuthSessionRepository`
- **Severity:** Low
- **File:** `src/main/java/com/xiangqi/online/auth/InMemoryAuthSessionRepository.java`, lines 7-8
- **Description:** The `sessions` ConcurrentHashMap grows without bound. Expired sessions are never removed from the map. Over time, this causes a memory leak. The `JdbcAuthSessionRepository` has the same issue at the database level (no cleanup job), but it is less critical since the DB can be queried with `expires_at > now()`.
- **Current code:**
```java
private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
```
- **Suggested fix:** Add a periodic cleanup task (e.g., `ScheduledExecutorService`) that removes expired sessions, or use a cache library like Caffeine with TTL-based eviction.

---

## BUG-013: No password change or session invalidation mechanism
- **Severity:** Low
- **File:** `src/main/java/com/xiangqi/online/auth/AuthService.java` (entire file)
- **Description:** The `AuthService` only provides `register()` and `login()` methods. There is no `changePassword()`, `invalidateAllSessions()`, or `deleteUser()` method. If a user's password is compromised, there is no way to change it or force-logout all sessions. This is a feature gap rather than a code bug, but it is a significant security concern for a production system.
- **Current code:** Only `register()` and `login()` exist in AuthService.
- **Suggested fix:** Add `changePassword(userId, oldPassword, newPassword)` and `invalidateAllSessions(userId)` methods to `AuthService` and the repository interfaces.

---

## BUG-014: Tests do not cover session expiration
- **Severity:** Low
- **File:** `src/test/java/com/xiangqi/online/AuthServiceTest.java` (entire file)
- **Description:** The test file only contains two tests: `registerAndLoginUseStableIdentityAndHashedPassword` and `duplicateUsernameIsRejected`. There are no tests for: (a) session expiration after 14 days, (b) invalid login credentials, (c) login with non-existent user, (d) password validation (minimum length), (e) username normalization (trim, lowercase), (f) logout/deletion of sessions. The fixed `Clock` at `2026-03-21T00:00:00Z` means time never advances, so expiration can never be tested without additional clock manipulation.
- **Current code:** Only 2 test methods, both using a fixed clock.
- **Suggested fix:** Add tests using `Clock.offset()` to simulate time advancing past session expiration. Test all error paths in `AuthService`.

---

## Summary Table

| Bug ID | Severity | File | Brief Description |
|--------|----------|------|-------------------|
| BUG-001 | Critical | web/app.js:277 | `fetchJson` is undefined — auth check always fails |
| BUG-002 | Critical | web/app.js:42 | Legacy `api()` missing `credentials:'same-origin'` — cookie never sent |
| BUG-003 | Critical | OnlineSiteServer.java:93 | No auth on WebSocket connections |
| BUG-004 | High | OnlineSiteServer.java:587 | WebSocket subscribe allows viewing any room (IDOR) |
| BUG-005 | High | InMemoryAuthSessionRepository.java:17 | In-memory sessions never expire |
| BUG-006 | High | AuthSessionRepository.java:10 | `deleteByToken()` default is a silent no-op |
| BUG-007 | High | OnlineSiteServer.java:426 | Cookie missing `Secure` flag |
| BUG-008 | Medium | OnlineSiteServer.java:426 | Cookie missing `SameSite` attribute (CSRF risk) |
| BUG-009 | Medium | OnlineSiteServer.java:203 | Login failure returns 400 instead of 401 |
| BUG-010 | Medium | schema.sql:8 | No index on `auth_sessions.user_id` |
| BUG-011 | Medium | schema.sql:8 | No FK constraint `auth_sessions.user_id -> users.id` |
| BUG-012 | Low | InMemoryAuthSessionRepository.java:8 | Memory leak: expired sessions never cleaned up |
| BUG-013 | Low | AuthService.java | No password change or session invalidation API |
| BUG-014 | Low | AuthServiceTest.java | Tests don't cover expiration, login failure, or edge cases |
