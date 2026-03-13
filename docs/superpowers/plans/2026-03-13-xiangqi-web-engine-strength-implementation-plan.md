# Xiangqi Web 引擎棋力提升 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不变更 Web API 字段契约的前提下，提升内置 Minimax 与 Pikafish 棋力，并以可复现基准通过 A1~A5 强制验收。

**Architecture:** 采用“引擎行为改进 + 基准工具固化 + 契约测试门禁”三层架构。核心引擎改动限定在 `com.xiangqi.ai`，基准/评估逻辑集中在 `com.xiangqi.tools`，验收门禁由 `src/test/java/com/xiangqi/**` 输出规范 JSON。实现过程使用 TDD 小步提交，确保任一步都可回滚与复验。

**Tech Stack:** Java 11, Maven 3.9+, JUnit 5, `com.sun.net.httpserver.HttpServer`, PowerShell, Git

---

## File Structure

### Existing files to modify
- `src/main/java/com/xiangqi/ai/MinimaxAI.java`: 调整难度预算与 fast-mode 触发阈值，优先增强 `HARD`，限制 `EASY/MEDIUM` 时延增长。
- `src/main/java/com/xiangqi/ai/PikafishUciEngine.java`: 固化 EASY/MEDIUM/HARD 参数映射，落实分阶段超时与一次重启重试。
- `src/main/java/com/xiangqi/ai/ConfigurableXiangqiEngine.java`: 固化 external->builtin->null 回退顺序，并避免 builtin 重复调用。
- `src/main/java/com/xiangqi/web/WebXiangqiServer.java`: 保持 `/api/new|/api/endgame|/api/state` 字段不变，补充 fallback 场景可测性钩子（仅测试可见）。
- `src/main/java/com/xiangqi/tools/AIBenchmarkMain.java`: 新增 `--output` 与 `--json`，输出 A1 契约字段。
- `src/main/java/com/xiangqi/ai/FenCodec.java`: 增加 `fromFen`（或新增等价解析器）供固定 FEN 基准复用。

### New production files to create
- `src/main/java/com/xiangqi/tools/XiangqiFenSuite.java`: CSV 读取、输入校验、权重命中率计算。
- `src/main/java/com/xiangqi/tools/XiangqiFenQualityBenchmarkMain.java`: A2 基准主程序与 JSON 输出。
- `src/main/java/com/xiangqi/tools/XiangqiEngineHeadToHeadMain.java`: A3 对战基准主程序与 JSON 输出。

### New test/support files to create
- `src/test/java/com/xiangqi/ai/MinimaxDifficultyBudgetTest.java`
- `src/test/java/com/xiangqi/ai/PikafishUciEngineMappingTest.java`
- `src/test/java/com/xiangqi/ai/PikafishUciEngineTest.java`
- `src/test/java/com/xiangqi/ai/ConfigurableXiangqiEngineTest.java`
- `src/test/java/com/xiangqi/web/WebXiangqiServerPvcFallbackTest.java`
- `src/test/java/com/xiangqi/web/WebXiangqiApiContractTest.java`
- `src/test/java/com/xiangqi/tools/AIBenchmarkMainJsonContractTest.java`
- `src/test/java/com/xiangqi/tools/XiangqiFenSuiteValidationTest.java`
- `src/test/java/com/xiangqi/tools/XiangqiFenQualityBenchmarkMainTest.java`
- `src/test/java/com/xiangqi/tools/XiangqiEngineHeadToHeadMainTest.java`
- `src/test/java/com/xiangqi/support/FallbackMetricsCollector.java`
- `src/test/java/com/xiangqi/support/FakeUciEngineMain.java`

### Benchmark data/artifacts
- `docs/benchmarks/fen-suite-v1.csv`: 60 个固定局面（opening=20, middlegame=30, endgame=10）。
- `docs/benchmarks/runs/.gitkeep`: 保留输出目录。

---

## Chunk 1: 引擎行为与稳定性

### Task 1: 调整 Minimax 难度预算并锁定 HARD 增强方向

**Files:**
- Modify: `src/main/java/com/xiangqi/ai/MinimaxAI.java`
- Test: `src/test/java/com/xiangqi/ai/MinimaxDifficultyBudgetTest.java`

- [ ] **Step 1: 写失败测试，先锁定预算行为目标**

