# Plan: Online Guofeng Web Redesign Without Membership

1. 调整路由与导航：
- 默认空 hash 进入 `home`
- 增加 `help` 页面
- 移除顶部“会员”标签，替换为无会员版本导航

2. 重构首页与大厅：
- `home` 改为桌面国风首页
- `play` 改为桌面对局大厅工作台
- `play/xiangqi`、`play/gomoku` 改为桌面棋种入口页

3. 重构棋盘页：
- 更新 `game/practice/analysis` 模板为三栏桌面布局
- 保留棋盘 autoscale、记录区滚动、分析跳步、求和/认输/悔棋等功能

4. 样式系统收口：
- 整理桌面专用国风组件样式
- 桌面隐藏底部导航，移动端保留
- 确保顶部导航与棋盘页新结构不冲突

5. 验证：
- `node --check src/main/resources/online/app.js`
- `mvn -q -DskipTests compile`
- 浏览器视觉验收至少覆盖 `home`、`play`、`practice`、`analysis`

6. 部署：
- 代码完成后提交 `main`
- 发布前若静态资源有变更，递增 `index.html` 版本参数
