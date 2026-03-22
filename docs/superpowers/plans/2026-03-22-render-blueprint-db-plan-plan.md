# Render Blueprint DB Plan Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Render deployment failure by removing the implicit paid PostgreSQL plan from the Blueprint.

**Architecture:** Keep the existing Docker web service and managed PostgreSQL wiring. Change only the Blueprint database declaration so Render provisions the intended free-tier database instead of defaulting to `basic-256mb`.

**Tech Stack:** Render Blueprint (`render.yaml`), Maven verification, Render CLI validation where possible

---

### Task 1: Confirm the deployment root cause

**Files:**
- Read: `render.yaml`
- Read: `Dockerfile`
- Read: `src/main/java/com/xiangqi/web/PublicWebMain.java`
- Read: `src/main/java/com/xiangqi/online/server/OnlineStore.java`

- [ ] **Step 1: Verify the application still builds**

Run: `mvn -q test`
Expected: Pass

- [ ] **Step 2: Verify the package build still succeeds**

Run: `mvn -q -DskipTests package`
Expected: Pass

- [ ] **Step 3: Compare with the earlier Blueprint revision**

Run: `git show fec6afe:render.yaml`
Expected: Earlier config has no managed database block

### Task 2: Apply the minimal Blueprint fix

**Files:**
- Modify: `render.yaml`

- [ ] **Step 1: Add an explicit free plan to the managed database**

Change:

```yaml
databases:
  - name: xiangqi-db
    plan: free
```

- [ ] **Step 2: Keep the service wiring unchanged**

Do not change:
- `runtime: docker`
- `dockerfilePath: ./Dockerfile`
- `XQ_DATABASE_URL` from database connection string

### Task 3: Re-verify after the config change

**Files:**
- Verify: `render.yaml`

- [ ] **Step 1: Re-run build verification**

Run: `mvn -q test`
Expected: Pass

- [ ] **Step 2: Re-run package verification**

Run: `mvn -q -DskipTests package`
Expected: Pass

- [ ] **Step 3: Attempt Blueprint validation if authenticated**

Run: `render blueprints validate render.yaml`
Expected: Validates successfully when Render CLI is authenticated

- [ ] **Step 4: Report the remaining external dependency**

If validation returns `401 Unauthorized`, report that repository changes are ready but Render CLI authentication is still required to validate or apply the Blueprint remotely.
