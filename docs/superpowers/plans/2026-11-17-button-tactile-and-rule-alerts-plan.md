# 轻棋局（XiangqiArena）按钮质感优化与对局特殊规则触发提醒实现计划

## 阶段一：Java 后端规则诊断与战术识别增强
1. 修改 `src/main/java/com/xiangqi/model/Board.java`：
   - 新增 `public String getIllegalMoveReason(Move move)` 方法，按“基本合法性 -> 棋子自身走法与特殊限制（蹩马腿、塞象眼、象不过河、九宫限制、炮隔山打牛/不越子、车不越子、兵卒不退/未过河） -> 棋盘全局限制（将帅照面、必须应将/不能送将）”顺序返回精准中文原因；合法时返回 `""`。
   - 让 `isValidMove(Move move)` 委托给 `getIllegalMoveReason(move).isEmpty()`，确保布尔结果与既有规则完全一致。
2. 修改 `src/main/java/com/xiangqi/online/game/OnlineMatchEngine.java` 与 `XiangqiMatch.java`：
   - `OnlineMatchEngine` 新增 `default String checkTactic() { return ""; }`。
   - `XiangqiMatch.previewMove` 使用 `board.getIllegalMoveReason(move)` 替代 `"illegal move"`。
   - `XiangqiMatch.checkTactic()` 在 `board.isInCheck(currentSide)` 时调用 `TacticDetector.detect(board, lastMove)` 返回具体将军战术名称。
3. 修改 `PracticeGameHub.java` 与 `OnlineRoomHub.java`：
   - 在对局快照 `snapshot` 中写入 `"checkTactic"` 字段。
4. 编写/扩展单元测试（`BoardTest` / `XiangqiMatchTest` / `PracticeGameHubTest`）：
   - 验证蹩马腿、塞象眼、象不过河、将帅照面、送将、三三禁手、将军战术名称透传等用例。

## 阶段二：前端按钮质感升级与规则提醒 UI
1. 修改 `src/main/resources/online/app.css` 与 `mobile.css`：
   - 升级 `.btn`、`.ghost`、`.danger`、`.btn-red`、`.btn-green`、`.btn-continue`、`.btn-block`、`.btn-back-custom`、`.btn-star-custom`、`.woodActions button` 等按钮质感：微渐变背景、顶部内高光、温和阴影、hover 微升、active 按压回弹、focus-visible 焦点环、`touch-action: manipulation`。
   - 新增 `.ruleAlertBanner`（含 `is-check`、`is-forbidden`、`is-rule`、`is-tactic` 变体）、`.ruleAlertBadge`、`.xiangqiCell.is-rule-blocked`、`.xiangqiCell.is-rule-blocker`、`.gomokuCell.is-rule-blocked` 样式与动画。
2. 修改 `src/main/resources/online/app.js`：
   - 扩展 `state.ruleAlert` 状态与 `parseRuleAlertFromError(game, payload, rawError)` 解析器，识别象棋特殊规则（蹩马腿、塞象眼、象不过河、将帅照面、必须应将、不能送将、炮隔山打牛、未过河兵卒等）与五子棋禁手（三三禁手、四四禁手、长连禁手），并在可能时定位阻挡格（如马腿/象眼）。
   - 新增 `renderRuleAlertBanner(game)` 并接入 `renderOnlineGamePage` 与 `renderPracticeGamePage` 棋盘上方区域；在将军/杀招/禁手/特殊规则触发时同步刷新横幅、Toast 与棋盘格子高亮。
   - 保留 `refreshGameInteractionUi` 局部刷新能力，确保规则提醒不引起整页闪烁。
3. 更新 `src/main/resources/online/index.html` 资源版本令牌为 `?v=20261117r1`。

## 阶段三：验证与收尾
1. 运行 `mvn test` 确保全量 Java 单元测试与契约测试通过。
2. 启动本地隔离 `PublicSiteServer`（随机/隔离端口 + 临时 H2/目录），使用 Playwright 真实浏览器进行端到端验证：
   - 检查按钮样式计算属性与交互态；
   - 象棋实战触发“蹩马腿”与“将军”提醒；
   - 五子棋实战触发“三三禁手”提醒；
   - 检查控制台无 JS 报错、无 4xx/5xx 意外错误。
