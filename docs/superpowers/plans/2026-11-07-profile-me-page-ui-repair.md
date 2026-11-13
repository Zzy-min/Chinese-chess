# 实施计划：轻棋局个人主页（#/me）UI 重构与异常修复

## 阶段 1：排查与基线确认（已完成）
- [x] 使用 Playwright 自动化诊断脚本覆盖游客态与登录态。
- [x] 涵盖桌面端（1440px）、平板端（768px）、手机端（375px、320px）各视口及 7 个子 Tab。
- [x] 收集截图证据与结构指标，准确定位 6 大根因。

## 阶段 2：重构前端 JS 逻辑与模板结构 (`src/main/resources/online/app.js`)
- [ ] **重构 `renderProfile()` 结构**：
  - 桌面态与移动态结构清晰化：
    - 桌面端：左栏 `<aside class="profileSidebar">`，右栏 `<div class="profileMain">`（概览态下内含 `headerCard` 与 `overviewExtra`；子页面下内含对应 `mainHtml`）。彻底废除导致 3 子节点网格错乱的 `profilePage--m9` 桌面层叠冲突！
    - 移动端：保持符合人体工学的纵向流（资料卡 -> 功能九宫格 -> 详细概览 / 子功能）。
  - **移除概览页冗余的 `secondaryNav`**：去除重复且容易混淆的“对局统计”、“最近对局”二重快捷按钮，让信息层级一目了然。
  - **实现精致游客态组件 `renderProfileGuest()`**：
    - 当 `!state.me` 时，展示带有朱红古印、“客卿待客”之称的水墨风格欢迎卡片。
    - 提供“立即登录”与“新弈客注册”行动按钮，以及 4 项专属特权预览卡片。
  - **优化学习档案（Study Tab）模板**：
    - 改用自适应 2 列卡片与清晰行动引导，解决 3 列网格少一列的空缺失衡。
  - **修复移动端子页面返回导航**：
    - 在 `renderMobileContentPage` 中，针对 `route.page === 'me'` 且 `meTab !== 'overview'`，将 `backPath` 设为 `'me'`，返回标签设为 `'返回个人中心'`。

## 阶段 3：CSS 样式体系升级与水墨调优 (`app.css` & `mobile.css`)
- [ ] **桌面端 `.profilePage` 布局加固 (`app.css`)**：
  - 设置最大宽度与自动居中：`max-width: 1200px; margin: 0 auto; width: 100%;`。
  - 严谨的 2 列网格：`grid-template-columns: 260px minmax(0, 1fr); gap: 24px; align-items: start;`。
- [ ] **头像与用户名防畸变与防溢出保护**：
  - `.profileSealAvatar`, `.profileUserRow .avatar`：增加 `flex: 0 0 64px; aspect-ratio: 1/1; min-width: 64px; min-height: 64px; border-radius: 50%; object-fit: cover; flex-shrink: 0;`。
  - `.profileUserMeta`：`min-width: 0; flex: 1;`。
  - 用户名与 Slogan：添加 `text-overflow: ellipsis; white-space: nowrap; overflow: hidden;`。
- [ ] **移动端 320px/375px 双列菜单防折行优化 (`mobile.css`)**：
  - `.profileSidebarItem`：`min-width: 0; padding: 10px 12px;`。
  - `.profileSidebarTexts`：文本两行均采用单行省略，杜绝出现“管理个\n人资料”的破碎排版。
  - 移动端小屏断点（<= 360px）微调图标与字号，达到极致精致。
- [ ] **补齐 `.settingRow` 与 `button.settingRow` 样式**：
  - 替换默认浏览器按钮外观为雅致水墨卡片样式，增加悬浮/点击微动效。
- [ ] **为游客态页面定制高雅水墨专属样式**：
  - `.profileGuestCard`, `.profileGuestFeatures`, `.profileGuestActions` 等国风水墨样式。

## 阶段 4：静态资源构建与本地运行验证
- [ ] 确保无需复杂打包或如果有编译步骤执行对应构建。
- [ ] 执行全套自动化单测：`mvn test`，确保后端与资源合约测试 100% 通过。

## 阶段 5：Playwright 真实浏览器多端对比与深度验收
- [ ] 重新运行 Playwright 巡检脚本，生成最新的全端截图。
- [ ] 检查比对：桌面端 1440px、平板 768px、手机 375px 和 320px 在游客态与登录态全部 Tab 的显示效果。
- [ ] 确认无任何溢出、无文本畸变、无空缺不对齐、控制台无报错。
- [ ] 编写 Review 文档并交付结果。
