# RESULT：轻棋局移动端国风 m9→m10 四 Tab Strict Align

**日期**：2026-09-25（Asia/Shanghai）  
**验收视口**：375 × 812（截图 `deviceScaleFactor: 2`，2× 位图 750 × 1624）  
**唯一视觉真源 A**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m10-tabs-refs/out/`（`REF-02-play.png` … `REF-05-me.png`；ChatGPT 真生，非 HTML 拼图）  
**对照 B（m9 实拍）**：`/workspace/Chinese-chess/shots-m9/`（镜像 `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m9/`）  
**最终验收实拍**：`/workspace/Chinese-chess/shots-m10/`（镜像 `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m10/`）  
**静态资源版本**：`?v=20260924m10`（四处一致：app.css / mobile.css / board.js / app.js）  
**本地热更**：`http://127.0.0.1:18388/online/`（PublicWebMain pid 13421 常驻；已热同步 `target/classes/online/` 与 `assets/site/`）  
**核心口径声明**：**`CSS 氛围 ≠ 新摄影插画`**；**`首字印 ≠ 写真`**；**观战无虚构段位**  
**禁区遵守**：未改 WS / 走棋 / 胜负 / 匹配 / 棋规 / AI；未伪造摄影级山水或汉服写真；未虚构会员墙 / VIP / 广告 / 付费 / 业余段位；首页未整页重做；**未 git push、未公网部署**。

---

## 一、改动文件清单

| 文件 | 改动性质 | 核心改动摘要 |
|------|----------|--------------|
| `src/main/resources/online/mobile.css` | **主战场（实质写入）** | 在 m9 块之后追加 `/* === 20260924m10 tabs Strict Align === */`（约 L6088–7127，~1040 行）：宣纸 `#F7F1E7`；页顶洗带宽 ~260px、`opacity: 0.178 ≤ 0.18`；页头 ~104–108px + 眉题 `— … —` 伪元素；内容列 14–16px；对局快速匹配木牌+多层金线内环+闪电圆印；人机竹影 / 候场扁舟墨意 CSS；棋谱残局 CTA 朱砂木牌、pill active `#9D3023`、教程卡左右印章；观战 Hero「觀」水印、去双「帥」、朱砂「实时观战」；我的 seal 头像 68px、双列菜单加高、active `#FAF0EC` 洗色；底栏 active `#9D3023`。 |
| `src/main/resources/online/app.js` | **本轮无新增 m10 展示壳** | 复用 m9 已落壳（`profilePage--m9`、`mobileLobbyCta--bamboo`、`mobileRoomDisclosure--ink`、`watchPieceDeco`、`profileSealAvatar`、`mobileHeaderSubtitle`）。**未改 action / 路由 / 数据语义**。工作区相对 origin 仍含 m9 未提交展示层 diff，非本轮新写。 |
| `src/main/resources/online/index.html` | **版本 Bump** | 四处 `?v=20260924m9` → `?v=20260924m10`。 |
| `target/classes/online/*` | **热更同步** | index.html / mobile.css（及 assets/site 同名）已 cp；18388 即时响应 `200`。 |
| `capture-m10.cjs` | **截图脚本** | 由 m9 复制；`V=20260924m10`；`OUT`/`OUT_MIRROR`=`shots-m10`；375×812 dsf=2。 |

---

## 二、逐 Tab 对照 A 判定

### 02 对局（A: REF-02-play ↔ B: shots-m9/02 → shots-m10/02）
- **页头 / 淡洗**：**【部分闭合】** 眉题 `— 对局 —`、标题「开始一盘棋」、页头更高；山水/竹/朝阳为 CSS 淡洗（≤0.18），肉眼仍弱于 A 摄影级水墨——**诚实：CSS≠摄影**。
- **快速匹配**：**【已闭合】** 朱砂木牌 + 金线多层内环 + 闪电圆印；`quick-start-public-match` 未改。
- **人机 / 好友房 / 房间码 / 候场**：**【已闭合】** 人机竹影 CSS；好友房「帥」木印；纸质房间码+朱砂「加入」；候场真实房间数 + CSS 墨意扁舟位（非摄影）。

### 03 棋谱（A: REF-03-learn）
- **版心 / Hero / 残局 CTA**：**【已闭合】** 14px 边距；「练习」badge +「棋谱库」+ 全宽朱砂「进入残局挑战」+ 搜索 pill。
- **filter / 教程卡**：**【已闭合】**「全部」实心朱砂；卡左右圆印/木印「帥」「將」；双按钮保留。诚实：右侧棋具为 CSS 印，非整物摄影。

