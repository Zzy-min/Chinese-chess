# RESULT：轻棋局移动端国风 m8→m9 底部四 Tab Strict Align 校准

**日期**：2026-09-24（Asia/Shanghai）  
**验收视口**：375 × 812（截图 `deviceScaleFactor: 2`，2× 位图 750 × 1624）  
**唯一视觉真源 A**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m9-tabs-refs/out/`（`REF-02-play.png` … `REF-05-me.png`；ChatGPT 真生，非 HTML 拼图）  
**对照 B（m8 实拍）**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m8/`（镜像 `/workspace/Chinese-chess/shots-m8/`）  
**最终验收实拍**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m9/`（镜像 `/workspace/Chinese-chess/shots-m9/`）  
**静态资源版本**：`?v=20260924m9`（四处一致：app.css / mobile.css / board.js / app.js）  
**本地热更**：`http://127.0.0.1:18388/online/`（PublicWebMain 常驻；已热同步 `target/classes/online/` 与 `assets/site/`）  
**核心口径声明**：**`CSS 氛围 ≠ 新摄影插画`**；**`首字印 ≠ 写真`**；**观战无虚构段位**  
**禁区遵守**：未改 WS / 走棋 / 胜负 / 匹配 / 棋规 / AI；未伪造摄影级山水或汉服写真；未虚构会员墙 / VIP / 广告 / 付费 / 业余段位；首页未整页重做；**未 git push、未公网部署**。

---

## 一、改动文件清单

| 文件 | 改动性质 | 核心改动摘要 |
|------|----------|--------------|
| `src/main/resources/online/mobile.css` | **主战场（实质写入）** | 追加 `/* === 20260924m9 tabs Strict Align === */`：宣纸 `#F7F1E7`；页顶淡洗加强但仍 `opacity ≤ 0.18`、洗带宽至 ~240px；页头更高（~96–100px）+ `.mobileHeaderSubtitle`；**消除双 gutter**（learn/watch/me 的 `.mobileContentBody` 左右 padding 归零，卡边距保留 ~14px）；对局 CTA 更高、闪电印更清晰、人机竹影 CSS 淡洗、公开候场墨意装饰位；棋谱/观战卡加高加宽；观战 Hero「觀」水印与朱砂 CTA；我的页 seal 头像 64px、菜单格更高、朱砂 active 洗色；底栏 active `#9D3023`。 |
| `src/main/resources/online/app.js` | **展示层配合** | `renderMobilePageHeader` 支持 subtitle；`me` 页眉副题「落子之间，自有风雅」；`renderProfile` overview **DOM 重排**：资料卡 → 双列菜单 → 次级列表（`profilePage--m9`）；菜单「个人信息」图标改为印章字「个」；观战卡补 `.watchPieceDeco`（帥/五，非写真）；人机 CTA 加 `mobileLobbyCta--bamboo`；公开候场加 `mobileRoomDisclosure--ink` 装饰壳。**不改 action / 路由 / 数据语义**。 |
| `src/main/resources/online/index.html` | **版本 Bump** | 四处 `?v=20260924m8` → `?v=20260924m9`。 |
| `target/classes/online/*` | **热更同步** | index.html / mobile.css / app.js 同步至 target 根与 `assets/site/`；18388 即时响应。 |
| `capture-m9.cjs` | **截图脚本** | 由 capture-m8 复制，输出 `shots-m9`，`V=20260924m9`，375×812 dsf=2。 |

---

## 二、逐 Tab 对照 A 判定

### 02 对局（A: REF-02-play ↔ B: shots-m8/02 → shots-m9/02）
- **页头 / 淡洗**：**【部分闭合】** 标题区更高、留白增加；山水/竹/朝阳为 CSS 淡洗（≤0.18），肉眼可见度仍弱于 A 摄影级水墨——**诚实：CSS≠摄影**。
- **主卡舒展**：**【已闭合】** Lead 卡 padding/CTA 高度加高，公开候场下移节奏更接近 A。
- **快速匹配**：**【已闭合】** 朱砂木牌 + 金线内环 + 闪电圆印；`data-action="quick-start-public-match"` 未改。
- **人机 / 好友房 / 候场**：**【已闭合】** 人机竹影 CSS 点缀；好友房「帥」木印；候场保留真实房间数，墨意装饰为 CSS 位（非扁舟摄影）。

