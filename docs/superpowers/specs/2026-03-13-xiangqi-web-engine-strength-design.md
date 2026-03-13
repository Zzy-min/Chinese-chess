# Xiangqi Web 端棋力提升设计

## 1. 背景与目标

本设计聚焦 `XiangqiGame` 网页端（`WebXiangqiServer`）人机对战棋力提升，在不新增前端高级开关的前提下，同时提升：
- 内置引擎 `MinimaxAI` 对局质量。
- 外部引擎 `PikafishUciEngine` 棋力与稳定性。

已确认业务约束：
- 仅网页端，桌面端不改。
- 先提升内置 AI，再完善外部引擎协同。
- `EASY/MEDIUM` 保持流畅，`HARD` 明显增强。
- 验收必须包含：胜率基准 + 固定 FEN 质量基准 + 外部引擎稳定性。

本期“完成”定义：必须满足第 8 节验收矩阵全部强制门槛。

## 2. 范围与非目标

### 2.1 本期范围

- 调优 `MinimaxAI` 难度预算与搜索阈值（参数级改动，不重写搜索框架）。
- 调优 `PikafishUciEngine` 的 `movetime/depth/skill/hash/threads` 映射和分阶段超时恢复。
- 明确并固化 FEN 质量基准数据协议，新增质量基准工具。
- 复用并增强 `AIBenchmarkMain`，输出可比较的结构化结果。
- 保持 Web API 兼容：不破坏现有前端调用。

### 2.2 非目标

- 不新增网页高级设置（手动深度、手动时间、手动 Hash 等）。
- 不重写 `MinimaxAI` 搜索架构（不引入全新搜索器）。
- 不改桌面端 `GameController` / Swing 流程。

## 3. 现状摘要

网页象棋 PVC 主链路：
- `WebXiangqiServer` 在 AI 回合异步触发搜索。
- `ConfigurableXiangqiEngine` 选择 `BuiltinXiangqiEngine` 或 `PikafishUciEngine`。
- 外部失败后回退内置，再失败则首合法着法兜底。

当前问题：
- `HARD` 档提升空间仍大，分档拉开不稳定。
- Pikafish 参数偏保守，超时恢复策略不够可验证。
- 固定 FEN 质量基准缺少统一数据与公式，难做跨版本比较。

## 4. 方案对比与结论

候选方案：
1. 参数调优优先（采用）
2. 动态引擎调度（暂缓）
3. 离线大规模自动调参管线（暂缓）

采用原因：
- 变更集中在现有边界内，风险最低。
- 能快速落地并通过统一基准量化收益。
- 与“后台自动调优、前端不加开关”的约束一致。

## 5. 架构与模块边界

### 5.1 `MinimaxAI`（内置引擎强度线）

职责：
- 在现有搜索框架中，通过预算与阈值调优提升 `HARD` 质量。

改动边界：
- 允许调整：`Difficulty` 初始预算、`tuneBudget(...)`、`searchFastMode` 触发阈值、相关 hard cap。
- 不允许调整：搜索主框架替换、对局规则语义改变。

### 5.2 `PikafishUciEngine`（外部引擎强度与稳定线）

职责：
- 提供外部引擎走子能力，异常时快速失败，交由上层回退。

参数映射（强制，精确值）：
- `EASY`: `movetime=450ms`, `depth=6`, `skill=8`, `threads=1`, `hash=32`
- `MEDIUM`: `movetime=1100ms`, `depth=10`, `skill=15`, `threads=1`, `hash=64`
- `HARD`: `movetime=2600ms`, `depth=15`, `skill=20`, `threads=2`, `hash=128`

分阶段超时与恢复（强制执行）：
- `uci` 初始化超时：`8000ms`
- `readyok` 等待超时：`3000ms`
- `bestmove` 超时：`min(12000ms, movetime*2 + 1200ms)`
- 单次请求最多 1 次引擎重启重试：
  - 首次超时/异常 -> `closeProcess()` -> 重启引擎
  - 重试预算降级计算规则（强制，向下取整）：
    - `movetime_retry = max(300, floor(movetime * 0.6))`
    - `depth_retry = max(6, depth - 2)`
  - 重试仍失败 -> 返回 `null`

