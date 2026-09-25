# 轻棋局（QingQiJu）移动端国风 m7→m8 底部四 Tab Strict Align 展示层校准 PROMPT
> 提供给 Antigravity（agy）或人工手改执行。
>
> 本文件只输出修改提示词；执行阶段改展示层代码，但**写稿本身不改业务代码**。
> 禁止生成新摄影插画冒充真源；禁止 push / 部署；本轮**不要跑 agy**（由主助手稍后喂）。
---
# 一、任务总则（最高优先级）

## 唯一视觉真源与本轮范围

### A = chatgpt-m8-tabs-delta/out（四 tab 真源）
本轮唯一视觉目标：

| 代号 | 绝对路径 | 页面 |
|------|----------|------|
| A-02 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m8-tabs-delta/out/REF-02-play.png` | 对局 `#/play` |
| A-03 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m8-tabs-delta/out/REF-03-learn.png` | 棋谱 `#/learn` |
| A-04 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m8-tabs-delta/out/REF-04-watch.png` | 观战 `#/watch` |
| A-05 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m8-tabs-delta/out/REF-05-me.png` | 我的 `#/me` |

### B = shots-tabs-baseline（当前 m7 实拍）
本轮唯一对照：

| 代号 | 绝对路径 | 页面 |
|------|----------|------|
| B-02 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-tabs-baseline/02-play.png` | 对局 |
| B-03 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-tabs-baseline/03-learn.png` | 棋谱 |
| B-04 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-tabs-baseline/04-watch.png` | 观战 |
| B-05 | `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-tabs-baseline/05-me.png` | 我的 |

严格只比较：`A-02↔B-02`、`A-03↔B-03`、`A-04↔B-04`、`A-05↔B-05`。

### 首页（本轮不整页重做）
首页风格已对齐，仅作气质对照：

- `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-refs/01-home.png`
- `/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-tabs-baseline/01-home.png`

**禁止**把首页整页重做写进本轮方案。仅当四 tab **共用组件**必须点到时，可极短微调：

- `renderMobileBottomNav()` / `.mobileBottomNav` / `.mobileBottomNav--strict`
- `renderMobilePageHeader()` / `.mobileContentHeader`
- 全局宣纸 token（`--bg-paper` 等）

禁止扩写成整站或首页重做。禁止把旧 `shots/`、`shots-m2/`…`shots-m7/`、对局中棋盘截图当作本轮四 tab 验收真源。

---

## 本轮任务定义

任务：`m7 → m8` **底部四 tab（对局 / 棋谱 / 观战 / 我的）展示层 Strict Align**。

这不是重新设计产品，不是新增功能，也不是重构业务。

目标：

`B（shots-tabs-baseline 02–05） → 四 tab 视觉层 / 布局层 / 展示组件微调 → A（REF-02…REF-05）`

底栏 IA 保持现状 **5 项**（首页 + 对局 + 棋谱 + 观战 + 我的）。本轮「四 tab」指要对齐气质的四个内容页，**不是**删掉首页 tab。

---

## 方案 A（必须采用）

继续保持：

`宣纸空间 + 水墨留白 + 朱砂强调 + 竹青墨绿 + 木纹棋类元素 + 克制交互`

产品气质：轻量国风棋类 App（与已对齐首页同一套 tokens）。

禁止变成：SaaS 后台、活动运营页、科技霓虹、玻璃拟态、大量厚阴影卡片堆叠。

---

## 资源版本

当前：`index.html` 中

`app.css` / `mobile.css` / `board.js` / `app.js` → `?v=20260924m7`

本轮完成后必须 bump：`?v=20260924m8`（四处一致）。

仅用于静态资源缓存刷新；禁止用版本 bump 掩盖未完成的视觉问题。

---

# 二、本轮硬范围（最高优先级）

## 要做
1. 对局页 `renderMobileLobby()` 展示层对准 A-02
2. 棋谱页 `renderLearnPage()`（经 `renderMobileContentPage` 包壳）对准 A-03
3. 观战页 `renderWatchPage()` + `renderWatchRooms` / `renderWatchReplays` 对准 A-04
4. 我的页 `renderProfilePage()` → `renderProfile()` 对准 A-05
5. 共用底栏 / 页头仅允许「对齐首页气质」的微调
6. 主战场 `mobile.css`；`app.js` 仅 class / DOM 展示顺序 / 已有结构复用；`index.html` 仅 bump m8

## 不要做
- 不整页重做首页 `renderMobileHomePage()`
- 不重做对局中棋盘 chrome（象棋/五子棋 match 屏）——那是先前 m6/m7 范围
- 不改路由语义、不删底栏「首页」项、不改成 4 列底栏去首页
- 不引入新摄影素材目录冒充 A 真源里的插画/人像/棋盘照片

---

# 三、严格禁区

## 业务禁区
禁止修改：

- WebSocket
- 走棋逻辑
- 胜负判断
- 匹配逻辑（`quick-start-public-match` / `join-by-code` / `create-room-xiangqi` 等行为契约）
- 棋规
- AI 行棋逻辑

允许保留现有 `data-action` / `data-nav`；只改外观与展示 DOM 壳。

---

## 产品真实性禁区
禁止：

- 虚构会员墙 / VIP / 付费解锁
- 新增广告位、运营活动入口
- 伪造「胜利/失败」等业务结果文案（真实数据是什么就显示什么）
- 把 `.vipBadge`「棋友」改成付费 VIP 墙

---

## 图片资源真实性禁区（写死）
禁止：

- 生成新图片冒充参考资源
- 用 CSS 渐变包装成摄影级山水 / 扁舟 / 围棋静物照片
- 用首字头像包装成人物写真（A 图右侧传统服饰人像 **不可**用假照片复刻）
- 引入未经仓库提供的外部插画作为 A 真源替代

必须保持诚实结论：

**`CSS 氛围优化 ≠ 新摄影插画`**

A 真源中可见的山水、竹叶、扁舟、棋盘静物、人像等，本轮只允许：

- 极淡 CSS wash / radial / linear（建议单层氛围 `opacity ≤ 0.18`）
- 已有 SVG `mobileIcon(...)` 线标
- 印章圆 / 单字印（如学、开、象、五、观）

禁止宣称「已换成摄影级资源」。

---

## 工程禁区
禁止：

- `git push`
- 部署 / 上线 / 改公网
- 停掉箱内已有 Java 服务（若需本地预览，只同步 `target/classes/online/` 静态资源）

---

# 四、代码基线与修改范围

## 仓库
`/workspace/Chinese-chess`

## 主战场（按优先级）
1. `src/main/resources/online/mobile.css`
2. `src/main/resources/online/app.js`（仅下列展示函数 / 相关 class）
3. `src/main/resources/online/index.html`（仅 `?v=20260924m7` → `?v=20260924m8`）

同步：改完后复制到 `target/classes/online/`（及若存在的 `assets/site`），使本地 `127.0.0.1:18388` 生效。

## JS 允许触达的函数（展示层）
- `renderMobileLobby()`
- `renderMobileContentPage(route, content)` / `renderMobilePageHeader(...)`
- `renderLearnPage(route)` / `renderLearnItemCard(...)`（仅 class / 结构微调）
- `renderWatchPage()` / `renderWatchRooms(...)` / `renderWatchReplays(...)`
- `renderProfilePage()` → `renderProfile()`（仅 class / 展示顺序 / 壳层）
- `renderMobileBottomNav(activePage)`（可微调，不得改 5 项 IA）

## JS 禁止
- 改 `loadLearnContent` / `loadWatchOverview` / `loadProfileDashboard` 等数据逻辑
- 改匹配、入房、完成教程、观战进入等 action 语义
- 大面积重写桌面 lobby / desk 路径（除非误伤，应避免）

## 已存在的关键 class / 结构（必须基于真名，勿臆造）

### 对局（`renderMobileLobby`）
- `.mobileLobby`
- `.mobileLobbyLead` / `.mobileEyebrow`
- `.mobileLobbyPrimaryActions`
- `.mobilePrimaryAction`（快速匹配，朱砂主 CTA；`data-action="quick-start-public-match"`）
- `.mobileLobbyCta`（人机练习；`data-action="quick-start-ai-practice"`）
- `.mobileRoomComposer` / `.mobileJoinRow` / `#joinCode`
- `.mobileRoomDisclosure`（公开候场 `<details>`）
- `.mobileSection` / `.mobileSectionHead` / `.mobileRoomList` / `.mobileGameSeal` / `.mobileEmptyState`

