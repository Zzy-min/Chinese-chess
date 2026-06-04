# 五子棋棋盘渲染修复设计说明书 (Gomoku Board Render Fix Spec)

## 1. 问题诊断
用户反馈在进入五子棋（Gomoku）对局时，棋盘渲染出现问题（棋子及最后一步高亮指示圈均无法正常显示）。

### 成因分析
- 在 CSS 样式文件 [app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css#L292) 中，五子棋的棋子样式定义在 `.gomokuCell.is-black .gomokuStone` 及 `.gomokuCell.is-white .gomokuStone` 选择器中，而最后一步高亮则定义在 `.gomokuLastMove` 中。
- 然而，在 [board.js](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/board.js#L163) 的 `GomokuBoard` 类的 `_init()` 阶段，只创建了外层的 `button` 容器（即 `.gomokuCell`），但**没有**在其内部创建 `.gomokuStone` 与 `.gomokuLastMove` 两个关键的子元素。
- 这导致 DOM 层级与 CSS 规则发生断层，渲染引擎无法正确匹配和渲染出棋子形状及高亮圈。

## 2. 解决方案
- **DOM 初始化修正**：在 [board.js:L163](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/board.js#L163) 的 `_init()` 循环创建 cell 时，使用 `document.createElement('span')` 为每个单元格预先注入子元素：
  - 一个用于绘制棋子的 `.gomokuStone` 元素。
  - 一个用于高亮最后一步的 `.gomokuLastMove` 元素。
- **渲染循环修正**：在 `_render()` 中，动态获取单元格内的 `.gomokuLastMove` 元素，并根据 `curr.lastMove` 中的信息（owner, pending 等）动态配置其可见性（display 属性）和特定样式类名，以确保最后落子标记得以实时且准确地渲染。
