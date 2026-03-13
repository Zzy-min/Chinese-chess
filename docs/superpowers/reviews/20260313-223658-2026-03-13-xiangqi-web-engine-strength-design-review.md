## Spec Review

**Status:** ❌ Issues Found

**Issues (if any):**
- [Section 7.1 vs 7.2 / 5.2]: "`uci` 阶段超时 8s 直接失败" 与“首次超时/异常后必须重启并重试一次”存在语义冲突/歧义 - 实现者可能一个会重试、一个不会，直接影响稳定性行为与 A4 结果一致性。
- [Section 5.4 + Section 8]: API 兼容性被定义为强制约束（`/api/new`、`/api/endgame`、`/api/state` 字段不变），但 A1-A4 没有对应的验收校验步骤 - 即使接口字段回归，也可能在当前矩阵下被误判通过。
- [Section 5.2 + Section 8]: Pikafish 难度映射被标注为“强制精确值”，但验收未直接验证每档实际下发的 UCI 选项（含重试参数） - 关键强制要求覆盖不足。

**Recommendations (advisory):**
- 增加一个显式接口契约验收项（可命名 A5），校验 `/api/new`、`/api/endgame`、`/api/state` 的请求/响应字段集合。
- 明确 `uci` 超时后的唯一处理流程（是否必须进入“一次重启重试”）。
- 在 `PikafishUciEngineTest` 增加对 EASY/MEDIUM/HARD 与重试参数下发值的断言。