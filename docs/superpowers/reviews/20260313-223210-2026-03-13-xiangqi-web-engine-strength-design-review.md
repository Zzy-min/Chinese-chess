## Spec Review

**Status:** ❌ Issues Found

**Issues (if any):**
- [Section 5.3]: Fallback order is fixed as `selected engine -> builtin -> null`, but behavior is unclear when `selected engine` is already `builtin`. - This can cause duplicate builtin evaluation and inconsistent latency behavior.
- [Sections 7.5 & 8.4]: Spec requires **per-scenario** 100% fallback success, but acceptance only validates aggregate `fallback_success_rate` and `deadlock_count`. - A run can pass while individual fault scenarios are missing or not independently validated.
- [Sections 8.3 & 8.4]: No explicit runtime contract for Pikafish environment (binary version/path/platform prerequisites, and policy when binary is unavailable). - Acceptance may become non-reproducible and fail due to environment, not implementation.

**Recommendations (advisory):**
- Add explicit dedup rule: if selected engine is builtin, fallback should go directly to `null` (or clearly define intended behavior).
- Extend fallback metrics with scenario-level fields (for example `scenario_results[]`) or require explicit per-scenario test assertions.
- Add a short “Benchmark Environment” block (Java/Maven version, Pikafish binary version/location, availability check command, failure policy).