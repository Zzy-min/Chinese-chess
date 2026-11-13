# 轻棋局（QingQiJu）移动端国风 m8→m9 底部四 Tab Strict Align 展示层校准 PROMPT

> 提供给 Antigravity（agy）或人工手改执行。
> **本文件只输出修改提示词；当前阶段不直接改码、不生成新图、不 push、不部署。**
> 本轮不是重新设计，而是基于 m8 实拍继续做 **m8→m9 四 Tab 展示层 Strict Align**，重点清理 **仍未闭合** 的 A/B 视觉差距。

---

# 一、任务总则（最高优先级）

## 1. 唯一视觉真源与本轮范围

### A = `chatgpt-m8-tabs-delta/out`

A 为本轮**唯一视觉真源**，且为 ChatGPT 真生图：

| 代号   | 文件                 | 页面           |
| ---- | ------------------ | ------------ |
| A-02 | `REF-02-play.png`  | 对局 `#/play`  |
| A-03 | `REF-03-learn.png` | 棋谱 `#/learn` |
| A-04 | `REF-04-watch.png` | 观战 `#/watch` |
| A-05 | `REF-05-me.png`    | 我的 `#/me`    |

视觉真源目录：

```text
chatgpt-m8-tabs-delta/out/
```

---

### B = `shots-m8`

B 为当前 **m8 实拍**，已经统一缩放到约 `375×812`，本轮必须逐张和 A 对照：

| 代号   | 文件               | 页面       |
| ---- | ---------------- | -------- |
| B-02 | `02-play.png`（对照上传名可写作 `B-02-play.png`）  | 对局       |
| B-03 | `03-learn.png` | 棋谱       |
| B-04 | `04-watch.png` | 观战       |
| B-05 | `05-me.png`    | 我的       |
| B-01 | `01-home.png`  | 首页，仅回归参照 |

磁盘真路径：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m8/`（文件名为上表，无 `B-` 前缀）。

严格比较：

```text
A-02 ↔ B-02
A-03 ↔ B-03
A-04 ↔ B-04
A-05 ↔ B-05
```

**禁止**把以下旧截图重新拿来当 m9 验收目标：

```text
shots-m2
shots-m3
shots-m4
shots-m5
shots-m6
shots-m7
shots-tabs-baseline
```

它们可以完全忽略。

---

## 2. 本轮任务定义

任务：

```text
m8 → m9
对局 / 棋谱 / 观战 / 我的
四 Tab 展示层 Strict Align
```

主目标不是“继续写一套国风”，而是：

```text
当前 B（shots-m8）
→ 根据 A/B 实际像素差继续修展示层
→ 得到 shots-m9
→ 让四页在结构比例、页面宽度、纵向节奏、卡片尺寸、留白、
   朱砂层级、国风氛围、底栏、页头上进一步逼近 A
```

**m8 RESULT 即使曾写“已闭合”，也不能直接当事实复述。**

本轮必须重新看 A/B：

> 只要肉眼还能明显看出差距，就属于 m9 待办。

尤其不要因为 m8 文档里写过“已完成”“已闭合”，就跳过当前截图里仍明显偏离 A 的部分。

---

## 3. 采用方案

必须采用：

```text
方案 A
```

视觉方向保持：

```text
宣纸空间
+ 水墨留白
+ 朱砂强调
+ 竹青 / 墨绿辅助
+ 宋体 / 明朝体式标题
+ 木纹棋类符号
+ 克制、轻量、安静的国风棋类 App
```

禁止跑偏成：

```text
SaaS 后台
游戏大厅运营页
重玻璃拟态
科技蓝紫霓虹
电商卡片
大面积强阴影
高饱和渐变
```

---

## 4. 当前资源版本与 m9 bump

当前 `index.html` 中资源均仍使用：

```text
?v=20260924m8
```

包括 `app.css`、`mobile.css`、`board.js`、`app.js`。

m9 修改完成后建议统一 bump：

```text
?v=20260924m9
```

不要只 bump `mobile.css` 而留下 `app.js` / 其他引用版本混杂。

---

# 二、硬禁区

以下为最高优先级禁区，任何“为了像 A”都不得突破。

## 业务与真实性

**禁止修改：**

* WebSocket / WS。
* 走棋。
* 胜负判定。
* 匹配算法。
* 房间机制。
* 中国象棋规则。
* 五子棋规则。
* AI 行为。
* AI 难度逻辑。
* 对局真实状态。
* 后端 API。
* 数据模型。

**禁止：**

* 为了视觉对齐人为制造第二个真人用户。
* 把 AI 冒充真人。
* 虚构新的实时直播人数。
* 虚构段位。
* 虚构积分。
* 虚构会员。
* 虚构 VIP。
* 虚构付费权益。
* 虚构广告。
* 虚构新的比赛、活动或社区数据。

---

## 本轮代码边界

主战场：

```text
mobile.css
```

必要时允许：

```text
app.js
```

但 `app.js` 仅限：

* 展示壳结构；
* DOM 排序；
* 增加稳定 class；
* 将现有真实内容映射进更接近 A 的展示容器；
* 补齐图标 span / 文案层级 wrapper；
* 不改变事件的 `data-action` / `data-nav` 语义；
* 不改变真实状态来源。

当前移动大厅本身已经有 `.mobileLobby--strict`、`.mobileLobbyLead`、快速匹配、人机练习、好友房、公开候场等真实结构；保持这些行为不变。 

共用底栏当前仍是：

```text
首页 / 对局 / 棋谱 / 观战 / 我的
```

5 项 IA，不准删除首页，也不准把所谓“四 Tab”误解成把底栏改成四项。当前 `renderMobileBottomNav()` 的导航结构应保持。

---

## 操作禁区

本轮：

```text
不 push
不部署
不改生产环境
不生成新参考图
不调用图片生成替代 A
不拿 HTML/CSS 拼图冒充 A 真源
```

本文件只是 **m8→m9 修改 PROMPT**。

---

# 三、开始前先做的检查

在真正修改 CSS 之前，先完成以下检查，不得直接继续往 m8 末尾堆一大坨覆盖规则。

## 1. 检查 m8 层叠

当前 CSS 已存在：

```text
/* === 20260924m8 tabs Strict Align === */
```

m8 已在移动端设置：

```css
.mobileLobby,
.mobileContentPage {
  background: #FAF7F0;
  min-height: 100dvh;
  ...
}
```

并已有顶部淡洗、页头、对局、棋谱、观战、我的及共用底栏样式。

**先核对：**

* 是否有更早的旧选择器仍压住 m8；
* 是否有 `.mobileContentBody` 的 inherited padding / width / max-width 让页面实际主体比 A 明显更窄；
* 是否有 inline style 在 `app.js` 中继续覆盖 CSS；
* 是否由于选择器优先级导致本来写了 m8 rule，但 B 实拍没有实际呈现；
* 是否部分装饰 DOM 存在但被 `display:none` / overflow / z-index 吞掉；
* 是否固定底栏高度导致正文可视区域比例偏差。

**m9 优先解决真实覆盖链，不要一律继续 `!important` 叠加。**

可以在 m8 block 后新增一个清晰的：

```css
/* === 20260924m9 tabs final delta === */
```

但只放 m9 差异，不要把 m8 整段复制一次。

---

# 四、共用 Tokens 与整体视觉基准

m9 不重新换设计系统。

建议沿用并进一步统一以下视觉 token：

```css
--m9-paper: #FAF7F0;
--m9-paper-card: rgba(252, 249, 242, 0.94);
--m9-paper-soft: #FAF6EF;