### 03 棋谱（A: REF-03-learn）
- **版心宽度**：**【已闭合】** 去掉 body+card 双重 gutter，卡宽回到约 14px 边距，告别 skinny strip。
- **Hero / 残局 CTA / pills**：**【已闭合】** 练习徽章、全宽朱砂「进入残局挑战」、全部 active 朱砂实心；免费说明保留，无会员墙。
- **教程卡**：**【已闭合】** 左圆印 + 右「帥/將」CSS 木印；双按钮层级保留。诚实：右侧棋具为 CSS 印，非整物摄影。

### 04 观战（A: REF-04-watch）
- **Hero**：**【已闭合】** 观棋台 + 淡「觀」水印 + 真实 live count pill。
- **对局卡**：**【已闭合】** 棋种印、首字圆印、VS、帥 装饰、朱砂「实时观战」；**仅真实用户名，无虚构业余段位**（A 图中的业余 N 级未实现）。
- **数据边界**：**【严格遵守】** 稀疏真实直播卡；不造第二真人 / VIP。

### 05 我的（A: REF-05-me）
- **DOM 顺序**：**【已闭合】** 资料卡 → 双列印章菜单 → 次级纸列表（相对 m8 菜单在上已纠正）。
- **资料卡**：**【已闭合】** 朱砂首字印「q」（**非汉服写真**）、棋友 badge、slogan、编辑资料、四格真实战绩。
- **菜单 / 次级**：**【已闭合】** 2 列印章字（个/谱/学/章/信/调/问）；次级保留对局统计 / 喜欢的棋谱 / 最近对局真实路由。

### 01 首页
- **仅回归**：**【通过】** 底栏 5 项未污染；首页未整页重做。

---

## 三、诚实差距摘要

| 项 | 状态 | 允许 | 禁止宣称 |
|----|------|------|----------|
| 页顶山水/竹/朝阳 | 氛围层加强 | CSS 淡洗 ≤0.18 | 已换成摄影级水墨插画 |
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
| agy | **已尝试**；因 `--model composer-2-fast` 非法模型名立即失败（见 `/tmp/agy-m9.log`）。未产生实质落码。 |
| 手改 | **主导实质落码**：按 m9 PROMPT + m8→m9 delta 手写 `mobile.css` m9 块、`app.js` 展示壳、`index.html` bump、capture-m9、本 RESULT。 |
| 工程 | **未 git push、未公网部署**；未停 18388 Java。 |

---

## 五、验收截图清单（375 × 812，dsf=2）

路径：
- `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m9/`
- `/workspace/Chinese-chess/shots-m9/`（镜像）

| 编号 | 文件名 | size (bytes) | 路由 | 状态 |
|------|--------|---------------|------|------|
| 01 | `01-home.png` | 604638 | `#/home` | 回归通过 |
| 02 | `02-play.png` | 192321 | `#/play` | 对照 A 结构通过（淡洗诚实差距保留） |
| 03 | `03-learn.png` | 236921 | `#/learn/puzzles/ALL` | 版心宽度闭合 |
| 04 | `04-watch.png` | 190636 | `#/watch` | 無虚构段位；觀水印保留 |
| 05 | `05-me.png` | 236177 | `#/me` | 资料卡→菜单顺序闭合；首字印 |

---

## 六、禁区声明（硬）

- [x] 未改 WS / 棋规 / 匹配 / AI / 业务 action 语义  
- [x] 未造第二真人 / 虚构 VIP·广告·段位  
- [x] 未宣称 CSS 淡洗 = 摄影插画，或首字印 = 写真  
- [x] 首页未整页重做  
- [x] **未 git push**  
- [x] **未公网部署**  
- [x] PublicWebMain `127.0.0.1:18388` 仍活（热更 cp，未杀 Java）  
