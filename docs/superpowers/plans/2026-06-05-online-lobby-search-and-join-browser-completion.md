# Implementation Plan: Online Lobby Search and Join Browser Completion

## 1. Backend
- `OnlineStore`
  - 新增用户搜索方法，支持按用户名模糊匹配。
- `PublicSiteServer`
  - 新增 `GET /online/api/lobby/search`
  - 组合 `roomHub.publicRoomSummaries()` 与 `store.searchUsers(...)`
  - 空查询返回空数组

## 2. Frontend
- `app.js`
  - 新增 `state.lobbySearch`
  - 新增 `loadLobbySearch(query)`
  - 大厅页搜索框改可编辑并加 `id`
  - 邀请码输入框补 `id="joinCode"` 并去掉 `readonly`
  - 渲染搜索结果区，至少显示房间和玩家两组
  - 绑定搜索输入与结果点击

## 3. Tests
1. `OnlineStoreTest`
- 用户搜索大小写不敏感

2. `PublicSiteServerTest`
- `GET /online/api/lobby/search`
- `POST /online/api/rooms/join-by-code`

## 4. Browser Verification
1. 创建公开房间
2. 第二账号在大厅输入房间码并成功加入
3. 大厅输入用户名或房间码，出现真实搜索结果
4. 保留截图与汇总 JSON
