# Online 在线对局朝向 + 落子可见性 + 河界层级修复实施计划（Rev2）

## Implementation

1. 文档与资源版本
- 新增 Rev2 设计与实施文档。
- 更新 `online/index.html` 静态资源版本号，确保线上刷新命中新包。

2. `online/app.js` 逻辑改造
- 增加在线对局专用棋盘视角映射函数，仅在 `#/game/*` 生效。
- 棋盘渲染链路接入“显示坐标与棋盘坐标映射”，并确保点击落子使用棋盘真实坐标。
- 在线对局状态区新增“你执/对手执/当前手”明确提示。
- 河界文案函数化并按视角切换顺序，替换固定硬编码。
- 提升落子标记可见性：`from/to` 双标注 + `to` 点脉冲。
- 选子与切换选择改为局部刷新，不触发整页重绘。
- 优化 `sendMove` 渲染次数，保留并发令牌防覆盖。
- WS 在线局增量更新优先局部刷新，异常时回退全量渲染。

3. `online/app.css` 样式改造
- 调整象棋河界层级：河界文字在棋子之下。
- 强化落子标记对比度、边框、阴影与动画。
- 保持棋盘整体视觉风格一致，不影响五子棋基础样式逻辑。

4. 契约测试补强
- 扩展 `LegacyHomepageResourceContractTest`：
  - 校验视角映射函数；
  - 校验河界文案函数；
  - 校验河界层级类标识；
  - 校验双点标注与动画标识；
  - 校验在线局局部刷新与并发令牌逻辑标识。

## Verification

- `mvn -q "-Dtest=PublicSiteServerTest,LegacyHomepageResourceContractTest,PracticeGameHubTest" test`
- `mvn -q test`

## Defaults

- 本轮仅覆盖 `XIANGQI/GOMOKU`。
- 朝向调整仅作用于 `#/game/*`，`practice/analysis` 不变。
- 不引入新的后端字段与接口。
