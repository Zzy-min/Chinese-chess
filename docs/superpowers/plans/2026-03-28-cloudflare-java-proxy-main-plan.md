# Cloudflare Java Proxy Main Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make current `main` deployable through a dedicated Cloudflare Worker front door while preserving the Java beige-lobby site as the real application origin.

**Architecture:** Add an isolated Worker project under `deploy/cloudflare-java-proxy/` that transparently proxies HTML, APIs, static assets, and websocket traffic to a configured Java origin. Keep `PublicWebMain` unchanged as the source of truth for app behavior, and move Cloudflare Git builds to the Worker subproject so repository-root `wrangler deploy` can never recur.

**Tech Stack:** Java 11 / Maven, Cloudflare Workers, Wrangler 4.x, Node.js, plain JavaScript for the Worker project, node:test for unit coverage.

---

## Chunk 1: Worker Subproject And Unit Contracts

### Task 1: Add the dedicated Cloudflare Worker project

**Files:**
- Create: `deploy/cloudflare-java-proxy/package.json`
- Create: `deploy/cloudflare-java-proxy/package-lock.json`
- Create: `deploy/cloudflare-java-proxy/wrangler.jsonc`
- Create: `deploy/cloudflare-java-proxy/src/index.js`
- Create: `deploy/cloudflare-java-proxy/src/proxy.js`
- Test: `deploy/cloudflare-java-proxy/test/proxy.test.js`

- [ ] **Step 1: Write failing unit tests for proxy URL rewriting, cache classification, and loop prevention**
- [ ] **Step 2: Run `node --test deploy/cloudflare-java-proxy/test/proxy.test.js` and confirm failure**
- [ ] **Step 3: Implement minimal proxy helpers in `src/proxy.js`**
- [ ] **Step 4: Implement Worker fetch handler in `src/index.js`**
- [ ] **Step 5: Run `node --test deploy/cloudflare-java-proxy/test/proxy.test.js` and confirm pass**

### Task 2: Lock the Worker build entry to the subproject

**Files:**
- Modify: `deploy/cloudflare-java-proxy/package.json`
- Modify: `deploy/cloudflare-java-proxy/wrangler.jsonc`

- [ ] **Step 1: Add stable scripts for `test`, `dev`, and `deploy`**
- [ ] **Step 2: Pin `wrangler` in the subproject lockfile**
- [ ] **Step 3: Verify `npm ci` works from `deploy/cloudflare-java-proxy`**

## Chunk 2: Java-Origin Integration Verification

### Task 3: Add an integration check against the local Java server

**Files:**
- Create: `deploy/cloudflare-java-proxy/test/integration-proxy.mjs`
- Optionally create: `deploy/cloudflare-java-proxy/scripts/check-local-proxy.mjs`

- [ ] **Step 1: Start the Java site locally with `mvn -q -DskipTests package` and `java -cp "target/classes;target/dependency/*" com.xiangqi.web.PublicWebMain` (or reuse an already running local instance)**
- [ ] **Step 2: Write a failing integration check that hits Worker dev against the Java origin for `/`, `/api/auth/me`, and one `/online/api/*` route**
- [ ] **Step 3: Run the integration check and confirm failure before wiring final proxy behavior**
- [ ] **Step 4: Adjust proxy forwarding until the integration check passes**
- [ ] **Step 5: Verify websocket upgrade for `/online/ws` through Worker dev**

### Task 4: Preserve Java test safety

**Files:**
- Test: existing Java test suite

- [ ] **Step 1: Run `mvn test` before finishing implementation**
- [ ] **Step 2: Confirm Java app behavior is unchanged by Worker-only additions**

## Chunk 3: Documentation And Recurrence Prevention

### Task 5: Document the new deployment contract

**Files:**
- Modify: `README.md`
- Modify: `README.en.md`
- Modify: `README.zh-CN.md`
- Modify: `docs/deployment/cloudflare-tunnel.md`
- Optionally create: `docs/deployment/cloudflare-java-proxy.md`

- [ ] **Step 1: Update docs so production path is described as `Cloudflare Worker front door + Java origin`**
- [ ] **Step 2: Explicitly state that Cloudflare Git builds must use `deploy/cloudflare-java-proxy` as root**
- [ ] **Step 3: Record the required runtime variable `ORIGIN_BASE_URL` and the no-loop rule**
- [ ] **Step 4: Record that non-production branch builds should stay disabled unless watch paths are scoped to the Worker subproject**

### Task 6: Add repository hygiene for local Cloudflare artifacts

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Ignore repo-local `.wrangler/` if not already ignored**
- [ ] **Step 2: Verify `git status` stays clean after local Worker dev/deploy commands**

## Chunk 4: Cloudflare Project Cutover

### Task 7: Deploy the Worker front door for current `main`

**Files:**
- Use: `deploy/cloudflare-java-proxy/*`

- [ ] **Step 1: Deploy the Worker manually with `wrangler deploy` from `deploy/cloudflare-java-proxy`**
- [ ] **Step 2: Verify production homepage returns `200` and serves the Java beige-lobby UI**
- [ ] **Step 3: Verify `/online` and one API route still function through the Worker**

### Task 8: Reconfigure Cloudflare Git builds to the new subproject

**Files:**
- No repo file if dashboard-only
- If automation is feasible, add only the minimal script/doc needed

- [ ] **Step 1: Set Cloudflare Worker Git root directory to `deploy/cloudflare-java-proxy`**
- [ ] **Step 2: Keep `Production branch = main`**
- [ ] **Step 3: Disable non-production branch builds by default**
- [ ] **Step 4: Verify future builds no longer run repository-root `npx wrangler deploy`**

## Verification Checklist

- [ ] `node --test deploy/cloudflare-java-proxy/test/proxy.test.js`
- [ ] Local Worker dev successfully proxies current Java homepage
- [ ] Websocket path `/online/ws` proxies successfully
- [ ] `mvn test`
- [ ] Production Worker returns `200 OK`
- [ ] Production Worker serves the current Java beige homepage rather than the old Node/OpenNext site
- [ ] Cloudflare build settings point to `deploy/cloudflare-java-proxy`

## Assumptions

- The fixed Java origin can run on a reachable host that Cloudflare can fetch.
- The production Worker name can continue using the existing `chinese-chess` service.
- Dashboard or API access is available to update Cloudflare build settings after the subproject is added.