### 内容页壳（`renderMobileContentPage` / `renderMobilePageHeader`）
- `.mobileContentPage` / `.mobileContentPage--learn|watch|me`
- `.mobileContentHeader`
- `.mobileContentBody`

### 棋谱（`renderLearnPage` / `renderLearnItemCard`）
- `.learnPage` / `.hero` / `.heroLeft` / `.learnSearchWrap`
- `.learnEndgameCta` / `.searchBar.searchBar--learn`
- `.learnTabs` / `.pill` / `.pill.is-active`
- `.learnMainContent`
- `.learnCard` / `.learnCardLeft` / `.learnCardBadge` / `.learnCardInfo` / `.learnAction`
- 移动端覆盖多在：`.mobileContentPage--learn ...`

### 观战（`renderWatchPage` / `renderWatchRooms` / `renderWatchReplays`）
- `.watchHero` / `.watchEyebrow` / `.watchLiveDot` / `.watchLiveCount`
- `.watchStage` / `.watchToolbar` / `.watchFilters` / `.watchRefresh`
- `.watchLiveGrid` / `.watchMatchCard` / `.watchCardTop` / `.watchGameTag` / `.watchNow` / `.watchPlayers` / `.watchCardFoot`
- `.watchReplayStage` / `.watchSectionHead` / `.watchReplayGrid` / `.watchReplayCard`
- `.watchEmpty` / `.watchEmptySeal`