```java
@Test
void hardBudgetShouldStayHigherThanMediumInMidgame() {
    MinimaxAI ai = new MinimaxAI();
    ai.setDifficulty(MinimaxAI.Difficulty.HARD);
    MinimaxAI.SearchBudget budget = MinimaxAiTestAccess.budgetFor(ai, standardMidgameBoard());
    assertTrue(budget.maxDepth() >= 9);
    assertTrue(budget.timeLimitMs() >= 1800);
}
```

- [ ] **Step 2: 运行测试确认失败（红灯）**

Run: `mvn -Dtest=MinimaxDifficultyBudgetTest test`
Expected: FAIL（当前预算/阈值不满足新断言）

- [ ] **Step 3: 最小实现：调整难度初始预算与 `tuneBudget(...)` 规则**

```java
public enum Difficulty {
    EASY("简单", 2, 420, 0.30),
    MEDIUM("中等", 5, 900, 0.03),
    HARD("困难", 10, 6200, 0.0);
}

searchFastMode = branchingNow >= 36
    || pressureNow >= 1.02
    || (difficulty != Difficulty.HARD && branchingNow >= 28);
```

- [ ] **Step 4: 运行测试确认通过（绿灯）**

Run: `mvn -Dtest=MinimaxDifficultyBudgetTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/ai/MinimaxAI.java src/test/java/com/xiangqi/ai/MinimaxDifficultyBudgetTest.java
git commit -m "feat(ai): retune minimax budget for stronger hard difficulty"
```

### Task 2: 实现 Pikafish 精确参数映射 + 分阶段超时 + 一次重试

**Files:**
- Modify: `src/main/java/com/xiangqi/ai/PikafishUciEngine.java`
- Test: `src/test/java/com/xiangqi/ai/PikafishUciEngineMappingTest.java`
- Test: `src/test/java/com/xiangqi/ai/PikafishUciEngineTest.java`
- Create: `src/test/java/com/xiangqi/support/FakeUciEngineMain.java`

- [ ] **Step 1: 写失败测试（映射值 + 重试降级公式）**

```java
@Test
void shouldMapHardToExpectedProfile() {
    Profile p = PikafishUciEngine.profileOf(MinimaxAI.Difficulty.HARD);
    assertEquals(2600, p.movetimeMs());
    assertEquals(15, p.depth());
    assertEquals(20, p.skill());
    assertEquals(2, p.threads());
    assertEquals(128, p.hashMb());
}
```

- [ ] **Step 2: 运行映射测试确认失败**

Run: `mvn -Dtest=PikafishUciEngineMappingTest test`
Expected: FAIL（当前映射值与 spec 不一致）

- [ ] **Step 3: 最小实现：引入 `Profile` 并固化超时/重试规则**

```java
private static final long UCI_INIT_TIMEOUT_MS = 8000L;
private static final long READY_TIMEOUT_MS = 3000L;

private long bestmoveTimeout(Profile p) {
    return Math.min(12000L, p.movetimeMs() * 2L + 1200L);
}

private Profile retryProfile(Profile p) {
    return new Profile(
        Math.max(300, (int)Math.floor(p.movetimeMs() * 0.6)),
        Math.max(6, p.depth() - 2),
        p.skill(), p.threads(), p.hashMb()
    );
}
```

- [ ] **Step 4: 运行引擎场景测试（start/ready/bestmove/invalid move）**

