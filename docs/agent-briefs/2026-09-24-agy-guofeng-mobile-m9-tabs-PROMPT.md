# agy PROMPT｜轻棋局 m8 → m9「m9-tabs Strict Align」

## 0. 任务与唯一视觉真源

请仅按本 PROMPT 做 m8→m9 的四个移动端 Tab 视觉对齐与验收，不在本步骤修改业务代码、协议或棋规。

- **A｜唯一视觉真源（本轮 ChatGPT true image gen）**
  - `chatgpt-m9-tabs-refs/out/REF-02-play.png`
  - `chatgpt-m9-tabs-refs/out/REF-03-learn.png`
  - `chatgpt-m9-tabs-refs/out/REF-04-watch.png`
  - `chatgpt-m9-tabs-refs/out/REF-05-me.png`
  - 四图均为 ChatGPT 网页生图工具生成的真实参考图，**不是 HTML/CSS 拼图、马赛克或截图拼贴**。
- **B｜m8 对照（磁盘真路径）**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/shots-m8/`
  - `02-play.png` / `03-learn.png` / `04-watch.png` / `05-me.png`（可选回归：`01-home.png`）
  - 镜像：`/workspace/Chinese-chess/shots-m8/`
- **A 完整路径前缀**：`/workspace/voonie-align/xiangqi-guofeng-mobile-2026-09-24/chatgpt-m9-tabs-refs/out/`
- 静态资源建议 bump：`?v=20260924m9`（`index.html` 四处一致）。
- 主战场：`src/main/resources/online/mobile.css` + 必要时 `app.js` 展示壳；**本文件只出提示词口径，执行改码须用户/总调度另行授权**。

A 决定 m9 的视觉事实；B 只用于识别 m8→m9 的差异。不要从 B 反向覆盖 A。

## 1. 共同 Strict Align 规则

### 1.0 工程选择器映射（优先沿用 m8，勿另造平行页面）

| 页面 | 根/壳 | 关键节点 |
|------|-------|----------|
| 对局 | `.mobileLobby--strict` / `renderMobileLobby` | `.mobileLobbyLead`、快速匹配 `data-action="quick-start-public-match"`、人机 `quick-start-ai-practice`、好友房 `create-room-xiangqi`、加入 `join-by-code`、公开候场 |
| 棋谱 | `renderLearnPage` / `.mobileContentPage` | Hero、残局 CTA `data-nav` 现有路由、`.learnCard`、filter pills |
| 观战 | `renderWatchRooms` | `.watchHero`、`.watchMatchCard`、filter pills、刷新 |
| 我的 | `renderProfile` | 双列菜单、首字印头像、战绩四格、二级入口 |
| 共用 | `renderMobileBottomNav` | 底栏 5 项；页头共用规范 |

若 m8 块 `/* === 20260924m8 tabs Strict Align === */` 已写但实拍未呈现，**先查覆盖链/特异性**，再追加 `/* === 20260924m9 tabs Strict Align === */`。

- 视口以 **375×812** 为基准；内容列左右约 **14–16px**，不得退化成窄条网页。
- 背景为暖宣纸 `#F7F1E7`；主行动朱砂 `#9D3023`；炭灰为正文/次级文字；细线约 `#D9CFBF`；少量竹青点缀。
- 页头要更高、更舒展、更有空气感；可用极淡 CSS 山水/竹/朝阳洗色逼近 A，但不盖住文字与控件。
- 底栏固定 5 项，当前 Tab 使用朱砂状态，其他项克制、低对比。
- 页面级容器、卡片、按钮和底栏要保持同一纸张/木牌/印章语汇；宽度在同一层级内一致。
- 所有中文文案必须保留语义与可读性；不得以视觉调整改变交互含义、数据含义或路由。

## 2. Play｜对局（A: REF-02-play 对 B: shots-m8/02）

### 可执行改法

