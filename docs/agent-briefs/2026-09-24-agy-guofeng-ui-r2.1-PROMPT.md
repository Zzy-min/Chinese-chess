# agy 任务：国风 R2.1 · 只修左栏扁舟/棋篓可见性

> **日期**：2026-09-24  
> **默认禁止** push / 部署 / 本机同步  
> **范围极窄**：只动左栏氛围插画可见性（`app.js` 的 `renderBoardAtmosphereSvg` / `.boardRailAtmosphere` 相关 CSS；必要时 bump `index.html` `?v=`）。**禁止**重开宣纸侧栏、认输、木纹、业务逻辑。

## 验收依据（必读）
- `/workspace/voonie-align/xiangqi-guofeng-2026-09-24-r2/ACCEPTANCE.md`（项 1 ❌）
- 截图：同目录 `shots/03-xiangqi-game.png`、`03-xiangqi-board-close.png`、`04-gomoku-game.png`（左栏底部无清晰扁舟/棋篓）
- 真源：`docs/agent-briefs/2026-09-20-guofeng-ui-reference.png` 屏 3/4

## 根因（已定位，按此修）
1. `.boardRail` 在桌面为 `height: auto` + `align-self: start` + `justify-content: flex-start`，左栏不随棋桌拉高；`.boardRailAtmosphere` 的 `margin-top: auto` **几乎无效**，插画挤在 note 下且易被父级 `overflow`/高度裁切或「贴死看不见」。
2. SVG 内 `fill-opacity`/`stroke-opacity` 过低（水 0.14～0.18，部分装饰更低），在 `#FAF6EE` 宣纸底上肉眼不可辨。
3. 可选：容器 `overflow: hidden` + 无 `min-height` 加剧裁切。

## 必须落地
1. **布局**：桌面（非 ≤768）让 `.boardDesk .boardRail`（含 `--practice`）**纵向拉满棋桌行高**（`align-self: stretch` / `height: 100%` / 与中栏同高），`.boardRailAtmosphere` 用 `margin-top: auto` 沉底；`min-height: ≥96px`；`flex-shrink: 0`；`overflow: visible`（或保证 SVG 完整可见）。
2. **对比度**：重画/加浓扁舟（象棋）与棋篓+竹（五子棋）SVG——低对比水墨即可，但**轮廓必须肉眼可读**（建议主体 fill 不透明或 ≥0.85，墨线 stroke ≥0.55；勿再用 0.14 级「几乎看不见」）。仍 `pointer-events: none`，不挡昵称/时钟/按钮。
3. **双路径**：`renderPracticeView` 与在线对局左栏两处氛围容器都生效。
4. **缓存**：`index.html` 资源 bump 为 `?v=20260924r21`。
5. **勿改**：认输样式、木纹、侧栏宣纸色、路由/API。

## 交付
写 `docs/fix-briefs/2026-09-24-agy-guofeng-ui-r2.1-RESULT.md`：
- 已改文件
- 根因 → 改法（布局 / opacity）
- 项「左栏扁舟/棋篓可见」自判：闭合 / 部分
- `node --check` 结果
- 未 push / 未部署

成功标准：按现截图同机位，左栏底部应能指出扁舟轮廓与棋篓轮廓（不必像素级抄真源）。
