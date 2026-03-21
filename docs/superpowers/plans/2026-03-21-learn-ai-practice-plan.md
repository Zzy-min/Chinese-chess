# 学习页 AI 对局实施计划

## 阶段 1：测试基线

- 新增 `PracticeGameHubTest`，覆盖：
  - 象棋练习局创建
  - 五子棋练习局创建
  - 玩家落子后 AI 自动响应
  - 玩家后手时 AI 开局先走
  - 认输后归档
- 扩展 `OnlineStoreTest`，覆盖：
  - 训练局出现在 `recentGamesForUser`
  - 训练局分析回放可用
  - 外部引擎偏好不可用时回退到内置

## 阶段 2：后端练习运行时

- 新增 `com.xiangqi.online.practice` 包。
- 实现 `PracticeGameHub`，维护活动练习局的内存态。
- 为象棋与五子棋分别实现 AI 走子调用与快照构建。
- 练习局与房间局严格分离，不接入 `OnlineRoomHub`。

## 阶段 3：持久化扩展

- 扩展 `online/schema.sql` 的 `games` 表训练元数据字段。
- 扩展 `OnlineStore` 的创建、更新、最近对局、分析查询。
- 让训练局仍写入 `game_moves`，保持分析回放兼容。

## 阶段 4：HTTP 接口

- 在 `OnlineSiteServer` 增加学习页练习对局接口。
- 对所有练习接口维持登录校验。
- 返回结构与现有 game snapshot 尽量对齐，附加训练字段。

## 阶段 5：前端学习页与练习页

- 将 `learn` 从占位页改为 AI 对局入口页。
- 增加 practice 路由和独立 UI。
- 创建、走子、认输、分析跳转走学习 API，不混入房间按钮和邀请语义。
- 在首页/个人页最近对局中标记 AI 练习。

## 阶段 6：验证

- 运行针对性测试，确认先红后绿。
- 运行完整 `mvn -q test`。
- 运行 `mvn -q -DskipTests package`。
- 只报告新鲜验证得到的实际结果与剩余边界。