### 我的（`renderProfile`）
- `.profilePage` / `.profileSidebar` / `.profileSidebarGroup` / `.profileSidebarLabel` / `.profileSidebarItem` / `.profileSidebarIcon`
- `.profileMain` / `.profileHeaderCard` / `.profileUserRow` / `.profileUserMeta` / `.vipBadge`
- `.profileStatsGrid` / `.statBox`
- `.profileOverviewGrid` / `.profileGrowth` / `.profileSectionHead`

### 底栏（`renderMobileBottomNav`）
- `.mobileBottomNav` / `.mobileBottomNav--strict`
- 五项：`首页 #/home` · `对局 #/play` · `棋谱 #/learn/puzzles/ALL` · `观战 #/watch` · `我的 #/me`
- active：`.is-active` + 朱砂色

注意：`mobile.css` 中曾出现过 `grid-template-columns: repeat(4, …)` 的旧规则；**以 5 列 strict 为准**，勿把「四 tab」理解成删首页。

---

# 五、四章 Strict Align（现状 → 目标 → 改法）

---

# 章 02：对局 A-02 ↔ B-02

## 现状（B-02）
`shots-tabs-baseline/02-play.png` + `renderMobileLobby()` 已具备：

- 页头：返回圆钮 + 朱砂 eyebrow「对局」+ 标题「开始一盘棋」+ 右侧个人入口
- 主卡 `.mobileLobbyLead`：「常用入口」+「三步之内，直接开局」
- 朱砂主按钮「快速匹配」（`data-action="quick-start-public-match"`）
- 描边次按钮「人机练习」（`data-action="quick-start-ai-practice"`）
- 好友房 + 房间码输入 +「加入」
- `details.mobileRoomDisclosure` 公开候场
- 底栏 5 项，「对局」active 朱砂

仍偏「干净组件页」：

- 页顶几乎无水墨淡洗，留白偏空，不像首页/A-02 的宣纸呼吸
- 主 CTA 已有朱砂，但印章/木牌纹理与内圈金线感弱于 A
- 好友房行缺少克制的棋类意象装饰（A 有棋盘/帅印氛围；B 纯文字行）
- 公开候场区域无底部水墨扁舟淡洗（A 有；本轮 **只允许 CSS 淡洗**，禁止假摄影扁舟）

