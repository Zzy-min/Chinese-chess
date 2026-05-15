# Online 棋盘回归修复实施计划

关联设计：`docs/superpowers/specs/2026-05-15-online-board-regression-rollback-and-refit.md`

## 1. 回滚危险布局改动

1. 恢复 `boardPane` 的旧 overflow 策略
2. 删除 `boardWrap` 的强制 `height:100%` / `overflow:hidden`
3. 删除本轮引入的全局 `.boardHost` 强约束样式

## 2. 收缩 JS 改动范围

1. 将 `fitBoardToViewport` 收缩回 practice 专用入口
2. 恢复旧的 practice 计算逻辑基线
3. 在 practice 的 `refreshLiveBoardSurface()` 中补“保存旧 cell / 写回新棋盘 / 再 refit”

## 3. 校准河界

1. 调整 `.xiangqiBoardRiver` 字号
2. 视情况加轻微纵向偏移
3. 保持 z-index 仍低于棋子层

## 4. 版本与验证

1. 更新 `index.html` 资源版本号
2. 运行：
   - `node --check src/main/resources/online/app.js`
3. 浏览器验证：
   - practice 页面可进入对局后棋盘可见
   - 点击棋子前后尺寸稳定
   - 河界位置正确

## 5. 部署

1. 本地验证通过后再提交
2. 推送 `main`
3. 执行 VPS 部署脚本
4. 部署后复查线上资源版本与页面行为
