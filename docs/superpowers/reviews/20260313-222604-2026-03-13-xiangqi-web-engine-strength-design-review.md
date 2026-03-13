## Spec Review

**Status:** ❌ Issues Found

**Issues (if any):**
- [Section 8.2 + Section 5.5 + “执行前提”]: Baseline run commands depend on artifacts that are not guaranteed to exist on baseline `2a02533` (`XiangqiFenQualityBenchmarkMain`, `XiangqiEngineHeadToHeadMain`, listed test classes, and `AIBenchmarkMain --output/--json` behavior) - this can make baseline/candidate diff non-reproducible.
- [Section 7.4 vs Section 8.3]: Observability contract is inconsistent (`external_timeout_count/external_restart_count/external_fallback_to_builtin_count/fallback_to_first_legal_count` are required in 7.4 but not in mandatory `fallback-metrics.json` schema) - implementers cannot know which metric set is authoritative.
- [Section 5.2]: Requirement is self-conflicting (“强制参数映射目标” but also “可小幅微调”) with no allowed range - pass/fail compliance is ambiguous.
- [Section 8.2 (A1/A3)]: Acceptance KPIs reference undefined computed fields (`HARD_vs_MEDIUM_score`, `pikafish_score`, `builtin_score`, “平均时长增幅”) without explicit formulas/schema - acceptance cannot be judged consistently.
- [Section 8.1/8.2]: No contract for invalid benchmark inputs (bad FEN row, duplicate `id`, invalid `weight`, invalid `expected_move`) - edge-case handling is underspecified and can break comparability.

**Recommendations (advisory):**
- Add a concrete baseline collection workflow (how to run baseline when tools/tests are introduced in candidate).
- Add required JSON schema and formula definitions for A1/A2/A3 outputs.
- Replace “小幅微调” with explicit allowed deltas (for example, per-parameter min/max).
- Unify Section 7.4 and 8.3 into one canonical metrics schema.
- Add strict input-validation/error-policy rules for benchmark suite parsing (fail-fast vs skip+count).