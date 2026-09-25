# agy PROMPT｜轻棋局 m10「四 Tab Strict Align」

## 0. 任务、范围与唯一视觉真源

请仅按本 PROMPT 做轻棋局移动端四个 Tab 的视觉 Strict Align 与验收说明；本步不修改 Chinese-chess 应用代码、协议、WS、棋规或数据契约，不 push、不 deploy。

### A｜m10 唯一视觉真源（ChatGPT true image generation）

以下四图均由已登录 ChatGPT 网页 **Create image / 真实图像生成工具**直接生成，再保存原图并 Lanczos 缩放至 375×812 PNG；不是 HTML/CSS 拼图、不是马赛克、不是截图拼贴：

- `chatgpt-m10-tabs-refs/out/REF-02-play.png`
- `chatgpt-m10-tabs-refs/out/REF-03-learn.png`
- `chatgpt-m10-tabs-refs/out/REF-04-watch.png`
- `chatgpt-m10-tabs-refs/out/REF-05-me.png`

原图在 `chatgpt-m10-tabs-refs/out/_raw/`。A 是本轮 m10 的唯一视觉事实；实现侧必须逐页一对一按图，不得用空泛「国风」替代具体结构。

### B｜m9 当前实拍对照

B 只用于保留真实结构、数据与交互，不得反向覆盖 A：

- `shots-m9/02-play.png`
- `shots-m9/03-learn.png`
- `shots-m9/04-watch.png`
- `shots-m9/05-me.png`
- `shots-m9/01-home.png` 仅作首页回归，不属于本轮四 Tab 真源。

建议静态资源版本：`?v=20260924m10`（相关入口处保持一致）。

真源会话：
- Play/Learn/Watch：<https://chatgpt.com/c/6ab54c7f-97e4-83e8-abda-1bdee247bf8e>
- Me 补帧：<https://chatgpt.com/c/6ab551a8-c924-83e9-8153-b4da7fcd8ce8>

## 1. 全局 Strict Align 规则

- 基准视口严格为 **375×812**；内容列左右约 **14–16px**，卡、CTA、pill、底栏在同一内容宽度内对齐。
- 暖宣纸背景 `#F7F1E7`；主朱砂 `#9D3023`；炭灰正文；细线 `#D9CFBF`；少量竹青/木色点缀。
- 页头有明确层级、充足上下留白，以及可见但克制的水墨山水/竹/朝阳氛围层；装饰不得覆盖文字、按钮或焦点。
- 纸卡、木牌 CTA、印章圆、pill active、阴影和细线要按 A 的大小/间距/层级复现，不改成窄条网页或海报。
- 底栏固定 5 项，项目与顺序沿用 B；当前 Tab 朱砂 active，其余炭灰/低对比；不得遮挡内容。
- 中文文案按 A 的位置和层级实现；不可读或不存在的业务数据宁可留白。

### 工程选择器（真实映射，勿另造平行页面）

| 页面 | 根/渲染函数 | 关键节点 |
|---|---|---|
| 对局 | `.mobileLobby--strict` / `renderMobileLobby` | `.mobileLobbyLead`、快速匹配 CTA、`quick-start-public-match`、人机练习、`create-room-xiangqi`、`join-by-code`、公开候场 |
| 棋谱 | `renderLearnPage` / `.mobileContentPage` | Hero、残局 CTA、`.learnCard`、search pill、filter pills |
| 观战 | `renderWatchRooms` | `.watchHero`、`.watchMatchCard`、filter pills、刷新 |
| 我的 | `renderProfile` | profile card、4-stat row、双列菜单、secondary list、首字印头像 |
| 共用 | `renderMobileBottomNav` | 固定底栏 5 项与 active 状态 |

追加或替换样式时，使用明确标记：

```css
/* === 20260924m10 tabs Strict Align === */
```

本文件仅给 agy/手改与验收口径；**本步不改码**。

## 2. REF-02｜对局 / Play

对照 A `chatgpt-m10-tabs-refs/out/REF-02-play.png` 与 B `shots-m9/02-play.png`。在 `.mobileLobby--strict` / `renderMobileLobby` 内保持 B 的真实入口结构，按 A 对齐：

1. 页头先显示眉题「对局」与标题「开始一盘棋」，页头更高、更舒展；背景为克制水墨山水/竹/朝阳。
2. 内容列保持约 14–16px 两侧边距；「常用入口」纸卡与「三步之内，直接开局」层级清楚，卡宽不要变窄。
3. 「快速匹配」是最强 CTA：朱砂 `#9D3023` 木牌、细金色内环/金线、闪电圆印；文案位置、按钮高度、圆角与 A 对齐。保留真实 `quick-start-public-match` 行为。
4. 其后按图排列「人机练习」「创建好友房」「房间码加入」「公开候场」；好友房有风格化「帥」印章，房间码是纸质输入/加入控件，候场可有低透明墨意装饰，但不填虚构数据。
5. 底栏 5 项，只有「对局」朱砂 active。

验收：页头不挤压首屏，快速匹配/人机/好友房/房间码/候场均可见；行为与 B 相同。

## 3. REF-03｜棋谱 / Learn

