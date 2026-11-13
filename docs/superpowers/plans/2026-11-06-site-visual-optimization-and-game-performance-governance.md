# 轻棋局项目全站视觉优化与游戏运行性能深度治理实施计划

- 日期：2026-11-06
- 阶段：Implementation Plan
- 目标：分步执行并验证对本地轻棋局项目的实际修复与性能提升。

---

## 阶段一：清理损坏样式数据与 Base64 编码 (P0)

1. **修改文件**：`src/main/resources/online/app.css`
   - 行 1287：移除末尾字符 `安`；
   - 行 2080：移除末尾字符 `起`；
   - 行 2086：移除多余分号 `;`。
2. **验证**：使用 `tools/inspect_b64.cjs` 检查所有 `data:image/webp;base64` 块，确保 `Invalid base64 chars: none`。

---

## 阶段二：五子棋/象棋双棋种角色与阵营徽章渲染重构 (P1)

1. **修改文件**：`src/main/resources/online/app.js`
   - 重构 `renderPracticeView` 内的 `playerSide` 与 `aiSide` 计算逻辑；
   - 适配五子棋 `BLACK` (黑) vs `WHITE` (白)；
   - 适配象棋 `RED` (红) vs `BLACK` (黑)；
   - 修复 AI 卡片上的硬编码 `is-black` 与 `data-clock-card="BLACK"`，改为根据实际 AI 阵营动态绑定；
   - 修复人类卡片徽章的单字展示与样式类名。
2. **验证**：运行对局流程，核验五子棋下双方分别呈现「黑」与「白」，且状态时钟正确激活。

---

## 阶段三：操作防抖与交互流畅性加固 (P1)

1. **修改文件**：`src/main/resources/online/app.js`
   - 检查落子触发函数 `onXiangqiCellClick`、`onGomokuCellClick`、`handlePracticeMoveSubmit`；
   - 确保 `moveInFlight` 锁在网络请求或动画进行时杜绝二次触发；
   - 走棋成功后的声音播放与棋盘高亮平滑过度。
2. **验证**：Playwright 快速双击棋子测试，验证不会产生重复请求或卡死。

---

## 阶段四：资源构建与全量端到端验证 (P0)

1. **资源同步**：
   - 执行 `mvn resources:resources`，同步最新资源至 `target/classes/online/`；
   - 重启后台测试服务 `com.xiangqi.web.PublicWebMain`。
2. **自动化测试**：
   - 运行 `mvn test`，确认全量 109 项测试 100% 通过；
   - 运行 `tools/diagnose_site.cjs`，检查 1440px / 768px / 375px / 320px 全路由无报错与无溢出；
   - 运行 `tools/test_game_flows.cjs`，验证象棋与五子棋真实对局。