## 目标（A-02）
`REF-02-play.png`：

- 整页宣纸连续感，与首页同一气质
- 页头上方可有极淡竹/远山 wash（不抢标题）
- 主卡更像宣纸功能牌，而非厚 SaaS 卡片
- 「快速匹配」继续是唯一强朱砂入口；人机为次级描边
- 好友房 / 房间码区更轻、更纸感
- 公开候场一行保留真实房间数文案
- 底栏与首页一致：宣纸底 + safe-area + 当前 tab 朱砂

## 改法
主改 CSS，必要时微调用 class：

1. **页壳氛围**  
   选择器：`.mobileLobby`（可挂 `::before`/`::after` 作极淡 wash）。  
   规则：单层氛围 `opacity ≤ 0.18`；禁止 background-image 指向新摄影文件。

2. **页头**  
   `.mobileContentHeader`（lobby 内由 `renderMobilePageHeader` 产出）  
   微调字距、eyebrow 朱砂、返回/右侧圆钮边线，与首页 header 圆钮一致。  
   右侧若仍是 `mobileIcon('me')` 线标或首字圆——**保持**；禁止换成写真头像。

3. **主卡**  
   `.mobileLobbyLead`：减厚阴影、加纸色、圆角 18–22px、内边距呼吸。  
   `.mobileEyebrow` 保持朱砂小字。  
   `h2` 保持文案「三步之内，直接开局」，只调字号/字重/serif。

4. **主次 CTA**  
   `.mobileLobbyPrimaryActions > .mobilePrimaryAction`：加强朱砂印章/木牌感（height、radius、轻微内高光、letter-spacing）。  
   `.mobileLobbyCta`：白底细边，勿升格为第二朱砂实心。  
   **禁止**改 `data-action`。

5. **好友房**  
   `.mobileRoomComposer` / `.mobileJoinRow` / `#joinCode` + `button[data-action="join-by-code"]`：  
   输入框纸感边线；「加入」保持小朱砂。  
   允许用极淡 CSS 棋印装饰（伪元素），禁止插入假棋盘 PNG。

6. **公开候场**  
   `.mobileRoomDisclosure > summary`：轻列表行，勿大卡片。  
   展开列表仍用现有 `.mobileRoomList` / `.mobileGameSeal`。

7. **函数**  
   `renderMobileLobby()`：仅允许补 class（如 `mobileLobby--strict`）或微调 DOM 展示顺序；不得改房间数据切片逻辑。

---

# 章 03：棋谱 A-03 ↔ B-03

## 现状（B-03）
`renderLearnPage` + `.mobileContentPage--learn`：

- 页头 eyebrow「学习」+「残局与教程」
- Hero：`pill`「练习」、`h1`「棋谱库」、免费说明、`.learnEndgameCta`「进入残局挑战」、搜索框
- `.learnTabs` 横向 pill（全部/象棋/五子棋/…）
- `.learnCard` 列表：圆印单字 + 标题 + meta + 摘要 +「标记完成」/「查看详情」
- 底栏「棋谱」active

差距：

- Hero 偏桌面双栏残留（虽已有 column 覆盖），纸感与 A 的「左侧文案 + 右侧棋类意象」仍弱
- A 右侧围棋静物为摄影感——**不可伪造**；只允许 CSS 近似艺术区或留白
- 搜索在 B 中贴在 hero 内；A 更像独立 pill 搜索条——可用 CSS/轻微 DOM 壳调整对齐层级，不改搜索行为
- 列表卡右侧 A 有淡棋子装饰；B 无——只允许 CSS 淡纹，禁止假图
- 多枚 filter pill 在窄屏易挤；保持横滑，勿删业务 filter

