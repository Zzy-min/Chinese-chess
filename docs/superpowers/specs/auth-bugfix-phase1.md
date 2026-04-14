# Spec: Authentication Bug Fix — Phase 1

**Date:** 2026-04-14  
**Status:** Approved  
**Scope:** Fix all Critical + High + Medium auth bugs (BUG-001 ~ BUG-011)

## 1. Problem Statement

The online chess platform has 14 confirmed auth bugs. Users cannot stay logged in because:
- Frontend `refreshAuth()` crashes on `fetchJson` (undefined)
- Legacy `api()` never sends auth cookies
- WebSocket connections accept anyone without auth
- In-memory sessions never expire
- Logout silently does nothing in some implementations
- Cookies lack `Secure`/`SameSite` attributes
- Login errors return wrong HTTP status
- Database schema missing indexes and FK constraints

## 2. Bugs to Fix (Phase 1: BUG-001 ~ BUG-011)

### Critical (must fix)
| Bug | Description | Fix Approach |
|-----|-------------|-------------|
| BUG-001 | `fetchJson` undefined in `web/app.js:277` | Replace with `fetch` + `credentials:'same-origin'` |
| BUG-002 | `api()` missing `credentials:'same-origin'` | Add `credentials:'same-origin'` to fetch options |
| BUG-003 | WebSocket no auth check | Extract cookie from upgrade request, validate session, reject unauthorized |

### High (must fix)
| Bug | Description | Fix Approach |
|-----|-------------|-------------|
| BUG-004 | WebSocket subscribe IDOR | Check room visibility + user membership before subscribing |
| BUG-005 | InMemory sessions never expire | Add `expiresAt` check in `findByToken()`, cleanup expired |
| BUG-006 | `deleteByToken()` default no-op | Remove default impl, make abstract |
| BUG-007 | Cookie missing `Secure` | Add `Secure` flag (conditional on HTTPS config) |

### Medium (should fix)
| Bug | Description | Fix Approach |
|-----|-------------|-------------|
| BUG-008 | Cookie missing `SameSite` | Add `SameSite=Lax` |
| BUG-009 | Login returns 400 not 401 | Return 401 for "invalid credentials" |
| BUG-010 | No index on `auth_sessions.user_id` | Add index in schema.sql |
| BUG-011 | No FK constraint | Add FK in schema.sql |

### Deferred to Phase 2 (Low)
- BUG-012: Memory leak cleanup (add Caffeine cache or scheduled cleanup)
- BUG-013: Password change / session invalidation API (new feature)
- BUG-014: Expanded test coverage

## 3. Non-Goals
- Adding new features (password change, session management UI)
- Migrating from H2 to PostgreSQL
- Adding rate limiting or brute-force protection
- Frontend UI redesign

## 4. Constraints
- Must not break existing game functionality
- Must maintain backward compatibility with existing sessions
- Changes must pass existing tests + new tests
- WebSocket auth must not block legitimate browser connections

## 5. Verification Criteria
1. All existing tests pass (`mvn test`)
2. New tests cover: session expiration, logout, WebSocket auth rejection
3. Manual verification: login → refresh page → still logged in
4. Manual verification: WebSocket connection without cookie → rejected
5. Cookie headers include `SameSite=Lax` (and `Secure` when HTTPS)
