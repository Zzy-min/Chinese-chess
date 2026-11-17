# 轻棋局（XiangqiArena）按钮质感优化与对局特殊规则触发提醒设计规范

## 1. 背景与目标

本次改进聚焦两项体验升级：
1. **全站按钮质感优化**：当前基础按钮（`.btn`、`.ghost`、`.danger`、`.btn-red`、`.btn-green`、`.btn-continue`、`.btn-block`、`.woodActions button` 等）偏扁平，缺少微立体高光、悬停浮起与按压回弹反馈。通过统一的新水墨国风温润质感体系（微渐变、内高光描边、层级阴影、按压态位移与焦点环），提升全站可点击控件的实体触感与视觉精致度。
2. **对局特殊规则触发提醒**：
   - **后端规则诊断增强**：
     - 象棋 `Board.getIllegalMoveReason(Move)` 返回精准的中文规则触发原因（涵盖：`蹩马腿`、`塞象眼`、`象不过河`、`将帅照面`、`必须应将`、`不能送将`、`炮隔山打牛`、`炮行不越子`、`车行不越子`、`兵卒不退`、`未过河兵卒`、`九宫限制`、`马走日字`、`相走田字`、`仕走斜线`、`不能吃己方棋子` 等），并由 `XiangqiMatch.previewMove` 透传，替代原先笼统的 `"illegal move"`。
     - 象棋将军/杀招战术识别：`OnlineMatchEngine` 新增 `default String checkTactic()`，`XiangqiMatch` 在处于将军状态时调用 `TacticDetector.detect(board, lastMove)` 输出具体杀招/将军类型（如 `将军`、`绝杀`、`白脸将`、`双炮将`、`闪击将`、`抽将`、`卧槽马`、`贴身车`），由 `PracticeGameHub` 和 `OnlineRoomHub` 随对局快照 `checkTactic` 字段返回。
   - **前端规则触发提醒与棋盘联动**：
     - 象棋对局中：
       - 当 `inCheckSide` 存在时，不仅棋盘将/帅棋子脉冲、弹出 Toast，还在棋盘区展示醒目的**特殊规则/将军提醒横幅（`ruleAlertBanner`）**，显示具体战术徽章（如 `双炮将` / `卧槽马` / `绝杀` / `将军`）及应对提示。
       - 当玩家触发走子规则限制（如 `蹩马腿`、`塞象眼`、`象不过河`、`将帅照面`、`必须应将`、`不能送将`、`炮隔山打牛`、`未过河兵卒` 等）时，展示对应规则标签与清晰解释，并在棋盘上短暂高亮提示受阻格/阻挡子（如马腿子、象眼子）。
     - 五子棋对局中：
       - 当黑方落子触发 `三三禁手`、`四四禁手`、`长连禁手` 时，规则提醒横幅展示对应禁手徽章（如 `三三禁手`）与规则说明（“黑棋禁止一子同时形成两个活三”等），并在棋盘目标交叉点短暂闪烁红色禁手提示环（`is-rule-blocked`）。
       - 当形成五子连珠胜局时，展示 `五子连珠` 触发提示。

## 2. 约束与兼容性

- **向后兼容**：`Board.isValidMove(Move)` 保持原有布尔语义完全一致，内部复用 `getIllegalMoveReason(Move).isEmpty()`。`OnlineMatchEngine.checkTactic()` 提供默认空字符串实现，不影响现有 `GomokuMatch` / `GoMatch` 等实现。
- **不破坏现有按钮降权设计**：保留 `.woodActions button.ghost.danger` / `.actionBtn--resign` 的低视觉权重幽灵样式，仅在通用质感与按压反馈上保持一致。
- **契约测试同步**：修改 `app.css`、`mobile.css`、`app.js` 后，同步更新 `src/main/resources/online/index.html` 中的 `?v=` 资源版本令牌为同一新版本（如 `20261117r1`），确保 `OnlineSiteResourceContractTest` 通过。
- **隔离验证**：所有构建、单测（`mvn test`）和 Playwright 真实浏览器端到端验证均在本地隔离端口与临时数据目录完成，不触碰生产服务器。
