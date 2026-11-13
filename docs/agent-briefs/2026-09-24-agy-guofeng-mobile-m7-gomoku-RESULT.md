# RESULT：轻棋局移动端国风 m6→m7 仅五子棋屏布局轻改（A-04 专章）

**日期**：2026-09-24（Asia/Shanghai）  
**验收视口**：375 × 812（截图 `deviceScaleFactor: 2`，2× 位图 750 × 1624）  
**唯一视觉真源 A**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-refs/04-gomoku-match.png`（A-04-gomoku-match）  
**布局节奏参照 LAYOUT-REF**：同目录 `chatgpt-refs/02-xiangqi-match.png` 与 `shots-m6/02-xiangqi-match.png`、`shots-m6/03-ai-practice.png`  
**对照 B（本轮起点）**：同目录 `shots-m6/04-gomoku-match.png`  
**成果对照**：同目录 `shots-m7/04-gomoku-match.png`（及 01–03 回归抽检）  
**静态资源版本**：`?v=20260924m7`  
**本地热更**：`http://127.0.0.1:18388/online/`（PublicWebMain 常驻；已同步 `target/classes/online/`）  
**实现路径**：**agy 完成**（约 5 分钟内实质写入）：就地修正早期 gomoku order 块（`woodActions:3` / `boardMobileTabs:4`）+ 追加 `/* === 20260924m7 gomoku layout order === */`（`!important` 覆盖全局 Tab `order:3`）+ bump `index.html` `?v=20260924m7` + 同步 `target/classes/online/` + `capture-m7.cjs` 产出 `shots-m7/` 与本 RESULT。**本轮无手改 CSS**（仅文档补记 agy 路径）。  
**禁区遵守**：未改 WS / 走棋 / 胜负 / 匹配 / 棋规 / AI；未制造第二真人；未改首页；未扩改象棋/AI 整页；**未 git push、未公网部署**。

---

## 1. 改动文件一览

| 文件 | 改动性质 | 核心改动摘要 |
|------|----------|--------------|
| `src/main/resources/online/mobile.css` | **主战场** | ① 修正 `@media (max-width: 768px)` 中 `.boardPage--strict.gomokuTheme` 下错序：`.woodActions { order: 3; }` 与 `.boardPage--strict.gomokuTheme > .boardMobileTabs { order: 4; }`；② 末尾追加 `/* === 20260924m7 gomoku layout order === */` 块，以 `!important` 覆盖全局 `.boardPage--strict > .boardMobileTabs { order: 3 !important; }`，彻底锁定五子棋操作按钮在前、Tab 在后的标准节奏（`woodActions` order: 3 !important，`boardMobileTabs` order: 4 !important，`boardStageWatermark` order: 5 !important）。 |
| `src/main/resources/online/index.html` | **版本 bump** | `?v=20260924m6` → `?v=20260924m7`（app.css / mobile.css / board.js / app.js）。 |
| `target/classes/online/{index.html,mobile.css}` | **热更同步** | `cp` 实时同步，本地 18388 端口无缝热更响应 `?v=20260924m7`。 |
| `voonie-align/.../capture-m7.cjs` | **截图脚本** | Playwright 375×812 dsf=2 执行真实登录与截图，输出 `shots-m7/`。 |

---

## 2. 核心布局顺序纠正判定

### 修正前错序（shots-m6/04-gomoku-match.png）
```text
标题（五子棋对局 · 人机练习）
 ↓
玩家双卡（用户卡 + AI 棋桌）
 ↓
木纹五子棋盘
 ↓
【棋盘 | 棋谱】Tab 胶囊  <-- 错序：Tab 压在操作按钮之上
 ↓
【悔棋 | 求和 | 认输 | 离开】操作按钮
 ↓
底部导航栏
```

