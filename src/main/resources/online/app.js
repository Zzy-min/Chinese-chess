const state = {
  bootstrap: null,
  me: null,
  lobby: null,
  room: null,
  game: null,
  profile: null,
  analysis: null,
  analysisStep: 0,
  authMode: 'login',
  authError: '',
  status: '',
  selectedFrom: null,
  ws: null,
  wsRoomId: '',
  learnConfig: {
    gameType: 'XIANGQI',
    difficulty: 'MEDIUM',
    humanFirst: true,
    preferredEngine: 'BUILTIN'
  }
};

const API_BASE = '/online/api';
const WS_BASE = '/online/ws';

const app = document.getElementById('app');
const routes = ['home', 'play', 'room', 'game', 'practice', 'analysis', 'learn', 'watch', 'community', 'me'];

window.addEventListener('hashchange', render);
window.addEventListener('load', boot);
window.setInterval(() => {
  const route = currentRoute();
  if ((route.page === 'game' || route.page === 'practice') && state.game) {
    render();
  }
}, 1000);

async function boot() {
  await Promise.all([loadBootstrap(), loadMe()]);
  render();
}

async function loadBootstrap() {
  state.bootstrap = await fetchJson(`${API_BASE}/site/bootstrap`).catch(() => null);
}

async function loadMe() {
  state.me = await fetchJson(`${API_BASE}/auth/me`).catch(() => null);
}