## 目标（A-03）
- 页头与对局页同一套 `mobileContentHeader` 气质
- Hero 宣纸大卡 + 朱砂「进入残局挑战」为强 CTA
- 搜索条轻、圆角 pill
- 分类 pill：active 朱砂实心，其余描边
- 列表卡：左印章圆、中文案、底双按钮（朱砂主 + ghost 次）
- 继续诚实展示「网页端学习能力免费可用」——**禁止**加会员墙

## 改法
1. `.mobileContentPage--learn` 页底宣纸连续；可加 `opacity≤0.18` 顶洗。  
2. `.mobileContentPage--learn .learnPage > .hero` / `.heroLeft` / `.learnSearchWrap`：单列纸卡、减阴影、统一 padding。  
3. `.learnEndgameCta`：全宽朱砂，圆角，可加轻微纹理（CSS），保持 `data-nav="learn/puzzles/ENDGAME_FEN"`。  
4. `.searchBar--learn` / `#learnSearchInput`：独立视觉层级，placeholder 不变。  
5. `.learnTabs .pill` / `.pill.is-active`：active 用 `var(--brand-cinnabar)`；其余纸色描边。  
6. `.learnCard` / `.learnCardBadge` / `.learnAction`：圆角 16–18；主按钮朱砂、次按钮描边；压缩 inline style 重量感（若改 `renderLearnItemCard`，只动 class/style 展示，不改 complete/detail 逻辑）。  
7. `renderLearnPage()`：允许把 hero 结构略调为更清晰的「文案区 / CTA / 搜索」层，**禁止**改 `loadLearnContent` / filter 语义。

---

# 章 04：观战 A-04 ↔ B-04

## 现状（B-04）
`renderWatchPage()`（由 `renderMobileContentPage` 包壳；页头 eyebrow「观战」+ 标题「正在进行的对局」）：

- `.watchHero`：eyebrow「公开直播 · 自动刷新」、`h1`「观棋台」、说明、`.watchLiveCount`
- `.watchToolbar` + `.watchFilters`（全部/中国象棋/五子棋）+ 刷新
- `.watchMatchCard` 列表（`.watchGameTag` / `.watchNow` / `.watchPlayers` / `.watchCardFoot`）
- `.watchReplayStage`「近期公开复盘」
- 已有淡「观」字氛围（部分实现）

差距：

- A Hero 右侧有强棋盘静物——**不可摄影复刻**；可强化已有 `::after` 淡「观」字 + 纸纹
- A 对局卡有双头像 VS、装饰棋印；B 为文字「甲 对 乙」——允许用首字圆 / CSS 印，禁止假头像照片
- A 主按钮「实时观战」更朱红；核对 `.watchCardFoot` 内行动钮 class，只升外观
- 复盘区标题气质；**文案可不改业务语义**；保持「仅展示已结束的公开真人对局」真实性

## 目标（A-04）
- 观棋台 Hero 成为视觉锚：纸卡 + 淡「观」+ 直播数 pill
- Filter pill 与棋谱页同一套朱砂 active
- 对局卡轻列表：棋种 tag、进行中红点、对手行、房间号、朱砂/描边行动钮
- 复盘区次级，不抢直播区
- 无直播时继续用 `.watchEmpty`，勿伪造房间

## 改法
1. `.watchHero` / `.watchHero::after`：控制淡字与 wash，`opacity≤0.18`；禁止新图。  
2. `.watchLiveCount`：小纸 pill，数字强调。  
3. `.watchFilters button.is-active`：朱砂实心；与 learn pills 对齐。  
4. `.watchMatchCard` / `.watchGameTag` / `.watchNow` / `.watchPlayers` / `.watchCardFoot`：减厚阴影、统一圆角、行动钮朱砂层级。  
5. `.watchReplayCard`：更扁的次级行。  
6. `renderWatchRooms` / `renderWatchReplays`：可加首字圆 class；**禁止**改观战进入 API / roomId 行为。  
7. `renderWatchPage()`：可微调 section class（如加 `watchHero--strict`），不改 `loadWatchOverview`。

---

# 章 05：我的 A-05 ↔ B-05

## 现状（B-05）
`renderProfile()`：