1. **页面/选择器定位**：以 `[data-tab="play"]`、`main[aria-label="对局"]` 或现有 Play 页面根节点为范围；不依赖脆弱的第 N 个 `div`。根节点使用暖宣纸背景，安全区内边距 14–16px。
2. **页头结构**：在 `.page-header`/语义页头内保留眉题「对局」与标题「开始一盘棋」，提高上下留白；加入低透明度山水/竹/朝阳装饰层，装饰 `aria-hidden="true"` 且不拦截点击。
3. **常用入口卡**：`.quick-entry-card` 为宽纸卡，标题「常用入口」，副句「三步之内，直接开局」。卡内 CTA 纵向间距舒展，不做窄横条。
4. **行动组**：主按钮 `[data-action="quick-match"]` 文案「快速匹配」，朱砂木牌质感、细金色内环、闪电印章小图形；次按钮「人机练习」为纸底朱砂描边。
5. **好友房**：`.friend-room-row` 采用木色纸牌行，左侧为风格化「帥」印章；房间码输入为纸张质感，加入按钮 `[data-action="join-room"]` 文案「加入」，朱砂实底。保留已有输入、校验与加入逻辑。
6. **公开候场**：`.public-waiting-row` 与主卡同宽，文案「公开候场」，可用淡墨小船/山水作为装饰，不添加虚构数据或 VIP 标识。

### 验收

- 页头高而不挤压首屏；主卡宽、四周留白与 A 同类；快速匹配最强但不霓虹；人机、好友房、公开候场均可见。
- Play 底栏只有「对局」为朱砂 active；点击/输入/加入行为与 m8 相同。

## 3. Learn｜棋谱（A: REF-03-learn 对 B: shots-m8/03）

### 可执行改法

1. **定位**：以 `[data-tab="learn"]`、`main[aria-label="棋谱"]`、`.learn-page` 为范围；将主内容宽度设为同一 content column，不使用固定窄宽。
2. **页头与 Hero**：舒展页头后放宽纸 Hero；badge「练习」、标题「棋谱库」、免费说明；主 CTA `[data-action="endgame-challenge"]` 全宽朱砂「进入残局挑战」；其下放轻量搜索胶囊，不制造会员墙。
3. **筛选**：`.learn-filters` 使用胶囊间距；「全部」为朱砂实底 active，「象棋」「五子棋」及其他选项为纸底细线。保持键盘/触控状态与现有筛选逻辑。
4. **教程卡**：`.tutorial-card` 宽度跟随内容列；左侧用圆形单字印/木色「帥」「將」绘饰，右侧标题摘要；底部双按钮「标记完成」「查看详情」，按钮不挤成窄条。
5. **视觉洗色**：淡山水只作为背景层；卡片使用宣纸/细线/低阴影，避免玻璃、霓虹和广告位。

### 验收

- 内容列明显宽而慷慨；Hero、搜索、筛选、教程卡不出现 B 中的 skinny centered strip。
- 「全部」active 明确；「进入残局挑战」在首屏层级突出；无会员付费墙、VIP 徽章或虚构教学数据。

## 4. Watch｜观战（A: REF-04-watch 对 B: shots-m8/04）

### 可执行改法

1. **定位**：以 `[data-tab="watch"]`、`main[aria-label="观战"]`、`.watch-page` 为范围；卡片和 Hero 共享宽 content column。
2. **Hero**：`.watch-hero` 为更高的纸 Hero，标题「观棋台」；加入极淡大字水印「觀」(`aria-hidden="true"`) 与真实 live count pill。水印不可伪装为用户数据。
3. **筛选**：`.watch-filters` 胶囊行，当前筛选朱砂实底；不要增加不存在的排行、段位或 VIP 入口。
4. **对局卡**：`.live-match-card` 做得更高更宽，保留稀疏、真实可解释的数据。每卡包含象/五游戏类型印章、首字圆印头像、`VS`、木色印章点缀与实底朱砂 `[data-action="watch-live"]`「实时观战」按钮。头像只能是单字印章/风格化 glyph，**不是照片肖像**。
5. **数据边界**：仅渲染现有 live 对局字段；不为填满卡片虚构排名、战绩、在线人数或真人身份。

### 验收

- Hero 有观棋台与觀水印；live count 清晰但克制；对局卡比 m8 更有层次且宽；当前观战底栏为朱砂。
- 两三张稀疏 live 卡即可，按钮、印章、VS、首字头像在 375×812 下可读。

## 5. Me｜我的（A: REF-05-me 对 B: shots-m8/05）

### 可执行改法