### 5.3 `ConfigurableXiangqiEngine`（路由与回退线）

职责：
- 路由引擎并保证失败回退链路。

接口契约（强制）：
- `XiangqiEngine` 接口签名不变。
- 回退顺序固定（去重后）：`selected external -> builtin -> null(交上层兜底)`。
- 当 `selected` 已经是 `builtin` 时，不允许再次调用第二次 `builtin`，失败后直接返回 `null`。
- 失败原因不通过新增 API 字段暴露；仅在统一指标文件与日志中体现。

### 5.4 `WebXiangqiServer`（对局执行线）

职责：
- AI 异步调度与兜底落子。

接口契约（强制）：
- `/api/new`、`/api/endgame`、`/api/state` 查询参数与 JSON 字段名保持不变。
- 本期不新增 `/api/state` 字段，不删除字段，不重命名字段。
- 允许的唯一“可见变化”是已有文本字段内容变化（例如 `xiangqiAiEngineText` 的回退提示后缀）。

### 5.5 基准工具（验证线）

改动项：
- 增强 `AIBenchmarkMain`：支持 `--output` 与 `--json`。
- 新增 `XiangqiFenQualityBenchmarkMain`：读取固定 FEN 套件，输出质量指标 JSON。
- 新增 `XiangqiEngineHeadToHeadMain`：用于 Pikafish 与内置引擎固定预算对比。
- 新增回退稳定性测试与指标输出能力（见 8.4）。
- 新增 API 契约快照测试（见 8.5）。

## 6. 数据流与兼容性

网页 PVC 象棋流程：
1. 前端 `/api/new` 提交 `difficulty + xiangqiEngine`。
2. Session 保存偏好并在 AI 回合异步搜索。
3. 外部引擎异常/超时返回 `null` 后，回退内置引擎。
4. 内置仍失败时首合法兜底，确保对局推进。
5. `/api/state` 返回既有字段集，前端无需改动。

兼容性约束：
- 前端 `src/main/resources/web/app.js` 不因本需求被迫修改协议解析逻辑。
- 任一回退路径均不得阻塞 `tick()` 主循环。

## 7. 错误处理与稳定性策略

### 7.1 分阶段超时策略（具体值）

- `uci` 阶段：超时 8s 后进入统一重试流程（见 7.2），不是直接终止。
- `isready` 阶段：每次 3s 超时，失败即重启。
- `bestmove` 阶段：按 `min(12000, movetime*2+1200)` 限时。

### 7.2 重试/重启规则

- 单次搜索最多一次重试。
- `uci`、`readyok`、`bestmove` 任一阶段首次失败（超时或异常）都必须进入该重试流程。
- 首次失败后必须销毁进程并重启，禁止复用疑似坏状态进程。
- 重试失败立即返回 `null`，由上层回退，不允许继续阻塞。

### 7.3 非法结果防护

- 外部返回着法必须过 `board.isValidMove`。
- 非法着法等价失败，进入回退链。

### 7.4 统一可观测指标（唯一权威契约）

权威指标文件：`docs/benchmarks/runs/<tag>-fallback-metrics.json`。

必须包含字段：
- `scenario_count`（int）
- `fallback_success_count`（int）
- `fallback_success_rate`（float）
- `deadlock_count`（int）
- `timeout_case_count`（int）
- `invalid_move_case_count`（int）
- `external_timeout_count`（int）
- `external_restart_count`（int）
- `external_fallback_to_builtin_count`（int）
- `fallback_to_first_legal_count`（int）
- `scenario_results`（array，元素结构：`{name, success_count, total_count, success_rate}`）

除该 JSON 外，其他日志字段不作为验收依据。

### 7.5 稳定性测试预期

