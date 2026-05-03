# Online 题库三级标签与对局流畅度修复设计（Rev2）

## Summary

在不改后端 API 的前提下，前端集中修复 5 个体验问题：

1. 题库支持三级主题标签与深链接。
2. 练习局轮询卡顿与重叠请求导致的“AI 思考中”停滞。
3. 用户落子反馈不灵敏（点击后缺少即时体感）。
4. AI 落子缺少清晰提示与最近一步标注。
5. 对局页棋盘偏大且页面存在整体滚动。

## Design

### 1) 学习页三级标签（题库主题）

- 保留学习二级标签：`tutorials | puzzles | practice`。
- 在 `puzzles` 子视图引入三级主题：`ALL | TACTIC | MATE | POSITION | ENDGAME_FEN`。
- 路由支持：
  - `#/learn/puzzles` -> 默认 `ALL`
  - `#/learn/puzzles/:theme` -> 指定主题
- 切换二级标签不丢当前题库主题。

### 2) AI 练习状态机与轮询节奏

- 关闭练习局整页定时重渲染。
- 保留乐观关闭策略：走子仅在服务端确认后落盘。
- 新增请求令牌 `moveRequestToken`，防止旧响应覆盖新状态。
- 轮询改为链式 `setTimeout`：
  - 落子后前 2 秒：250ms
  - 之后：500ms
- 轮询停止条件：
  - `aiPending=false`
  - 对局结束
  - 离开练习/训练对局路由
  - 登出或认输

### 3) 交互体感与 AI 提示

- 棋盘点击增加轻量“按下态”反馈，不依赖服务端返回。
- 最近一步标注统一：
  - 象棋：`from/to` 双点标注
  - 五子棋：最后落点环形标注
- 用户与 AI 标注样式一致、颜色区分。
- 练习状态栏增加短时提示：`AI 已落子：<notation>`。

### 4) 对局页单屏适配

- 仅对 `game/practice/analysis` 生效：
  - 页面级禁滚动
  - 棋盘区域与记录区域分栏，记录区内部滚动
  - 移动端提供“棋盘/记录”切换，不再整页纵向滚动
- 棋盘整体缩小：
  - 象棋、五子棋单元尺寸同步下调

## Scope

- 主要改动文件：
  - `src/main/resources/online/app.js`
  - `src/main/resources/online/app.css`
  - `src/main/resources/online/index.html`
  - `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`
- 后端 HTTP API 无新增、无破坏性变更。