对照 A `chatgpt-m10-tabs-refs/out/REF-03-learn.png` 与 B `shots-m9/03-learn.png`，使用 `renderLearnPage` 与 `.learnCard`：

1. 保持 B 已修好的宽内容列、约 14–16px 边距和 A 的页头空气感。
2. Hero 标题「棋谱库」与练习 badge 在明确层级内；紧接全宽朱砂 CTA「进入残局挑战」，再放搜索 pill。
3. filter pills 横向间距按图；「全部」为实心朱砂 active，其他筛选为纸底细线。
4. 教程 `.learnCard` 逐卡宽度一致；卡内为圆形单字印/木质「帥」「將」装饰、标题摘要与双按钮，避免窄条或会员墙。
5. 当前底栏「棋谱」朱砂 active，其余克制；不改变现有筛选/路由语义。

## 4. REF-04｜观战 / Watch

对照 A `chatgpt-m10-tabs-refs/out/REF-04-watch.png` 与 B `shots-m9/04-watch.png`，使用 `.watchHero`、`.watchMatchCard` 与 `renderWatchRooms`：

1. `.watchHero` 高度、内边距、背景洗色按 A；标题「观棋台」，加入极淡「觀」水印与真实 live count pill。
2. active filter 为实心朱砂，其余 pill 纸底；保持刷新控件真实存在。
3. `.watchMatchCard` 使用 A 的卡宽、高度、左右间距和垂直节奏；每卡仅使用真实可解释字段。
4. 卡内有「象」/「五」类型印章、**首字印圆形头像**、`VS`、木质印章点缀、实心「实时观战」按钮。头像不是照片，不造第二真人。
5. 不发明业余段位、VIP、广告、虚构排名/战绩/身份；没有真实字段就保持稀疏。
6. 当前底栏「观战」朱砂 active。

## 5. REF-05｜我的 / Profile

对照 A `chatgpt-m10-tabs-refs/out/REF-05-me.png` 与 B `shots-m9/05-me.png`，使用 `renderProfile`：

1. 顺序必须严格为：profile card → 2 列印章菜单 → secondary list；不要把次级入口提前。
2. profile card 宽度、内边距和层级按 A；头像是朱砂印章圆形首字 glyph，绝不使用汉服照片/真人肖像；显示「棋友」badge、slogan「落子之间，自有风雅。」与真实 4-stat row。
3. 2 列菜单的 seal-stamp icons、激活洗色、列间距和卡高按 A；保持真实导航语义。
4. secondary list 为纸张质感、细线分隔、低对比墨意；只保留 B 中真实的入口和可验证字段。
5. 当前底栏「我的」朱砂 active，其余克制，保持固定 5 项。

## 6. 诚实局限表（必须保留）

| 事实 | 实现允许 | 禁止宣称/禁止做法 |
|---|---|---|
| **CSS ≠ 摄影/插画** | CSS 淡洗、渐变、纹理、`opacity ≤ 0.18` 的山水/竹/朝阳氛围，按 A 的可实现布局对齐 | 不得宣称 CSS 已变成摄影级水墨插画；不得偷塞未经授权的摄影资源 |
| **首字印 ≠ 写真** | 用朱砂圆印、首字/initial glyph 复现头像占位 | 不得引入汉服写真、真人肖像或第二 fake human 冒充对齐 |
| **观战无虚构段位** | 只显示真实用户名/真实 live 字段；稀疏留白是合规结果 | 不得虚构业余 N 级、排名、战绩、第二真人身份或 VIP 来填卡 |
| 棋具/扁舟装饰 | 用 CSS 形状、印章 glyph、低透明墨意逼近 A 的层级 | 不得宣称已嵌入真实棋具静物/摄影级扁舟；不得让装饰承担业务数据 |

核心口径：**允许逼近，不得宣称。**

## 7. 硬禁区与验收

- 不造第二 fake human；不虚构 VIP、会员墙、广告、充值入口。
- 不改 WS、棋规、规则、对局协议、匹配语义、权限语义、服务端契约或真实数据。
- 01-home 只做回归；不借本步整页重做首页。
- 不以 CSS/HTML mosaic 冒充 A；不把 A 的图片当代码实现结果。
- 本步骤只产出 agy/手改说明与验收；**本步不改码、不 push、不 deploy**。
- 验收四帧：375×812；内容列 14–16px；页头层级、卡宽/边距、CTA 朱砂木牌与金线、印章圆、pill active、底栏 5 项与 active 色均逐页对照 A。

## 8. 交付清单

- A 四图：`chatgpt-m10-tabs-refs/out/REF-02-play.png` … `REF-05-me.png`。
- B 四图：`shots-m9/02-play.png` … `05-me.png`；`shots-m9/01-home.png` 仅回归。
- 版本建议：`?v=20260924m10`。
- 真实选择器：`.mobileLobby--strict`, `renderMobileLobby`, `renderLearnPage`, `.learnCard`, `.watchHero`, `.watchMatchCard`, `renderProfile`, `renderMobileBottomNav`。
- 标记：`/* === 20260924m10 tabs Strict Align === */`。
- 图像生成确认见 `out/NOTES.txt` 与 `SESSION.txt`。
