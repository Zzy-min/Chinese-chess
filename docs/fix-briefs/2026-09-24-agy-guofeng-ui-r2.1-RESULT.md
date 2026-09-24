# 国风 R2.1 · 左栏扁舟/棋篓可见性修复报告（RESULT）

- **日期**：2026-09-24（Asia/Shanghai）
- **项目**：轻棋局 · `Chinese-chess`（Web 前端：`src/main/resources/online/`）
- **依据 Brief**：国风 R2.1 · 只修左栏扁舟/棋篓可见性
- **验收依据**：`/workspace/voonie-align/xiangqi-guofeng-2026-09-24-r2/ACCEPTANCE.md`（项 1 ❌ 复核闭合）
- **真源对照**：`docs/agent-briefs/2026-09-20-guofeng-ui-reference.png`（屏 3 象棋对局 & 屏 4 五子棋对局）
- **同机位实测参照**：`shots/03-xiangqi-game.png`、`shots/03-xiangqi-board-close.png`、`shots/04-gomoku-game.png`
- **交付约束**：已本地落盘，**明确未 git push，未部署，未本机同步，未停 PublicWebMain 进程**。

---

## 一、已改文件清单

改动严格限定在左栏氛围插画可见性与对应容器布局契约，禁止重开宣纸侧栏、认输、木纹及业务逻辑：

| 文件路径 | 改动性质 | 核心改动说明 |
| :--- | :--- | :--- |
| `src/main/resources/online/app.js` | 氛围插画渲染与轮廓强化 | 1. 重画/加浓 `renderBoardAtmosphereSvg(gameType)`：<br>   - 象棋：重构「水墨一叶扁舟」，船身弧线加厚，主体 fill 采用炭木色 `#3E3428`（`opacity="0.95"`）+ 焦墨描边 `#221C16`（`stroke-width="1.3"`）；中间配备拱顶船篷（`#524434`）与细致蔑席骨线，船尾立一笠翁（`#2B2117`）与斜插水面的竹篙（`#1E1710`），水波微澜墨线加深（`stroke-opacity 0.6~0.75`），辅以水鸟与远山淡影；<br>   - 五子棋：重构「水墨棋篓与雅竹」，左侧以苍劲竹节与浓密墨竹叶（`#24372A` / `#314838`，`fill-opacity 0.9~0.95`）向上舒展，右侧配备深古朴陶质棋罐（`#5C4A38`，`fill-opacity="0.95"`），罐口透出黑白棋子，台面上散落高光黑白双子与温润投影；<br>2. 保持 `pointer-events: none` 与 `aria-label` 声明，对局与练习双路径完全生效。 |
| `src/main/resources/online/app.css` | 棋桌行高拉满与左栏沉底布局 | 1. **纵向拉满**：桌面端 `.boardDesk .boardRail` 声明 `align-self: stretch !important; height: 100% !important; min-height: 100% !important; box-sizing: border-box !important; overflow: visible !important;`，纵向完全拉满棋桌行高（与中栏 `.boardPane` 严格同高）；<br>2. **氛围插画容器沉底**：`.boardRailAtmosphere` 声明 `margin-top: auto !important; min-height: 96px !important; flex-shrink: 0 !important; overflow: visible !important; display: flex !important; align-items: flex-end !important; justify-content: center !important; pointer-events: none !important;`，杜绝任何容器裁切；<br>3. **左栏卡片适度紧致化**：微调桌面端左栏上方玩家卡（`.clockCard` / `.boardPlayerCard`）与说明信息（`.boardRailNote`）的内边距与字体排印节奏，使静态组件占用高度由原先 570px+ 收敛至 ~310px，为左栏底部腾出 ~190px+ 宽裕纵向空间，确保即便在 479px 紧凑首屏与同机位截图下，插画也完整可见且自然沉底；<br>4. **移动端保全**：在 `@media (max-width: 768px)` 保持 `.boardRailAtmosphere { display: none !important; }` 与 `.boardDesk .boardRail { height: auto !important; align-self: auto !important; }`，移动端体验完全不受影响。 |
| `src/main/resources/online/index.html` | 静态缓存版本号升级 | 将所有资源引入版本号（`app.css`、`mobile.css`、`board.js`、`app.js`）一致升级至 `?v=20260924r21`。 |
| `target/classes/online/*` | 运行时热更新（非停服务） | 同步覆盖 `app.css`、`app.js`、`index.html` 到 `target/classes/online/`，常驻 PublicWebMain 进程（listen 18388）即时生效最新资源。 |

---

## 二、根因定位与对应改法

### 1. 布局层（Layout）
- **根因**：
  1. 原 CSS 中 `.site.is-board-route .boardRail` 声明了 `align-self: start; height: auto; justify-content: flex-start;`，左栏高度完全由内容撑开，不随棋桌拉高；
  2. 左栏内上方玩家卡与状态信息原高度达 570px+（玩家卡单张高度达 178px），已超越对局桌中栏在常见视口下的行高（479px～576px），导致 `.boardRailAtmosphere` 被推至 600px+ 处；
  3. `.boardRailAtmosphere` 的 `margin-top: auto` 因无剩余可用空间而失去沉底效果，反被外层容器（`.boardDesk` / 视口截图边界）无情截断，截图中仅能截到卡片上中部，底部插画完全滑出画面。
