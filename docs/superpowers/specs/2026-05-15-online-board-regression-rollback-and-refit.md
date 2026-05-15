# Online 棋盘回归修复设计

日期：2026-05-15

## 背景

2026-05-10 之后对 `online/app.css` 与 `online/app.js` 连续做了多轮“全棋盘页统一缩放”改造。当前用户反馈出现两个回归：

1. 棋盘交互后仍会出现尺寸异常，甚至在最新版本里“棋盘直接没有了”。
2. 正常象棋棋盘的“楚河 汉界”视觉位置不正确。

## 新鲜证据

1. 当前分支最近相关提交链：
   - `1f97ff9 fix: 补齐 boardWrap 高度链 + 河界字体跟随 cell 缩放`
   - `7c8ae70 fix: 彻底重写棋盘适配 — 同步计算无异步RAF`
   - `38a21ce fix: 棋盘交互后立即同步应用保存的 cell size`
2. 相对 `e92807a` 的 diff 显示：
   - `.site.is-board-route .boardPane` 从 `overflow:auto` 改成了 `overflow:hidden`
   - 新增 `.site.is-board-route .boardWrap{height:100%;overflow:hidden}`
   - practice 专用 `fitPracticeBoardToViewport` 被替换成全路由 `fitBoardToViewport`
   - `refreshLiveBoardSurface` 增加了基于 `savedW/savedH` 的同步 cell 计算

## 根因判断

### 问题 1：棋盘消失/交互后异常

根因不是单点，而是“稳定基线被同时改掉了”：

1. 已知较稳定版本 `e92807a` 只对 practice 路由做缩放，且 `boardPane` 允许 `overflow:auto`。
2. 后续把缩放逻辑泛化到 `game/practice/analysis`，同时把 `boardPane`/`boardWrap` 改成隐藏溢出，导致：
   - 某些布局下 `boardHost` 可用高度过小甚至为 0
   - 局部重渲染后新棋盘使用错误的 host 高度重新算 cell
   - 最终表现为棋盘被裁切，或直接不可见

### 问题 2：楚河汉界位置不正确

当前河界文字位置不仅受 `top:50%` 影响，还受字体尺寸策略影响。

1. 旧版使用视口驱动字号：`clamp(22px,2.8vw,34px)`，在小棋盘上会显得过大。
2. 最新版改成 `calc(var(--xi-cell-size) * 0.68)` 后又可能过小，且未对静态/正常棋盘单独校准。

本轮目标不是继续大改布局，而是：

1. 回到已知稳定的 practice 基线。
2. 只补“局部刷新后丢失缩放”的缺口。
3. 单独校准河界字号与垂直位置。

## 方案

### 1. 回滚到稳定的容器行为

恢复接近 `e92807a` 的棋盘容器语义：

1. `boardPane` 恢复 `overflow:auto`
2. 删除 `.site.is-board-route .boardWrap{height:100%;overflow:hidden}`
3. 删除本轮引入的全局 `.boardHost` 强约束布局

目的：先恢复“棋盘至少可见”的稳定性，停止继续扩大 CSS 回归面。

### 2. 缩放逻辑收敛

1. 恢复 practice 专用缩放入口 `fitPracticeBoardToViewport`
2. 维持 `game/analysis` 不做本轮泛化
3. 在 `refreshLiveBoardSurface()` 中，仅当当前路由是 practice 时：
   - 替换 HTML 前读取旧棋盘的实际 cell size
   - 替换后立即把该值写回新棋盘
   - 再触发一次 practice refit 收敛

这样修的是“交互刷新导致尺寸跳回默认值”，而不是继续碰整个在线页布局。

### 3. 河界单独校准

1. 保持河界在棋子下层的层级关系不变
2. 将 `.xiangqiBoardRiver` 字号调整为更保守的 cell 关联值
3. 必要时增加微小垂直校正，让文字视觉中心回到河界带中线

## 范围与非目标

### 范围

1. `src/main/resources/online/app.css`
2. `src/main/resources/online/app.js`
3. `src/main/resources/online/index.html`

### 非目标

1. 不改后端协议
2. 不改棋盘朝向规则
3. 不重做 game/analysis 全路由自适应

## 验收标准

1. practice 交互棋盘可见，点击棋子后不消失
2. practice 棋盘在交互前后尺寸稳定，不再跳回默认大尺寸
3. 象棋“楚河 汉界”位于正确河界带，且仍在棋子下层
4. `node --check src/main/resources/online/app.js` 通过
5. 浏览器实际验证通过后才允许部署
