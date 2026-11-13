# 残局页精致按钮重构与五子棋高清素材修复实施计划 (Plan)

- 日期：2026-11-07
- 阶段：Implementation Plan
- 关联 Spec：`docs/superpowers/specs/2026-11-07-endgame-button-and-gomoku-image-enhancement.md`

---

## 阶段一：高清素材准备与编码转换
- [ ] 1.1 从已生成的 1376×768 高清国风五子棋水墨画卷（`target/gomoku_1000_inspect.png`）转换为 1000×558、WebP Quality 85 的高质量图片。
- [ ] 1.2 生成标准的 Base64 Data URI 字符串，校验 WebP 格式与 Base64 严格有效性（无非法字符、无尾部截断、Padding 严格对齐）。
- [ ] 1.3 提取 `bg-detail_xiangqi` 的完整 Base64 字符串，用于修复 `.xiangqiTheme` 的截断缺失。

## 阶段二：样式表与组件交互重构
- [ ] 2.1 修改 `src/main/resources/online/app.css`：
  - 更新 `.bg-detail_gomoku` 与 `.gomokuTheme` 为高清 WebP 数据；
  - 修复 `.xiangqiTheme` 的 Base64 数据；
  - 添加全局 `.mobileIcon` 尺寸限制（`width: 16px; height: 16px;`）；
  - 全新定义 `.learnEndgameCta` 水墨国风精致按钮样式（尺寸、圆角、字号、阴影、Hover/Active 微动效、图标平移）；
  - 强化 `.bg-detail_gomoku`、`.gomokuTheme`、`.modeTopIllust`、`.deskHeroIllustRight`、`.deskModePanelBg` 的 `image-rendering: -webkit-optimize-contrast; image-rendering: auto;`。
- [ ] 2.2 修改 `src/main/resources/online/mobile.css`：
  - 统一规范移动端 `@media` 下 `.mobileContentPage--learn .learnEndgameCta` 样式，调整为高度 42px、字号 14px、圆角 10px。
- [ ] 2.3 修改 `src/main/resources/online/app.js`：
  - 在 `renderLearnPage` 中为残局挑战按钮增加情境语义切换（`isEndgameRoute ? '开启 AI 研习' : '进入残局挑战'`），保持 `data-nav="learn/puzzles/ENDGAME_FEN"` 核心契约。
- [ ] 2.4 修改 `src/main/resources/online/index.html`：
  - 递增统一缓存失效版本号 `?v=20261107r1`。

## 阶段三：编译打包与本地服务同步
- [ ] 3.1 运行 `mvn -f pom.xml test-compile` 与测试，确保 109 项单元/契约测试全部通过。
- [ ] 3.2 启动本地 Undertow 站点服务器（端口 18388），验证静态资源热更与路由响应。

## 阶段四：真机/无头浏览器环境视觉与功能巡检
- [ ] 4.1 运行 Playwright 跨端诊断脚本（1440px 桌面端、768px 平板、375px/320px 移动端）。
- [ ] 4.2 审查残局页面（`#/learn/puzzles/ALL`、`#/learn/puzzles/ENDGAME_FEN`）截图，验证雷霆大按钮已被优雅现代的国风组件替代。
- [ ] 4.3 审查五子棋大厅卡片、模式选择页（`#/play/gomoku`）、对局背景图，验证画卷清晰细腻、无模糊失真。
- [ ] 4.4 检查浏览器控制台无报错、网络请求无失败、各视口无横向溢出。
