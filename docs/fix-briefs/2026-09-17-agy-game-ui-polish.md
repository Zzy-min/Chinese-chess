# agy brief：轻棋局对局页 UI 打磨（双人实战截图对照）

日期：2026-09-17  
仓：`/workspace/Chinese-chess`  
约束：**只改前端**（`src/main/resources/online/**`）；**禁止 git push / commit 非必要**；本轮**不做**结算弹窗/非法 Toast/WS 心跳。

## 对照材料（必须阅读）
- `/workspace/voonie-align/xiangqi-qa-2026-09-17/report-dual-ui.md`
- `/workspace/voonie-align/xiangqi-ui-look-2026-09-17/dual-board-full.png`
- `/workspace/voonie-align/xiangqi-ui-look-2026-09-17/dual-board-ui-close.png`

## 要改的 5 点
1. **减重复标签**：顶栏与左侧双方卡片不要堆同一信息（如「中国象棋 / 你执X / 对手执Y / 对局中 / 实时 / 轮到…」与左栏重复）。顶栏保留最少必要状态；细节放左栏或一处权威来源。
2. **去掉误导「等待中」**：房间/对局已 `PLAYING` 时，黑/红方卡片不得再显示「等待中」。仅在真正 WAITING/未轮到且语义正确时使用。
3. **时钟一眼能读**：局时 / 步时 / 当前正在扣谁的剩余时间，标签与排版区分清楚；避免「14:02 vs 00:50」旁再并列「局时15:00 / 步时01:30」却读不懂扣的是哪一种。
4. **棋谱用字与棋盘一致**：红方着法不要记成「卒」、黑方不要记成「兵」这种与棋盘/惯用色称冲突的混称；与盘面棋子汉字体系统一。
5. **三栏栏宽 + 按钮权重**：桌面近景不要切掉右侧棋子；中栏棋盘优先；右栏棋谱可缩。认输不要视觉权重大过悔棋/求和/离开（认输可降为 ghost/危险次级，操作区对齐）。

## 主要文件（按需）
- `src/main/resources/online/app.js`
- `src/main/resources/online/app.css`
- `src/main/resources/online/mobile.css`（若影响桌面栏宽可轻微联动，但本轮以桌面对局页为主）

## 交付
写结果到：`docs/fix-briefs/2026-09-17-agy-game-ui-RESULT.md`，含：
- 改动文件清单
- 每条需求对应改法（前后对照文字）
- 自测方式
- 明确未做项（结算/Toast/WS）

成功标准：5 点均落地；相关对局页逻辑不回退；诚实不虚报。
