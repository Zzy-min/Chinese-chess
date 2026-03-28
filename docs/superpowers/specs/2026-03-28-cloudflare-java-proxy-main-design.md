# Cloudflare Worker Front Door For Main Java Site

## Summary

Current `main` is a Java web application whose public entry is `com.xiangqi.web.PublicWebMain`. It serves the beige homepage, the in-page AI board flow, the online lobby, auth endpoints, practice/game APIs, and the `/online/ws` websocket endpoint. It is not a native Cloudflare Worker app and cannot be deployed directly by pointing Cloudflare Git builds at the repository root and running `npx wrangler deploy`.

The new deployment shape will be:

- a dedicated Cloudflare Worker project stored in-repo under `deploy/cloudflare-java-proxy/`
- Cloudflare Git builds targeting only that subproject
- the Worker acting as the public front door for the production domain
- the existing Java service remaining the fixed origin behind the Worker

This keeps `main` shipping the Java beige-lobby experience while removing the branch/root-directory mismatch that caused the previous Cloudflare build failures.

## Problem Statement

The current Cloudflare Worker project is connected directly to `Zzy-min/Chinese-chess`, with:

- `Production branch = main`
- `Root directory = /`
- `Deploy command = npx wrangler deploy`
- non-production branch builds enabled

That configuration is structurally wrong for the current repository:

- repository root does not contain a Worker app
- repository root does not contain a stable `wrangler` project
- `main` now documents and ships `Cloudflare Tunnel + Java origin` as the production runtime
- pushing feature branches or merging PRs triggers a Worker build against the wrong project root

This mismatch surfaced as:

- failing PR checks from Cloudflare
- `wrangler` being unable to detect the expected static/Worker project
- accidental resurrection of an older Node/OpenNext deployment path during incident response

## Chosen Architecture

### 1. Dedicated Worker Subproject

Create a standalone Worker project under:

- `deploy/cloudflare-java-proxy/`

This project owns all Cloudflare-specific build artifacts:

- `package.json`
- `wrangler.jsonc`
- `src/index.ts`
- minimal tests for route classification / URL rewriting
- local README or deployment notes if needed

Cloudflare Git integration must point to this directory, not repository root.

### 2. Worker As Public Front Door

The Worker will:

- accept all public requests for the production hostname
- proxy them to the Java origin
- preserve method, headers, query string, path, and body
- pass websocket upgrade traffic for `/online/ws`
- cache only safe static assets

The Worker will not reimplement application logic.

### 3. Java Service Remains The Origin

The existing Java runtime stays unchanged in responsibility:

- `PublicWebMain` continues serving `/`
- `PublicSiteServer` continues serving `/api/*`
- `PublicSiteServer` continues serving `/online`, `/online/api/*`, and `/online/ws`

The Worker simply forwards traffic to a fixed origin base URL.

### 4. Explicit Origin Configuration

The Worker will require an origin environment variable, for example:

- `ORIGIN_BASE_URL=https://origin.example.internal`

Rules:

- this value must point to the Java origin directly
- it must not point back to the public Worker hostname
- it must not create a proxy loop

The origin value will be configured in Cloudflare Worker settings, not committed.

## Request Routing Design

### Proxy Behavior

All incoming requests route through the Worker and are rewritten to:

- `new URL(request.url).pathname + search` on top of `ORIGIN_BASE_URL`

The Worker preserves:

- HTTP method
- request body
- cookies and auth headers
- query parameters
- websocket upgrade headers

### Cache Policy

Default proxy policy is conservative:

- no cache for HTML documents
- no cache for `/api/*`
- no cache for `/online/api/*`
- no cache for auth-related responses
- no cache for websocket traffic

Safe static paths receive explicit cache treatment:

- `/assets/ui/*`
- `/assets/audio/*`
- `/online/assets/site/*`

This avoids stale auth/session state while still reducing origin load for immutable frontend assets.

### WebSocket Support

The Worker must forward `/online/ws` without changing the protocol semantics.

Design requirement:

- if request contains websocket upgrade semantics, proxy via `fetch` to origin and return the upgraded response untouched

This path must be tested explicitly because online play depends on it.

## Cloudflare Project Configuration

The future stable Cloudflare Worker project must use:

- `Git repository = Zzy-min/Chinese-chess`
- `Production branch = main`
- `Root directory = deploy/cloudflare-java-proxy`
- deploy/build commands scoped to that subproject only

To prevent a repeat of the current incident, defaults are:

- disable non-production branch builds initially
- if preview builds are later re-enabled, restrict watch paths to `deploy/cloudflare-java-proxy/**`
- do not allow repository-root Worker builds for this project

This is the core recurrence-prevention measure.

## Public Runtime Boundaries

### In Scope

- Worker front door for the current Java beige-lobby site
- reverse proxying and static asset caching
- Cloudflare project configuration that is permanently tied to the Worker subproject
- deployment documentation updates so future pushes do not hit the wrong root

### Out Of Scope

- rewriting the Java app into a Worker-native application
- replacing Java persistence/auth/game logic with D1/KV/DO
- redesigning the homepage or online UI
- replacing the current origin hosting mechanism in this phase

## Verification Requirements

Implementation will be considered correct only if all of these are verified freshly:

- `mvn test` still passes for the Java app
- local Worker dev/proxy can load `/` from the Java origin
- proxied `/api/auth/me` and one representative `/online/api/*` route behave correctly
- `/online/ws` websocket upgrade path is verified against the Java origin
- production Worker deployment returns `200` for the homepage
- production Worker serves the current `main` beige homepage, not the older Node/OpenNext site
- Cloudflare Git build configuration is scoped to `deploy/cloudflare-java-proxy`
- a future PR touching unrelated Java/frontend files no longer triggers the old repository-root Worker failure

## Risks And Defaults

### Main Risk

The biggest risk is confusing “Worker front door” with “Worker-hosted application”.

Mitigation:

- keep the Worker minimal
- keep all app logic in Java
- test transparent proxying, not reimplementation

### Operational Risk

If Cloudflare continues building from repository root, the same breakage can recur.

Mitigation:

- dedicated subproject root
- branch-build restrictions
- explicit deployment documentation

### Chosen Defaults

- use Worker proxy + Java origin, not Worker rewrite
- cache only immutable static assets
- disable non-production branch builds by default
- store origin URL only as Cloudflare runtime config, never in git

## Acceptance Criteria

- `main` remains the source of truth for the beige Java homepage and online lobby
- Cloudflare production domain serves the Java site through the Worker front door
- Cloudflare no longer attempts repository-root `wrangler deploy`
- deployment setup is reproducible and branch/root mismatches are structurally prevented