- 页头「我的 / 战绩与偏好」（由 `renderMobileContentPage` 注入）
- `.profileSidebar` 在移动端为多列网格（个人信息/对局记录/学习档案/我的成就/消息通知/偏好设置/帮助与反馈）
- `.profileHeaderCard`：SVG 首字圆头像 + 用户名 +「棋友 · id」+ `.profileStatsGrid`
- active 项有浅朱砂底

差距：

- A 顶区有水墨淡洗与副文案——可对 overview 展示层补一句**已有产品 slogan**（首页已用「落子之间，自有风雅」），勿编造新营销口号；头像保持首字印，禁止写真
- A 菜单项带副标题与 chevron；B 偏 icon+短标题——允许在展示层为 sidebar item 增加 `small` 副文案（静态映射），不改路由 `data-nav="me/..."`
- A 底部另有扩展列表——若无真实数据源，**不要虚构内容**；可用现有 records/study 入口强化，或诚实留空
- 统计数字已用真实 `summary`——保持；只调朱砂数字样式

## 目标（A-05）
- 与首页同一宣纸壳 + 朱砂强调
- 菜单网格：active 浅朱砂底 + 印/图标
- 资料卡：首字朱砂圆 + 用户名 + 棋友身份 + 四格战绩
- 底栏「我的」active
- 无 VIP 墙、无广告

## 改法
1. `.mobileContentPage--me` 顶洗 `opacity≤0.18`。  
2. `.profileSidebar` / `.profileSidebarItem.is-active` / `.profileSidebarIcon`：网格间距、active 浅底、图标朱砂。  
3. `.profileHeaderCard` / `.profileUserRow` / `.avatar` / `.statBox strong`：头像保持 SVG/首字；数字朱砂；「开始对局」按钮可调外观但不改 `data-nav`。  
4. `renderProfile()` overview 分支：允许展示层增加 slogan 一行、编辑入口若已有路由则挂现有 settings；**禁止**新增付费「开通会员」。  
5. `.vipBadge` 文案保持「棋友」——这是身份标签，不是付费 VIP；勿改成会员墙。

---

# 六、共用底栏与页头（极短）

## 底栏
函数：`renderMobileBottomNav(activePage)`  
结构（保持 5 项，勿改成删首页）：

```
首页 #/home | 对局 #/play | 棋谱 #/learn/puzzles/ALL | 观战 #/watch | 我的 #/me
```

CSS：`.mobileBottomNav` / `.mobileBottomNav--strict`  
对齐首页：宣纸底、`safe-area`、细顶线、active `var(--brand-cinnabar)`。

## 页头
`renderMobilePageHeader({ eyebrow, title, ... })` + `.mobileContentHeader`  
四 tab 共用；统一圆钮、eyebrow 朱砂、serif 标题。右侧保持线标/首字，禁止假人像。

---

# 七、诚实局限（必须写入 RESULT）

| 项 | 状态 | 允许 | 禁止宣称 |
|----|------|------|----------|
| 页顶山水/竹/扁舟 | 仍在 | CSS 淡洗 ≤0.18 | 已换成摄影级插画 |
| A 图人像头像 | 已知局限 | 首字印 / 线标 | 已对齐传统服饰写真 |
| 棋谱/观战右侧静物照片 | 已知局限 | CSS 艺术区近似 | 已嵌入真实静物摄影 |
| 观战双头像 | 部分 | 首字圆 | 已用真实用户照片 |
| 「我的」扩展列表无数据 | 仍在 | 挂已有入口 | 已有完整假数据列表 |
| 首页 | 本轮不重做 | 共用组件微调 | 本轮重做了首页 |

口径写死：**CSS 氛围 ≠ 新摄影插画**。

---

# 八、Tokens（四 tab 与首页同一套）

继续使用：

```
--bg-paper --bg-paper-soft --bg-panel
--brand-cinnabar --brand-cinnabar-deep --brand-cinnabar-soft
--text-ink --text-muted
--line-soft --wood-board --wood-border
--mobile-pine（竹青墨绿，约 #315c4d / #3B5B4F）
```

