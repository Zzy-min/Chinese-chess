# WSL 抓取学习资源导入实施计划

## Goal

把 WSL 抓取的象棋残局资源导入 Online 学习种子，并在前端显示 FEN 字段。

## Scope

- Modify: `src/main/resources/online/learn-content.seed.json`
- Modify: `src/main/resources/online/app.js`
- Verify: `src/main/java/com/xiangqi/online/server/OnlineStore.java`（无需改协议）

## Tasks

### Task 1: 数据导入

- [ ] 从 WSL `~/xiangqi_scrape/xqbase/残局题_FEN.json` 读取 222 条
- [ ] 转换为学习题库结构，保留 `fen/source`
- [ ] 追加到现有 `puzzles` 数组

### Task 2: 前端展示

- [ ] 在学习页题库卡片增加 `FEN` 展示块（存在则显示）

### Task 3: 验证与发布

- [ ] `mvn -q test`
- [ ] 本地接口检查 `puzzles` 数量
- [ ] 重启服务并验证公网接口与页面可见