- 模拟场景：引擎不可用、启动失败、ready 超时、bestmove 超时、非法着法返回。
- 每个场景下回退成功率必须 `100%`（逐场景统计），且测试进程无死锁/假死。

## 8. 测试与验收矩阵（强制）

执行前提：
- 基线代码基点：`2a02533`。
- 候选代码：当前实现分支。
- 统一 seed：`20260313`。

### 8.0 基准环境契约与执行前置步骤

环境约束：
- Java：`11+`；Maven：`3.9+`。
- Pikafish 二进制路径由 `XQ_XIANGQI_PIKAFISH_CMD` 指定，命令必须可执行。
- 基线与候选必须使用同一 Pikafish 二进制（同一路径或同一文件哈希）。
- 可用性检查命令（必须成功）：`where.exe pikafish` 或配置命令路径探测成功。
- 若环境不可用：A3/A4 直接判定为不通过（不得跳过）。

执行前置步骤（两边都要执行）：
1. `mvn -DskipTests clean package`
2. 创建输出目录：`docs/benchmarks/runs`

### 8.1 FEN 套件契约与输入校验规则

FEN 套件文件（强制）：`docs/benchmarks/fen-suite-v1.csv`

格式（UTF-8, 含表头）：
`id,phase,fen,expected_move,weight`

内容约束（强制）：
- 总局面数：`60`
- phase 分布：`opening=20, middlegame=30, endgame=10`
- `expected_move` 使用 UCI 坐标（如 `b2e2`）
- `weight` 为 `1..5` 正整数
- 按 `id` 升序执行，不随机打乱

输入校验策略（强制，fail-fast）：
- `id` 重复 -> 立即失败并返回非 0。
- `weight` 非法 -> 立即失败并返回非 0。
- `fen` 解析失败 -> 立即失败并返回非 0。
- `expected_move` 非法坐标 -> 立即失败并返回非 0。

质量分数公式（强制）：
- `weighted_hit_rate = sum(weight_i * hit_i) / sum(weight_i)`
- `hit_i = 1` 当首选着等于 `expected_move`，否则 `0`

### 8.2 基线采集工作流（解决 baseline 可复现性）

由于部分基准工具/测试由本需求新增，基线采集采用 “Benchmark Harness 回灌” 工作流：

1. 从 `2a02533` 创建 `benchmark-harness` 分支。
2. 只回灌基准相关文件，不回灌任何引擎行为改动。
3. 允许改动路径仅限：
   - `src/main/java/com/xiangqi/tools/**`
   - `src/test/java/com/xiangqi/**`
   - `docs/benchmarks/**`
4. 路径约束校验命令（必须通过）：
   - `git diff --name-only 2a02533..benchmark-harness`
   - 输出中不得包含 `src/main/java/com/xiangqi/ai/**` 与 `src/main/java/com/xiangqi/web/**`
5. 在 `benchmark-harness` 运行基线命令，产出 baseline JSON。
6. 在候选分支运行同一组命令，产出 candidate JSON。

### 8.3 A1/A2/A3 验收命令与门槛

