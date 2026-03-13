## Spec Review

**Status:** ❌ Issues Found

**Issues (if any):**
- [Section 8.2 + 8.4 + 5.2]: Baseline workflow says `benchmark-harness` must not include engine behavior changes, but A4 baseline command includes `PikafishUciEngineMappingTest`, and A4 requires that test to assert EASY/MEDIUM/HARD + retry mapping values defined in 5.2. If baseline mapping differs (likely), baseline A4 cannot pass by design, breaking the stated reproducible baseline flow.
- [Section 8.3 (A1 thresholds/formula)]: `avg_time_growth` is defined, but `candidate_avg_think_ms` / `baseline_avg_think_ms` source and aggregation scope are not explicitly bound to JSON paths (per difficulty? per pair? weighted how?). This makes acceptance non-deterministic across implementations.
- [Section 5.5 + 8.3]: New benchmark tools are required, but output schema contracts for `*-ai.json`, `*-fen-builtin.json`, and `*-h2h.json` are not fully specified (unlike fallback metrics in 7.4). Missing explicit schema can cause incompatible tool outputs and disputed pass/fail results.

**Recommendations (advisory):**
- No TODO/TBD/placeholder markers found; document is largely complete in structure.
- Add explicit JSON schema tables for A1/A2/A3 artifacts (required keys, types, and formulas by key path).
- Clarify A4 baseline handling: either exclude mapping-value assertions from baseline run, or make baseline/candidate expected mappings explicitly parameterized.
- Add execution preconditions for benchmark commands (`mvn -DskipTests package` and output directory creation) to reduce runbook ambiguity.