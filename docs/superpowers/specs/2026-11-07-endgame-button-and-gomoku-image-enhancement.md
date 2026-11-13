# 残局页精致按钮重构与五子棋高清素材修复设计规范 (Spec)

- 日期：2026-11-07
- 阶段：Spec / Design
- 仓库路径：`C:\Users\Lenovo\Chinese-chess`（实体路径 `D:\Projects\Active\Backend\Chinese-chess`）
- 目标：彻底修复残局页（`#/learn`、`#/learn/puzzles`）巨大违和的雷霆按钮组件，重新设计为符合全站现代水墨国风的精致按钮；并修复五子棋相关卡片、顶栏配图与背景的模糊失真问题，换用高质量清晰国风素材及优化 CSS 渲染。

---

## 1. 背景与现状诊断

用户现场体验后反馈两点问题：
1. **残局页的那个雷霆大按钮改了**：
   - 现存问题诊断：在 `src/main/resources/online/app.js` 的 `renderLearnPage` 中，右上角存在 `<button class="btn learnEndgameCta" data-nav="learn/puzzles/ENDGAME_FEN"><span>进入残局挑战</span>${mobileIcon('chevron')}</button>`。
   - 由于桌面端缺少对 `.mobileIcon` 的尺寸限制（未指定 `width` / `height`），内联 SVG 在 flex 容器内撑开至容器全宽（300px+ 高度），使得按钮呈现为一个带有巨大折角箭头（类似雷霆折线）的突兀暗红巨方块（尺寸超过 300px × 300px），严重破坏了全站克制、清雅的水墨国风体验。
   - 移动端虽然有针对 `.learnEndgameCta` 的媒体查询，但高度被多次覆盖至 54px，风格过于粗重笨拙。

2. **五子棋的图片糊了**：
   - 现存问题诊断：在 `src/main/resources/online/app.css` 中，`.bg-detail_gomoku` 与 `.gomokuTheme` 目前内嵌的 WebP Base64 编码为早期损坏/过度压缩的样本（解压后呈现大量灰度宏块噪点与断裂像素失真）。
   - 同时，行 2080 的 `.xiangqiTheme` 存在 Base64 截断导致填充解码异常。
   - 在桌面端 Hero 右侧配图（`.deskHeroIllustRight`）、大厅五子棋入口面板（`.deskModePanelBg`）、模式选择页顶部画卷（`.modeTopIllust`）以及棋盘背景（`.gomokuTheme`）上均呈现出明显的低清失真。

---

## 2. 详细设计方案

### 2.1 残局挑战按钮重构设计 (Refined Guofeng CTA Button)

1. **桌面端与通用尺寸规范**：
   - 高度定为 40px（`min-height: 40px`，`height: 40px`），内边距 `0 18px`，圆角 8px。
   - 内部布局：`display: inline-flex; align-items: center; justify-content: center; gap: 8px;`。
   - 全局 SVG 图标约束：在 `app.css` 中定义 `.mobileIcon { width: 16px; height: 16px; display: inline-block; vertical-align: middle; flex-shrink: 0; }`，彻底防止任何内联 SVG 在桌面端意外膨胀。
   - 按钮内箭头图标：`.learnEndgameCta .mobileIcon { width: 14px; height: 14px; stroke-width: 2.2; transition: transform 0.2s cubic-bezier(0.2, 0, 0, 1); }`。

2. **水墨国风视觉调色板与质感**：
   - 背景色：温润朱砂红微渐变 `linear-gradient(180deg, #963529 0%, #7E261C 100%)`。
   - 边框：`1px solid rgba(139, 46, 33, 0.4)`，带有泥金微光；
   - 内阴影：顶部高光 `inset 0 1px 0 rgba(255, 255, 255, 0.18)`，营造微凸浮雕印章质感；
   - 外阴影：`0 2px 6px rgba(139, 46, 33, 0.16)`；
   - 字体：`font-family: var(--font-serif, "Noto Serif SC", "STKaiti", "KaiTi", serif); font-size: 14px; font-weight: 600; color: #FFF9F0; letter-spacing: 0.05em;`。