- **改法**：
  1. 将 `.boardDesk .boardRail` 设置为 `align-self: stretch !important; height: 100% !important; min-height: 100% !important;`，强制左栏拉满整行棋桌高度；
  2. 适度优化左栏上方卡片内间距与排版（头像 28px、时钟 18px、紧凑 padding），将静态内容高度压缩至 ~310px，剩余 170px～260px 空间由 `margin-top: auto` 完全让渡给底部氛围插画；
  3. 配置 `.boardRailAtmosphere` 为 `min-height: 96px !important; flex-shrink: 0 !important; overflow: visible !important;`，彻底根除挤压与裁切。

### 2. 对比度层（Opacity & Contrast）
- **根因**：
  原 SVG 内江面水体与倒影采用 `fill-opacity="0.14"` / `0.16"`，木舟船体纵向厚度不足 2px，竹叶多为 `0.35` 极淡薄绿，在 `#FAF6EE` 宣纸暖米底上缺乏黑白对比度，即便渲染出来也呈现「几乎看不见」的淡灰痕迹。
- **改法**：
  1. 采用浓淡得当的中国传统水墨色谱：舟身、船篷与笠翁采用高饱和炭墨/焦墨色（`#3E3428`、`#524434`、`#2B2117`），fill 达到 `0.95`（接近不透明实底），主墨线 stroke 达到 `1.2~1.4px`（opacity ≥ 0.70）；
  2. 五子棋棋罐采用厚重深陶色 `#5C4A38`（`opacity="0.95"`），黑白棋子黑白分明，竹叶采用层次分明的墨绿色谱（`#24372A`、`#314838`），轮廓鲜明、形体立体；
  3. 保持 `pointer-events: none`，视觉醒目同时绝不干扰下方操作或文字阅读。

---

## 三、项「左栏扁舟/棋篓可见」自判

- **自判结论**：**【闭合】**
- **判定依据**：
  1. **同机位实拍验证**：使用 Playwright 在箱内常驻本地服务 `http://127.0.0.1:18388/` 实拍同机位（1280x656 视口及 1024x525 首屏），象棋练习局（`6fa37c50-6291-4ac6-bb8d-85501a6b02b5`）与五子棋练习局（`5cb629e0-ee79-4be0-8832-a8ce6add775b`）：
     - 象棋近景（`live_03-xiangqi-board-close.png`）与全页（`live_03-xiangqi-game.png`）：左栏底部完整呈现「一叶扁舟」，木舟弧度、船舱篷顶、蓑衣笠翁、执篙长桨、微澜水波及上方水鸟清晰可辨；
     - 五子棋近景（`live_04-gomoku-board-close.png`）与全页（`live_04-gomoku-game.png`）：左栏底部完整呈现「雅竹与棋篓」，挺拔竹节、浓密竹叶、饱满棋罐、罐内与桌面散落的黑白双子清晰可辨；
  2. **容器边界与拉伸核验**：
     - `boardDesk` 边界与 `boardRail` 边界纵向完全对齐（`railRect.height == deskRect.height`，如 504px / 648px 同高拉满）；
     - `atmRect.bottom <= railRect.bottom`，插画完整容纳在宣纸侧栏卡片之内，无下溢出，无负边距裁切；
  3. **双路径生效**：`renderPracticeView`（AI 练习）与 `renderOnlineGameView`（在线真人对局）均直接挂载生效；
  4. **信息遮挡零侵入**：插画全部声明 `pointer-events: none !important;`，玩家头像、昵称、包干时钟、回合标签、认输/悔棋操作完全独立正常运作。

---

## 四、代码语法与完整性核验

1. **JavaScript 语法检查**：
   ```bash
   $ node --check src/main/resources/online/app.js src/main/resources/online/board.js
   # Exit Code: 0 (app.js 与 board.js 均顺利通过语法解析，零错误)
   ```
2. **CSS 括号匹配与语法检查**：
   ```bash
   $ python3 -c "with open('src/main/resources/online/app.css') as f: t = f.read(); assert t.count('{') == t.count('}')"
   # Exit Code: 0 (1049 对大括号严格对称闭合)
   ```
3. **运行时资源直达验证**：
   ```bash
   $ curl -s http://127.0.0.1:18388/online/ | grep "20260924r21"
   # 返回包含 app.css?v=20260924r21, mobile.css?v=20260924r21, board.js?v=20260924r21, app.js?v=20260924r21
   ```

---

## 五、Git 与交付禁区确认

1. **未执行 `git push`**：所有改动严格保存在本地工作区文件系统；
2. **未执行公网 / 生产部署**；
3. **未停止或中断箱内常驻 PublicWebMain 进程**（服务持续监听在 `*:18388` 且正常响应）；
4. **未触碰非目标区域**：
   - 认输按钮幽灵降权样式未变动；
   - 象棋深润木纹及五子棋深榧木纹未变动；
   - 侧栏 `#FAF6EE` 宣纸暖米底色未变动；
   - 后端走棋、时钟逻辑、WebSocket 协议与 REST API 未做任何修改。
