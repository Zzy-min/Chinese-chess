# agy brief：对局页 UI 收尾（补 CSS + RESULT）

仓：`/workspace/Chinese-chess`  
对照：`/workspace/voonie-align/xiangqi-ui-look-2026-09-17/dual-board-*.png`、`.../report-dual-ui.md`  
现状：`app.js` 已有部分改动（时钟文案、等待中语义、棋谱 formatMoveNotation、认输 ghost）。**禁止 git push**。

## 必须完成
1. **三栏栏宽 CSS**（`app.css`，必要时 `mobile.css`）：桌面对局页中栏棋盘优先，右栏棋谱可缩；近景/常见桌面宽不要切掉棋盘右侧棋子。调整 grid/flex/minmax，勿大幅改交互逻辑。
2. **减重复顶栏标签**：若 `app.js` 顶栏仍与左栏堆同一信息（中国象棋/你执/对手执/对局中/实时等），继续精简到最少必要（建议顶栏只留游戏名+轮到谁或单一状态）。
3. **写 RESULT**：`docs/fix-briefs/2026-09-17-agy-game-ui-RESULT.md`（改动清单、5 点前后对照、自测、未做：结算/Toast/WS、未 push）。
4. **测试**：不要跑全仓 `mvn test`。可跳过测试，或只做静态检查/极小相关验证。上一轮因 `mvn test` 拖到 45m timeout——本次严禁再开全量 mvn。

成功：CSS 落盘 + RESULT 写完 + 顶栏不重复堆砌。只改 `src/main/resources/online/**` 与 RESULT/brief。
