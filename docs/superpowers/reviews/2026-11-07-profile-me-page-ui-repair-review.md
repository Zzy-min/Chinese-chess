# 个人主页（#/me）UI 异常重构与修复审查报告

## 审查基本信息
- **审查目标**: 修复 `http://127.0.0.1:18388/online#/me` 个人主页在多端视口及登录态下的 UI 布局异常、组件错位、溢出挤压与交互缺陷
- **关联设计规范**: `docs/superpowers/specs/2026-11-07-profile-me-page-ui-repair.md`
- **关联实施计划**: `docs/superpowers/plans/2026-11-07-profile-me-page-ui-repair.md`
- **执行日期**: 2026-11-07
- **测试环境**: 本地 Undertow 服务（127.0.0.1:18388）、Node Playwright 诊断套件、Maven surefire 测试套件

---

## 1. 缺陷根因排查与解决对照

| 缺陷编号 | 现象与根因 | 修复方案与代码变更 | 验证结论 |
| :--- | :--- | :--- | :--- |
| **D1: 桌面端 3 节点崩塌** | `renderProfile()` 在 `profilePage` 容器下返回 3 个同级节点（`profileMain--top`、`profileSidebar`、`profileMain--bottom`），而 CSS 仅定义 2 列网格且无 grid-template-areas，导致第三个节点被硬塞入第 1 列侧边栏下方（头像被挤压至 25px，最近对局被压成 20px 宽度单字垂直换行）。 | 将桌面端重构为严谨的双列网格：左侧为固定 260px 侧边栏（`profileSidebar`），右侧为自适应 `profileMain`（内含 `headerCard` 与 `overviewExtra`）。 | **PASSED**：桌面端（1440px）左侧侧边栏 260px，右侧内容自适应，完美居中。 |
| **D2: 印章头像拉伸变形** | 头像未设置 `flex-shrink: 0`、`aspect-ratio: 1/1` 及 `min-width: 64px`，在窄屏和弹性容器中被挤压成不规则椭圆或扁条。 | 新增 `.profileSealAvatar` 及统一 `.avatar` 样式，设置 `flex: 0 0 64px`、`aspect-ratio: 1/1`、水墨汉仪红双边框与国风印章质感，内部文字 `user-select: none` 居中。 | **PASSED**：桌面端、平板、手机端（375px/320px）均维持严正几何正方形，无畸变。 |
| **D3: 移动端 320px 文字溢出折行** | 侧边栏功能项（如「对局记录」副文案「查看历史对局」）在 320px 极窄屏（iPhone SE）下折成 3 行，卡片被纵向撑破。 | 添加 `white-space: nowrap; overflow: hidden; text-overflow: ellipsis;`，并为 `@media (max-width: 360px)` 引入极致紧凑规则（图标缩小至 28px，字号微调至 10-12.5px，间距紧凑）。 | **PASSED**：在 320px 下所有 7 个菜单项整齐排布于 2 列网格中，副文本平滑截断，无任何横向溢出。 |
| **D4: 游客态灰盒无质感** | 未登录访问 `#/me` 呈现空洞的灰色边框小卡片 `renderPlaceholder`，毫无国风韵味和引导价值。 | 重新设计 `renderProfileGuest()`，采用轻棋局「登堂入室」宣纸朱砂印章封面板，提供专属水墨特性卡片（云端对局、战绩追踪、残局研习）以及「立即登录/注册」与「暂不登录，先去逛逛」引导按钮。 | **PASSED**：游客态在各端均呈现雅致水墨国风门户，点击登录即刻调起鉴权弹窗，点击逛逛平滑直达对局大厅。 |
| **D5: 设置页面原生按键突兀** | 移动端检查更新按钮 `<button class="settingRow mobileVersionRow">` 缺失样式类继承，呈现浏览器原生灰色立体凹凸按钮，破坏整体水墨质感。 | 编写 `button.settingRow` 完整规范，统一为宣纸底色、轻阴影、hover/active 反馈明确的国风设置卡片，带右侧操作图标与水墨微交互。 | **PASSED**：设置项与音效切换按钮浑然一体，交互反馈清晰明快。 |
| **D6: 移动端子页面导航体验割裂** | 移动端访问 `/me/records` 等子页面时，顶部返回按钮硬编码跳回 `#/home`，且子页面顶部被 7 个巨大功能卡片占满整个首屏。 | 移动端将总览页（`meTab === 'overview'`）与子页面（`meTab !== 'overview'`）分离：子页面顶部采用横向弹性滑动的胶囊药丸导航栏（`profileMobileTabs`），顶部返回键智能回退至 `#/me`。 | **PASSED**：移动端子页面顶部胶囊横滑自如，点击返回准确退回 `#/me` 主页。 |

