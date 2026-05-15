# Practice 棋盘高度驱动适配设计

日期：2026-05-15

## 背景

最新线上 `#/practice/:gameId` 仍有一类回归：用户点击并等待 AI 应手后，棋盘在部分桌面环境里会保持偏大的尺寸，底部被页面裁掉。

本轮新增证据来自用户截图与新鲜浏览器复测：

1. 当前线上资源版本已是 `20260515c`，不是旧 bundle 误缓存。
2. 现有逻辑用 `boardHost.getBoundingClientRect().height` 作为可用高度。
3. practice 页里 `boardHost` 所在 grid 行本身又受棋盘内容高度影响，导致“用内容高度反过来决定内容尺寸”的循环测量。
4. 一旦浏览器实际布局结果比预期高，页面外层又有 `height:100vh` + `overflow:hidden`，棋盘就会被直接裁断，而不是滚动。

## 根因

`fitXiangqiPracticeBoard()` / `fitGomokuPracticeBoard()` 目前把 `boardHost` 自身高度当成最终可信的可用高度，但 practice 页面里真正稳定的约束来自整个 `.boardPane--practice` 的剩余高度：

1. pane 总高度是稳定的。
2. 顶部 meta、状态和底部按钮行高度也是稳定可测的。
3. `boardHost` 自身高度不是独立输入，而是 grid 分配结果，存在循环依赖。

因此本轮要把高度测量改成：

1. 从 `.boardPane--practice` 读取总可用高度。
2. 扣除除 `boardHost` 外兄弟节点高度与 row gap。
3. 用剩余高度和可用宽度共同决定最终 cell size。

## 方案

### 1. 新增 practice pane 剩余空间测量

新增一个仅服务于 practice 路由的测量函数，返回：

1. `availableWidth`
2. `availableHeight`

其中：

1. 宽度优先取 `boardHost` 自身宽度。
2. 高度优先按 `boardPane.clientHeight - siblingHeights - gaps` 计算。
3. 若 pane 不存在，再回退到旧的 `boardHost.getBoundingClientRect()`。

### 2. 缩放逻辑改为高度驱动

`fitXiangqiPracticeBoard()` / `fitGomokuPracticeBoard()` 改为消费上述测量结果，不再直接信任 `boardHost` 当前高度。

### 3. 轻量 CSS 补强

只给 practice 的 `data-live-board-host` 增加更明确的：

1. `align-self:stretch`
2. `overflow:hidden`

让 host 更明确地占用 grid 分配到的那一行。

## 验收标准

1. 宽屏 practice 页面进入对局后棋盘完整显示。
2. 点击落子并等待 AI 应手后，棋盘仍完整显示，不被底部裁断。
3. 窄高视口下棋盘仍会继续收缩，不回退成默认大尺寸。
4. `node --check src/main/resources/online/app.js` 通过。
5. 浏览器多视口真实交互验证通过后才允许部署。