| ID | 目标 | 基线命令（在 `benchmark-harness`） | 候选命令（在候选分支） | 通过门槛 |
|---|---|---|---|---|
| A1 | 内置引擎强度/时延 | `java -cp target/classes com.xiangqi.tools.AIBenchmarkMain --gamesPerPair 12 --maxPlies 100 --openingJitter 2 --seed 20260313 --output docs/benchmarks/runs/baseline-ai.md --json docs/benchmarks/runs/baseline-ai.json` | `java -cp target/classes com.xiangqi.tools.AIBenchmarkMain --gamesPerPair 12 --maxPlies 100 --openingJitter 2 --seed 20260313 --output docs/benchmarks/runs/candidate-ai.md --json docs/benchmarks/runs/candidate-ai.json` | `delta_hard_vs_medium_score >= 0.05`；`easy_avg_time_growth <= 0.15`；`medium_avg_time_growth <= 0.15`；`hard_avg_time_growth <= 0.50` |
| A2 | 内置引擎固定局面质量 | `java -cp target/classes com.xiangqi.tools.XiangqiFenQualityBenchmarkMain --suite docs/benchmarks/fen-suite-v1.csv --engine BUILTIN --difficulties EASY,MEDIUM,HARD --movetimeMsByDifficulty EASY:450,MEDIUM:900,HARD:1500 --seed 20260313 --output docs/benchmarks/runs/baseline-fen-builtin.json` | `java -cp target/classes com.xiangqi.tools.XiangqiFenQualityBenchmarkMain --suite docs/benchmarks/fen-suite-v1.csv --engine BUILTIN --difficulties EASY,MEDIUM,HARD --movetimeMsByDifficulty EASY:450,MEDIUM:900,HARD:1500 --seed 20260313 --output docs/benchmarks/runs/candidate-fen-builtin.json` | `delta_hard_weighted_hit_rate >= 0.06`；`easy_weighted_hit_rate_drop <= 0.02`；`medium_weighted_hit_rate_drop <= 0.02` |
| A3 | Pikafish 棋力 KPI | `java -cp target/classes com.xiangqi.tools.XiangqiEngineHeadToHeadMain --redEngine PIKAFISH --blackEngine BUILTIN --difficulty HARD --games 20 --mirrored true --movetimeMs 1500 --seed 20260313 --output docs/benchmarks/runs/baseline-h2h.json` | `java -cp target/classes com.xiangqi.tools.XiangqiEngineHeadToHeadMain --redEngine PIKAFISH --blackEngine BUILTIN --difficulty HARD --games 20 --mirrored true --movetimeMs 1500 --seed 20260313 --output docs/benchmarks/runs/candidate-h2h.json` | `delta_pikafish_minus_builtin_score >= 0.08` |

### 8.4 A4 稳定性验收命令与门槛

基线命令（在 `benchmark-harness`）：
- `mvn -Dtest=ConfigurableXiangqiEngineTest,PikafishUciEngineTest,WebXiangqiServerPvcFallbackTest -Dfallback.metrics.output=docs/benchmarks/runs/baseline-fallback-metrics.json test`

候选命令（在候选分支）：
- `mvn -Dtest=ConfigurableXiangqiEngineTest,PikafishUciEngineTest,PikafishUciEngineMappingTest,WebXiangqiServerPvcFallbackTest -Dfallback.metrics.output=docs/benchmarks/runs/candidate-fallback-metrics.json test`

通过门槛：
- 两条命令返回码都为 0。
- 候选命令中的 `PikafishUciEngineMappingTest` 必须断言 EASY/MEDIUM/HARD 与重试参数下发值。
- `candidate-fallback-metrics.json` 满足：
  - `fallback_success_rate = 1.0`
  - `deadlock_count = 0`
  - `scenario_results` 至少覆盖：`engine_unavailable`、`start_failure`、`ready_timeout`、`bestmove_timeout`、`invalid_move`
  - `scenario_results[*].success_rate = 1.0`（逐场景都必须 100%）

### 8.5 A5 API 契约兼容性验收（强制）

基线命令（在 `benchmark-harness`）：
- `mvn -Dtest=WebXiangqiApiContractTest -Dapi.contract.output=docs/benchmarks/runs/baseline-api-contract.json test`

候选命令（在候选分支）：
- `mvn -Dtest=WebXiangqiApiContractTest -Dapi.contract.output=docs/benchmarks/runs/candidate-api-contract.json test`

通过门槛：
- 两条命令返回码都为 0。
- `candidate-api-contract.json` 与 `baseline-api-contract.json` 在以下端点字段集合完全一致：
  - `/api/new` 请求参数键集合
  - `/api/endgame` 请求参数键集合
  - `/api/state` 响应 JSON 顶层字段键集合
- 若仅允许字符串值变化（例如引擎文本），必须由测试显式白名单声明，不能默认忽略。

### 8.6 A1/A2/A3 输出 JSON 契约（强制）

#### 8.6.1 `*-ai.json`（A1）