--m9-ink: #231C16;
--m9-ink-secondary: #5A4738;
--m9-muted: #7E7365;
--m9-muted-light: #9A8C7E;

--m9-cinnabar: #8C2E21;
--m9-cinnabar-bright: #A43224;
--m9-cinnabar-dark: #7B2118;

--m9-green: #315C4D;

--m9-line: rgba(210, 195, 175, 0.58);
--m9-line-soft: rgba(215, 200, 180, 0.42);

--m9-card-radius: 16px;
--m9-card-radius-lg: 20px;

--m9-shadow-soft: 0 3px 14px rgba(68, 48, 29, 0.035);
```

原则：

```text
A 的“高级感”主要来自：
空间 + 比例 + 留白 + 字体层级 + 低对比背景
而不是靠加重阴影。
```

---

# 五、共用页头：四页先统一，再逐页差异化

A-02～A-05 的页面共同特点非常明确：

* 顶部并不是一条传统 AppBar；
* 页面头部有较大呼吸区；
* 左返回圆钮；
* 中间为小朱砂 eyebrow + 宋体主标题；
* 右侧为头像 / 个人入口；
* 背后有非常淡的国风山水 / 竹叶 / 朝阳氛围；
* 标题区与首张内容卡之间有清晰呼吸。

当前 m8 CSS 的 `.mobileContentHeader` 最小高度仅约 `70px`，并使用 `16px` 左右 padding。

从 B-03/B-04/B-05 看，当前共同问题是：

```text
页头视觉高度明显偏短
山水背景存在感不足
标题离顶部较近
首卡进入得过早
整个内容像普通移动网页，而不是 A 中的“国风页头 + 内容层”
```

## m9 共用调整方向

优先检查：

```css
.mobileContentHeader
.mobileContentPage::before
.mobileContentBody
```

必要时为四页增加统一 strict scope：

```css
.mobileContentPage--learn
.mobileContentPage--watch
.mobileContentPage--me
```

并让可视页头区接近：

```text
约 118–145px 的视觉区域
```

不是把 header DOM 硬写 145px，而是：

```text
header 本体
+ 背景淡洗
+ header 后的留白
共同形成 A 的高度
```

推荐：

* header 圆按钮 `40–44px`；
* 顶部安全区后再给 `8–12px`；
* eyebrow `10–11px`；
* 主标题 `22–25px`；
* 主标题行高不要过松；
* 首内容卡顶部和页头文字之间保持约 `20–28px` 的视觉空气；
* 右侧入口不要出现现代“裸 outline 用户 icon”过强的 SaaS 感。

---

# 六、第一页：对局 A-02 ↔ B-02

## 6.1 当前 B-02 现状

B-02 已经有：

* `开始一盘棋`；
* 常用入口；
* 快速匹配；
* 人机练习；
* 好友房；
* 房间码；
* 公开候场；
* 正确的底部导航。

因此 **不要重写业务结构**。

真实 DOM 已经按以下顺序存在：

```text
页头
→ .mobileLobbyLead
  → 常用入口
  → 快速匹配
  → 人机练习
  → 好友房 / 房间码
→ .mobileRoomDisclosure
```

`.mobileLobby--strict` 与这些 action 已经存在。

---

## 6.2 B-02 相比 A-02 仍未闭合的差距

### 差距 1：整个页面过“平”、页头氛围不够

A-02：

* 顶部水墨山峦更明显；
* 左侧竹影较丰富；
* 右上朝阳 / 山体共同包围标题；
* 标题区高度更大；
* 内容卡不是直接贴着 header。

B-02：

* 顶部更像纯米白；
* 背景山水辨识度太弱；
* 页头和主卡之间的空间太少。

### 修改

`.mobileLobby--strict` 也必须拿到和其他三页一致的顶部氛围，而不是只依赖 `.mobileContentPage::before`。

增加：

```css
.mobileLobby--strict::before
```

或把现有：

```css
.mobileLobby::before
```

m9 调整为更能在实拍里看见、但仍满足 opacity 限制的淡洗。

建议：

```text
opacity 0.14–0.18
```

不要超过 `0.18`。

重点不是提高黑度，而是优化：

```text
gradient 位置
山体面积
竹影面积
顶部高度
```

---

### 差距 2：A 的主卡明显更“大块、更舒展”

B-02 当前主内容整体太快收缩：

* 快速匹配卡偏矮；
* 人机练习偏矮；
* 好友房块偏紧；
* 公开候场出现得太早；
* A 中第一整块主卡明显占据首屏中上部更大的纵向空间。

### 修改优先级

优先调：

```css
.mobileLobbyLead
.mobileLobbyPrimaryActions
.mobilePrimaryAction
.mobileLobbyCta
.mobileRoomComposer
.mobileCreateRoomBtn
.mobileJoinRow
```

不要单纯给 `.mobileLobbyLead` 一个巨大固定高度。

应通过真实内部间距撑开：

```text
.mobileLobbyLead
margin-top: 8–14
padding-top: 20–24
padding-inline: 14–16
padding-bottom: 18–20

