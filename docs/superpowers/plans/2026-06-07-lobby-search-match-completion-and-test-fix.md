# 实时在线对弈大弈大厅入口、搜索补齐及测试修复 Plan

本 Plan 旨在实现对弈大厅公开待战房间列表展示、解决匹配按钮事件冒泡以及修复已知的 Java 单元测试失败。

---

## 1. 实施步骤

### 步骤 1：修改 `src/main/resources/online/app.js`

1. **补齐加入房间函数**：
   在 `app.js` 中新增 `joinRoomById(roomId)` 异步函数，在其中调用 `POST /online/api/rooms/{roomId}/join` API。成功加入后更新 `state.room` 并导航至 `room/{roomId}`。
   ```javascript
   async function joinRoomById(roomId) {
     if (!state.me) {
       state.showAuthModal = true;
       state.authError = '请先登录，再加入房间。';
       render();
       return;
     }
     try {
       state.status = '';
       const room = await fetchJson(`${API_BASE}/rooms/${roomId}/join`, { method: 'POST', body: '{}' });
       state.room = room;
       await refreshBootstrapAndProfile();
       navTo(`room/${roomId}`);
     } catch (error) {
       state.status = error.message;
       render();
     }
   }
   ```

2. **渲染大厅公开房间列表**：
   在 `renderPlayLobbyDesk` 方法的中栏合适位置（比如最近对局上方）插入公开待战房的渲染逻辑。读取 `state.lobby.rooms`，过滤出状态为 `WAITING` 且 `guestUsername` 为空（待战）的房间：
   ```javascript
   const lobbyRooms = ((state.lobby && state.lobby.rooms) || []).filter(r => r.status === 'WAITING' && !r.guestUsername);
   ```
   如果 `lobbyRooms` 长度大于 0，则渲染房间列表：
   ```html
   <section class="panel deskLobbyRecent" style="margin-bottom: 18px;">
     <div class="deskSectionHeader">
       <h3>待战棋房</h3>
       <button class="ghost" data-action="refresh-lobby">刷新房间</button>
     </div>
     <div class="recentList">
       ${lobbyRooms.map(room => `
         <div class="recentRow">
           <span class="gameBadge ${room.gameType === 'XIANGQI' ? 'red' : 'green'}">${room.gameType === 'XIANGQI' ? '帅' : '五'}</span>
           <div class="gameDetails">
             <strong>邀请码 ${escapeHtml(room.roomCode)} · ${room.gameType === 'XIANGQI' ? '中国象棋' : '五子棋'}</strong>
             <span class="muted">房主：${escapeHtml(room.hostUsername)} · 局时 ${room.initialTimeSeconds / 60} 分钟</span>
           </div>
           <button class="btn btn-red btn-small" data-action="join-room-by-id" data-room-id="${room.roomId}">加入对局</button>
         </div>
       `).join('')}
     </div>
   </section>
   ```
   如果列表为空，则展示温馨提醒：
   ```html
   <div class="banner">当前暂无公开待战房，您可以自己创建房间或通过快速匹配寻找对手。</div>
   ```

3. **绑定新元素事件与阻止冒泡**：
   - 在 `bindCommon()` 中，为 `[data-action="join-room-by-id"]` 按钮绑定点击监听器，执行 `joinRoomById(roomId)`。
   - 为 `[data-action="refresh-lobby"]` 按钮绑定点击监听器，执行 `loadLobby()`。
   - 阻断匹配卡片和模式卡片内按钮的冒泡。将 `bindCommon` 中的 `on` 绑定逻辑微调，以接收事件对象并调用 `e.stopPropagation()`：
     ```javascript
     on('[data-action="quick-match-xiangqi"]', (e) => {
       if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
       quickStartPublicMatch('XIANGQI', 300);
     });
     on('[data-action="quick-match-gomoku"]', (e) => {
       if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
       quickStartPublicMatch('GOMOKU', 300);
     });
     on('[data-action="create-room-xiangqi"]', (e) => {
       if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
       createRoomWithPreset({ gameType: 'XIANGQI', initialTimeSeconds: 900, isPublic: false });
     });
     on('[data-action="create-room-gomoku"]', (e) => {
       if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
       createRoomWithPreset({ gameType: 'GOMOKU', initialTimeSeconds: 600, isPublic: false });
     });
     on('[data-action="start-xiangqi-game"]', (e) => {
       if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
       quickStartPublicMatch('XIANGQI', 300);
     });
     on('[data-action="start-gomoku-game"]', (e) => {
       if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
       quickStartPublicMatch('GOMOKU', 300);
     });
     ```

### 步骤 2：修复单元测试

1. **修改 `src/test/java/com/xiangqi/web/LegacyHomepageResourceContractTest.java`**：
   - 将第 58 行的 `assertTrue(onlineJs.contains("practiceInfoLine"));` 改成 `assertTrue(onlineJs.contains("boardRailNote"));`。

2. **修改 `src/test/java/com/xiangqi/web/OnlinePracticeAsyncMoveContractTest.java`**：
   - 将第 23 行的 `assertTrue(js.contains("AI 会思考并自动应手"));` 改成 `assertTrue(js.contains("你的落子已落下，等待进入 AI 思考..."));` 或 `assertTrue(js.contains("AI 思考中..."));`。

---

## 2. 验证与部署

1. **单元测试验证**：
   在根目录下运行 `mvn test`，确认无任何编译和测试错误。

2. **行为验证**：
   - 验证大厅页面中“待战棋房”列表正确渲染出来，若有 WAITING 状态房间时，列表应当显示。
   - 验证点击“加入对局”按钮是否可以成功向后台发送 join 请求并正确跳转至 `#/room/{roomId}`。
   - 验证点击“快速匹配”、“智能约局”按钮时，不会发生冒泡引发的外层 Card data-nav 跳转。