必须字段（键路径 -> 类型）：
- `meta.seed` -> int
- `difficulty_stats.EASY.avg_think_ms` -> number
- `difficulty_stats.MEDIUM.avg_think_ms` -> number
- `difficulty_stats.HARD.avg_think_ms` -> number
- `pair_scores.HARD_vs_MEDIUM.score` -> number

计算公式（固定）：
- `score = (win + 0.5 * draw) / total`
- `delta_hard_vs_medium_score = candidate.pair_scores.HARD_vs_MEDIUM.score - baseline.pair_scores.HARD_vs_MEDIUM.score`
- `easy_avg_time_growth = (candidate.difficulty_stats.EASY.avg_think_ms - baseline.difficulty_stats.EASY.avg_think_ms) / baseline.difficulty_stats.EASY.avg_think_ms`
- `medium_avg_time_growth = (candidate.difficulty_stats.MEDIUM.avg_think_ms - baseline.difficulty_stats.MEDIUM.avg_think_ms) / baseline.difficulty_stats.MEDIUM.avg_think_ms`
- `hard_avg_time_growth = (candidate.difficulty_stats.HARD.avg_think_ms - baseline.difficulty_stats.HARD.avg_think_ms) / baseline.difficulty_stats.HARD.avg_think_ms`

#### 8.6.2 `*-fen-builtin.json`（A2）

必须字段（键路径 -> 类型）：
- `suite.path` -> string
- `difficulty_results.EASY.weighted_hit_rate` -> number
- `difficulty_results.MEDIUM.weighted_hit_rate` -> number
- `difficulty_results.HARD.weighted_hit_rate` -> number

计算公式（固定）：
- `delta_hard_weighted_hit_rate = candidate.difficulty_results.HARD.weighted_hit_rate - baseline.difficulty_results.HARD.weighted_hit_rate`
- `easy_weighted_hit_rate_drop = baseline.difficulty_results.EASY.weighted_hit_rate - candidate.difficulty_results.EASY.weighted_hit_rate`
- `medium_weighted_hit_rate_drop = baseline.difficulty_results.MEDIUM.weighted_hit_rate - candidate.difficulty_results.MEDIUM.weighted_hit_rate`

#### 8.6.3 `*-h2h.json`（A3）

必须字段（键路径 -> 类型）：
- `players.PIKAFISH.score` -> number
- `players.BUILTIN.score` -> number

计算公式（固定）：
- `delta_pikafish_minus_builtin_score = (candidate.players.PIKAFISH.score - candidate.players.BUILTIN.score) - (baseline.players.PIKAFISH.score - baseline.players.BUILTIN.score)`

## 9. 风险与缓解

- 风险：`HARD` 预算过高导致体感卡顿。
  - 缓解：以 A1 的时延门槛硬约束。
- 风险：Pikafish 二进制差异导致参数兼容波动。
  - 缓解：参数发送容错 + 失败即回退 + A4 稳定性门槛。
- 风险：只追求胜率导致局部算路退化。
  - 缓解：A2 固定 FEN 质量门槛强制通过。

## 10. 交付物清单

必须交付：
- 代码改动（`MinimaxAI`、`PikafishUciEngine`、相关测试与工具）。
- `docs/benchmarks/fen-suite-v1.csv`（固定套件）。
- baseline/candidate 对比结果：
  - `docs/benchmarks/runs/*-ai.json`
  - `docs/benchmarks/runs/*-fen-builtin.json`
  - `docs/benchmarks/runs/*-h2h.json`
  - `docs/benchmarks/runs/*-fallback-metrics.json`
  - `docs/benchmarks/runs/*-api-contract.json`
- 验收结论文档：`docs/benchmarks/runs/acceptance-summary.md`（逐项写明 A1~A5 是否通过）。

## 11. 实施边界结论

本 spec 为单一子系统（网页象棋引擎提升），边界清晰、接口契约明确、验收门槛可执行。

下一步：进入实现计划文档（`writing-plans`）。