常用入口 → 标题：约 8
标题 → 快速匹配：约 16–20

快速匹配：68–78px
快速匹配 → 人机：10–12px
人机：64–72px
人机 → 好友房：12–14px

好友房 / 房间码整体：约 158–178px
```

目标是让 B-02 的公开候场块整体**往下回到更接近 A 的位置**。

---

### 差距 3：快速匹配 CTA 的图标权重不足

A-02 的快速匹配：

* 左侧有明显朱砂暗圆；
* 圆里是白色闪电；
* 标题更突出；
* 副文案紧随其下；
* 右箭头固定靠右；
* CTA 有木牌 / 朱砂纸牌感。

当前 DOM 已有：

```html
<span class="mobilePrimaryIcon">...</span>
<span class="mobileActionTexts">...</span>
<span class="mobileActionArrow">...</span>
```

因此不要造新按钮。

m9 明确检查：

```css
.mobileLobby--strict .mobilePrimaryAction
.mobileLobby--strict .mobilePrimaryIcon
.mobileLobby--strict .mobilePrimaryIcon .mobileIcon
.mobileLobby--strict .mobileActionTexts
.mobileLobby--strict .mobileActionArrow
```

要求：

```text
左图标必须肉眼清晰存在
图标圆 42–46px
闪电图标约 20–22px
圆内暗朱砂 / 半透明深色
标题 17–18px 宋体
副文案 11px
箭头不要贴按钮边
```

若 B-02 实拍图标仍消失，先排查：

```text
svg color
width/height
flex-shrink
overflow
旧 mobileIcon rule
```

而不是换 emoji。

---

### 差距 4：好友房缺少 A 的“棋类点睛”

A 中右侧有很明显的棋子式“帥”。

当前 DOM 已真实存在：

```html
<span class="mobilePieceDeco">帥</span>
```

因此 m9 必须让它真正发挥视觉作用。

调整：

```css
.mobilePieceDeco
```

建议：

```text
40–44px
浅木色径向 / 线性渐变
朱砂「帥」
极轻内外阴影
靠右
和好友房标题垂直居中
```

不要做成真实照片。

---

### 差距 5：房间码区域过于标准表单感

A：

* label 较轻；
* input 像宣纸输入槽；
* 加入按钮有朱砂块；
* 整体是好友房卡的一部分。

m9 调：

```css
.mobileRoomComposer label
.mobileJoinRow
.mobileJoinRow input
.mobileJoinRow button
```

让输入区：

```text
input 高约 46–50px
按钮宽约 72–78px
按钮 radius 10–12px
输入框边框更淡
```

不要产生 Bootstrap form 感。

---

### 差距 6：公开候场块位置与宽度

A 中：

* 独立大圆角纸卡；
* 离主卡约 `14–18px`；
* 左图标圆；
* 两行文本；
* 宽度接近主卡；
* 右侧箭头。

保持 `.mobileRoomDisclosure`，只重新校：

```css
.mobileRoomDisclosure
.mobileRoomDisclosure > summary
.mobileDisclosureLeft
.mobileDisclosureIcon
```

目标：

```text
主卡与候场卡同一水平外边界
左右约 12–14px viewport gutter
候场卡高度约 68–78px
```

---

# 七、第二页：棋谱 A-03 ↔ B-03

## 7.1 当前 B-03 最大问题：主体明显偏窄

这是 m9 必须优先解决的一处。

A-03 中：

```text
英雄卡
搜索框
filter pills
教程卡
```

几乎都使用接近全屏的内容宽度，左右主要留：

```text
约 12–14px
```

而 B-03 中：

```text
.hero
.learnCard
```

肉眼明显收在一个更窄的居中列内。

这不是简单“卡片 radius 不对”，而是**版心宽度错误**。

---

## 7.2 首先查宽度来源

检查：

```css
.mobileContentBody
.learnPage
.mobileContentPage--learn .learnPage
.mobileContentPage--learn .hero
.mobileContentPage--learn .learnMainContent
.mobileContentPage--learn .learnCard
```

以及旧规则里的：

```text
max-width
width
margin-inline
padding-inline
```

**m9 必须消除重复 gutter。**

目标结构：

```text
viewport 375
→ 页面内容左右约 12–14
→ 卡片宽度约 347–351
```

不要形成：

```text
viewport
→ body 16px padding
→ hero 再 margin 14px
→ 最终卡宽只剩 287–315px
```

若 `.mobileContentBody` 已经有 `padding-inline:16px`，则 strict 页面中二选一：

```text
A. body gutter 保留，子卡 margin-inline 改 0
```

或：

```text
B. strict body 横向 padding 归零，子卡自己使用 12–14px margin
```

**不得双重留白。**

推荐 B，控制更清晰。

---

## 7.3 Hero 卡

当前真实结构：

```text
.hero
→ heroLeft
→ 练习 pill
→ 棋谱库
→ 说明
→ learnEndgameCta
→ searchBar
```

代码里已有 `进入残局挑战` 与搜索，不要新增假功能。

A 的 hero：

* 大纸卡；
* 高度更充足；
* 标题显著；
* CTA 宽而稳；
* 搜索框不拥挤；
* 内容左对齐；
* 卡片和背景山水相互叠合。

m9 建议：

```css
.mobileContentPage--learn .learnPage > .hero
```

目标：

```text
margin-inline: 12–14px
padding: 18–20px 16px 14–16px
radius: 18–20px
gap: 10–12px
```

标题：

```text
24–27px
```

CTA：

```text
50–54px
```

搜索：

```text
44–48px
```

---

## 7.4 filter pills

A：

```text
全部 / 象棋 / 五子棋 / 精彩对局 / 名师教程……
```

表现为横向轻量胶囊。

当前真实 filter 列表比首屏可见项更多，不要为了截图删除后面的真实筛选项。当前 `renderLearnPage()` 仍包含多种真实 filter。

m9 做：

```css
.learnTabs
.learnTabs .pill
.learnTabs .pill.is-active
```

重点：

* 横向 overflow 必须自然；
* 禁止换行导致两排；
* active 朱砂；
* inactive 米白；
* pill 之间间距 `8px` 左右；
* 整体左边界必须跟 hero / cards 对齐；
* 高度约 `32–34px`。

如果末端有半截 pill，允许保留，这反而符合横向可滑动列表。

---

## 7.5 `.learnCard`

m8 已经专门定义 `.learnCard` 和伪元素“帥/將”；选择器也真实存在。

m9 不要再说“需要新增 learnCard”。

需要做的是**把它从当前 B 的窄卡与弱层级修到 A**。

重点：

```css
.mobileContentPage--learn .learnCard
.learnCardBadge
.learnCardInfo
.learnCardInfo h3
.learnAction
```

### 卡片宽度

先修到：

```text
左右 12–14px
```

与 hero 同宽。

### 卡片高度

A 首张卡约：

```text
120–130px
```

后续类似。

不要压成 90px 信息条。

### 左圆章

应有：

```text
较大的“开 / 棋 / 中”等印章视觉
```

如果 DOM 已经有 `.learnCardBadge`，增强它即可：

```text
46–50px
朱砂径向渐变
白 / 宣纸色字
双圈或内描边
```

### 文本

标题：

```text
15–17px
宋体 / serif
700
```

元信息：

```text
10–11px
```

摘要：

```text
11–12px
1.4–1.5 line-height
```

不要所有字都变成灰色小字。

### 底部按钮

A：

```text
左：标记完成（朱砂）
右：查看详情（纸白描边）
```

要求两按钮形成明确的 `1:~0.8` 关系，而不是两个同权重 CTA。

---

## 7.6 棋谱右侧“棋具静物”的处理

A-03 中右侧有棋盘 / 棋子式静物。

m9 **不能导入真实棋具照片**。

允许继续使用：

```css
.learnCard::before
```

生成：

```text
「帥」
「將」
```

的木纹圆印，或用极淡 CSS 线条增加棋盘格暗示。

建议：

```text
位置：右 12–18px
上 16–24px
尺寸：38–44px
opacity：0.75–0.9
```

同时要给正文保留：

```text
padding-right: 52–64px
```

不要让标题和伪元素互压。

---

# 八、第三页：观战 A-04 ↔ B-04

## 8.1 当前 B-04 现状

已有：

```text
公开直播
观棋台
直播数量
棋种筛选
刷新
watchMatchCard
公开复盘
```

且 `.watchMatchCard` 内真实用户名来自现有公开房间数据，两个头像目前使用用户名首字圆徽标。

不要为了 A 的人物头像去生成真人图。

---

## 8.2 最大差距 1：A 的 Hero 更丰富，B 仍过于“空白网页”

A-04 hero：

* 有更完整的页顶山水；
* hero 大卡内部右侧明显有棋盘 / 棋子水墨静物；
* 左文案和右视觉平衡；
* “2 局正在直播”胶囊位于左下附近；
* Hero 整体更高。

B：

* 大字“觀”虽然已经是一种气氛，但过于单薄；
* 缺少 A 那种右侧视觉重量；
* Hero 较矮 / 内容压缩。

m8 当前已有：

```css
.watchHero::after {
  content: "觀";
  ...
}
```

以及约 `110px` 的淡字装饰。

m9 不要删掉，但应降低它作为“唯一装饰”的存在感。

可以组合：

```text
淡「觀」
+ CSS 棋盘线纹
+ 小型「帥 / 將」木纹圆印
```

但全部必须为 CSS，不得引入假摄影。

---

## 8.3 Hero 尺寸与布局

调整：

```css
.mobileContentPage--watch .watchHero
.mobileContentPage--watch .watchHero::after
.watchLiveCount
```

建议 hero：

```text
左右：12–14px
高度：约 145–165px
padding：18–20px 16px
radius：18–20px
```

文本区保留左侧约：

```text
60–68%
```

右侧给装饰留位置。

直播计数不要被挤在右上小角落。

优先逼近 A 的：

```text
左下胶囊统计
```

如果结构改动很小，可以在 `app.js` 中调整 `.watchLiveCount` 所处位置，但只做展示顺序，不改 rooms 数量来源。

---

## 8.4 过滤条

A：

```text
全部 / 中国象棋 / 五子棋      刷新
```

一行清晰完成。

B 当前虽已有，但整体宽度、间距和 hero / 卡片没有形成统一栅格。

调整：

```css
.watchToolbar
.watchFilters
.watchFilters button
.watchRefresh
```

目标：

```text
左右 12–14px
active 朱砂实心
inactive 宣纸描边
刷新单独置右
```

如果 375 宽不足：

```text
缩小按钮 padding
```

而不是让刷新掉到很下面。

---

## 8.5 `.watchMatchCard`

这是 m9 的重点。

B-04 当前首卡：

* 仍过窄；
* 内部玩家信息挤；
* 卡片层级偏像数据面板；
* A 中卡片更宽、更高、更有棋局氛围；
* 右侧“棋子 / 棋盘”有明显视觉重心。

调整：

```css
.mobileContentPage--watch .watchMatchCard
.watchCardTop
.watchPlayers
.watchPlayer
.watchAvatar
.watchPlayerInfo
.watchVs
.watchCardFoot
```

目标卡片：

```text
左右 12–14px
高度约 128–145px
padding 14–16px
radius 16–18px
```

---

## 8.6 玩家区不能再过度压缩

当前 m8 曾给：

```text
.watchCardTop max-width: 230px
.watchPlayers max-width: 230px
```

这在 B-04 中使正文区被压得过窄。

m9 重新评估：

```css
.watchCardTop
.watchPlayers
```

推荐不要再固定 `230px`。

可改为：

```text
width: calc(100% - 54px)
```

或：

```text
padding-right: 52–58px
```

让右侧棋子装饰有位置，同时左侧玩家名字能完整显示更多字符。

---

## 8.7 头像真实性

继续：

```text
用户名首字圆徽标
```

不要：

```text
抓取真人头像
随机生成汉服头像
复用 A 图肖像冒充线上真人
```

可让首字头像更接近 A：

```text
30–34px
宣纸 / 浅木色
细描边
宋体首字
```

---

## 8.8 右侧棋子装饰

当前 m8 已使用：

```css
.watchMatchCard::before {
  content: "帥";
}
```

m9 允许继续。

但为了更像 A，可以针对：

```text
象棋卡：帥 / 將
五子棋卡：不要强行放“帥”
```

如果 DOM 有 game type class，优先分别控制。

如果没有，**不要为了这个视觉点大改业务 DOM**。

五子棋可以只用：

```text
黑白小圆棋子 CSS 装饰
```

或不加。

---

## 8.9 卡片 CTA

A 首个直播卡：

```text
实时观战 >
```

位置靠右下。

m9：

```css
.watchCardFoot button
```

建议：

```text
40–44px 高
朱砂
padding-inline 14–18px
radius 8–10px
```

但不要把整个 footer 做成按钮条。

---

## 8.10 “精彩回放”区域

A 中：

```text
精彩回放
查看更多
列表卡
```

B 中若表现为：

```text
落子有痕
近期公开复盘
```

不要强迫改成假数据。

可以调整**展示标题**，但真实列表必须继续来自现有 replay 数据。

重点修：

```text
标题字号
上下留白
分隔线
列表密度
```

不要新增假回放。

---

# 九、第四页：我的 A-05 ↔ B-05

## 9.1 当前结构方向是对的，但比例仍未闭合

m8 已经做成：

```text
2 列菜单网格
+ 资料卡
+ 4 项统计
+ 二级入口
```

这是正确方向，不要推翻。

m9 的任务是让 B-05 从“功能已经像”继续变成“视觉比例真正接近 A”。

---

# 十、我的页第一块：菜单网格

A-05：

```text
个人信息
对局记录
学习档案
我的成就
消息通知
偏好设置
帮助与反馈
```

2 列纸卡网格，其中：

* 每个 item 有圆形 seal icon；
* 左上“个人信息”处于柔和朱砂选中态；
* 文字层级清楚；
* 卡片整体更舒展；
* 大网格本身像一个统一的宣纸内容区，而非零散设置按钮。

m8 当前已将 `.profileSidebar` 设为 2 列网格，并有 `.profileSidebarItem` / `.profileSidebarIcon` / `.profileSidebarTexts` 等选择器。

m9 调：

```css
.mobileContentPage--me .profileSidebar
.mobileContentPage--me .profileSidebarItem
.mobileContentPage--me .profileSidebarIcon
.mobileContentPage--me .profileSidebarItem.is-active
.mobileContentPage--me .profileSidebarTexts strong
.mobileContentPage--me .profileSidebarTexts small
.mobileContentPage--me .profileSidebarChevron
```

---

## 10.1 宽度

和棋谱、观战一样，B-05 当前内容列仍显得偏窄。

菜单 grid 应：

```text
左右约 14px
```

不应该再叠第二层 30px+ 内缩。

每列宽度约：

```text
(375 - 28 - 8) / 2 ≈ 169.5px
```

而不是 130～145px。

---

## 10.2 卡片尺寸

建议：

```text
每格高度：58–66px
gap：8–10px
radius：12–14px
padding：8–10px
```

A 的菜单网格更“铺开”。

不要把文字压成很多难读的小字。

---

## 10.3 图标改成“印章感”，而不是普通设置 icon

`.profileSidebarIcon`：

```text
34–38px
浅宣纸 / 浅木
朱砂 / 棕色字
```

active：

```text
朱砂圆
白色 icon / 字
```

不要使用彩色系统 emoji。

---

# 十一、我的页第二块：个人资料卡

A：

* seal avatar 明显；
* username；
* `棋友 · ID: ...`；
* 风雅短句；
* 编辑资料；
* 下方一排 4 个朱砂数字；
* 信息密但不拥挤。

B 当前方向已经正确，但：

```text
卡宽仍偏窄
头像略小
username 行容易截断过早
编辑按钮与用户名关系略挤
统计区整体尺寸偏小
```

---

## 11.1 `.profileHeaderCard`

改：

```css
.mobileContentPage--me .profileHeaderCard
```

目标：

```text
margin-inline: 14px
padding: 18–20px 14–16px
radius: 18–20px
```

卡宽必须跟菜单区基本一致。

---

## 11.2 `.profileSealAvatar`

A 的圆章是重要视觉锚点。

m9 建议：

```text
60–66px
```

可增加：

```text
内圈
外圈
非常轻的纸纹 / radial gradient
```

不要变成拟物玻璃按钮。

---

## 11.3 古典肖像禁区

A-05 右上或资料语境中存在古典女性肖像。

**禁止引入真实/生成汉服写真头像来冒充对齐。**

继续使用：

```text
首字朱砂印章圆
```

这是正确口径。

---

## 11.4 统计区

A：

```text
对局数 / 胜率 / 胜负 / 获得成就
```

大数字朱砂。

m9：

```css
.profileStatsGrid
.statBox
.statBox strong
.statBox span
```

建议：

```text
数字 21–24px
label 10–11px
统计区顶部留 14–16px
竖分割线极浅
```

不要把 0% / 0/9 之类真实值“美化”成虚构更好看的数字。

---

# 十二、我的页第三块：二级列表

A 中继续有：

```text
对局统计
我喜欢的棋谱
最近浏览 / 最近对局
```

m8 已经存在 `.profileSecondaryNav` 等展示结构。

这部分 m9 不必大改，只要让：

```text
卡宽
圆角
icon
字体
行高
chevron
```

与上方统一。

建议：

```text
每项 54–60px
margin-inline 14px
gap 8px
```

不要做成 iOS Settings 那种纯白硬分割列表。

---

# 十三、共用底栏 `renderMobileBottomNav`

当前真实底栏是：

```text
首页
对局
棋谱
观战
我的
```

并由 `renderMobileBottomNav(activePage)` 管理激活态。

本轮禁止改变导航信息架构。

---

## 13.1 A/B 仍未闭合的地方

A-02～A-05：

* 底栏更像“宣纸底板”；
* 高度稳定；
* icon 约 19–21px；
* label 很轻；
* active 仅朱砂，不需要厚背景；
* 上边界几乎没有强阴影；
* 页面正文不会被底栏遮挡。

B 当前已经接近，但 m9 继续校：

```css
.mobileBottomNav
.mobileBottomNav--strict
.mobileBottomNav a
.mobileBottomNav a .mobileIcon
.mobileBottomNav a.is-active
```

当前 selector index 也确认这些共用底栏规则存在多处定义，要特别防止旧层叠干扰。 

---

## 13.2 m9 底栏目标

```text
高度：约 56–60px + safe-area
背景：#FAF6EF 左右
top border：极浅
无明显 drop shadow
icon：20px 左右
label：10px
icon-label gap：2px
active：#8C2E21
```

保证：

```text
首页 B-01
对局 B-02
棋谱 B-03
观战 B-04
我的 B-05
```

共用同一底栏尺寸，不出现四个内容页对齐后首页底栏突然变形。

---

# 十四、首页 B-01：只回归，不整页重做

首页只用来检查：

```text
底栏
共用 token
页头类公共规则
背景基色
```

**不允许把 B-01 纳入整页重做。**

如果 m9 调整：

```css
.mobileBottomNav
```

则必须回归首页。

如果 m9 调整：

```css
.mobileContentHeader
```

但首页没用该组件，则无需牵连首页。

如果更改：

```css
:root
body
.mobilePage
```

必须检查 B-01 是否被误伤。

允许：

```text
共用规范自然继承
```

禁止：

```text
顺便把首页全部重写
```

---

# 十五、诚实局限与客观说明（必须写入 RESULT）

以下口径不可省略。

原 m8 诚实说明已经明确：

* 页顶山水 / 竹 / 朝阳只允许 `CSS 淡洗 ≤0.18`，不得宣称已换成摄影级水墨插画；
* A 图古典肖像只能用首字朱砂印章圆逼近，不能宣称已对齐传统汉服写真；
* 棋谱 / 观战右侧棋具允许 CSS「帥」「將」木纹印章等近似，不能宣称嵌入真实棋具静物摄影；
* 首页只作为对照，不得宣称本轮整页重做。

口径核心仍然是：

> **CSS 氛围优化 ≠ 新摄影插画。** 

m9 RESULT 必须继续正面写明：

| 项           | m9 允许                                              | m9 禁止宣称                 |
| ----------- | -------------------------------------------------- | ----------------------- |
| 山水 / 竹 / 朝阳 | CSS gradient / pseudo-element 淡洗，整体 opacity ≤ 0.18 | “已替换成 A 中摄影级 / 手绘级水墨背景” |
| A 中古典女性肖像   | 用户名首字朱砂印章圆                                         | “已对齐汉服写真头像”             |
| 棋谱右侧棋具      | CSS「帥」「將」、棋盘线纹、轻木纹圆印                               | “已嵌入真实棋具照片”             |
| 观战右侧棋具      | CSS 印章 / 棋盘线纹                                      | “已使用 A 的真实棋盘静物”         |
| 观战人物头像      | 真实用户名首字徽标                                          | “已获得真人照片”               |
| 我的资料头像      | 首字 seal avatar                                     | “已还原 A 的写真头像”           |
| 段位          | 仅显示已有真实数据                                          | 不虚构段位                   |
| 会员 / VIP    | 不新增                                                | 不得声称已做 VIP              |
| 付费          | 不新增                                                | 不得造会员权益 / 付费墙           |
| 广告          | 不新增                                                | 不得造广告位                  |
| 首页          | 只做共用规则回归                                           | 不得说 m9 整页重做首页           |

---

# 十六、m9 具体 CSS 执行优先级

不要随机调。

按下面顺序执行。

## P0：先修版心宽度

必须先解决：

```text
B-03
B-04
B-05
```

明显比 A 窄的问题。

重点检查：

```css
.mobileContentBody
.learnPage
.watchStage
.watchReplayStage
.profilePage
.hero
.learnCard
.watchMatchCard
.profileSidebar
.profileHeaderCard
.profileSecondaryNav
```

先消除重复 padding / margin。

**P0 不闭合前，不要花大量时间微调棋子阴影。**

---

## P1：修共用页头高度与首屏纵向节奏

目标：

```text
A 的页头更舒展
B 当前过紧
```

调：

```css
.mobileContentHeader
.mobileLobby--strict
.mobileLobby--strict::before
.mobileContentPage::before
```

---

## P2：各页主卡尺寸

依次：

```text
对局 .mobileLobbyLead
棋谱 .hero
观战 .watchHero
我的 .profileSidebar / .profileHeaderCard
```

让四页首屏视觉重量统一。

---

## P3：CTA / filter / 功能层级

包括：

```text
快速匹配
残局挑战
filter pills
实时观战
编辑资料
```

朱砂只用于最核心动作和当前态。

---

## P4：棋类装饰与氛围

最后再处理：

```text
帥
將
棋盘线纹
淡墨山水
竹影
朝阳
```

这些是 finishing layer，不允许反过来掩盖版式问题。

---

# 十七、结构改动原则

只有 CSS 无法完成时才动 `app.js`。

允许做：

```text
新增展示 wrapper
新增 class
调整已有展示 DOM 顺序
把已有真实字段换一个展示位置
给现有按钮补 display span
```

禁止做：

```text
改变 data-action
改变 data-nav 的业务语义
修改 API 请求
修改 state
伪造数组
伪造 rooms
伪造 wins
伪造 user
伪造 rank
伪造 replay
```

---

# 十八、重点选择器清单

m9 实现时优先围绕现有 selector，不要另造一套完全平行的页面。

## 对局

```css
.mobileLobby
.mobileLobby--strict
.mobileLobbyLead
.mobileLobbyLead .mobileEyebrow
.mobileLobbyLead h2
.mobileLobbyPrimaryActions
.mobilePrimaryAction
.mobilePrimaryIcon
.mobileActionTexts
.mobileActionArrow
.mobileLobbyCta
.mobileCtaIcon
.mobileRoomComposer
.mobileCreateRoomBtn
.mobileComposerIcon
.mobilePieceDeco
.mobileJoinRow
.mobileRoomDisclosure
.mobileDisclosureIcon
```

现有 selector index 已确认 `.mobileLobbyLead` 及其相关 rule 在 CSS 中多处存在，因此修改时必须注意 cascade，而不是假设只有 m8 一处规则。 

---

## 棋谱

```css
.mobileContentPage--learn
.learnPage
.learnPage > .hero
.heroLeft
.learnEndgameCta
.searchBar--learn
.learnTabs
.learnTabs .pill
.learnMainContent
.learnCard
.learnCardBadge
.learnCardLeft
.learnCardInfo
.learnAction
```

---

## 观战

```css
.mobileContentPage--watch
.watchHero
.watchHero::after
.watchEyebrow
.watchLiveDot
.watchLiveCount
.watchStage
.watchToolbar
.watchFilters
.watchRefresh
.watchMatchCard
.watchMatchCard::before
.watchCardTop
.watchGameSeal
.watchGameTag
.watchNow
.watchPlayers
.watchPlayer
.watchAvatar
.watchPlayerInfo
.watchVs
.watchCardFoot
.watchReplayStage
.watchReplayCard
```

---

## 我的

```css
.mobileContentPage--me
.profilePage
.profileSidebar
.profileSidebarItem
.profileSidebarIcon
.profileSidebarTexts
.profileSidebarChevron
.profileHeaderCard
.profileUserRow
.profileSealAvatar
.profileUserMeta
.profileSlogan
.profileEditBtn
.profileStatsGrid
.statBox
.profileSecondaryNav
.profileSecondaryItem
.profileSecIcon
.profileSecTexts
```

---

## 共用

```css
.mobileContentHeader
.mobileContentBody
.mobileBottomNav
.mobileBottomNav--strict
.mobileBottomNav a
.mobileBottomNav a .mobileIcon
.mobileBottomNav a.is-active
```

---

# 十九、字体与字号规范

不要给每页任意一套数字。

## 主标题

```text
22–26px
Noto Serif SC / Songti SC / serif
700
#231C16
```

## 卡片主标题

```text
15–18px
600–700
```

## 小 eyebrow

```text
10–11px
朱砂
700
letter-spacing 0.10–0.16em
```

## 正文

```text
11–13px
#7E7365 / #5A4738
```

## 数字统计

```text
20–24px
朱砂
serif
```

---

# 二十、间距基线

建议尽量收敛到一组有限 spacing：

```text
4
6
8
10
12
14
16
18
20
24
```

避免出现大量：

```text
13px
17px
21px
27px
```

这种无系统的补丁值。

---

# 二十一、圆角规范

建议：

```text
输入框 / 小按钮：10–12px
菜单 item：12–14px
普通内容卡：16–18px
Hero / 主资料卡：18–20px
filter：999px
头像 / seal：50%
```

不要所有东西统一 20px，否则会变成“气泡 UI”。

---

# 二十二、颜色层级

## 朱砂主动作

```text
#8C2E21
#A43224
```

用在：

* 当前 Tab；
* 快速匹配；
* 残局挑战；
* 当前 filter；
* 实时观战；
* active menu seal；
* 统计数字。

## 不要用朱砂的地方

不要把：

```text
所有标题
所有 icon
所有边框
所有文字
所有卡片背景
```

全部染红。

A 的关键是克制。

---

# 二十三、不要再做的事情

以下做法 m9 明确禁止：

```text
1. 再把 m8 全部 CSS 原样复制一份，只改少量数值
2. 因为 RESULT 写“已闭合”就不看截图
3. 用大量 !important 掩盖真实 cascade 问题
4. 为了 A 的头像去新增写真文件
5. 为了棋盘静物去嵌入假摄影
6. 为了丰富观战而造两个真人
7. 把真实房间数改成参考图里的固定数字
8. 把真实用户名改成参考图名字
9. 把我的真实 0% 改成好看的百分比
10. 新增 VIP / Pro / 会员按钮
11. 给页面塞广告
12. 删除首页 Tab
13. 把首页整页纳入 m9 重构
14. 修改 WS / AI / 规则 / 匹配
15. push
16. 部署
```

---

# 二十四、M9 验收清单

完成后必须用 **375×812 左右同一 viewport** 重新实拍：

```text
shots-m9/
```

至少输出：

```text
B-01-home.png
B-02-play.png
B-03-learn.png
B-04-watch.png
B-05-me.png
```

也可以命名为：

```text
01-home.png
02-play.png
03-learn.png
04-watch.png
05-me.png
```

但 RESULT 中要写清映射。

---

## A. 共用

* [ ] 四页背景统一为暖宣纸，不发灰、不纯白。
* [ ] 四页顶部都有可感知但克制的水墨淡洗。
* [ ] 氛围 opacity 不超过 `0.18`。
* [ ] 页头高度明显比 m8 更接近 A。
* [ ] 左返回圆钮尺寸统一。
* [ ] eyebrow 朱砂统一。
* [ ] 主标题字体与字号统一。
* [ ] 右上入口不出现突兀现代 UI。
* [ ] 内容不再因 `.mobileContentBody` + 子卡 margin 双重 gutter 而偏窄。
* [ ] 底栏五项不变。
* [ ] 每页 active Tab 正确。
* [ ] 底栏不遮挡正文。
* [ ] B-01 首页未被共用 CSS 误伤。

---

## B. 对局

* [ ] `.mobileLobby--strict` 顶部国风氛围明显改善。
* [ ] `.mobileLobbyLead` 宽度接近 A。
* [ ] 首主卡整体高度更接近 A。
* [ ] “常用入口”与“三步之内，直接开局”层级正确。
* [ ] 快速匹配 CTA 更高、更饱满。
* [ ] 闪电圆 icon 清晰可见。
* [ ] 人机练习为次级纸卡。
* [ ] 好友房与房间码是一个完整视觉模块。
* [ ] 右侧“帥”是 CSS 印章，不是图片。
* [ ] input / 加入按钮比例接近 A。
* [ ] 公开候场整体往下回到更接近 A 的位置。
* [ ] 不修改匹配/房间业务。

---

## C. 棋谱

* [ ] Hero / filters / `.learnCard` 不再明显窄于 A。
* [ ] 页面 gutter 收敛为约 `12–14px`。
* [ ] Hero 高度、标题、说明、CTA、搜索的节奏更接近 A。
* [ ] `进入残局挑战` 仍使用真实 route。
* [ ] 搜索功能不变。
* [ ] filter 可横向滚动。
* [ ] active filter 为朱砂。
* [ ] 第一张 `.learnCard` 视觉高度更接近 A。
* [ ] 左侧 seal badge 更有“开 / 棋 / 中”的印章感。
* [ ] 标题 / 元信息 / 摘要层级清楚。
* [ ] 标记完成 / 查看详情主次正确。
* [ ] CSS「帥/將」不会覆盖正文。
* [ ] 不嵌入假棋具摄影。

---

## D. 观战

* [ ] Hero 宽度与高度接近 A。
* [ ] “觀”不再是唯一右侧视觉装饰。
* [ ] 可以用 CSS 棋盘线 / seal 增强，但无假摄影。
* [ ] 直播数量仍来自真实 rooms。
* [ ] filters 与刷新保持一行优先。
* [ ] `.watchMatchCard` 不再明显偏窄。
* [ ] `.watchCardTop` / `.watchPlayers` 不被 `230px` 类死宽度过度压缩。
* [ ] 两个玩家都仍来自真实数据。
* [ ] 用户头像继续使用首字圆徽标。
* [ ] 直播 CTA 在右下层级清楚。
* [ ] 回放区域仍使用真实 replay 数据。
* [ ] 不造第二真人。
* [ ] 不造直播数字。

---

## E. 我的

* [ ] 菜单网格整体宽度接近 A。
* [ ] 保持 2 列。
* [ ] 个人信息 active 使用柔朱砂。
* [ ] seal icon 明显但不花哨。
* [ ] 菜单文字不因卡片过窄而频繁断成三四行。
* [ ] `.profileHeaderCard` 与上方网格同宽。
* [ ] seal avatar 约 60px+，视觉锚点足够。
* [ ] 不引入 A 中古典写真头像。
* [ ] username / ID / 棋友 / slogan / 编辑资料排布不拥挤。
* [ ] 4 项统计横排。
* [ ] 统计真实值不被篡改。
* [ ] 二级入口统一为纸卡列表。
* [ ] 不虚构会员、段位、付费。

---

# 二十五、截图对比方法

不要只凭“感觉差不多”。

每页建议同时开：

```text
A
B-m8
B-m9
```

按以下顺序看：

```text
1. 页面整体版心宽度
2. 页头高度
3. 首卡顶部位置
4. 首卡宽度
5. 首卡高度
6. 页面首屏内容数量
7. 主色比例
8. 字体大小
9. 具体装饰
```

如果：

```text
B-m9 比 B-m8 更“精致”
但整体版心 / 卡片位置仍和 A 差很多
```

则不能判定 Strict Align 完成。

---

# 二十六、m9 RESULT 必须包含

最终执行完成后输出：

```text
RESULT-m9.md
```

至少包含以下内容。

## 1. 修改范围

明确：

```text
mobile.css
必要时 app.js 展示壳
index version bump
```

以及哪些文件没有动。

---

## 2. 四页逐页改了什么

严格按：

```text
对局
棋谱
观战
我的
```

分别说明：

```text
m8 的仍存差距
m9 实际修改
m9 验收结果
```

禁止把 m8 的“已完成事项”直接复制成 m9 修改记录。

---

## 3. 共用改动

写清：

```text
页面版心
页头
顶部淡洗
底栏
tokens
```

---

## 4. 诚实局限

必须写本 PROMPT 第十五章的核心口径。

尤其明确：

```text
CSS 氛围淡洗 ≠ 摄影级山水
首字朱砂印 ≠ 汉服写真
CSS 帥/將 ≠ 真实棋具静物
首字头像 ≠ 真人照片
```

---

## 5. 业务真实性

确认：

```text
未修改 WS
未修改走棋
未修改胜负
未修改匹配
未修改棋规
未修改 AI
未造第二真人
未造段位
未造会员
未造付费
未造广告
```

---

## 6. 首页回归

明确写：

```text
首页仅作为底栏 / 共用规范回归对象
未整页重做首页
```

---

## 7. 截图

列出：

```text
shots-m9/01-home.png
shots-m9/02-play.png
shots-m9/03-learn.png
shots-m9/04-watch.png
shots-m9/05-me.png
```

或实际对应文件名。

---

# 二十七、最终交付物

本轮最终应得到：

```text
1. m9 展示层修改
2. ?v=20260924m9
3. shots-m9/
   - 首页回归图
   - 对局
   - 棋谱
   - 观战
   - 我的
4. RESULT-m9.md
```

不要：

```text
push
deploy
生成新 A 图
伪造素材
改业务逻辑
```

---

# 二十八、最终执行口令

请严格执行以下原则：

> **不要把 m8 文档中的“已闭合”当作 m9 的结论。先重新看 A-02…A-05 与 B-02…B-05 的实拍差异，再修改。**

本轮最重要的不是继续增加更多国风装饰，而是优先闭合仍明显存在的：

```text
版心过窄
页头过短
首卡比例不足
纵向节奏过紧
卡片宽高不一致
CTA 视觉层级不足
右侧装饰与正文空间冲突
四页与首页共用底栏尺寸不稳定
```

执行顺序保持：

```text
P0 宽度 / gutter
→ P1 页头 / 首屏节奏
→ P2 主卡尺寸
→ P3 CTA / filter / 信息层级
→ P4 水墨 / seal / 棋类装饰
→ 375×812 实拍
→ A/B/m9 三方复核
→ RESULT-m9
```

**以 A 图视觉比例为目标，以真实业务数据为边界，以 B-m9 实拍为唯一验收依据。**