### 04 观战（A: REF-04-watch）
- **Hero / pills**：**【已闭合】**「观棋台」+ 淡「觀」水印 + 真实 live count；「全部」朱砂 active；刷新保留。
- **对局卡**：**【已闭合】**「象」印、首字圆印、VS、單「帥」装饰、朱砂「实时观战」；**仅真实用户名，无虚构业余段位**（A 图业余 N 级故意不实现）。
- **数据边界**：**【严格遵守】** 稀疏真实直播；不造第二真人 / VIP。

### 05 我的（A: REF-05-me）
- **DOM 顺序**：**【已闭合】** 资料卡 → 双列印章菜单 → 次级纸列表。
- **资料卡**：**【已闭合】** 朱砂首字印「q」（**非汉服写真**）、棋友 badge、slogan、编辑资料、四格真实战绩；seal ~68px。
- **菜单 / 次级**：**【已闭合】** 2 列印章字 + active 朱砂淡洗；次级真实入口。

### 01 首页
- **仅回归**：**【通过】** 底栏 5 项未污染；首页未整页重做（`01-home.png` 603797 bytes）。

---

## 三、诚实差距摘要

| 项 | 状态 | 允许 | 禁止宣称 |
|----|------|------|----------|
| 页顶山水/竹/朝阳 | 氛围层加强（0.178） | CSS 淡洗 ≤0.18 | 已换成摄影级水墨插画 |
| A「我的」古典肖像 | 禁区遵守 | 首字朱砂印章圆 | 已对齐汉服写真头像 |
| 棋谱/观战右侧棋具 | CSS 印逼近 | 「帥」「將」「五」木印 | 已嵌入真实棋具静物文件 |
| A 观战「业余 N 级」 | **故意不实现** | 仅真实用户名首字 | 已虚构段位填满卡片 |
| 公开候场扁舟 | CSS 墨意位 | 低透明 radial 点缀 | 已嵌入扁舟摄影 |
| CSS 淡洗 = 摄影 | **否** | — | 宣称本轮已摄影级对齐 |

口径重申：**`CSS 氛围优化 ≠ 新摄影插画`**；**`首字印 ≠ 写真`**；**观战无虚构段位**。

---

## 四、执行方式（agy vs 手改）

| 项 | 说明 |
|----|------|
| agy | **主导实质落码**。CLI：`agy --model gemini-3.8-flash-high --dangerously-skip-permissions --print=…`（合法模型；未用非法 `composer-2-fast`）。约 15min 完成：追加 m10 CSS 块、index bump、热更 target、`capture-m10.cjs`、五帧截图双路径落盘。日志：`/tmp/agy-m10.log` / 会话终端记录。 |
| 手改 | **本轮未再扩展改码**。验收与 RESULT 由执行子代理收口；无 post-agy 手改补丁。 |
| 工程 | **未 git push、未公网部署**；未停 18388 Java（pid 13421 仍活，HTTP 200）。 |

---

## 五、验收截图清单（375 × 812，dsf=2 → 750×1624）

路径：
- `/workspace/Chinese-chess/shots-m10/`
- `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m10/`（镜像）

| 编号 | 文件名 | size (bytes) | 路由 | 状态 |
|------|--------|---------------|------|------|
| 01 | `01-home.png` | 603797 | `#/home` | 回归通过 |
| 02 | `02-play.png` | 236417 | `#/play` | 对照 A 结构通过（淡洗诚实差距保留） |
| 03 | `03-learn.png` | 280272 | `#/learn/puzzles/ALL` | CTA/pill/卡印闭合 |
| 04 | `04-watch.png` | 205031 | `#/watch` | 無虚构段位；觀水印保留 |
| 05 | `05-me.png` | 258057 | `#/me` | 资料卡→菜单顺序；首字印 68px |

---

## 六、禁区声明（硬）

- [x] 未改 WS / 棋规 / 匹配 / AI / 业务 action 语义  
- [x] 未造第二真人 / 虚构 VIP·广告·段位  
- [x] 未宣称 CSS 淡洗 = 摄影插画，或首字印 = 写真  
- [x] 首页未整页重做  
- [x] **未 git push**  
- [x] **未公网部署**  
- [x] PublicWebMain `127.0.0.1:18388` 仍活（热更 cp，未杀 Java）  