---

## 2. 自动化验证记录

### 2.1 单元测试与契约测试
- **测试命令**: `mvn -f C:\Users\Lenovo\Chinese-chess\pom.xml test`
- **执行结果**:
  - `Tests run: 109, Failures: 0, Errors: 0, Skipped: 0`
  - `com.xiangqi.web.OnlineSiteResourceContractTest` (19 个契约测试全数通过)
  - `com.xiangqi.web.PublicSiteServerTest` (27 个端到端服务测试全数通过)
  - `BUILD SUCCESS`，总耗时 38.4 秒。

### 2.2 真实浏览器多端视觉排查（Playwright）
- **工具脚本**: `tools/diagnose_me_page.cjs`
- **覆盖视口与状态**:
  1. 桌面端 1440x900（游客态 `guest-desktop-1440.png`、已登录总览态 `auth-overview-desktop-1440.png`、已登录设置页 `auth-settings-desktop-1440.png`、已登录研习页 `auth-study-desktop-1440.png`）
  2. 平板端 768x1024（已登录总览态 `auth-overview-tablet-768.png`）
  3. 移动端 375x812（已登录总览态 `auth-overview-mobile-375.png`、已登录对局记录页 `auth-records-mobile-375.png`）
  4. 极窄移动端 320x568（已登录总览态 `auth-overview-mobile-320.png`）
- **指标检验**:
  - `scrollWidth <= clientWidth` 全视口验证通过，无任何水平横向溢出。
  - Console 报错与警告数量均为 0。

### 2.3 真实浏览器交互流程测试（Playwright）
- **测试脚本**: `tools/test_me_interactions.cjs`
- **验证项**:
  - [x] **TEST 1 游客态动作**: 点击「立即登录/注册」触发 `shouldShowAuthOverlay` 鉴权弹窗；点击「暂不登录，先去逛逛」导航至 `#/play`。
  - [x] **TEST 2 桌面端已登录交互**: 个人印章用户名正确匹配当前登录用户；点击「编辑资料」准确跳转至 `#/me/settings`；点击音效设置项即时触发音效状态切换（开启/关闭）并提示 Toast；点击复制诊断信息成功获取运行时日志。
  - [x] **TEST 3 移动端子页面与导航交互**: 在移动端视口（375x812）下访问 `#/me/records`，横滑胶囊 Tab 处于「对局记录」激活态；点击「学习档案」胶囊平滑切换路由至 `#/me/study`；点击顶部返回按钮准确回退至 `#/me` 个人总览页。
- **执行结果**: `ALL INTERACTION TESTS PASSED!`

---

## 3. 代码变更清单

- `src/main/resources/online/app.js`:
  - 增加 `renderProfileGuest()` 游客态水墨质感引导页；
  - 重构 `renderProfile()` 结构，消除多子节点在网格下的崩塌；
  - 移动端子路由支持横滑胶囊导航（`profileMobileTabs`）；
  - `renderMobileContentPage()` 针对 `#/me` 子路由提供定制返回路径（`backPath = 'me'`）与雅致副标题；
  - 调整 `shouldShowAuthOverlay`，使游客态能直接浏览 `#/me` 专属水墨门户。
- `src/main/resources/online/app.css`:
  - 重写 `.profilePage` 桌面端双列自适应布局（`260px minmax(0, 1fr)`，最大宽度 1200px 居中）；
  - 规范 `.profileSealAvatar` 及 `.avatar` 样式，保证正方形宽高比（`flex: 0 0 64px; aspect-ratio: 1/1`）；
  - 统一四栏战绩卡片 `.profileStatsGrid`、设置行卡片 `.settingRow`、学习进度卡片 `.profileStudyStatsGrid` 与游客卡片 `.profileGuestPage`。
- `src/main/resources/online/mobile.css`:
  - 增加移动端专属总览流 `.profileMobileOverview` 及子页面容器 `.profileMobileSubpage`；
  - 增加横向无滚动条毛玻璃胶囊药丸导航 `.profileMobileTabs`；
  - 增加 `.profileSidebarTexts` 单行截断防护以及 320px 极限紧凑样式，杜绝挤压溢出。
- `target/classes/online/*`:
  - 同步复制最新静态资源，确保线上/本地 Undertow 运行时即刻生效。

---

## 4. 结论与交付声明
个人主页 `#/me` 在桌面端、平板端、手机端（常规及极限 320px 宽度）及不同登录态下的所有已知 UI 缺陷均已彻底修复，完全符合轻棋局统一的水墨国风视觉规范，全部自动化与人工测试均已通过验证。