Run: `mvn -Dtest=PikafishUciEngineTest test`
Expected: PASS（首次失败后仅重启一次，二次失败返回 `null`）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/ai/PikafishUciEngine.java src/test/java/com/xiangqi/ai/PikafishUciEngineMappingTest.java src/test/java/com/xiangqi/ai/PikafishUciEngineTest.java src/test/java/com/xiangqi/support/FakeUciEngineMain.java
git commit -m "feat(ai): enforce pikafish profile mapping with staged timeout retry"
```

### Task 3: 固化 ConfigurableXiangqiEngine 回退语义并去重 builtin

**Files:**
- Modify: `src/main/java/com/xiangqi/ai/ConfigurableXiangqiEngine.java`
- Test: `src/test/java/com/xiangqi/ai/ConfigurableXiangqiEngineTest.java`

- [ ] **Step 1: 写失败测试，覆盖 external->builtin->null 与 builtin 去重**

```java
@Test
void shouldNotInvokeBuiltinTwiceWhenSelectedAlreadyBuiltin() {
    FakeEngine builtin = new FakeEngine(null);
    ConfigurableXiangqiEngine engine = TestableConfigurableEngine.withSelectedBuiltin(builtin);
    Move move = engine.findBestMove(new Board(), PieceColor.RED, MinimaxAI.Difficulty.MEDIUM);
    assertNull(move);
    assertEquals(1, builtin.calls());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=ConfigurableXiangqiEngineTest test`
Expected: FAIL（当前实现可能重复调用 builtin）

- [ ] **Step 3: 最小实现：按固定回退链执行**

```java
if (selected == builtin) {
    return safeBuiltin(board, aiColor, difficulty);
}
Move external = safeFind(selected, board, aiColor, difficulty);
if (external != null) return external;
selectedText = builtin.getEngineText() + "（外部引擎异常已回退）";
Move builtinMove = safeBuiltin(board, aiColor, difficulty);
return builtinMove; // may be null
```

- [ ] **Step 4: 重新运行测试确认通过**

Run: `mvn -Dtest=ConfigurableXiangqiEngineTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/ai/ConfigurableXiangqiEngine.java src/test/java/com/xiangqi/ai/ConfigurableXiangqiEngineTest.java
git commit -m "fix(ai): make xiangqi engine fallback order deterministic"
```

### Task 4: 让 Web PVC 回退链可验证并输出 fallback 指标 JSON

**Files:**
- Modify: `src/main/java/com/xiangqi/web/WebXiangqiServer.java`
- Create: `src/test/java/com/xiangqi/support/FallbackMetricsCollector.java`
- Test: `src/test/java/com/xiangqi/web/WebXiangqiServerPvcFallbackTest.java`

- [ ] **Step 1: 写失败测试，覆盖 5 个必选场景并断言逐场景 100% 成功率**

```java
@Test
void bestmoveTimeoutShouldFallbackToBuiltinAndMoveForward() {
    ScenarioResult r = runScenario("bestmove_timeout");
    assertEquals(1.0, r.successRate());
    assertEquals(0, r.deadlockCount());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=WebXiangqiServerPvcFallbackTest -Dfallback.metrics.output=docs/benchmarks/runs/candidate-fallback-metrics.json test`
Expected: FAIL（文件未产出或场景统计不完整）

- [ ] **Step 3: 最小实现：补充 test-only metrics 记录点并在 JVM 结束写 JSON**

```java
FallbackMetricsCollector.record("bestmove_timeout", success,
    externalTimeoutCount, externalRestartCount,
    externalFallbackToBuiltinCount, fallbackToFirstLegalCount);
```

- [ ] **Step 4: 按 A4 命令重跑并确认 JSON 契约字段齐全**

Run: `mvn -Dtest=ConfigurableXiangqiEngineTest,PikafishUciEngineTest,PikafishUciEngineMappingTest,WebXiangqiServerPvcFallbackTest -Dfallback.metrics.output=docs/benchmarks/runs/candidate-fallback-metrics.json test`
Expected: PASS，且 JSON 包含 `scenario_count`、`scenario_results[*].success_rate`

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/web/WebXiangqiServer.java src/test/java/com/xiangqi/support/FallbackMetricsCollector.java src/test/java/com/xiangqi/web/WebXiangqiServerPvcFallbackTest.java
git commit -m "test(web): add pvc fallback metrics scenarios and json output"
```

---

## Chunk 2: 基准工具与数据契约

### Task 5: 增强 AIBenchmarkMain，输出 A1 结构化 JSON

**Files:**
- Modify: `src/main/java/com/xiangqi/tools/AIBenchmarkMain.java`
- Test: `src/test/java/com/xiangqi/tools/AIBenchmarkMainJsonContractTest.java`

- [ ] **Step 1: 写失败测试，断言 `--output` 与 `--json` 参数生效**

```java
@Test
void shouldWriteAiJsonWithRequiredFields() throws Exception {
    AIBenchmarkMain.main(new String[]{
        "--gamesPerPair", "1", "--maxPlies", "20", "--seed", "20260313",
        "--output", outMd.toString(), "--json", outJson.toString()
    });
    assertJsonHas(outJson, "meta.seed", "difficulty_stats.HARD.avg_think_ms", "pair_scores.HARD_vs_MEDIUM.score");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=AIBenchmarkMainJsonContractTest test`
Expected: FAIL（当前无 `--json`）

- [ ] **Step 3: 最小实现：新增 CLI 参数与 JSON 渲染**

```java
Path mdOut = pathArg(args, "--output", defaultMdOut());
Path jsonOut = pathArg(args, "--json", defaultJsonOut());
writeUtf8(mdOut, renderReport(...));
writeUtf8(jsonOut, renderJson(...));
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=AIBenchmarkMainJsonContractTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/tools/AIBenchmarkMain.java src/test/java/com/xiangqi/tools/AIBenchmarkMainJsonContractTest.java
git commit -m "feat(tools): add json output contract for ai benchmark"
```

### Task 6: 新增 FEN 套件解析与 A2 质量基准工具

**Files:**
- Create: `src/main/java/com/xiangqi/tools/XiangqiFenSuite.java`
- Create: `src/main/java/com/xiangqi/tools/XiangqiFenQualityBenchmarkMain.java`
- Modify: `src/main/java/com/xiangqi/ai/FenCodec.java`
- Create: `docs/benchmarks/fen-suite-v1.csv`
- Test: `src/test/java/com/xiangqi/tools/XiangqiFenSuiteValidationTest.java`
- Test: `src/test/java/com/xiangqi/tools/XiangqiFenQualityBenchmarkMainTest.java`

- [ ] **Step 1: 写失败测试，先锁定 CSV fail-fast 规则**

```java
@Test
void duplicateIdShouldFailFast() {
    Exception ex = assertThrows(IllegalArgumentException.class,
        () -> XiangqiFenSuite.load(csvWithDuplicateId()));
    assertTrue(ex.getMessage().contains("duplicate id"));
}
```

- [ ] **Step 2: 运行校验测试确认失败**

Run: `mvn -Dtest=XiangqiFenSuiteValidationTest test`
Expected: FAIL

- [ ] **Step 3: 最小实现：CSV 校验 + `weighted_hit_rate` 计算 + JSON 输出**

```java
double weightedHitRate = weightedHitSum / weightSum;
json.put("difficulty_results", Map.of(
    "EASY", Map.of("weighted_hit_rate", easyRate),
    "MEDIUM", Map.of("weighted_hit_rate", mediumRate),
    "HARD", Map.of("weighted_hit_rate", hardRate)
));
```

- [ ] **Step 4: 运行工具测试确认通过，并写入 `fen-suite-v1.csv` 60 局面**

Run: `mvn -Dtest=XiangqiFenSuiteValidationTest,XiangqiFenQualityBenchmarkMainTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/tools/XiangqiFenSuite.java src/main/java/com/xiangqi/tools/XiangqiFenQualityBenchmarkMain.java src/main/java/com/xiangqi/ai/FenCodec.java docs/benchmarks/fen-suite-v1.csv src/test/java/com/xiangqi/tools/XiangqiFenSuiteValidationTest.java src/test/java/com/xiangqi/tools/XiangqiFenQualityBenchmarkMainTest.java
git commit -m "feat(bench): add fen suite validation and builtin quality benchmark"
```

### Task 7: 新增 A3 引擎对战基准工具（PIKAFISH vs BUILTIN）

**Files:**
- Create: `src/main/java/com/xiangqi/tools/XiangqiEngineHeadToHeadMain.java`
- Test: `src/test/java/com/xiangqi/tools/XiangqiEngineHeadToHeadMainTest.java`

- [ ] **Step 1: 写失败测试，锁定 `players.PIKAFISH.score` JSON 契约**

```java
@Test
void shouldEmitPlayersScoreContract() throws Exception {
    XiangqiEngineHeadToHeadMain.main(new String[]{
        "--redEngine", "BUILTIN", "--blackEngine", "BUILTIN",
        "--games", "2", "--seed", "20260313", "--output", out.toString()
    });
    assertJsonHas(out, "players.PIKAFISH.score", "players.BUILTIN.score");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=XiangqiEngineHeadToHeadMainTest test`
Expected: FAIL（类尚未实现）

- [ ] **Step 3: 最小实现：双向镜像对局并输出 players 分数字段**

```java
double score = (wins + 0.5 * draws) / games;
players.put("PIKAFISH", Map.of("score", pikafishScore));
players.put("BUILTIN", Map.of("score", builtinScore));
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=XiangqiEngineHeadToHeadMainTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/xiangqi/tools/XiangqiEngineHeadToHeadMain.java src/test/java/com/xiangqi/tools/XiangqiEngineHeadToHeadMainTest.java
git commit -m "feat(bench): add xiangqi head-to-head benchmark json"
```

---

## Chunk 3: 契约门禁与验收流水线

### Task 8: 新增 A5 API 契约快照测试

**Files:**
- Test: `src/test/java/com/xiangqi/web/WebXiangqiApiContractTest.java`

- [ ] **Step 1: 写失败测试，先锁定 `/api/state` 顶层字段集合**

```java
@Test
void shouldCaptureApiContractSnapshot() throws Exception {
    ApiContractSnapshot snapshot = runAgainstLiveServer();
    assertTrue(snapshot.stateKeys().contains("xiangqiAiEngineText"));
    assertTrue(snapshot.newRequestKeys().contains("difficulty"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=WebXiangqiApiContractTest -Dapi.contract.output=docs/benchmarks/runs/candidate-api-contract.json test`
Expected: FAIL（快照输出未实现）

- [ ] **Step 3: 最小实现：启动服务器抓取键集合并输出 JSON**

```java
Files.writeString(out,
    "{\"api\":{\"/api/new\":...},\"state_keys\":[...],\"string_whitelist\":[\"xiangqiAiEngineText\"]}",
    StandardCharsets.UTF_8);
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=WebXiangqiApiContractTest -Dapi.contract.output=docs/benchmarks/runs/candidate-api-contract.json test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/xiangqi/web/WebXiangqiApiContractTest.java
git commit -m "test(web): add api contract snapshot output gate"
```

### Task 9: 补齐 A4 命令要求中的映射测试与 fallback 指标门禁

**Files:**
- Test: `src/test/java/com/xiangqi/ai/PikafishUciEngineMappingTest.java`
- Test: `src/test/java/com/xiangqi/ai/PikafishUciEngineTest.java`
- Test: `src/test/java/com/xiangqi/web/WebXiangqiServerPvcFallbackTest.java`
- Create/Modify: `src/test/java/com/xiangqi/support/FallbackMetricsCollector.java`

- [ ] **Step 1: 写失败测试，显式断言 EASY/MEDIUM/HARD 与重试参数下发值**

```java
@Test
void retryProfileShouldApplyFloorRule() {
    Profile retry = PikafishUciEngine.retryProfileOf(profile(1100, 10));
    assertEquals(660, retry.movetimeMs());
    assertEquals(8, retry.depth());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=PikafishUciEngineMappingTest test`
Expected: FAIL

- [ ] **Step 3: 完成断言与指标聚合写盘逻辑**

```java
assertEquals(1.0, metrics.fallbackSuccessRate());
assertEquals(0, metrics.deadlockCount());
assertScenarioSuccess("engine_unavailable");
assertScenarioSuccess("start_failure");
assertScenarioSuccess("ready_timeout");
assertScenarioSuccess("bestmove_timeout");
assertScenarioSuccess("invalid_move");
```

- [ ] **Step 4: 按 A4 全量命令跑通**

Run: `mvn -Dtest=ConfigurableXiangqiEngineTest,PikafishUciEngineTest,PikafishUciEngineMappingTest,WebXiangqiServerPvcFallbackTest -Dfallback.metrics.output=docs/benchmarks/runs/candidate-fallback-metrics.json test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/xiangqi/ai/PikafishUciEngineMappingTest.java src/test/java/com/xiangqi/ai/PikafishUciEngineTest.java src/test/java/com/xiangqi/web/WebXiangqiServerPvcFallbackTest.java src/test/java/com/xiangqi/support/FallbackMetricsCollector.java
git commit -m "test(ai): gate pikafish mapping and fallback metrics acceptance"
```

### Task 10: 产出 A1~A5 执行脚本与 `acceptance-summary.md`

**Files:**
- Create: `docs/benchmarks/runs/acceptance-summary.md`
- Create: `tools/run-xiangqi-acceptance.ps1`
- Modify: `README.zh-CN.md` (追加“棋力验收”章节)

- [ ] **Step 1: 先写脚本单测式自检（参数、路径、缺文件时 exit code）**

```powershell
if (-not (Test-Path $BaselineAiJson)) {
  Write-Error "missing baseline-ai.json"
  exit 2
}
```

- [ ] **Step 2: 运行脚本自检确认失败场景可控**

Run: `pwsh ./tools/run-xiangqi-acceptance.ps1 -Mode check-only`
Expected: 当输入缺失时返回非 0

- [ ] **Step 3: 实现公式计算与 A1~A5 PASS/FAIL 汇总写入 `acceptance-summary.md`**

```powershell
$deltaHardVsMedium = $candidate.pair_scores.HARD_vs_MEDIUM.score - $baseline.pair_scores.HARD_vs_MEDIUM.score
$passA1 = ($deltaHardVsMedium -ge 0.05) -and ...
```

- [ ] **Step 4: 运行脚本并生成汇总文档**

Run: `pwsh ./tools/run-xiangqi-acceptance.ps1 -Mode candidate`
Expected: 生成 `docs/benchmarks/runs/acceptance-summary.md`

- [ ] **Step 5: 提交**

```bash
git add tools/run-xiangqi-acceptance.ps1 docs/benchmarks/runs/acceptance-summary.md README.zh-CN.md
git commit -m "docs(bench): add acceptance runner and summary template"
```

### Task 11: 执行基线回灌工作流并跑完 A1~A5 双分支对比

**Files:**
- Modify (harness only): `src/main/java/com/xiangqi/tools/**`
- Modify (harness only): `src/test/java/com/xiangqi/**`
- Modify (harness only): `docs/benchmarks/**`

- [ ] **Step 1: 创建并校验 `benchmark-harness` 分支路径约束**

Run:
```bash
git checkout -b benchmark-harness 2a02533
# 仅cherry-pick基准工具/测试提交
```
Expected: `git diff --name-only 2a02533..benchmark-harness` 不含 `src/main/java/com/xiangqi/ai/**` 与 `src/main/java/com/xiangqi/web/**`

- [ ] **Step 2: 在 `benchmark-harness` 运行 baseline A1~A5 命令**

Run: 使用 spec 第 8.3~8.5 的 baseline 命令全集
Expected: 产出 `baseline-*.json`

- [ ] **Step 3: 回到候选分支运行 candidate A1~A5 命令**

Run: 使用 spec 第 8.3~8.5 的 candidate 命令全集
Expected: 产出 `candidate-*.json`

- [ ] **Step 4: 执行验收脚本并核对门槛**

Run: `pwsh ./tools/run-xiangqi-acceptance.ps1 -Mode compare`
Expected: `acceptance-summary.md` 清晰列出 A1/A2/A3/A4/A5 是否通过

- [ ] **Step 5: 提交基准结果工件（如仓库策略允许）**

```bash
git add docs/benchmarks/runs/*.json docs/benchmarks/runs/acceptance-summary.md
git commit -m "chore(bench): record baseline vs candidate acceptance artifacts"
```

---

## Cross-Chunk Verification Checklist

- [ ] `mvn -DskipTests clean package`
- [ ] `mvn -Dtest=MinimaxDifficultyBudgetTest,ConfigurableXiangqiEngineTest test`
- [ ] `mvn -Dtest=PikafishUciEngineMappingTest,PikafishUciEngineTest test`
- [ ] `mvn -Dtest=AIBenchmarkMainJsonContractTest,XiangqiFenSuiteValidationTest,XiangqiFenQualityBenchmarkMainTest,XiangqiEngineHeadToHeadMainTest test`
- [ ] `mvn -Dtest=WebXiangqiApiContractTest -Dapi.contract.output=docs/benchmarks/runs/candidate-api-contract.json test`
- [ ] `mvn -Dtest=ConfigurableXiangqiEngineTest,PikafishUciEngineTest,PikafishUciEngineMappingTest,WebXiangqiServerPvcFallbackTest -Dfallback.metrics.output=docs/benchmarks/runs/candidate-fallback-metrics.json test`

## Skill References

- 执行本计划时：`@superpowers:subagent-driven-development`
- 每个任务完成前验真：`@superpowers:verification-before-completion`
- 若某测试异常失败：`@superpowers:systematic-debugging`

## Plan Review Loop Execution Notes

- 每个 Chunk 完成后，用 `writing-plans/plan-document-reviewer-prompt.md` 派发一次 plan reviewer 子代理复审。
- 复审状态为 `Issues Found` 时，只修对应 Chunk，再复审，直到 `Approved`。
- 若单 Chunk 复审超过 5 轮仍未通过，升级给人工裁决。
