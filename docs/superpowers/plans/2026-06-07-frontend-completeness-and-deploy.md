# 前端完整性校验、功能补齐与部署 Plan

本 Plan 定义了补齐社区实时观战、排行榜游戏类型切换、学习页面子 Tab 题库及教程内容展示三个功能，并对服务进行构建部署的实施计划。

---

## 1. 实施步骤

### 步骤 1：补齐社区观战房的实时观战按钮
1. 打开 `src/main/resources/online/app.js`，定位到 `renderWatchRooms(items)` 函数。
2. 更改房间按钮的渲染逻辑，当 `item.status === 'PLAYING'` 且 `item.gameId` 存在时，按钮显示为“实时观战”，并且导航到 `game/${item.gameId}`；若为结束对局，则显示“复盘分析”，导航到 `analysis/${item.gameId}`。

### 步骤 2：补齐排行榜（社区榜单）页面的游戏类型切换
1. 定位到 `renderCommunityPage()` 函数。
2. 在榜单标题下方添加“象棋 / 五子棋”的 Tab 切换布局。
3. 从 `state.leaderboardGameType` 中读取当前选中的棋种（默认 `XIANGQI`），获取展示 items 时使用 `leaderboardItems(leaderboardType, boardType)` 过滤。
4. 在 `bindCommon()` 中绑定 `[data-community-game-type]` 的 click 事件，点击时更新 `state.leaderboardGameType` 并重新渲染。

### 步骤 3：补齐学习页面（练习与棋谱）的子 Tab 逻辑展示
1. 定位到 `renderLearnPage(route)`。
2. 检查 `state.learnContent` 是否已加载，若为空，自动调用 `loadLearnContent()` 和 `loadLearnProgress()`，并展示“内容加载中”提示。
3. 根据当前路由，若 `route.id` 为空或为 `overview`，则展示原本的四大卡片网格。
4. 否则，高亮对应的 Tab 项，并在下方调用 `renderLearnTabContent(route.learnTab, state.learnContent, progress, route.puzzleTheme)` 渲染详细列表（如题库题目列表、教程复盘列表等）。

### 步骤 4：在 `restore_app_js.py` 原本的 Python 恢复修改逻辑中，加入上述 3 个修改并重新构建 `app.js`

为了绝对安全，排查换行符 `\r\n` 对 diff 替换的干扰，我们直接把这 3 个修改写成 Python 代码，附加在 `restore_app_js.py`（或重建这个临时脚本）中。通过运行该 Python 脚本一次性生成干净且带有完整新功能的 `src/main/resources/online/app.js`。

### 步骤 5：单元测试与重新部署

1. 运行 `mvn test` 确认全绿。
2. 运行 `mvn clean package` 重新打包。
3. 重启后台服务，以确保应用在新端口/设置下成功部署生效。

---

## 2. 详细 Python 替换逻辑设计

### 社区观战房修改
```python
old_watch_rooms = """function renderWatchRooms(items) {
  if (!items.length) {
    return '<div class="banner">暂无公开房间。</div>';
  }
  return items.map(item => `
    <div class="move">
      <div>
        <strong>${escapeHtml(item.gameType || '')}</strong>
        <div class="muted">${escapeHtml(item.players && item.players.first ? item.players.first.username : '')} vs ${escapeHtml(item.players && item.players.second ? item.players.second.username : '等待加入')}</div>
        <div class="muted">状态：${escapeHtml(item.status || '')}</div>
      </div>
      ${item.gameId ? `<button class="ghost" data-nav="analysis/${escapeHtml(item.gameId)}">分析</button>` : '<span class="pill">等待开局</span>'}
    </div>
  `).join('');
}"""

new_watch_rooms = """function renderWatchRooms(items) {
  if (!items.length) {
    return '<div class="banner">暂无公开房间。</div>';
  }
  return items.map(item => {
    let actionBtn = '<span class="pill">等待开局</span>';
    if (item.gameId) {
      if (item.status === 'PLAYING') {
        actionBtn = `<button class="btn btn-red btn-small" data-nav="game/${escapeHtml(item.gameId)}">实时观战</button>`;
      } else {
        actionBtn = `<button class="ghost" data-nav="analysis/${escapeHtml(item.gameId)}">复盘分析</button>`;
      }
    }
    return `
      <div class="move">
        <div>
          <strong>${escapeHtml(item.gameType || '')}</strong>
          <div class="muted">${escapeHtml(item.players && item.players.first ? item.players.first.username : '')} vs ${escapeHtml(item.players && item.players.second ? item.players.second.username : '等待加入')}</div>
          <div class="muted">状态：${escapeHtml(item.status || '')}</div>
        </div>
        ${actionBtn}
      </div>
    `;
  }).join('');
}"""
```

