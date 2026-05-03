# Online 题库三级标签 + 对局流畅度修复实施计划（Rev2）

## Phase 1. 路由与学习页结构

1. 在 `app.js` 新增题库主题常量与解析函数。
2. 扩展 `#/learn/puzzles/:theme` 路由并兼容 `#/learn/puzzles` 默认主题。
3. 学习页渲染补充三级主题标签与过滤逻辑。

## Phase 2. 对局交互与轮询重构

1. 移除练习局固定间隔整页重渲染依赖。
2. 引入 `moveRequestToken` 与并发保护，防止旧响应覆盖。
3. 轮询从 `setInterval` 改为链式 `setTimeout`（250ms/500ms 分段）。
4. 按停止条件统一回收轮询状态。

## Phase 3. 提示与标注

1. 统一最近一步标注模型（象棋 from/to、五子棋单点）。
2. 增加 AI 落子短时提示文本，并绑定到练习状态栏。
3. 增加棋盘点击即时按下反馈（不影响服务端真值）。

## Phase 4. 页面适配与版本

1. 对 `game/practice/analysis` 启用单屏容器与内部滚动。
2. 移动端加棋盘/记录切换，避免页面级滚动。
3. 下调象棋与五子棋棋盘尺寸，确保常见视口可用。
4. 更新 `index.html` 静态资源版本号，确保线上命中新包。

## Phase 5. 验证

1. 资源契约测试扩展：三级主题路由、tick、链式轮询、最近一步标注与 AI 提示函数。
2. 执行：
   - `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest,PracticeGameHubTest" test`
   - `mvn -q test`
3. 手动验收：
   - 题库主题切换与深链接可用
   - 快速落子无重复提交与明显卡顿
   - AI 应手稳定出现并有提示
   - 对局页无页面级滚动，仅局部容器滚动
