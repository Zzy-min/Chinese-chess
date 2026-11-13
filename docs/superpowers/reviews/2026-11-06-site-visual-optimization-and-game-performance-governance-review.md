# 轻棋局全站视觉优化与游戏运行性能深度治理审查与复核报告

- 日期：2026-11-06
- 阶段：Review & Verification
- 仓库路径：`D:\Projects\Active\Backend\Chinese-chess`（软链接 `C:\Users\Lenovo\Chinese-chess`）

## 1. 深入审查与问题诊断

在对前序工作成果及实际运行表现进行全盘排查后，确认并解决了以下真实缺陷：

### 1.1 移动端对局屏被全局底栏遮挡阻断问题 (P0 / 严重布局与交互阻断)
- **输入/现象**：在移动端视口（375px、320px）进入象棋或五子棋对局或练习屏，棋盘底部的核心对局控制区（「悔棋」、「求和」、「认输」、「离开」）被全局固定底栏（`mobileBottomNav` 56px）完全覆盖截断，用户无法点击认输或悔棋。
- **根因分析**：`src/main/resources/online/app.js` 中的 `renderBottomNav` 移除了 `isBoardRoutePage(activePage)` 豁免条件，导致对局、练习与复盘界面全量被注入底栏；且 `measureBoardHostSpace` 的移动端预留边距 `belowHostSpace` 设置过小（仅 64px），不足以容纳操作区及安全边距。
- **修复措施**：
  1. 恢复 `renderBottomNav` 中的 `if (isBoardRoutePage(activePage) || activePage === 'welcome') return '';`，遵守契约测试 `boardRoutesDoNotRenderMobileBottomNavigation`；
  2. 将移动端 `measureBoardHostSpace` 预留缓冲底距提升至 76px，保证即使无外层高度约束，操作按钮也拥有完整展示空间。

### 1.2 桌面端人机练习界面缺失阵营指示 (P1)
- **输入/现象**：桌面端（1440px）人机练习对局中，人类卡片仅展示「业余棋手」，AI 卡片仅展示「人机练习」，无任何红/黑/白阵营标识；且头像旁圆标样式使用了仅在移动端媒体查询内生效的 `.mobileSideBadge`，被 `@media (min-width: 769px) { .mobileSideBadge { display: none !important; } }` 强行隐藏。
- **根因分析**：桌面端缺少独立的通用印章徽标类名与阵营文本标签。
- **修复措施**：
  1. 在 `app.css` 中为 `.boardPlayerCardTop .sideBadge` 增加通用/桌面端徽标样式，支持 `.is-red`、`.is-black`、`.is-white`；
  2. 在 `app.js` 的 `renderPracticeView` 中更新人类卡片为 `${sideLabel(game.gameType, playerSide)} · 业余棋手`，AI 卡片为 `${sideLabel(game.gameType, aiSide)} · 人机练习`，并同时挂载 `sideBadge mobileSideBadge` 双类名，确保桌面端与移动端均呈现清晰的阵营辨识。

### 1.3 五子棋在线对局白方头像底色错误 (P2)
- **输入/现象**：在线五子棋中，执白玩家的头像 SVG 填充色使用了纯黑 `#2C2A26`，与黑棋头像混淆。
- **根因分析**：`renderClockCard` 中仅使用三元运算 `side === 'RED' ? '%238B2E2E' : '%232C2A26'`，未适配五子棋 `WHITE` 阵营。
- **修复措施**：重构为 `side === 'RED' ? '%238B2E2E' : (side === 'WHITE' ? '%237A8B99' : '%232C2A26')`。

### 1.4 五子棋移动端 Chrome 头部层级与导航不一致 (P2)
- **输入/现象**：五子棋移动端 Chrome 缺失返回按钮，且在对局区顶部额外插入了一级全量 6 Tab，挤占了近 100px 的棋盘高度。
- **修复措施**：统一 `renderMobileMatchChrome`，象棋与五子棋均具备左上角返回按钮与统一品牌、通知、个人中心布局，移除对局中无关的一级导航占用。

### 1.5 巡检诊断脚本假通过风险治理 (P2)
- **输入/现象**：`tools/diagnose_site.cjs` 中原逻辑直接导航至 `#/practice/xiangqi` 与 `#/practice/gomoku` 虚假路由，导致棋盘未实际渲染（`xiangqiPiecesCount: 0`）。
- **修复措施**：在诊断脚本中补充鉴权与真实对局创建流程，真实采集 32 颗象棋棋子与 225 格五子棋盘渲染，确保诊断测试与指标真实可信。

## 2. 真实测试验证记录

- **Maven 测试套件**：109 项测试 100% 通过（0 failures, 0 errors, 0 skipped）。
- **Playwright 端到端对局全流程 (`tools/test_game_flows.cjs`)**：
  - 用户注册/鉴权成功；
  - 象棋人机对局创建与实战走子（红炮 7,1 -> 7,4），AI 响应应手，UI 走法记录正确累计；
  - 五子棋人机对局创建与落子（7,7 天元），AI 响应落子，黑白棋子正确渲染；
  - 无无效 URL 报错，无未捕获异常。
- **全视口响应式自动化巡检 (`tools/diagnose_site.cjs`)**：
  - 测试视口：桌面 1440×900、平板 768×1024、手机 375×812、超窄小屏 320×568；
  - 水平溢出检测：0 处溢出（`hasHorizontalOverflow: false`）；
  - 棋盘与棋子渲染：`xiangqiPiecesCount: 32`, `xiangqiBoardRendered: true`, `gomokuBoardRendered: true`；
  - 核心性能指标：FCP 68ms，DOM Complete 50.6ms。
- **实测真机截图审校**：
  - `mobile_375_xq_fixed.png` 与 `mobile_320_xq_fixed.png`：底栏遮挡已彻底消除，四项对局控制按钮 100% 完整可视且可触控，红黑阵营徽标清晰；
  - `mobile_375_gm_fixed.png` 与 `mobile_320_gm_fixed.png`：五子棋黑白天元与控制面板完全呈现，无遮挡；
  - `desktop_xq_fixed.png` 与 `desktop_gm_fixed.png`：桌面端红棋/黑棋/白棋印章徽标与阵营角色文字对齐一致。