### 排行榜页面切换 Tab 修改
```python
old_community_page = """function renderCommunityPage() {
  if (!state.communityLeaderboard) {
    loadCommunityLeaderboard();
    return '<section class="panel"><h2 class="sectionTitle">社区榜单加载中</h2></section>';
  }
  const board = state.communityLeaderboard || { winBoard: [], activityBoard: [] };
  const isWin = state.communityTab === 'win';
  const items = isWin ? (board.winBoard || []) : (board.activityBoard || []);
  const title = isWin ? '胜局榜' : '活跃榜';
  const quickEntry = state.me
    ? '<div class="roomRow" style="margin-top:12px"><button class="btn" data-nav="me">查看我的主页</button><button class="ghost" data-nav="play">进入对局大厅</button></div>'
    : '<div class="banner" style="margin-top:12px">登录后会高亮你的榜单位置，并提供个人主页快捷入口。</div>';
  return `
    <section class="hero">
      <div class="meta">排行榜</div>
      <h1>周榜与活跃榜</h1>
      <p>默认按最近 ${board.windowDaysUsed || board.requestedWindowDays || 30} 天统计。若样本不足会自动回退到全量历史。</p>
      ${quickEntry}
    </section>
    <section class="panel" style="margin-top:18px">
      <div class="roomRow" style="margin-bottom:12px">
        <button class="${isWin ? 'btn' : 'ghost'}" data-community-tab="win">胜局榜</button>
        <button class="${!isWin ? 'btn' : 'ghost'}" data-community-tab="activity">活跃榜</button>
      </div>
      ${board.fallbackToAllTime ? '<div class="banner">近 30 天样本较少，当前展示全量历史榜单。</div>' : ''}
      <h2 class="sectionTitle">${title}</h2>
      <div class="moves">${renderCommunityItems(items, isWin)}</div>
    </section>
  `;
}"""

new_community_page = """function renderCommunityPage() {
  if (!state.communityLeaderboard) {
    loadCommunityLeaderboard();
    return '<section class="panel"><h2 class="sectionTitle">社区榜单加载中</h2></section>';
  }
  const board = state.communityLeaderboard || { winBoard: [], activityBoard: [] };
  const isWin = state.communityTab === 'win';
  const leaderboardType = state.leaderboardGameType === 'GOMOKU' ? 'GOMOKU' : 'XIANGQI';
  const items = isWin ? leaderboardItems(leaderboardType, 'winBoard') : leaderboardItems(leaderboardType, 'activityBoard');
  const title = isWin ? '胜局榜' : '活跃榜';
  const quickEntry = state.me
    ? '<div class="roomRow" style="margin-top:12px"><button class="btn" data-nav="me">查看我的主页</button><button class="ghost" data-nav="play">进入对局大厅</button></div>'
    : '<div class="banner" style="margin-top:12px">登录后会高亮你的榜单位置，并提供个人主页快捷入口。</div>';
  return `
    <section class="hero">
      <div class="meta">排行榜</div>
      <h1>周榜与活跃榜</h1>
      <p>默认按最近 ${board.windowDaysUsed || board.requestedWindowDays || 30} 天统计。若样本不足会自动回退到全量历史。</p>
      ${quickEntry}
    </section>
    <section class="panel" style="margin-top:18px">
      <div class="roomRow" style="margin-bottom:12px; display: flex; justify-content: space-between; align-items: center;">
        <div style="display: flex; gap: 8px;">
          <button class="${isWin ? 'btn' : 'ghost'}" data-community-tab="win">胜局榜</button>
          <button class="${!isWin ? 'btn' : 'ghost'}" data-community-tab="activity">活跃榜</button>
        </div>
        <div class="tabHeader" style="display: flex; gap: 4px;">
          <button class="tabItem ${leaderboardType === 'XIANGQI' ? 'active' : ''}" data-community-game-type="XIANGQI">象棋</button>
          <button class="tabItem ${leaderboardType === 'GOMOKU' ? 'active' : ''}" data-community-game-type="GOMOKU">五子棋</button>
        </div>
      </div>
      ${board.fallbackToAllTime ? '<div class="banner">近 30 天样本较少，当前展示全量历史榜单。</div>' : ''}
      <h2 class="sectionTitle">${leaderboardType === 'XIANGQI' ? '中国象棋' : '五子棋'} · ${title}</h2>
      <div class="moves">${renderCommunityItems(items, isWin)}</div>
    </section>
  `;
}"""
```