视觉原则：

`宣纸 + 水墨 + 朱砂 + 竹青 + 留白`

禁止重新建立颜色体系；禁止霓虹/玻璃拟态。

---

# 九、M8 验收 Checklist

## 总则
- [ ] 只依据 A-02…A-05 与 B-02…B-05
- [ ] 不使用旧 shots-m* 当四 tab 验收真源
- [ ] 方案 A；首页未整页重做
- [ ] `?v=20260924m8`
- [ ] 未改 WS/走棋/胜负/匹配/棋规/AI
- [ ] 未虚构会员墙
- [ ] 未造假摄影资源
- [ ] 未 push / 未部署
- [ ] RESULT 诚实写「CSS 氛围 ≠ 新摄影插画」

## A-02 对局
- [ ] 宣纸连续感接近 A-02
- [ ] 快速匹配为唯一强朱砂 CTA
- [ ] 人机为次级描边
- [ ] 好友房/房间码纸感
- [ ] 公开候场真实房间数
- [ ] 底栏「对局」active
- [ ] 无假扁舟摄影图

## A-03 棋谱
- [ ] Hero 纸卡 + 朱砂残局 CTA
- [ ] 搜索条轻量
- [ ] pill active 朱砂
- [ ] learnCard 印章圆 + 双按钮层级
- [ ] 无会员墙
- [ ] 底栏「棋谱」active
- [ ] 无假围棋静物图

## A-04 观战
- [ ] 观棋台 Hero 纸感 + 淡「观」
- [ ] filter 与全局 pill 一致
- [ ] 对局卡行动钮层级正确
- [ ] 无伪造直播房间
- [ ] 底栏「观战」active

## A-05 我的
- [ ] 菜单网格 active 浅朱砂
- [ ] 首字头像（非写真）
- [ ] 战绩数字真实数据
- [ ] 「棋友」非付费 VIP 墙
- [ ] 底栏「我的」active

## 共用
- [ ] 底栏仍为 5 项含首页
- [ ] 页头四 tab 视觉统一
- [ ] tokens 与首页一致

---

# 十、交付说明

## 截图
目录：

`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m8/`

至少五帧（含首页对照，首页允许与 baseline 基本一致）：

| 文件 | 路由 |
|------|------|
| `01-home.png` | `#/home`（对照，不验收为重做目标） |
| `02-play.png` | `#/play` |
| `03-learn.png` | `#/learn/puzzles/ALL`（或当前棋谱默认） |
| `04-watch.png` | `#/watch` |
| `05-me.png` | `#/me` |

规格：`375 × 812`，`deviceScaleFactor = 2`。

## RESULT
路径：

`/workspace/Chinese-chess/docs/agent-briefs/2026-09-24-agy-guofeng-mobile-m8-tabs-RESULT.md`

必须记录：

- `A = chatgpt-m8-tabs-delta/out/REF-02…05`
- `B = shots-m8/02…05`（完成后的新实拍）
- 已闭合 / 部分闭合 / 仍在 / 已知局限
- 明确：未改 WS、走棋、胜负、匹配、棋规、AI；未造假图；未 push；未部署
- 明确：`CSS 氛围 ≠ 新摄影插画`

## 改动文件清单（预期）
- `src/main/resources/online/mobile.css`
- `src/main/resources/online/app.js`（展示层）
- `src/main/resources/online/index.html`（bump m8）
- 同步 `target/classes/online/` 对应静态资源

---

# 最终要求

本 PROMPT 只用于：`m7 → m8 底部四 tab（对局/棋谱/观战/我的）展示层 Strict Align`。

- 采用方案 A；当前 `?v=20260924m7` → bump `?v=20260924m8`
- 首页不整页重做
- 不要生成摄影插画冒充资源
- 不要修改业务、WS、走棋、胜负、匹配、棋规或 AI
- 不要 push、不要部署
- 只让 B-02…B-05 向 A-02…A-05 收敛，并与已对齐首页共用同一国风 tokens
