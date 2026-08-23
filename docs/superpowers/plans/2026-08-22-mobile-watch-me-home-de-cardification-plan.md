# 首页长度适配与观战/我的去卡片化实施计划

针对用户提出的两项新要求：
1. **首页不要留白，长度也要适配**
2. **观战页和我的界面仍存在卡片式布局问题（彻底去卡片化）**

## 拟变更文件与组件

### 1. `src/main/resources/online/app.js`
- **`renderMobileHomePage`**：扩容战绩至 5 局，加入今日棋理微条，优化垂直流布局。
- **`renderWatchPage` / `renderWatchRooms` / `renderWatchGames`**：
  - 改造为专属结构，移除嵌套 `.panel` 与 `.split`。
  - 单行输出带有古典印章与对战详情的条目。
- **`renderProfile`**：
  - 移动端模式下输出一体化古典弈者信息、4 列战绩格与连续功能菜单。
  - 支持子页面（战绩、设置、学习）一键返回。

### 2. `src/main/resources/online/mobile.css`
- **首页长度适配**：配置 `.mobileHome` 弹性伸缩与各模块间距。
- **观战页古典列表样式**：`.clWatchFilterBar`, `.clWatchSection`, `.clWatchItem`, `.clWatchSeal` 等。
- **我的页面水墨档案样式**：`.clProfileHeader`, `.clProfileStatsGrid`, `.clProfileMenu`, `.clProfileMenuItem` 等。

---

## 验证计划
1. **自动化测试**：执行 `mvn test`，保证全量测试用例 100% 通过。
2. **真机全链路验证**：
   - 截取首页真机截图，确认底部不再留白，长度自然贴合屏幕。
   - 截取观战页真机截图，确认无任何嵌套卡片，列表水墨清爽。
   - 截取我的页面真机截图，确认一体化档案与功能菜单，无多重卡片堆叠。
