# Legacy Homepage Board Face Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复首页旧棋盘 AI 对局视图中 canvas 一直被隐藏、导致棋盘区域空白的问题。

**Architecture:** 该修复只涉及首页旧棋盘资源层。HTML 负责为棋盘面提供 `data-board-face` 标识，JS 负责给 `flipStage` 写入当前 `data-game`，CSS 继续使用现有选择器控制显示。测试层新增资源契约检查，防止 HTML/JS 脱节再次发生。

**Tech Stack:** HTML, vanilla JS, CSS, Java 17, Maven, JUnit 5, Playwright CLI

---

## File Structure

### Existing files to modify
- `src/main/resources/web/index.html`: 为棋盘面补 `data-board-face` 属性
- `src/main/resources/web/app.js`: 在 `syncGamePanels(...)` 中设置 `flipStage.dataset.game`

### New test files to create
- `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

---

## Chunk 1: 先锁资源契约

### Task 1: 写一个失败测试，固定首页棋盘面显示契约

**Files:**
- Create: `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

- [ ] **Step 1: 读取 `/web/index.html` 与 `/web/app.js` 资源**

```java
String html = readResource("/web/index.html");
String js = readResource("/web/app.js");
```

- [ ] **Step 2: 写失败断言，要求 HTML 提供两种棋盘面的标记**

```java
assertTrue(html.contains("data-board-face=\"XIANGQI\""));
assertTrue(html.contains("data-board-face=\"GOMOKU\""));
```

- [ ] **Step 3: 写失败断言，要求 JS 同步激活当前棋种**

```java
assertTrue(js.contains("flipStage.dataset.game"));
```

- [ ] **Step 4: 运行测试确认当前失败**

Run: `mvn -q -Dtest=LegacyHomepageResourceContractTest test`
Expected: FAIL

---

## Chunk 2: 最小实现

### Task 2: 补齐 HTML 和 JS 的棋盘面激活信息

**Files:**
- Modify: `src/main/resources/web/index.html`
- Modify: `src/main/resources/web/app.js`

- [ ] **Step 1: 在首页棋盘两个 `.boardFace` 上补 `data-board-face`**

```html
<div class="boardFace boardFaceFront" data-board-face="XIANGQI">
<div class="boardFace boardFaceBack" data-board-face="GOMOKU">
```

- [ ] **Step 2: 在 `syncGamePanels(...)` 中写入 `flipStage.dataset.game = g`**

```js
if (flipStage) {
  flipStage.dataset.game = g;
}
```

- [ ] **Step 3: 保持现有 CSS 选择器不变**

Run: file edit only
Expected: 无需改 CSS 即可让激活面显示

---

## Chunk 3: 验证

### Task 3: 跑测试并做浏览器验证

**Files:**
- Modify: `src/main/resources/web/index.html`
- Modify: `src/main/resources/web/app.js`
- Test: `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`

- [ ] **Step 1: 运行目标测试确认通过**

Run: `mvn -q -Dtest=LegacyHomepageResourceContractTest test`
Expected: PASS

- [ ] **Step 2: 运行完整测试集**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 3: 用浏览器验证首页旧棋盘视图**

Run: open `https://xiangqiarena.com/`
Expected: 点击“中国象棋”入口后，棋盘 canvas 不再空白

- [ ] **Step 4: 用 DOM 验证激活棋盘面可见**

Run: evaluate `#flipStage.dataset.game` and `.boardFace` computed styles
Expected: 当前棋种对应的 `.boardFace` 为 `display:flex`

- [ ] **Step 5: 提交**

```bash
git add src/main/resources/web/index.html src/main/resources/web/app.js src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java docs/superpowers/specs/2026-03-27-legacy-board-face-design.md docs/superpowers/plans/2026-03-27-legacy-board-face-fix-plan.md
git commit -m "fix(web): restore legacy homepage board canvas visibility"
```
