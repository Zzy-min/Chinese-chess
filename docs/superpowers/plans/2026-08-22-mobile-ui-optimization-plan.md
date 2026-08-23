# 轻棋局移动端 UI 界面优化实施计划 (Mobile UI Optimization Plan)

## 目标与背景

根据设计规范《`docs/superpowers/specs/2026-08-22-mobile-ui-optimization-spec.md`》，对“轻棋局 Online”移动端 UI/UX 进行系统级优化与美化重构。
提升视觉美感（国风水墨与现代移动端质感融合）、触控体验（44px 最小触控区、微动反馈、防误触）、棋盘对战沉浸感（上下对手自适应排版、倒计时、走子辅助）以及全流程页面的响应式体验。

---

## 阶段规划

### Phase 1: 视觉基础与调色盘升级 (Design Tokens & Base CSS)
- **文件**: `src/main/resources/online/mobile.css`, `src/main/resources/online/app.css`
- **任务**:
  1. 完善全局国风 CSS 变量（宣纸底色 `--mobile-paper`, 沉香深色 `--mobile-paper-deep`, 朱砂红 `--mobile-cinnabar`, 松青 `--mobile-pine`, 泥金 `--mobile-gold`, 浓墨 `--mobile-ink` 等）。
  2. 规范移动端全局字体、行高、圆角体系（`border-radius: 16px ~ 28px`）、水墨柔和投影与模糊玻璃质感（`backdrop-filter`）。
  3. 优化 Safe-Area 顶部与底部边缘适配，确保刘海屏、灵动岛与手势条不遮挡内容。

### Phase 2: 首页、快速开局弹层与对局大厅美化 (Home, Quick Start & Lobby)
- **文件**: `src/main/resources/online/app.js`, `src/main/resources/online/mobile.css`
- **任务**:
  1. **首页 (Mobile Home)**:
     - 升级 Hero 区域视觉卡片，增加水墨太极微光背景与朱砂红主 CTA 按钮动效。
     - 重构 4 格快捷模式金刚区（真人匹配、AI 对弈、好友约棋、残局闯关），搭配专属印章图标与副标说明。
     - 升级“继续未完棋局 / 今日残局”动态条与最近战绩卡片。
  2. **快速开局底部抽屉 (Quick Start Sheet)**:
     - 优化半模态抽屉滑入动画与遮罩层点击关闭逻辑。
     - 增强象棋/五子棋双段切换、快棋（3/5/10分钟）、人机难度选择的触控交互。
  3. **对局大厅 (Mobile Lobby)**:
     - 优化房间码快速输入区（大号字体、一键粘贴与加入）。
     - 美化公开对局房间列表与观战入口。

### Phase 3: 对弈棋盘工作台与操作体验重构 (Board Arena & Game Desk)
- **文件**: `src/main/resources/online/app.js`, `src/main/resources/online/mobile.css`, `src/main/resources/online/board.js`
- **任务**:
  1. **上下对手信息与时钟 (Player Cards & Clocks)**:
     - 上部显示对手昵称、头像、Elo 积分、高对比倒计时时钟；
     - 下部显示己方信息与思考中脉冲徽章；
     - 倒计时低时（<30秒）警示变色与脉冲动画。
  2. **棋盘居中与自适应 (Board Fit & Viewport Layout)**:
     - 确保在 360px ~ 430px 各类手机屏幕下，象棋与五子棋棋盘完美居中、不留白、不溢出。
  3. **移动端底部操作条 (Mobile Wood Actions Bar)**:
     - 悔棋 (Undo)、求和 (Draw)、认输 (Resign)、提示 (AI Hint)、翻转 (Flip) 布局优化。
     - 认输/求和添加确认二次防误触交互，防止误触。
  4. **“将军！”、“绝杀！”水墨书法警示横幅**:
     - 优化走子动效、落点提示、将军动态字幅。

### Phase 4: 学习残局、观战、社区与个人中心全面优化 (Learn, Watch, Community, Me)
- **文件**: `src/main/resources/online/app.js`, `src/main/resources/online/mobile.css`
- **任务**:
  1. **学习与残局 (Learn & Puzzles)**:
     - 残局主题分类胶囊药丸样式优化。
     - 残局卡片增加星级难度评定与“已破/未破”国风印章标识。
  2. **社区排行榜 (Community Leaderboards)**:
     - 前三名金/银/铜印章奖牌榜首设计，支持按象棋/五子棋自由切换。
  3. **个人中心与设置 (Me & Settings)**:
     - 用户战绩仪表盘（胜率环形比例/胜负和场次）、棋盘皮肤与音效震动偏好设置。
  4. **对局结算模态框 (Game End Modal)**:
     - 大印章胜负鉴定（红胜/黑胜/和局）、Elo 积分增减、一键再战与复盘按钮。

### Phase 5: 响应式验证与全量自动化测试 (Verification & Regression)
- **任务**:
  1. 运行 `mvn clean test` 验证后端接口及资源契约测试 100% 通过。
  2. 启动本地 Undertow 服务，检查移动端各页面路由与组件渲染。
  3. 验证手机视口（375px、390px、414px）与平板/桌面端兼容性。
