# Render Blueprint Database Plan Design

## Context

The Render deployment started failing after the Blueprint gained a managed PostgreSQL database in `render.yaml`.
The application itself still builds and tests successfully with Maven, and the web entrypoint already honors Render's `PORT` and `BIND_HOST` environment variables.

## Evidence

- `mvn -q test` passes.
- `mvn -q -DskipTests package` passes.
- `PublicWebMain` binds to `PORT` and `BIND_HOST`.
- `OnlineStore` accepts `postgres://`, `postgresql://`, and JDBC URLs from `XQ_DATABASE_URL`.
- The prior working Blueprint revision (`fec6afe`) did not provision a database.
- The current Blueprint provisions a database without an explicit `plan`.
- Render Blueprint documentation states that an omitted PostgreSQL `plan` defaults to `basic-256mb`, which can fail in workspaces expecting free-tier deployment.

## Root Cause

The deployment risk is not in the Java service image or startup command. The likely failing step is Render resource provisioning: the Blueprint requests a managed PostgreSQL instance but does not set its plan explicitly, so Render falls back to a paid database plan.

## Constraints

- Keep existing product boundaries unchanged.
- Do not revert unrelated frontend edits.
- Preserve the managed database path so persisted users, sessions, archived games, and move records continue to work.
- Minimize deployment risk by changing only the Blueprint resource declaration.

## Decision

Add an explicit free database plan to the Blueprint:

```yaml
databases:
  - name: xiangqi-db
    plan: free
```

This keeps the existing application/database integration intact while removing the hidden paid-plan default.

## Non-Goals

- No redesign of persistence architecture.
- No changes to runtime code, schema logic, or app startup.
- No attempt to solve Render account authentication or workspace setup inside the repository.
