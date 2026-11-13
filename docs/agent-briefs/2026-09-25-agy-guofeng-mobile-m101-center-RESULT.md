# RESULT：轻棋局移动端国风 m10.1 观战/我的版心居中

**日期**：2026-09-25（Asia/Shanghai）  
**验收视口**：375 × 812（截图 `deviceScaleFactor: 2` → 750 × 1624）  
**真源 A**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m10-tabs-refs/out/REF-04-watch.png`、`REF-05-me.png`  
**对照 B（m10）**：`/workspace/Chinese-chess/shots-m10/04-watch.png`、`05-me.png`  
**本轮实拍**：`/workspace/Chinese-chess/shots-m101/`（镜像 `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m101/`）  
**静态资源版本**：`?v=20260925m101`（四处：app.css / mobile.css / board.js / app.js）  
**本地热更**：`http://127.0.0.1:18388/online/`（PublicWebMain **pid 13421** 常驻未杀；已热同步 `target/classes/online/` 与 `assets/site/`）  
**禁区遵守**：未改对局/棋谱大改；未改 WS/协议/走棋/胜负/匹配；**未 git push、未公网部署**。

---

## 一、改动文件清单

| 文件 | 改动性质 | 摘要 |
|------|----------|------|
| `src/main/resources/online/mobile.css` | **主战场** | m10 块之后追加 `/* === 20260925m101 watch/me center === */`：清零 `route-watch/me .shell` 水平 16px；消观战 stage/filters 双重 gutter；Hero/卡/复盘/toolbar 统一 14px；「帮助」`grid-column: 1 / -1`；次级列表父级拉满。 |
| `src/main/resources/online/app.js` | **小补** | 侧栏渲染：`help` 项加 class `profileSidebarItem--full`（仍 `data-nav="me/help"`）。未改路由/数据语义。 |
| `src/main/resources/online/index.html` | **版本 Bump** | 四处 `?v=20260924m10` → `?v=20260925m101`。 |
| `target/classes/online/*`（及 assets/site） | **热更** | index / mobile.css / app.js 已 cp；18388 `200`。 |
| `capture-m101.cjs` | **截图脚本** | 由 m10 复制；`V=20260925m101`；`OUT`/`OUT_MIRROR`=`shots-m101`。 |

---

## 二、居中 / 通栏如何修

### 观战（04）
1. **根因**：早期 `mobile.css` 对 `.site.route-watch .shell` 设 `padding: 16px 16px …`，而对局/home 的 shell 水平为 `0`；m9 又给 `.watchStage`/toolbar 加 14px，m10 卡再 `margin: 0 14px` → 叠成 ~30px 且易左右不对称。  
2. **修法（m101 块）**：
   - `.site.route-watch .shell`：`padding-left/right: 0`（底栏安全区保留）
   - `.watchStage` / `.watchLiveGrid` / `.watchFilters`：水平 padding/margin = 0
   - `.watchToolbar`：仅自身 `padding: 0 14px 12px`
   - `.watchHero` / `.watchMatchCard` / `.watchReplayStage`：`margin-left/right: 14px`，与对局 Lead 同宽（DOM 实测 **left=14 / right=14 / width=347**）

### 我的（05）
1. **通栏**：「帮助与反馈」为服务组第 3 项，双列 grid 奇数落左半宽。  
   - CSS：`.profileSidebarItem[data-nav="me/help"]`、`.profileSidebarItem--full { grid-column: 1 / -1 }`  
   - JS：help 按钮加 `profileSidebarItem--full`  
   - 实测 help：**left=14 / right=14 / width=347**（通栏）  
2. **版心**：同清零 `route-me .shell` 水平 16px；资料卡 / 双列菜单 / 次级列表 margin 14px；`profileMain--bottom` `width:100%` 消除次级半宽右空。

### 与对局对照
| 元素 | left | right | width |
|------|------|-------|-------|
| 对局 `.mobileLobbyLead` | 14 | 14 | 347 |
| 观战 `.watchHero` / `.watchMatchCard` / 复盘 | 14 | 14 | 347 |
| 我的 header / sidebar / help / secondary | 14 | 14 | 347 |

---

## 三、agy vs 手改（诚实）

| 项 | 说明 |
|----|------|
| agy | 已启动 `agy --model gemini-3.8-flash-high --dangerously-skip-permissions --print-timeout 170s`；约 2m50s print timeout，**日志无实质 diff / 未写入 m101 标记**。 |
| 手改 | **本轮实质落码全部手改闭合**（CSS 块、app.js class、index bump、热更、截图、RESULT）。 |
| RESULT 口径 | **agy 尝试失败 → 手改**（非 agy 主导）。 |

---

## 四、诚实差距

| 项 | 状态 |
|----|------|
| 观战/我的与对局 14px 版心 | **已闭合**（DOM 盒模型 14/14/347） |
| 「帮助与反馈」通栏 | **已闭合**（不再半宽左挂） |
| 观战插画/摄影级水墨 | 仍为 m10 CSS 氛围，**本轮不宣称摄影对齐** |
| 棋谱（03）shell 仍 16px | **本轮故意不改**（任务仅观战+我的）；对照截图保留 |
| push / 公网 deploy | **否** |

---

## 五、验收截图（shots-m101）

| 文件 | size (bytes) |
|------|---------------|
| 01-home.png | 603797 |
| 02-play.png | 236417 |
| 03-learn.png | 280895 |
| 04-watch.png | 211760 |
| 05-me.png | 266427 |

双路径一致：  
- `/workspace/Chinese-chess/shots-m101/`  
- `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m101/`

---

## 六、结论

- **观战**：消除 shell+stage 双 gutter，Hero/筛选/对局卡/复盘标题与对局同 14px 居中版心。  
- **我的**：双列居中；「帮助与反馈」通栏单卡；次级列表同版心。  
- **版本**：`20260925m101`；**push=否**；**执行=手改**（agy 超时无 diff）。
