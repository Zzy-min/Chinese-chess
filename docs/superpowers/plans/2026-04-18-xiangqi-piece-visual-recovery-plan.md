# Online 象棋棋子视觉恢复实施计划

## Goal

恢复 Online 象棋棋子的圆形可视样式，解决“有字无子”的问题。

## Scope

- Modify: `src/main/resources/online/app.css`
- Modify: `src/main/resources/online/index.html`（版本号刷新）

## Tasks

### Task 1: 样式修复

- [ ] 为 `.xiangqiCell .piece` 增加圆形棋子底盘样式
- [ ] 调整红黑文字色与字号适配
- [ ] 为选中态增加不破坏底盘的高亮样式

### Task 2: 发布与验证

- [ ] 升级静态资源版本号
- [ ] `mvn -q test` 全量通过
- [ ] 本地与公网截图验收棋子视觉恢复