function currentRoute() {
  const raw = location.hash.replace(/^#\/?/, '');
  if (!raw) return { page: 'home', id: '' };
  const parts = raw.split('/');
  return { page: routes.includes(parts[0]) ? parts[0] : 'home', id: parts[1] || '' };
}

function navTo(path) {
  location.hash = path;
}

function render() {
  const route = currentRoute();
  app.innerHTML = `
    <div class="site">
      ${renderTopbar(route.page)}
      <main class="shell">
        ${renderPage(route)}
      </main>
      ${state.me ? '' : renderAuthOverlay()}
    </div>
  `;
  bindCommon(route);
  syncRealtime(route);
}

function renderTopbar(active) {
  const me = state.me;
  return `
    <header class="topbar">
      <div class="brand">
        <div class="brandMark">棋</div>
        <div class="brandText">
          <strong>轻棋局 Online</strong>
          <span>在线房间对局大厅</span>
        </div>
      </div>
      <nav class="nav">
        ${navLink('home', '首页', active)}
        ${navLink('play', '对局', active)}
        ${navLink('learn', '学习', active)}
        ${navLink('watch', '观战', active)}
        ${navLink('community', '社区', active)}
        ${navLink('me', '个人', active)}
      </nav>
      <div class="userBar">
        <span class="muted">${me ? `@${me.username}` : '未登录'}</span>
        ${me ? '<button class="ghost" data-action="logout">退出</button>' : '<button class="btn" data-action="show-auth">登录 / 注册</button>'}
      </div>
    </header>
  `;
}

function navLink(page, label, active) {
  return `<a class="${active === page ? 'is-active' : ''}" href="#/${page}">${label}</a>`;
}

function renderPage(route) {
  switch (route.page) {
    case 'play':
      return renderPlay();
    case 'room':
      return renderRoom(route.id);
    case 'game':
      return renderGame(route.id);
    case 'practice':
      return renderPractice(route.id);
    case 'analysis':
      return renderAnalysis(route.id);
    case 'learn':
      return renderLearn();
    case 'watch':
      return renderPlaceholder('观战', '首版仅保留结构，实时观战与旁观者模式暂未接入。');
    case 'community':
      return renderPlaceholder('社区', '排行榜、俱乐部和活动入口仍为壳层，后续再接真数据。');
    case 'me':
      return renderProfile();
    default:
      return renderHome();
  }
}

function renderHome() {
  const b = state.bootstrap || { recentGames: [], activeRooms: 0, totalUsers: 0, totalGames: 0, activity: {} };
  const activity = b.activity || {};
  const activeRoom = activity.room;
  const activeGame = activity.game;
  return `
    <section class="hero">
      <div class="meta">Online Lobby</div>
      <h1>这里专注在线房间对局</h1>
      <p>中国象棋与五子棋都可以通过房间码或公开房间开始在线对战。AI 对局已经回到站点首页，围棋在线仍保留占位，观战与社区仍是壳层。</p>
      <div class="grid cards">
        <div class="card"><div class="meta">Rooms</div><h3>${b.activeRooms}</h3><p>当前活动房间</p></div>
        <div class="card"><div class="meta">Players</div><h3>${b.totalUsers}</h3><p>已注册用户</p></div>
        <div class="card"><div class="meta">Games</div><h3>${b.totalGames}</h3><p>已归档在线局与练习局</p></div>
      </div>
    </section>
    <div class="split">
      <section class="panel">
        <h2 class="sectionTitle">快速开始</h2>
        <div class="grid cards">
          <div class="card"><h3>在线房间对局</h3><p>创建房间、分享邀请码、实时对战，支持棋钟、求和、认输与局后分析。</p><button class="btn" data-nav="play">进入大厅</button></div>
          <div class="card"><h3>首页 AI 对局</h3><p>需要人机练习时，直接回到首页使用旧棋盘开局与复盘。</p><button class="btn" data-action="go-home-ai">回到首页</button></div>
          <div class="card"><h3>围棋</h3><p>统一入口已保留，在线对战和 AI 练习都将在后续补齐。</p><button class="ghost" disabled>即将开放</button></div>
        </div>
        ${activeRoom || activeGame ? renderActivityBanner(activeRoom, activeGame) : ''}
      </section>
      <section class="panel">
        <h2 class="sectionTitle">最近对局</h2>
        <div class="moves">
          ${(b.recentGames || []).length ? b.recentGames.map(renderRecentGameCard).join('') : '<div class="banner">还没有归档对局，先去大厅或首页开始一局。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderLearn() {
  return `
    <section class="hero">
      <div class="meta">Learn</div>
      <h1>学习页仍保留壳层</h1>
      <p>AI 对局已经回到首页旧棋盘入口。这里先保留后续学习能力的位置，围棋、观战和社区的边界暂不变化。</p>
    </section>
    <div class="split">
      <section class="panel">
        <h2 class="sectionTitle">当前入口调整</h2>
        <div class="stack">
          <div class="banner">首页已经接管 AI 对局入口，并继续使用原有棋盘与复盘交互。</div>
          <button class="btn" data-action="go-home-ai">回到首页开始 AI 对局</button>
        </div>
      </section>
      <section class="panel">
        <h2 class="sectionTitle">后续预留</h2>
        <div class="moves">
          <div class="move"><div><strong>学习内容位</strong><div class="muted">指定走法训练、残局题与复盘建议仍会继续放在这里。</div></div></div>
          <div class="move"><div><strong>统一分析入口</strong><div class="muted">无论在线局还是练习局，分析页仍复用同一套回放数据。</div></div></div>
        </div>
      </section>
    </div>
  `;
}

function renderActivityBanner(room, game) {
  return `
    <div class="activityBanner">
      <div>
        <strong>${game ? '继续当前对局' : '返回活动房间'}</strong>
        <div class="muted">${game ? `${game.gameType} · ${game.status}` : `${room.gameType} · ${room.status}`}</div>
      </div>
      <button class="btn" data-nav="${game ? `game/${game.gameId}` : `room/${room.roomId}`}">${game ? '回到对局' : '回到房间'}</button>
    </div>
  `;
}

function renderPlay() {
  const rooms = (state.lobby && state.lobby.rooms) || [];
  return `
    <div class="split">
      <section class="panel">
        <h2 class="sectionTitle">创建房间</h2>
        <div class="stack">
          <div class="field"><label>棋种</label><select id="createGameType"><option value="XIANGQI">中国象棋</option><option value="GOMOKU">五子棋</option><option value="GO" disabled>围棋（即将开放）</option></select></div>
          <div class="field"><label>基础时长（秒）</label><select id="createTime"><option value="300">5 分钟</option><option value="600" selected>10 分钟</option><option value="900">15 分钟</option></select></div>
          <div class="field"><label>公开房间</label><select id="createPublic"><option value="true">是</option><option value="false" selected>否</option></select></div>
          <button class="btn" data-action="create-room">创建在线房间</button>
          <div class="status">${state.status || ''}</div>
        </div>
      </section>
      <section class="panel">
        <h2 class="sectionTitle">加入房间</h2>
        <div class="stack">
          <div class="field"><label>邀请码</label><input id="joinCode" placeholder="输入 8 位房间码" /></div>
          <button class="btn" data-action="join-by-code">通过邀请码加入</button>
          <h3>公开房间</h3>
          <div class="moves">
            ${rooms.length ? rooms.map(room => `
              <div class="move">
                <div>
                  <strong>${room.gameType}</strong>
                  <div class="muted">${room.hostUsername}${room.guestUsername ? ` vs ${room.guestUsername}` : ' · 等待加入'}</div>
                </div>
                <button class="ghost" data-nav="room/${room.roomId}">查看</button>
              </div>`).join('') : '<div class="banner">当前没有公开房间，创建一个新的也可以。</div>'}
          </div>
        </div>
      </section>
    </div>
  `;
}

function renderRoom(roomId) {
  const room = state.room;
  if (!room || room.roomId !== roomId) {
    loadRoom(roomId);
    return '<section class="panel"><h2 class="sectionTitle">房间加载中</h2></section>';
  }
  return `
    <section class="panel">
      <div class="roomRow">
        <span class="pill">房间码 ${room.roomCode}</span>
        <span class="pill">${room.gameType}</span>
        <span class="pill">${room.status}</span>
      </div>
      <h2 class="sectionTitle">邀请对手加入并准备</h2>
      <p class="muted">分享当前链接或房间码。双方都点击准备后会自动进入在线对局。时长 ${room.initialTimeSeconds || 600} 秒。</p>
      <div class="split compactSplit">
        <div class="card">
          <div class="meta">Host</div>
          <h3>${room.host.username}</h3>
          <p>${room.hostReady ? '已准备' : '等待准备'}</p>
        </div>
        <div class="card">
          <div class="meta">Guest</div>
          <h3>${room.guest ? room.guest.username : '等待加入'}</h3>
          <p>${room.guest ? (room.guestReady ? '已准备' : '等待准备') : '打开链接即可加入'}</p>
        </div>
      </div>
      <div class="roomRow" style="margin-top:18px">
        ${room.guest ? '' : '<button class="ghost" data-action="join-room">加入当前房间</button>'}
        <button class="btn" data-action="toggle-ready">${isViewerReady(room) ? '取消准备' : '我已准备'}</button>
        ${room.gameId ? `<button class="btn" data-nav="game/${room.gameId}">进入对局</button>` : ''}
      </div>
    </section>
  `;
}

function renderGame(gameId) {
  const game = state.game;
  if (!game || game.gameId !== gameId) {
    loadGame(gameId);
    return '<section class="panel"><h2 class="sectionTitle">对局加载中</h2></section>';
  }
  if (game.isTraining) {
    return renderPracticeView(game);
  }
  return renderOnlineGameView(game);
}

function renderPractice(gameId) {
  const game = state.game;
  if (!game || game.gameId !== gameId) {
    loadPractice(gameId);
    return '<section class="panel"><h2 class="sectionTitle">练习局加载中</h2></section>';
  }
  return renderPracticeView(game);
}

function renderOnlineGameView(game) {
  const board = game.gameType === 'GOMOKU' ? renderGomokuBoard(game) : renderXiangqiBoard(game);
  const drawOffer = game.drawOffer;
  const viewerSide = game.viewerSide || inferViewerSide(game);
  const canRespondDraw = drawOffer && drawOffer.side !== viewerSide;
  const canOfferDraw = game.status === 'PLAYING' && !drawOffer;
  return `
    <div class="split">
      <section class="boardWrap">
        <div class="gameMetaRow">
          <span class="pill">${game.gameType}</span>
          <span class="pill">轮到 ${game.currentTurn || '-'}</span>
          <span class="pill">${game.status}</span>
          <span class="pill">${game.terminationReason || 'LIVE'}</span>
        </div>
        <div class="clockGrid">
          ${renderClockCard(game, 'first')}
          ${renderClockCard(game, 'second')}
        </div>
        <div class="status">${game.resultText || '在线对局进行中'}</div>
        ${drawOffer ? renderDrawOfferBanner(drawOffer, canRespondDraw) : ''}
        ${board}
        <div class="roomRow">
          <button class="ghost" data-nav="room/${game.roomId}">回到房间</button>
          <button class="ghost" data-nav="analysis/${game.gameId}">进入分析</button>
          ${canOfferDraw ? '<button class="ghost" data-action="offer-draw">求和</button>' : ''}
          ${game.status === 'PLAYING' ? '<button class="danger" data-action="resign">认输</button>' : ''}
        </div>
      </section>
      <section class="panel">
        <h2 class="sectionTitle">走子记录</h2>
        <div class="moves">
          ${(game.moves || []).length ? game.moves.map(renderMoveRow).join('') : '<div class="banner">等待第一步落子。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderPracticeView(game) {
  const board = game.gameType === 'GOMOKU' ? renderGomokuBoard(game) : renderXiangqiBoard(game);
  const ai = practiceAiMeta(game);
  return `
    <div class="split">
      <section class="boardWrap">
        <div class="gameMetaRow">
          <span class="pill">AI 练习</span>
          <span class="pill">${game.gameType}</span>
          <span class="pill">${game.viewerSide || inferViewerSide(game)}</span>
          <span class="pill">轮到 ${game.currentTurn || '-'}</span>
          <span class="pill">${game.status}</span>
        </div>
        <div class="grid cards">
          <div class="card"><div class="meta">AI</div><h3>${ai.engineText}</h3><p>${ai.engineId} · ${ai.difficulty}</p></div>
          <div class="card"><div class="meta">对手</div><h3>${practiceOpponent(game)}</h3><p>${game.aiSide || ai.side || '-'}</p></div>
        </div>
        <div class="status">${game.resultText || '轮到你落子后，后端会立刻返回 AI 应手。'}</div>
        ${board}
        <div class="roomRow">
          <button class="ghost" data-nav="learn">返回学习页</button>
          <button class="ghost" data-nav="analysis/${game.gameId}">进入分析</button>
          ${game.status === 'PLAYING' ? '<button class="danger" data-action="resign">认输</button>' : '<button class="btn" data-nav="learn">再开一局</button>'}
        </div>
      </section>
      <section class="panel">
        <h2 class="sectionTitle">练习记录</h2>
        <div class="moves">
          ${(game.moves || []).length ? game.moves.map(renderMoveRow).join('') : '<div class="banner">等待第一步落子。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderClockCard(game, slot) {
  const player = slot === 'first' ? game.players.first : game.players.second;
  const side = player.side;
  const active = game.status === 'PLAYING' && game.currentTurn === side;
  const remaining = slot === 'first' ? effectiveRemaining(game, game.firstRemainingSeconds, side) : effectiveRemaining(game, game.secondRemainingSeconds, side);
  return `
    <div class="clockCard ${active ? 'is-active' : ''}">
      <div class="meta">${side}</div>
      <strong>${player.username}</strong>
      <div class="clockValue">${formatClock(remaining)}</div>
    </div>
  `;
}

function renderDrawOfferBanner(drawOffer, canRespondDraw) {
  return `
    <div class="banner actionBanner">
      <div>
        <strong>${drawOffer.username}</strong> 发起了求和请求
      </div>
      <div class="roomRow">
        ${canRespondDraw ? '<button class="btn" data-action="accept-draw">接受</button><button class="ghost" data-action="reject-draw">拒绝</button>' : '<span class="muted">等待对手回应</span>'}
      </div>
    </div>
  `;
}

function renderAnalysis(gameId) {
  const analysis = state.analysis;
  if (!analysis || analysis.gameId !== gameId) {
    loadAnalysis(gameId);
    return '<section class="panel"><h2 class="sectionTitle">分析加载中</h2></section>';
  }
  const boards = analysis.historyBoards || [analysis.board || []];
  const step = Math.max(0, Math.min(state.analysisStep, boards.length - 1));
  const board = boards[step] || analysis.board || [];
  const move = step === 0 ? null : (analysis.moves || [])[step - 1];
  return `
    <div class="split">
      <section class="boardWrap">
        <div class="gameMetaRow">
          <span class="pill">${analysis.gameType}</span>
          ${analysis.isTraining ? '<span class="pill">AI 练习</span>' : ''}
          <span class="pill">${analysis.status}</span>
          <span class="pill">步数 ${step}/${Math.max(0, boards.length - 1)}</span>
        </div>
        <div class="status">${analysis.isTraining ? `${practiceOpponent(analysis)} · ${analysis.aiEngine || '-'} · ${analysis.difficulty || '-'}` : '归档对局回放'}${move ? ` · ${move.side} ${move.notation}` : step === 0 ? ' · 开局局面' : ''}</div>
        ${analysis.gameType === 'GOMOKU' ? renderStaticGomokuBoard(board) : renderStaticXiangqiBoard(board)}
        <div class="roomRow">
          <button class="ghost" data-analysis-step="0">开局</button>
          <button class="ghost" data-analysis-step="${Math.max(0, step - 1)}">上一步</button>
          <button class="ghost" data-analysis-step="${Math.min(boards.length - 1, step + 1)}">下一步</button>
          <button class="ghost" data-analysis-step="${boards.length - 1}">终局</button>
        </div>
      </section>
      <section class="panel">
        <h2 class="sectionTitle">全部着法</h2>
        <div class="moves">
          ${(analysis.moves || []).length ? analysis.moves.map(moveItem => `
            <button class="move ${step === moveItem.index ? 'is-current' : ''}" data-analysis-step="${moveItem.index}">
              <div><strong>#${moveItem.index}</strong><div class="muted">${moveItem.side}</div></div>
              <div>${moveItem.notation}</div>
            </button>`).join('') : '<div class="banner">当前没有可回放着法。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderProfile() {
  const me = state.me;
  if (!me) {
    return renderPlaceholder('个人', '登录后可查看战绩、最近对局与活动入口。');
  }
  if (!state.profile) {
    loadProfile();
    return '<section class="panel"><h2 class="sectionTitle">个人摘要加载中</h2></section>';
  }
  const summary = state.profile.summary || {};
  const activity = state.profile.activity || {};
  return `
    <section class="hero">
      <div class="meta">Profile</div>
      <h1>@${me.username}</h1>
      <p>当前提供基础战绩摘要、最近对局和返回活动对局入口。AI 练习与在线房间对局共用归档与分析入口。</p>
    </section>
    <div class="grid cards" style="margin-top:18px">
      <div class="card"><div class="meta">Total</div><h3>${summary.totalGames || 0}</h3><p>归档对局</p></div>
      <div class="card"><div class="meta">Wins</div><h3>${summary.wins || 0}</h3><p>胜局</p></div>
      <div class="card"><div class="meta">Draws</div><h3>${summary.draws || 0}</h3><p>和局</p></div>
      <div class="card"><div class="meta">Losses</div><h3>${summary.losses || 0}</h3><p>负局</p></div>
    </div>
    ${activity.room || activity.game ? renderActivityBanner(activity.room, activity.game) : ''}
    <section class="panel" style="margin-top:18px">
      <h2 class="sectionTitle">最近对局</h2>
      <div class="moves">
        ${(state.profile.recentGames || []).length ? state.profile.recentGames.map(renderProfileGameCard).join('') : '<div class="banner">你还没有归档对局。</div>'}
      </div>
    </section>
  `;
}

function renderProfileGameCard(game) {
  return `
    <div class="move">
      <div>
        <strong>${gameLabel(game)}</strong>
        <div class="muted">${game.side} vs ${game.opponentUsername || '-'}</div>
        <div class="muted">${game.resultText || game.terminationReason || '-'}</div>
      </div>
      <button class="ghost" data-nav="analysis/${game.gameId}">分析</button>
    </div>
  `;
}

function renderRecentGameCard(game) {
  return `
    <div class="move">
      <div>
        <strong>${gameLabel(game)}</strong>
        <div class="muted">${game.firstUsername} vs ${game.secondUsername}</div>
      </div>
      <button class="ghost" data-nav="analysis/${game.gameId}">分析</button>
    </div>
  `;
}

function renderMoveRow(move) {
  return `
    <div class="move">
      <div><strong>#${move.index}</strong><div class="muted">${move.side}</div></div>
      <div>${move.notation}</div>
    </div>
  `;
}

function renderPlaceholder(title, desc) {
  return `<section class="placeholder"><h2>${title}</h2><p>${desc}</p></section>`;
}

function renderAuthOverlay() {
  return `
    <div class="authOverlay">
      <div class="authCard">
        <div class="meta">Authentication</div>
        <h2 class="sectionTitle">登录后才能创建或加入在线房间</h2>
        <div class="authTabs">
          <button class="${state.authMode === 'login' ? 'btn' : 'ghost'}" data-auth-mode="login">登录</button>
          <button class="${state.authMode === 'register' ? 'btn' : 'ghost'}" data-auth-mode="register">注册</button>
        </div>
        <div class="stack">
          <div class="field"><label>用户名</label><input id="authUsername" placeholder="例如 river-horse" /></div>
          <div class="field"><label>密码</label><input id="authPassword" type="password" placeholder="至少 8 位" /></div>
          <button class="btn" data-action="submit-auth">${state.authMode === 'login' ? '登录' : '注册并登录'}</button>
          <div class="status">${state.authError || ''}</div>
        </div>
      </div>
    </div>
  `;
}

function renderXiangqiBoard(game) {
  const rows = game.board || [];
  const disabled = !canInteractWithBoard(game);
  return `<div class="xiangqiBoard">
    ${rows.map((row, r) => row.map((cell, c) => {
      const cls = ['xiangqiCell'];
      if (cell && !isRedPiece(cell)) cls.push('is-black');
      if (state.selectedFrom && state.selectedFrom.row === r && state.selectedFrom.col === c) cls.push('is-selected');
      return `<button class="${cls.join(' ')}" data-board="xiangqi" data-row="${r}" data-col="${c}" ${disabled ? 'disabled' : ''}>${cell || ''}</button>`;
    }).join('')).join('')}
  </div>`;
}

function renderStaticXiangqiBoard(board) {
  const rows = normalizeBoard(board);
  return `<div class="xiangqiReviewBoard">
    <div class="xiangqiReviewRiver">楚河　汉界</div>
    <div class="xiangqiReviewPalace xiangqiReviewPalace--top"></div>
    <div class="xiangqiReviewPalace xiangqiReviewPalace--bottom"></div>
    <div class="xiangqiReviewGrid">
      ${rows.map(row => row.map(cell => `<button class="xiangqiReviewCell ${cell && !isRedPiece(cell) ? 'is-black' : ''}" disabled>${cell || ''}</button>`).join('')).join('')}
    </div>
  </div>`;
}

function renderGomokuBoard(game) {
  const rows = game.board || [];
  const disabled = !canInteractWithBoard(game);
  return `<div class="gomokuBoard">
    ${rows.map((row, r) => row.map((cell, c) => {
      const cls = ['gomokuCell'];
      if (cell === 'BLACK') cls.push('is-black');
      if (cell === 'WHITE') cls.push('is-white');
      return `<button class="${cls.join(' ')}" data-board="gomoku" data-row="${r}" data-col="${c}" ${disabled ? 'disabled' : ''}></button>`;
    }).join('')).join('')}
  </div>`;
}

function renderStaticGomokuBoard(board) {
  const rows = normalizeBoard(board);
  return `<div class="gomokuBoard">
    ${rows.map(row => row.map(cell => `<button class="gomokuCell ${cell === 'BLACK' ? 'is-black' : ''} ${cell === 'WHITE' ? 'is-white' : ''}" disabled></button>`).join('')).join('')}
  </div>`;
}

function normalizeBoard(board) {
  if (!Array.isArray(board)) return [];
  return board.map(row => Array.isArray(row) ? row : []);
}

function isRedPiece(text) {
  return ['帥', '帅', '仕', '相', '馬', '車', '砲', '卒'].includes(text);
}

function bindCommon(route) {
  document.querySelectorAll('[data-nav]').forEach(el => el.addEventListener('click', () => navTo(el.getAttribute('data-nav'))));
  document.querySelectorAll('[data-auth-mode]').forEach(el => el.addEventListener('click', () => {
    state.authMode = el.getAttribute('data-auth-mode');
    state.authError = '';
    render();
  }));
  document.querySelectorAll('[data-learn-field]').forEach(el => el.addEventListener('change', event => updateLearnConfig(event.currentTarget)));
  on('[data-action="logout"]', logout);
  on('[data-action="go-home-ai"]', () => { window.location.href = '/home-ai'; });
  on('[data-action="submit-auth"]', submitAuth);
  on('[data-action="create-room"]', createRoom);
  on('[data-action="join-by-code"]', joinByCode);
  on('[data-action="join-room"]', joinCurrentRoom);
  on('[data-action="toggle-ready"]', toggleReady);
  on('[data-action="create-practice"]', createPracticeGame);
  on('[data-action="offer-draw"]', offerDraw);
  on('[data-action="accept-draw"]', () => respondDraw(true));
  on('[data-action="reject-draw"]', () => respondDraw(false));
  on('[data-action="resign"]', resignGame);
  document.querySelectorAll('[data-board="xiangqi"]').forEach(el => el.addEventListener('click', onXiangqiCellClick));
  document.querySelectorAll('[data-board="gomoku"]').forEach(el => el.addEventListener('click', onGomokuCellClick));
  document.querySelectorAll('[data-analysis-step]').forEach(el => el.addEventListener('click', () => {
    state.analysisStep = Number(el.getAttribute('data-analysis-step'));
    render();
  }));
  if (route.page === 'play' && !state.lobby) {
    loadLobby();
  }
}

function updateLearnConfig(input) {
  const field = input.getAttribute('data-learn-field');
  if (!field) return;
  if (field === 'humanFirst') {
    state.learnConfig.humanFirst = input.value === 'true';
  } else {
    state.learnConfig[field] = input.value;
  }
  if (field === 'gameType') {
    const allowed = engineOptions(state.learnConfig.gameType).map(item => item.value);
    if (!allowed.includes(state.learnConfig.preferredEngine)) {
      state.learnConfig.preferredEngine = allowed[0];
    }
  }
  state.status = '';
  render();
}

function on(selector, handler) {
  const element = document.querySelector(selector);
  if (element) {
    element.addEventListener('click', handler);
  }
}

async function submitAuth() {
  const username = document.getElementById('authUsername').value.trim();
  const password = document.getElementById('authPassword').value;
  state.authError = '';
  try {
    const url = state.authMode === 'login' ? `${API_BASE}/auth/login` : `${API_BASE}/auth/register`;
    const data = await fetchJson(url, { method: 'POST', body: JSON.stringify({ username, password }) });
    state.me = data.user;
    await refreshBootstrapAndProfile();
    render();
  } catch (error) {
    state.authError = error.message;
    render();
  }
}

async function logout() {
  await fetchJson(`${API_BASE}/auth/logout`, { method: 'POST' }).catch(() => null);
  state.me = null;
  state.profile = null;
  state.room = null;
  state.game = null;
  state.analysis = null;
  state.bootstrap = null;
  state.status = '';
  await loadBootstrap();
  render();
}

async function createRoom() {
  try {
    state.status = '';
    const room = await fetchJson(`${API_BASE}/rooms`, {
      method: 'POST',
      body: JSON.stringify({
        gameType: document.getElementById('createGameType').value,
        initialTimeSeconds: Number(document.getElementById('createTime').value),
        isPublic: document.getElementById('createPublic').value === 'true'
      })
    });
    state.room = room;
    state.lobby = null;
    await refreshBootstrapAndProfile();
    navTo(`room/${room.roomId}`);
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function createPracticeGame() {
  try {
    state.status = '';
    const game = await fetchJson(`${API_BASE}/learn/practice-games`, {
      method: 'POST',
      body: JSON.stringify(state.learnConfig)
    });
    state.game = enrichGame(game);
    await refreshBootstrapAndProfile();
    navTo(`practice/${game.gameId}`);
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function joinByCode() {
  try {
    state.status = '';
    const room = await fetchJson(`${API_BASE}/rooms/join-by-code`, {
      method: 'POST',
      body: JSON.stringify({ roomCode: document.getElementById('joinCode').value.trim() })
    });
    state.room = room;
    await refreshBootstrapAndProfile();
    navTo(`room/${room.roomId}`);
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function joinCurrentRoom() {
  const route = currentRoute();
  try {
    state.room = await fetchJson(`${API_BASE}/rooms/${route.id}/join`, { method: 'POST', body: '{}' });
    await refreshBootstrapAndProfile();
    render();
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function toggleReady() {
  const route = currentRoute();
  try {
    state.room = await fetchJson(`${API_BASE}/rooms/${route.id}/ready`, {
      method: 'POST',
      body: JSON.stringify({ ready: !isViewerReady(state.room) })
    });
    await refreshBootstrapAndProfile();
    if (state.room.gameId) navTo(`game/${state.room.gameId}`);
    else render();
  } catch (error) {
    state.status = error.message;
    render();
  }
}

function isViewerReady(room) {
  if (!room || !state.me) return false;
  if (room.host && room.host.id === state.me.id) return !!room.hostReady;
  if (room.guest && room.guest.id === state.me.id) return !!room.guestReady;
  return false;
}

async function offerDraw() {
  await sendGameAction(`${gameActionBase(state.game)}/draw-offer`, {});
}

async function respondDraw(accept) {
  await sendGameAction(`${gameActionBase(state.game)}/draw-response`, { accept });
}

async function resignGame() {
  await sendGameAction(`${gameActionBase(state.game)}/resign`, {});
}

async function sendGameAction(url, body) {
  try {
    state.game = enrichGame(await fetchJson(url, { method: 'POST', body: JSON.stringify(body) }));
    await refreshBootstrapAndProfile();
    render();
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function loadLobby() {
  state.lobby = await fetchJson(`${API_BASE}/lobby/overview`).catch(() => ({ rooms: [] }));
  render();
}

async function loadRoom(roomId) {
  state.room = await fetchJson(`${API_BASE}/rooms/${roomId}`).catch(() => null);
  render();
}

async function loadGame(gameId) {
  state.game = enrichGame(await fetchJson(`${API_BASE}/games/${gameId}`).catch(() => null));
  render();
}

async function loadPractice(gameId) {
  state.game = enrichGame(await fetchJson(`${API_BASE}/learn/practice-games/${gameId}`).catch(() => null));
  render();
}

async function loadAnalysis(gameId) {
  state.analysis = await fetchJson(`${API_BASE}/games/${gameId}/analysis`).catch(() => null);
  state.analysisStep = Math.max(0, ((state.analysis && state.analysis.historyBoards) || []).length - 1);
  render();
}

async function loadProfile() {
  state.profile = await fetchJson(`${API_BASE}/profile/summary`).catch(() => null);
  render();
}

async function refreshBootstrapAndProfile() {
  await loadBootstrap();
  if (state.me) {
    state.profile = await fetchJson(`${API_BASE}/profile/summary`).catch(() => state.profile);
  }
}

function onXiangqiCellClick(event) {
  if (!canInteractWithBoard(state.game)) return;
  const row = Number(event.currentTarget.dataset.row);
  const col = Number(event.currentTarget.dataset.col);
  if (!state.selectedFrom) {
    state.selectedFrom = { row, col };
    render();
    return;
  }
  const move = { fromRow: state.selectedFrom.row, fromCol: state.selectedFrom.col, toRow: row, toCol: col };
  state.selectedFrom = null;
  sendMove(move);
}

function onGomokuCellClick(event) {
  if (!canInteractWithBoard(state.game)) return;
  sendMove({ row: Number(event.currentTarget.dataset.row), col: Number(event.currentTarget.dataset.col) });
}

async function sendMove(payload) {
  try {
    state.game = enrichGame(await fetchJson(`${gameActionBase(state.game)}/move`, { method: 'POST', body: JSON.stringify(payload) }));
    await refreshBootstrapAndProfile();
    render();
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function syncRealtime(route) {
  const desiredRoom = route.page === 'room'
    ? route.id
    : (route.page === 'game' && state.game && !state.game.isTraining ? state.game.roomId : '');
  if (!desiredRoom) {
    closeSocket();
    return;
  }
  if (state.ws && state.wsRoomId === desiredRoom) return;
  closeSocket();
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  state.ws = new WebSocket(`${protocol}//${location.host}${WS_BASE}`);
  state.wsRoomId = desiredRoom;
  state.ws.onopen = () => state.ws.send(JSON.stringify({ type: 'subscribe', roomId: desiredRoom }));
  state.ws.onmessage = async event => {
    const data = JSON.parse(event.data);
    if (data.room) state.room = data.room;
    if (data.game) state.game = enrichGame(data.game);
    if (state.room && state.room.gameId && currentRoute().page === 'room') {
      await refreshBootstrapAndProfile();
      navTo(`game/${state.room.gameId}`);
      return;
    }
    render();
  };
  state.ws.onclose = () => {
    state.ws = null;
    state.wsRoomId = '';
  };
}

function closeSocket() {
  if (state.ws) state.ws.close();
  state.ws = null;
  state.wsRoomId = '';
}

function enrichGame(game) {
  if (!game) return null;
  if (!game.viewerSide) {
    game.viewerSide = inferViewerSide(game);
  }
  return game;
}

function inferViewerSide(game) {
  if (!game || !state.me || !game.players) return '';
  if (game.players.first && game.players.first.id === state.me.id) return game.players.first.side;
  if (game.players.second && game.players.second.id === state.me.id) return game.players.second.side;
  return '';
}

function effectiveRemaining(game, remaining, side) {
  if (!game || game.status !== 'PLAYING' || game.clockState !== 'RUNNING' || game.currentTurn !== side || !game.lastTickAt) {
    return remaining || 0;
  }
  const elapsed = Math.floor((Date.now() - Date.parse(game.lastTickAt)) / 1000);
  return Math.max(0, (remaining || 0) - Math.max(0, elapsed));
}

function formatClock(totalSeconds) {
  const safe = Math.max(0, Number(totalSeconds) || 0);
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function engineOptions(gameType) {
  if (gameType === 'GOMOKU') {
    return [
      { value: 'BUILTIN', label: '内置 AI' },
      { value: 'AUTO', label: '自动选择' },
      { value: 'RAPFI', label: 'Rapfi' },
      { value: 'ALPHAGOMOKU', label: 'AlphaGomoku' }
    ];
  }
  return [
    { value: 'BUILTIN', label: '内置 AI' },
    { value: 'AUTO', label: '自动选择' },
    { value: 'PIKAFISH', label: 'Pikafish' }
  ];
}

function gameActionBase(game) {
  return game && game.isTraining
    ? `${API_BASE}/learn/practice-games/${game.gameId}`
    : `${API_BASE}/games/${game.gameId}`;
}

function canInteractWithBoard(game) {
  return !!(game && game.status === 'PLAYING' && game.viewerSide && game.currentTurn === game.viewerSide);
}

function gameLabel(game) {
  return game.isTraining ? `${game.gameType} · AI 练习` : game.gameType;
}

function practiceAiMeta(game) {
  return game.ai || {
    engineId: game.aiEngine || '-',
    engineText: game.aiEngine || 'AI',
    difficulty: game.difficulty || '-',
    side: game.aiSide || ''
  };
}

function practiceOpponent(game) {
  if (game.players && game.players.first && game.players.second) {
    if (game.players.first.opponentType === 'AI') return game.players.first.username;
    if (game.players.second.opponentType === 'AI') return game.players.second.username;
  }
  return game.opponentUsername || 'AI';
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    credentials: 'same-origin',
    ...options
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(data && data.error ? data.error : `Request failed (${response.status})`);
  }
  return data;
}
