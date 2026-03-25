# GitHub Upload And Render Deploy Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Push the verified Node/TS four-game site to GitHub and prepare a Render-ready deployment blueprint for the new stack.

**Architecture:** Keep the new deployment topology split into `qiju-api` and `qiju-web`, but make the browser speak to the API through a same-origin `/backend` rewrite on the web service. Preserve the legacy Java Render blueprint as a separate file.

**Tech Stack:** Git, GitHub CLI, Render Blueprint, Next.js rewrites, Fastify cookie auth

---

## Chunk 1: Deployment Adaptation

### Task 1: Make the web app deployment-safe behind a same-origin backend proxy

**Files:**
- Modify: `apps/web/next.config.ts`
- Modify: `apps/web/src/lib/api-base.ts`
- Modify: `apps/server/src/auth.ts`

- [ ] **Step 1: Add backend rewrite support to Next.js**
- [ ] **Step 2: Make API base and WebSocket base support relative `/backend` deployment paths**
- [ ] **Step 3: Make cookie settings configurable for secure deployment mode**
- [ ] **Step 4: Re-run web and server verification**

### Task 2: Add deployment scripts and Render blueprint files

**Files:**
- Modify: `apps/server/package.json`
- Modify: `apps/web/package.json`
- Create: `render.yaml`
- Create: `render-java.yaml`

- [ ] **Step 1: Add explicit start scripts for Render runtime use**
- [ ] **Step 2: Move the legacy Java blueprint to `render-java.yaml`**
- [ ] **Step 3: Write a new root `render.yaml` for the Node/TS web + API stack**
- [ ] **Step 4: Attempt blueprint validation and record the exact result**

## Chunk 2: GitHub Upload

### Task 3: Commit the verified work

**Files:**
- Modify: `README.md`
- Modify: `docs/current-node-site-matrix.md`
- Modify: `docs/superpowers/specs/2026-03-25-github-render-deploy-design.md`

- [ ] **Step 1: Ensure docs reflect the shipped state and deployment shape**
- [ ] **Step 2: Run fresh verification before commit**
- [ ] **Step 3: Stage the full Node/TS site and deployment files**
- [ ] **Step 4: Create a conventional commit**

### Task 4: Push to GitHub

**Files:**
- No file edits

- [ ] **Step 1: Push `deploy/main-sync` to `origin` with upstream tracking**
- [ ] **Step 2: Confirm the remote branch exists**
- [ ] **Step 3: Record the exact GitHub branch URL**

## Chunk 3: Render Deployment Attempt

### Task 5: Attempt Render deployment with honest status reporting

**Files:**
- No guaranteed file edits

- [ ] **Step 1: Verify Render authentication state**
- [ ] **Step 2: If authenticated, validate blueprint and trigger/import deployment**
- [ ] **Step 3: If unauthenticated, stop short of false success and record the exact blocker**
- [ ] **Step 4: Provide the shortest manual next step needed to finish deployment**
