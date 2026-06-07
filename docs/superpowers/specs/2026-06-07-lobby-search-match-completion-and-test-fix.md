# 实时在线对弈大厅入口、搜索补齐及测试修复 Spec

本项目目前正在进行一个基于中国象棋和五子棋的国风 Web 应用重构。由于国风 UI 重构，很多前端布局元素、类名以及文本提示发生了变化，这导致原有的 Java 单元测试出现契约断言失效。此外，在线对弈大厅（Lobby）尚缺少直接展示公开待战房间并允许玩家加入的“大厅房间列表”入口，且匹配卡片内部按钮的点击事件会冒泡到外层容器引发非预期的路由跳变。

本 Spec 旨在定义这些问题的修复方案，以确保前后端能力对齐，并完成功能在 PC 与移动端的完整落地。

---

## 1. 核心需求分析

### 1.1 大厅公开待战房间列表（实时在线对战入口）
* **现状**：后端 API `/online/api/lobby/overview` 会返回包含 `rooms`（公开活动房间列表）的结构。前端在 `loadLobby()` 中成功获取了该数据并存储在 `state.lobby` 中，但 `renderPlayLobbyDesk` 没有在界面上渲染这些房间。
* **目标**：在大厅中栏的合适位置渲染“公开待战房”列表。
  - 展示房间的邀请码（邀请码格式为 8 位）、棋种、房主、是否在等待玩家（Guest 未加入）。
  - 若房间未满（等待加入），显示“加入棋局”的按钮。点击时调用 `joinRoomById(roomId)` 发送 `POST /online/api/rooms/{roomId}/join`，并将 `state.room` 设为返回的房间快照，最后跳转路由 `#/room/{roomId}`。
  - 样式复用现有的国风水墨/古雅木纹组件，与整体设计风格对齐。

### 1.2 快速匹配按钮的冒泡冲突
* **现状**：在大厅首页/对局页中，许多匹配卡片（例如“象棋对局”大 Card）本身带有 `data-nav="play/xiangqi"`。卡片内部有一个“立即匹配”或“进入大厅”按钮。点击内部按钮时，事件会向上冒泡，导致在触发快速对局匹配的同时也触发了卡片容器的路由跳转，破坏了匹配时的界面响应。
* **目标**：修改前端的绑定逻辑，在匹配按钮的点击处理程序中调用 `event.stopPropagation()`，彻底切断冒泡，避免路由冲突。

### 1.3 修复 Java 单元测试契约失效
* **现状**：`mvn test` 有两个测试失败：
  1. `LegacyHomepageResourceContractTest.legacyHomepageDeclaresBoardFacesAndSyncsCurrentGame:58`（因为旧版 `app.js` 的 `practiceInfoLine` 类名在国风重写中被移除，导致对 `practiceInfoLine` 的文本包含断言失效）。
  2. `OnlinePracticeAsyncMoveContractTest.practiceHumanMoveDoesNotTriggerImmediatePollAndKeepsAsyncMessaging:23`（因为 `AI 会思考并自动应手` 提示句在国风版重写中更换为其他文本，断言失效）。
* **目标**：在不改变逻辑的前提下，更新上述测试文件中的硬编码断言字符，使之符合国风 UI 下真实的 `app.js` 代码。

---

## 2. 设计与接口定义

### 2.1 待战房间加入 API 对齐
前端在大厅界面渲染 `state.lobby.rooms`，对于状态为 `WAITING` 且 `guestUsername` 为空的房间，加入一个按钮：
```html
<button class="btn btn-red btn-small" data-action="join-room-by-id" data-room-id="${room.roomId}">加入棋房</button>
```
点击该按钮时，前端执行：
```javascript
async function joinRoomById(roomId) {
  // 请求 POST /online/api/rooms/{roomId}/join
  // 成功后 navTo(`room/${roomId}`)
}
```

### 2.2 大厅公开待战房间列表渲染设计
在 `renderPlayLobbyDesk` 中，我们将“公开待战房”作为一个独立的板块展示在中栏，位于“快捷入口”或“在线象棋/五子棋”卡片的下方、最近对局的上方。
界面样式参考：
```html
<section class="panel deskLobbyRooms">
  <div class="deskSectionHeader">
    <h3>大厅待战棋房</h3>
    <button class="ghost" data-action="refresh-lobby-rooms">刷新房间</button>
  </div>
  <div class="lobbyRoomsList">
    <!-- 动态循环渲染房间 -->
  </div>
</section>
```
如果无待战棋房，展示提示文案：“当前暂无公开待战棋房，你可以自己创建一个房间或使用快速匹配。”

### 2.3 测试契约调整
- 将 `LegacyHomepageResourceContractTest.java` 第 58 行的 `practiceInfoLine` 断言替换为 `boardRailNote` 或 `boardPlayerCard` 等国风 UI 独有的渲染类名。
- 将 `OnlinePracticeAsyncMoveContractTest.java` 第 23 行的 `AI 会思考并自动应手` 替换为 `'你的落子已落下，等待进入 AI 思考...'` 或者是 `'AI 思考中...'`。

---

## 3. 验证方案

1. **测试编译通过性**：执行 `mvn test`，确认所有 56 项单元测试 100% 通过（无 Error 或 Failure）。
2. **大厅公开房加入联调**：利用后台正在运行的服务，或通过模拟操作，确认点击大厅房间列表的加入按钮时，能够发送正确的 POST 请求并导航到对战房间 `#/room/{roomId}`。
3. **事件冒泡阻断验证**：确认点击匹配按钮后，页面不产生到 `play/xiangqi` 页面的误跳转，匹配流正确执行。
