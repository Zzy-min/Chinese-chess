# Online 全棋盘页裁切修复实施计划

关联设计：`docs/superpowers/specs/2026-05-10-online-all-board-routes-autoscale-no-scroll-design.md`

## 1. 前端结构调整

1. 在 `game/practice/analysis` 三类棋盘页统一引入 `.boardHost` 容器。
2. 为三类棋盘 pane 增加语义类并配置各自行模板。
3. 保持记录区结构不变，继续由 `.moves` 承担滚动。

## 2. 缩放逻辑改造

1. 将 `fitPracticeBoardToViewport` 重构为 `fitBoardToViewport`。
2. 扩展适用路由为 `game/practice/analysis`。
3. 以 host 的宽高和棋盘动态外框计算目标格子尺寸。
4. 增加超出安全收敛循环，直到容纳或达最小格子下限。
5. 零尺寸 host 跳过并等待下一次触发。

## 3. 样式策略

1. `boardPane` 在棋盘路由中禁止内部滚动。
2. 三类 pane 分别设置 grid 行模板，棋盘行使用 `minmax(0,1fr)`。
3. 统一触摸与 overscroll 约束在棋盘路由内生效。

## 4. 验证步骤

1. 语法与构建：
   - `node --check src/main/resources/online/app.js`
   - `mvn -q -DskipTests compile`
2. 视觉验收矩阵：
   - 路由：`practice`、`game`、`analysis`
   - 棋种：`XIANGQI`、`GOMOKU`
   - 视口：`1366x768`、`1536x864`、`1920x1080`、`390x844`
3. 验收点：
   - 棋盘完整可见、无底部裁切
   - 棋盘 pane 无滚动、记录区可滚动
   - 缩放后层级与标记无错位
   - resize / 页签切换后稳定

## 5. 回滚

1. 回滚文件：
   - `src/main/resources/online/app.js`
   - `src/main/resources/online/app.css`
2. 若只需撤回流程文档，不影响运行逻辑，可单独回滚新增 docs 文件。

