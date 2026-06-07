# 实施计划：移动端底部导航遮挡与滚动间距修复

本计划实施 CSS 底部安全内边距的修改，以及 Playwright 测试截屏逻辑的优化。

---

## 1. 详细实施步骤

### 步骤一：追加 CSS 底部留白
- 打开 [src/main/resources/online/app.css](file:///C:/Users/Lenovo/Chinese-chess/src/main/resources/online/app.css)。
- 在 `@media (max-width: 768px)` 媒体查询（第 3263 行左右）中，将 `.shell, .wrap` 的 `padding: 8px !important;` 替换为：
  ```css
  padding: 8px 8px 80px 8px !important;
  ```
  这为页面底端预留了 80px 的空隙。

### 步骤二：更新 Playwright 测试脚本以消除截图伪影
- 打开 [C:\Users\Lenovo\.gemini\antigravity-cli\brain\97274f14-5066-4afb-9186-d47ff35e0154\scratch\test_mobile_layout.js](file:///C:/Users/Lenovo/.gemini/antigravity-cli/brain/97274f14-5066-4afb-9186-d47ff35e0154/scratch/test_mobile_layout.js)。
- 在执行首页全屏截图前，添加隐藏 `.bottomNav` 的 evaluate 逻辑，并在截图后将其恢复。
- 保证生成的 `mobile_home_verified.png` 干净美观。

### 步骤三：同步并重启服务验证
- 运行 `mvn compile` 重新构建资源包。
- 重新执行 `test_mobile_layout.js` 截屏比对，校验布局。

---

## 2. 预期成果
- 滚动到大厅或棋盘底部时，所有操作按钮完全展露，无被遮挡死角。
- `mobile_home_verified.png` 全图布局清晰，快捷入口卡片文字不再重叠或缺损。