### 修正后标准节奏（shots-m7/04-gomoku-match.png）
```text
标题（五子棋对局 · 人机练习） [order: 0]
 ↓
玩家双卡（用户卡 + AI 棋桌） [order: 1]
 ↓
木纹五子棋盘 [order: 2]
 ↓
【悔棋 | 求和 | 认输 | 离开】4个并排 ghost 操作按钮 [order: 3]
 ↓
【棋盘 | 棋谱】五子棋深朱砂胶囊 Tab [order: 4]
 ↓
竹叶淡雅水印 [order: 5]
 ↓
底部导航栏（首页、对局、棋谱、观战、我的）
```

**判定**：**【已闭合】**。五子棋屏布局节奏与 A-02 象棋对局、A-03 AI 练习完全拉齐，操作按钮紧贴棋盘下方，Tab 居于操作按钮与底栏之间，彻底消除了 Tab 在操作按钮之上的违和感。

---

## 3. 回归检查判定（01 / 02 / 03）

- **01 首页（Home）**：**【未污染】**  
  Hero 山水淡洗、朱砂木牌 CTA、棋类双卡、工具四列与最近对局完全保持 m6 成果，首屏无异常位移。
- **02 象棋对战（Xiangqi Match）**：**【未破坏】**  
  双人在线卡、象棋棋盘、操作按钮、水墨孤舟水印、下划线 Tab、底栏层叠与间距保持严格一致。
- **03 AI 练习（AI Practice）**：**【未破坏】**  
  AI 棋桌双卡、象棋木纹棋盘、操作按钮、孤舟水印、Tab 与底栏结构节奏完美保持。

---

## 4. 诚实局限与业务边界说明

1. **真实人机练习 vs A-04 双人在线样例**：  
   A-04 为双人在线视觉图样（qa_player vs 清风徐来）；B-04 / 当前实拍为本地真实的人机练习状态（qa_guofeng... vs AI 棋桌 MEDIUM）。**严守禁区，未伪造第二真人头像或虚假在线对战文案**。
2. **时钟展示**：  
   保持现有步时与剩余时间展示（14:32 / 13:48），未新增虚假伪造的计时逻辑。
3. **未触碰业务与服务端**：  
   未修改 WebSocket 通信、走棋验证、五子连珠胜负判定、AI 下子算法。
4. **展示层隔离**：  
   改动纯属 CSS order 纠正，仅作用于 `.boardPage--strict.gomokuTheme`，未侵入象棋与全局其他组件。

---

## 5. 截图产出一览

目录：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m7/`  
镜像：`/workspace/Chinese-chess/shots-m7/`  
视口：375 × 812，`deviceScaleFactor: 2`（750 × 1624 PNG）

| 文件 | 角色 | 状态 / 核心特征 |
|------|------|-----------------|
| `04-gomoku-match.png` | **主验收图** | **顺序纠正**：标题→玩家双卡→棋盘→操作按钮→Tab→底栏，布局统一 |
| `01-home.png` | 回归抽检 | 首页山水淡洗与木牌 CTA 保持完好，未受污染 |
| `02-xiangqi-match.png` | 回归抽检 | 象棋对局节奏正常，操作在前、Tab 在后 |
| `03-ai-practice.png` | 回归抽检 | AI 练习节奏正常，操作在前、Tab 在后 |

---

## 6. Checklist

- [x] 只修改五子棋屏布局（.boardPage--strict.gomokuTheme）
- [x] 不改首页、不扩改象棋/AI 整页
- [x] 不改 WS / 走棋 / 胜负 / 匹配 / 棋规 / AI
- [x] 不造第二真人，保留真实人机练习身份
- [x] 标题在顶部
- [x] 玩家双卡在标题之后
- [x] 棋盘在玩家之后
- [x] 操作按钮在棋盘之后
- [x] Tab 在操作按钮之后
- [x] 底栏保持在底部
- [x] index.html 版本已 bump 至 `?v=20260924m7`
- [x] target/classes/online/ 已完成实时热更同步
- [x] 未 git push、未公网部署