1. **定位**：以 `[data-tab="me"]`、`main[aria-label="我的"]`、`.profile-page` 为范围；根节点与其余 Tab 共用背景和宽 content column。
2. **资料卡**：`.profile-card` 使用更宽纸卡；头像为朱砂圆印首字/initial glyph，明确是风格化印章而非真人照片；显示用户名、badge「棋友」、slogan「落子之间，自有风雅。」与描边「编辑资料」按钮。编辑行为保持原有权限与流程。
3. **统计**：`.profile-stats` 四格等宽，数值使用朱砂，标签炭灰；不要增加不可验证的统计。
4. **双列菜单**：`.profile-menu-grid` 两列，图标为单字印章感 glyph；当前项使用柔和朱砂洗色，其余纸底。保持现有语义导航与可点击区域。
5. **次级列表**：`.profile-secondary-list` 使用纸质列表卡、细线、少量淡墨；保留真实「对局统计」「我喜欢的棋谱」「最近对局」等已有入口，不添加 VIP/会员/广告。

### 验收

- 资料卡、四格统计、双列印章菜单、次级纸质列表都能在 375×812 中顺畅浏览；「我的」底栏朱砂 active。
- 头像是印章首字，不被当作写真/第二真人；菜单 active 仅是视觉状态，不改变权限。

## 6. 诚实局限与禁止事项（硬边界）

- **CSS ≠ 摄影/插画**：A 图中的山水、扁舟、围棋静物、棋子特写等是 **ChatGPT 生图装饰**。实现侧允许 CSS 淡洗（建议 opacity ≤0.18）、渐变、纹理、纯 CSS「帥」「將」木印逼近；**禁止宣称**已换成摄影级插画或已嵌入真实棋具静物文件。
- **首字印 ≠ 写真**：A「我的」等处若出现风格化头像，实现只用朱砂圆印首字/initial；**禁止**引入汉服写真素材冒充对齐。
- **观战卡数据**：A 可能画出业余段位/多名对局——实现只渲染**真实字段**；禁止为像 A 而虚构段位、第二真人身份或 VIP。
- 不造第二 fake human，不虚构 VIP、会员墙、广告、充值入口或排名。
- 不改 WebSocket（WS）、服务端数据契约、规则、棋规、对局协议、匹配语义和权限语义。
- 不用视觉改动掩盖空数据、错误状态或不可用交互；没有真实字段就保持留白/稀疏。
- 本文件只给 **agy/手改与验收**；此步骤不改码、不生成 HTML/CSS 拼图、不 push、不部署。

## 7. 全局验收清单

- [ ] A 四图路径与 B 四张 m8 对照路径均准确，访问建议带 `?v=20260924m9`。
- [ ] 四个 Tab 在 375×812 下逐一与 A 对照，内容列约 14–16px 边距，未变成窄条。
- [ ] 宣纸 `#F7F1E7`、朱砂 `#9D3023`、炭灰、细线 `#D9CFBF`、竹青与纸/木/印章语汇一致。
- [ ] 页头高、舒展，淡山水/竹/朝阳仅作装饰；文字、按钮、焦点与触控不被遮挡。
- [ ] 底栏始终 5 项，当前 Tab active 朱砂，切换与原有路由/状态一致。
- [ ] 无 HTML/CSS mosaic 冒充 A；无第二真人、VIP、广告或虚构数据。
- [ ] WS、棋规、规则、对局协议、权限、现有业务文案和交互语义未被视觉任务改写。
- [ ] 本轮只做手改/验收说明；不改 Chinese-chess 业务代码，不 push/deploy。

## 8. 交付口径

完成后请以 A 为唯一视觉真源逐 Tab 截图验收，记录视口、路径版本与未完成项；任何无法由现有数据支持的内容宁可留白，不得编造。

## 9. 真源会话与生成说明

- ChatGPT 会话：https://chatgpt.com/c/6ab53ef2-5bf4-83e8-9a94-420be878f98a
- 四帧均为该会话内 **Create image** 真生图；原图见 `chatgpt-m9-tabs-refs/out/_raw/`，交付图 Lanczos → 375×812。
- NOTES：`chatgpt-m9-tabs-refs/out/NOTES.txt`
- 本 PROMPT 正文在生图完成后定稿（会话内文本框曾卡住，口径与禁区已按本轮 A 图与 m8 delta 差距对齐）；**图为 ChatGPT 真生，未用 HTML 拼图**。
