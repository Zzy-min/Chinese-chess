# Implementation Plan: Online Backend Capability Parity for Settings and Segmented Leaderboards

## 1. Preferences Backend
- 在 `src/main/resources/online/schema.sql` 增加 `user_preferences` 表。
- 在 `OnlineStore` 增加：
  - `profilePreferences(userId)`
  - `saveProfilePreferences(userId, patch)`
- 在 `PublicSiteServer` 增加：
  - `GET /online/api/profile/preferences`
  - `POST /online/api/profile/preferences`
- 校验规则：
  - `boardTheme` 仅允许 `wood` / `ink`
  - 布尔字段只接受布尔值

## 2. Community Segmented Leaderboards
- 扩展 `OnlineStore.communityLeaderboard(...)`：
  - 保留总榜
  - 新增 `byGameType`
- 为 `queryWinBoard` / `queryActivityBoard` 增加可选 `gameType` 过滤参数。
- `PublicSiteServer` 不变更路由，仅返回更丰富响应体。

## 3. Frontend Wiring
- `app.js`
  - state 中加入 `profilePreferences`
  - 新增 `loadProfilePreferences()` / `saveProfilePreferencesPatch()`
  - `boot()` 在登录态下加载偏好
  - `toggle-sound`、`toggle-theme`、`flip-board` 切换后写回后端
  - 修复 `toggle-theme` 当前不会正确切换的问题
- 首页/大厅榜单读取：
  - 优先从 `communityLeaderboard.byGameType` 取对应榜单
  - 没有时回退现有 `winBoard` / `activityBoard`

## 4. Verification
1. `node --check src/main/resources/online/app.js`
2. `mvn -q -DskipTests compile`
3. 本地接口验证：
   - 登录
   - `GET/POST /online/api/profile/preferences`
   - `GET /online/api/community/leaderboard`
4. 浏览器验证：
   - 登录后切换棋桌设置并刷新
   - 首页/大厅榜单正常显示且不报错