3. **微交互反馈 (Interactive Feedback)**：
   - Hover：轻微上浮 1px，渐变提亮至 `#A43B2E ~ #8C2C21`，投影加深至 `0 4px 12px rgba(139, 46, 33, 0.22)`，箭头向右平移 2px。
   - Active：按压下沉 `translateY(0)`，投影收缩至 `0 1px 3px rgba(139, 46, 33, 0.2)`，暗红加深至 `#7E261C ~ #6E1E15`。

4. **移动端适配 (`mobile.css`)**：
   - 统一覆盖移动端各媒体查询断点，规范为：宽度 100%，高度 42px，圆角 10px，字号 14px，与移动端其他卡片按钮节奏完美拉齐。

5. **情境感知语义 (Contextual Semantic)**：
   - 在全量棋谱/教程页面时，显示 `进入残局挑战 →`，点击导向 `#/learn/puzzles/ENDGAME_FEN`；
   - 在已处于残局分类页面（`isEndgameRoute`）时，感知切换为 `开启 AI 研习 ▷`，导向 `#/learn/practice`，避免原地冗余重跳。

---

### 2.2 五子棋高清画卷与图像渲染治理 (High-Definition Gomoku Illustration)

1. **素材设计与生成**：
   - 严格匹配 `detail_xiangqi` 的宋代写意水墨国风：米友仁式远山云雾、曲水行舟、苍翠墨竹。
   - 前景展现温润木纹棋盘与黑白两色饱满圆润的立体棋子，右侧陈列青瓷石制棋罐（棋罐内置黑白棋子）。
   - 纯净宣纸底色（`#F7F4EB`），不含任何非自然噪点或文字瑕疵。
   - 原始分辨率达到 1000 × 558，采用高质量 WebP（Quality 85, Method 6）进行紧凑编码，保证 Retina / 高 DPI 移动端极致清晰的同时控制体积在 80KB 级别。

2. **样式替换与修复**：
   - 替换 `src/main/resources/online/app.css` 中的 `.bg-detail_gomoku` 与 `.gomokuTheme` 背景图为新版高清 WebP Data URI。
   - 同步修复行 2080 的 `.xiangqiTheme`，使用完整的 `bg-detail_xiangqi` 图像数据，消除 Base64 截断警告。

3. **渲染优化规则 (`image-rendering`)**：
   - 为 `.bg-detail_gomoku`、`.gomokuTheme`、`.bg-detail_xiangqi`、`.xiangqiTheme`、`.modeTopIllust`、`.deskHeroIllustRight`、`.deskModePanelBg` 显式声明：
     ```css
     background-size: cover;
     background-position: center;
     background-repeat: no-repeat;
     image-rendering: -webkit-optimize-contrast;
     image-rendering: auto;
     ```
   - 确保在各类屏幕分辨率、视网膜缩放（Retina 2x/3x）下平滑细腻且边缘分明。

4. **静态资源缓存更新**：
   - 更新 `src/main/resources/online/index.html` 的资源版本标识，保证所有客户端强制获取全新编译打包样式。

---

## 3. 验证准则

1. `mvn test` 109 项既有测试 100% 通过（保持所有契约不变）。
2. Undertow 18388 真实服务运行下，通过 Playwright 自动化巡检桌面端（1440px）、平板端（768px）和移动端（375px/320px）。
3. 截图比对：
   - `#/learn/puzzles/ALL` 顶部无任何膨胀的巨型按钮，呈现典雅国风小巧胶囊 CTA。
   - `#/home`、`#/play/gomoku`、`#/play` 中五子棋配图清晰细腻，竹墨相映，绝无像素块或模糊失真。
