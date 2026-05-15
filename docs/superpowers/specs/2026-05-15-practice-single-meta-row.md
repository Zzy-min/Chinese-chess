# Practice 单行状态条设计

日期：2026-05-15

## 背景

最新线上 practice 页虽然已经修复了“AI 应手后棋盘重新放大”的回归，但用户继续反馈两个布局问题：

1. practice 顶部信息 pill 仍分成两行，占掉了过多垂直空间。
2. 棋盘下方在部分桌面视口里仍显示不完整。

## 根因

practice 页当前结构里有两组独立信息条：

1. `.gameMetaRow`
2. `.practiceInfoLine`

两组都允许 `flex-wrap:wrap`，所以在宽度足够时仍会占两行。practice 页面本身又是固定高度布局，顶部多占一行就会直接压缩棋盘可用高度。

## 方案

### 1. 合并为单行元信息条

将 practice 视图的两组 pill 合并成一个 `practiceMetaLine`，包含：

1. AI 练习
2. 游戏类型
3. 执子方
4. 当前轮次
5. 状态
6. AI 引擎文案
7. 引擎 ID
8. 难度
9. 对手
10. AI 方

### 2. 明确单行行为

`practiceMetaLine` 采用：

1. `flex-wrap:nowrap`
2. `overflow-x:auto`
3. 每个 pill `flex:0 0 auto`

这样在正常桌面宽度下一行完整展示，极窄宽度下则横向滚动而不是换行。

### 3. 收回棋盘高度

practice grid 行从原来的：

1. meta 第一行
2. meta 第二行
3. status
4. board
5. actions

收缩为：

1. 单行 meta
2. status
3. board
4. actions

把被第二行 pill 占掉的高度还给棋盘。

## 验收标准

1. practice 顶部 pill 在桌面端为单行显示。
2. 较矮桌面视口里，棋盘底部完整显示。
3. 落子并等待 AI 应手后，棋盘尺寸仍保持稳定。
4. `node --check src/main/resources/online/app.js` 通过。