### 学习（learn）页面重写
```python
old_learn_page = """function renderLearnPage(route) {
  return `
    <div class="learnPage">
      <section class="hero">
        <div class="meta">练习</div>
        <h1>练习与棋谱</h1>
        <p>从残局题库、教程复盘到 AI 对战，当前网页端的学习能力全部保持免费可用。</p>
      </section>
      
      <div class="learnTabs">
        <button class="pill is-active">总览</button>
        <button class="pill" data-nav="learn/puzzles/ALL">残局题库</button>
        <button class="pill" data-nav="learn/tutorials">教程复盘</button>
        <button class="pill" data-nav="learn/practice">AI 对战</button>
        <button class="pill" data-action="quick-start-gomoku-practice">五子棋练习</button>
      </div>
      
      <div class="learnGrid">
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge xq" style="background:var(--brand-red); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">帅</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">象棋残局题库</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">从战术、杀法到残局局面，按题型进入专项训练。</p>
            </div>
          </div>
          <button class="btn btn-red btn-small" data-nav="learn/puzzles/ALL" style="padding:6px 14px;">进入题库</button>
        </div>
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge xq" style="background:var(--brand-red); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">帅</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">象棋教程复盘</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">按主题回看经典局面，逐步理解布局与中局转换。</p>
            </div>
          </div>
          <button class="btn btn-red btn-small" data-nav="learn/tutorials" style="padding:6px 14px;">查看教程</button>
        </div>
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge xq" style="background:var(--brand-red); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">帅</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">象棋 AI 对战</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">直接进入 AI 棋桌，保留悔棋、认输和局后分析能力。</p>
            </div>
          </div>
          <button class="btn btn-red btn-small" data-action="quick-start-ai-practice" style="padding:6px 14px;">开始练习</button>
        </div>
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge go" style="background:var(--brand-green); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">五</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">五子棋专项训练</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">从快速练习进入五子棋 AI 对局，保留复盘与统计入口。</p>
            </div>
          </div>
          <button class="btn btn-green btn-small" data-action="quick-start-gomoku-practice" style="padding:6px 14px;">开始练习</button>
        </div>
      </div>
    </div>
  `;
}"""

new_learn_page = """function renderLearnPage(route) {
  if (!state.learnContent) {
    loadLearnContent();
    if (state.me && !state.learnProgress) {
      loadLearnProgress();
    }
    return '<section class="panel"><h2 class="sectionTitle">学习内容加载中</h2></section>';
  }
  const progress = state.learnProgress || { tutorialsCompleted: [], puzzlesCompleted: [] };
  const isOverview = !route.id || route.id === 'overview';
  
  let tabContentHtml = '';
  if (isOverview) {
    tabContentHtml = `
      <div class="learnGrid">
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge xq" style="background:var(--brand-red); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">帅</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">象棋残局题库</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">从战术、杀法到残局局面，按题型进入专项训练。</p>
            </div>
          </div>
          <button class="btn btn-red btn-small" data-nav="learn/puzzles/ALL" style="padding:6px 14px;">进入题库</button>
        </div>
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge xq" style="background:var(--brand-red); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">帅</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">象棋教程复盘</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">按主题回看经典局面，逐步理解布局与中局转换。</p>
            </div>
          </div>
          <button class="btn btn-red btn-small" data-nav="learn/tutorials" style="padding:6px 14px;">查看教程</button>
        </div>
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge xq" style="background:var(--brand-red); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">帅</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">象棋 AI 对战</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">直接进入 AI 棋桌，保留悔棋、认输和局后分析能力。</p>
            </div>
          </div>
          <button class="btn btn-red btn-small" data-action="quick-start-ai-practice" style="padding:6px 14px;">开始练习</button>
        </div>
        <div class="panel learnCard">
          <div class="learnCardLeft">
            <span class="learnCardBadge go" style="background:var(--brand-green); color:#fff; width:36px; height:36px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:16px;">五</span>
            <div class="learnCardInfo" style="margin-left:14px;">
              <h3 style="margin:0; font-size:16px;">五子棋专项训练</h3>
              <p class="muted" style="margin:4px 0 0; font-size:12px;">从快速练习进入五子棋 AI 对局，保留复盘与统计入口。</p>
            </div>
          </div>
          <button class="btn btn-green btn-small" data-action="quick-start-gomoku-practice" style="padding:6px 14px;">开始练习</button>
        </div>
      </div>
    `;
  } else {
    tabContentHtml = renderLearnTabContent(route.learnTab, state.learnContent, progress, route.puzzleTheme);
  }

  return `
    <div class="learnPage">
      <section class="hero">
        <div class="meta">练习</div>
        <h1>练习与棋谱</h1>
        <p>从残局题库、教程复盘到 AI 对战，当前网页端的学习能力全部保持免费可用。</p>
      </section>
      
      <div class="learnTabs">
        <button class="pill ${isOverview ? 'is-active' : ''}" data-nav="learn">总览</button>
        <button class="pill ${route.learnTab === 'puzzles' ? 'is-active' : ''}" data-nav="learn/puzzles/ALL">残局题库</button>
        <button class="pill ${route.learnTab === 'tutorials' ? 'is-active' : ''}" data-nav="learn/tutorials">教程复盘</button>
        <button class="pill ${route.learnTab === 'practice' ? 'is-active' : ''}" data-nav="learn/practice">AI 对战</button>
        <button class="pill" data-action="quick-start-gomoku-practice">五子棋练习</button>
      </div>
      
      <div class="learnMainContent" style="margin-top:18px;">
        ${tabContentHtml}
      </div>
    </div>
  `;
}"""
```
