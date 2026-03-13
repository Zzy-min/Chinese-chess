## Spec Review

**Status:** ❌ Issues Found

**Issues (if any):**
- [Section 8.1]: Acceptance thresholds are labeled as “建议阈值” (suggested), not mandatory criteria - planning cannot define a clear “done” gate.
- [Section 8.2]: Fixed-FEN benchmark is underspecified (no dataset source/path/size, no metric formula, no numeric pass thresholds, no deterministic run settings like seed/time control) - implementations will diverge and results won’t be comparable.
- [Section 5.3 / 5.4 / 6]: “增强失败状态描述” and possible extra state fields are not defined as an interface contract (field names, value enums, backward-compat guarantees) - integration with `/api/state` is ambiguous.
- [Section 5.2 / 7.1]: Stage timeout strategy is concept-only; concrete timeout values, retry/restart behavior, and per-stage test expectations are missing - stability behavior is not testable as written.
- [Section 1 + 8]: Goal includes both built-in AI and Pikafish strength improvement, but measurable acceptance only covers built-in strength and Pikafish stability - one major objective lacks success criteria.

**Recommendations (advisory):**
- Add a single acceptance matrix with exact commands, fixed benchmark parameters, and pass/fail thresholds.
- Define the FEN benchmark artifact contract (`input file format`, `location`, `output schema`, `scoring formula`).
- Specify the `/api/state` extension contract explicitly (or state “no new fields this phase”).
- Add explicit Pikafish strength KPI (for example, quality benchmark delta or head-to-head target under fixed movetime/depth).