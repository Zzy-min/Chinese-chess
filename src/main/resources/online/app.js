const state = {
  bootstrap: null,
  me: null,
  lobby: null,
  room: null,
  game: null,
  profile: null,
  analysis: null,
  analysisStep: 0,
  endgames: null,
  endgamesLoaded: false,
  hintHighlight: null,
  authMode: 'login',
  authDialogOpen: false,
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
const routes = ['home', 'play', 'room', 'game', 'practice', 'analysis', 'learn', 'ai', 'watch', 'community', 'me'];
const moveAudio = new Audio('/assets/audio/move.wav');
const resultAudio = new Audio('/assets/audio/mate.wav');
moveAudio.preload = 'auto';
resultAudio.preload = 'auto';
moveAudio.volume = 0.72;
resultAudio.volume = 0.92;
let audioUnlocked = false;
let soundEnabled = (localStorage.getItem('xq_online_sound_enabled') ?? '1') !== '0';
let lastMoveSoundKey = '';
let lastResultSoundKey = '';

window.addEventListener('hashchange', render);
window.addEventListener('load', boot);
document.addEventListener('pointerdown', unlockAudio, { once: true });
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
      ${shouldShowAuthOverlay(route) ? renderAuthOverlay(route.page === 'home') : ''}
    </div>
  `;
  bindCommon(route);
  syncRealtime(route);
  syncAudioFeedback();
}

function renderTopbar(active) {
  const me = state.me;
  return `
    <header class="topbar">
      <div class="brand">
        <div class="brandMark">棋</div>
        <div class="brandText">
          <strong>轻棋局</strong>
          <span>线上对弈、AI 棋桌与复盘分析</span>
        </div>
      </div>
      <nav class="nav">
        ${navLink('home', '首页', active)}
        ${navLink('play', '在线大厅', active)}
        ${navLink('ai', 'AI 棋桌', active)}
        ${navLink('learn', '学习', active)}
        ${navLink('watch', '观战', active)}
        ${navLink('community', '社区', active)}
        ${navLink('me', '个人', active)}
      </nav>
      <div class="userBar">
        <span class="muted">${me ? `@${me.username}` : '未登录'}</span>
        <button class="ghost" data-action="toggle-sound">${soundEnabled ? '音效开启' : '音效关闭'}</button>
        ${me ? '<button class="ghost" data-action="logout">退出</button>' : '<button class="btn" data-action="show-auth">登录 / 注册</button>'}
      </div>
    </header>
  `;
}

function navLink(page, label, active) {
  return `<a class="${active === page ? 'is-active' : ''}" href="#/${page}">${label}</a>`;
}

function shouldShowAuthOverlay(route) {
  if (state.me) return false;
  return route.page === 'home' ? state.authDialogOpen : true;
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
    case 'ai':
      return renderAiSetup();
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
      <div class="meta">现在开始下棋</div>
      <h1>轻棋局，把 AI 对局、在线对局和复盘放到同一个首页。</h1>
      <p>先开一局，还是直接进入房间对战，都可以从这里开始。首页只负责把模式讲清楚，把下一步做得直接。</p>
      <div class="grid cards heroStats">
        <div class="card"><div class="meta">在线房间</div><h3>${b.activeRooms}</h3><p>此刻可进入的实时对局</p></div>
        <div class="card"><div class="meta">棋手</div><h3>${b.totalUsers}</h3><p>已创建账号的玩家</p></div>
        <div class="card"><div class="meta">归档棋局</div><h3>${b.totalGames}</h3><p>结束后可直接回放与复盘</p></div>
      </div>
      ${activeRoom || activeGame ? renderActivityBanner(activeRoom, activeGame) : ''}
    </section>
    <div class="split homeSplit">
      <section class="panel">
        <div class="sectionHead">
          <h2 class="sectionTitle">选择模式</h2>
          <p class="muted">AI 对局放在第一位，在线对局作为另一种模式放在首页，进入后再决定棋种和玩法细节。</p>
        </div>
        <div class="grid cards modeCards">
          <div class="card modeCard modeCardPrimary">
            <div class="meta">Mode 01</div>
            <h3>AI 对局</h3>
            <p>进入专属棋桌后再选中国象棋或五子棋，支持难度、先后手、复盘和结果播报。</p>
            <button class="btn" data-action="go-home-ai">进入 AI 棋桌</button>
          </div>
          <div class="card modeCard">
            <div class="meta">Mode 02</div>
            <h3>在线对局</h3>
            <p>创建房间、输入邀请码或加入公开房间，支持棋钟、求和、认输与局后分析。</p>
            <button class="ghost" data-nav="play">进入在线大厅</button>
          </div>
          <div class="card modeCard">
            <div class="meta">More</div>
            <h3>观战与学习</h3>
            <p>观战、学习和社区继续保留在导航里，保持统一入口，不在首页占太大篇幅。</p>
            <button class="ghost" data-nav="learn">查看学习内容</button>
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="sectionHead">
          <h2 class="sectionTitle">最近归档</h2>
          <p class="muted">刚结束的棋局会立刻进入可分析状态，首页可以直接回看。</p>
        </div>
        <div class="moves">
          ${(b.recentGames || []).length ? b.recentGames.map(renderRecentGameCard).join('') : '<div class="banner">还没有归档对局，先开始一局 AI 对局或进入在线大厅。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderAiSetup() {
  const cfg = state.learnConfig;
  const engines = engineOptions(cfg.gameType);
  return `
    <section class="hero">
      <div class="meta">AI 对局</div>
      <h1>AI 棋桌</h1>
      <p>选择棋种、难度和先后手，后端会自动匹配合适的 AI 引擎。练完直接复盘，练习局会自动归档到最近对局。</p>
    </section>
    <section class="panel">
      <h2 class="sectionTitle">配置对局</h2>
      <div class="stack">
        <div class="field">
          <label>棋种</label>
          <select data-learn-field="gameType">
            <option value="XIANGQI" ${cfg.gameType === 'XIANGQI' ? 'selected' : ''}>中国象棋</option>
            <option value="GOMOKU" ${cfg.gameType === 'GOMOKU' ? 'selected' : ''}>五子棋</option>
          </select>
        </div>
        <div class="field">
          <label>难度</label>
          <select data-learn-field="difficulty">
            <option value="EASY" ${cfg.difficulty === 'EASY' ? 'selected' : ''}>简单</option>
            <option value="MEDIUM" ${cfg.difficulty === 'MEDIUM' ? 'selected' : ''}>中等</option>
            <option value="HARD" ${cfg.difficulty === 'HARD' ? 'selected' : ''}>困难</option>
          </select>
        </div>
        <div class="field">
          <label>先后手</label>
          <select data-learn-field="humanFirst">
            <option value="true" ${cfg.humanFirst ? 'selected' : ''}>我先手（红方 / 黑方）</option>
            <option value="false" ${!cfg.humanFirst ? 'selected' : ''}>AI 先手</option>
          </select>
        </div>
        <div class="field">
          <label>AI 引擎</label>
          <select data-learn-field="preferredEngine">
            ${engines.map(e => `<option value="${e.value}" ${cfg.preferredEngine === e.value ? 'selected' : ''}>${e.label}</option>`).join('')}
          </select>
        </div>
        <button class="btn" data-action="create-practice">开始对局</button>
        <div class="status">${state.status || ''}</div>
      </div>
    </section>
  `;
}

function renderLearn() {
  if (!state.endgamesLoaded) {
    loadEndgames();
  }
  const endgames = state.endgames || [];
  const groups = { beginner: [], intermediate: [], advanced: [] };
  endgames.forEach(eg => {
    const d = (eg.difficulty || 'beginner').toLowerCase();
    if (groups[d]) groups[d].push(eg);
  });
  const labels = { beginner: '入门', intermediate: '进阶', advanced: '高级' };
  const icons = { beginner: '♟', intermediate: '♞', advanced: '♛' };
  const total = endgames.length;
  return `
    <section class="hero">
      <div class="meta">残局练习</div>
      <h1>残局训练场</h1>
      <p>精选 ${total} 道残局题，从基本杀法到经典江湖残局，逐步提升你的棋力。点击任意残局即可开始练习。</p>
    </section>
    <section class="panel learnPanel">
      <div class="learnFilter">
        <span class="muted">难度筛选：</span>
        <button class="ghost learnFilterBtn is-active" data-filter="all">全部 (${total})</button>
        <button class="ghost learnFilterBtn" data-filter="beginner">入门 (${groups.beginner.length})</button>
        <button class="ghost learnFilterBtn" data-filter="intermediate">进阶 (${groups.intermediate.length})</button>
        <button class="ghost learnFilterBtn" data-filter="advanced">高级 (${groups.advanced.length})</button>
      </div>
      ${['beginner', 'intermediate', 'advanced'].map(diff => `
        <div class="learnGroup" data-difficulty="${diff}">
          <div class="learnGroupHead">
            <span class="learnGroupIcon">${icons[diff]}</span>
            <h2>${labels[diff]}</h2>
            <span class="muted">${groups[diff].length} 题</span>
          </div>
          <div class="grid cards learnCards">
            ${groups[diff].map(eg => `
              <div class="card learnCard ${eg.solved === 'true' ? 'learnCard-solved' : ''}" data-endgame-id="${eg.id}">
                <div class="learnCardMeta">
                  <span class="pill learnDifficulty learnDifficulty-${diff}">${labels[diff]}</span>
                  ${eg.solved === 'true' ? '<span class="pill learnSolvedBadge">已破解</span>' : ''}
                  <span class="muted">${eg.category || '残局'}</span>
                </div>
                <h3>${eg.name}</h3>
                <p>${eg.description || ''}</p>
                <div class="learnCardSource muted">${eg.source || ''}</div>
              </div>
            `).join('')}
          </div>
        </div>
      `).join('')}
    </section>
  `;
}

async function loadEndgames() {
  if (state.endgamesLoaded) return;
  state.endgamesLoaded = true;
  try {
    const data = await fetchJson(`${API_BASE}/learn/endgames`);
    state.endgames = data.endgames || [];
  } catch (_e) {
    state.endgames = [];
  }
  render();
}

async function requestHint() {
  const game = state.game;
  if (!game || game.status === 'FINISHED' || !game.endgameName) return;
  try {
    const hint = await fetchJson(`${API_BASE}/learn/practice-games/${game.gameId}/hint`, { method: 'POST' });
    state.hintHighlight = { fromRow: hint.fromRow, fromCol: hint.fromCol, toRow: hint.toRow, toCol: hint.toCol };
    state.game = enrichGame({ ...game, hintUsed: hint.hintUsed });
    render();
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function startEndgamePractice(endgameId) {
  try {
    state.status = '';
    state.hintHighlight = null;
    const data = await fetchJson(`${API_BASE}/learn/endgames`);
    const eg = (data.endgames || []).find(e => e.id === endgameId);
    if (!eg) { state.status = '残局不存在'; render(); return; }
    const config = {
      gameType: 'XIANGQI',
      difficulty: 'MEDIUM',
      humanFirst: true,
      preferredEngine: 'BUILTIN',
      fen: eg.fen,
      endgameId: eg.id,
      endgameName: eg.name
    };
    const game = await fetchJson(`${API_BASE}/learn/practice-games`, {
      method: 'POST',
      body: JSON.stringify(config)
    });
    state.game = enrichGame(game);
    await refreshBootstrapAndProfile();
    navTo(`practice/${game.gameId}`);
  } catch (error) {
    state.status = error.message;
    render();
  }
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
  const finished = game.status !== 'PLAYING';
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
        ${finished ? renderFinishedBanner(game, true) : `<div class="status">${game.resultText || '在线对局进行中'}</div>`}
        ${drawOffer ? renderDrawOfferBanner(drawOffer, canRespondDraw) : ''}
        ${board}
        ${finished ? renderFinishedActions(game, true) : `
          <div class="roomRow">
            <button class="ghost" data-nav="room/${game.roomId}">回到房间</button>
            <button class="ghost" data-nav="analysis/${game.gameId}">进入分析</button>
            ${canOfferDraw ? '<button class="ghost" data-action="offer-draw">求和</button>' : ''}
            <button class="danger" data-action="resign">认输</button>
          </div>
        `}
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
  const finished = game.status !== 'PLAYING';
  return `
    <div class="split">
      <section class="boardWrap">
        <div class="gameMetaRow">
          <span class="pill">AI 练习</span>
          ${game.endgameName ? `<span class="pill pill-endgame">${game.endgameName}</span>` : ''}
          <span class="pill">${game.gameType}</span>
          <span class="pill">${game.viewerSide || inferViewerSide(game)}</span>
          <span class="pill">轮到 ${game.currentTurn || '-'}</span>
          <span class="pill">${game.status}</span>
        </div>
        <div class="grid cards">
          <div class="card"><div class="meta">AI</div><h3>${ai.engineText}</h3><p>${ai.engineId} · ${ai.difficulty}</p></div>
          <div class="card"><div class="meta">对手</div><h3>${practiceOpponent(game)}</h3><p>${game.aiSide || ai.side || '-'}</p></div>
        </div>
        ${finished ? renderFinishedBanner(game, false) : `
          <div class="status">${game.resultText || '轮到你落子后，后端会立刻返回 AI 应手。'}</div>
          ${game.endgameName ? `<div class="hintRow"><button class="ghost" data-action="request-hint">提示</button>${game.hintUsed ? `<span class="hintCount muted">已用 ${game.hintUsed} 次提示</span>` : ''}</div>` : ''}
        `}
        ${board}
        ${finished ? renderFinishedActions(game, false) : `
          <div class="roomRow">
            <button class="ghost" data-nav="home">返回首页</button>
            <button class="ghost" data-nav="analysis/${game.gameId}">进入分析</button>
            ${game.endgameName ? '<button class="ghost" data-action="request-hint">提示</button>' : ''}
            <button class="danger" data-action="resign">认输</button>
          </div>
        `}
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

function renderFinishedBanner(game, isOnline) {
  const isPuzzleWin = !isOnline && game.endgameName && game.winnerSide && game.winnerSide === (game.viewerSide || inferViewerSide(game));
  return `
    <div class="banner endgameBanner ${isPuzzleWin ? 'puzzleWinBanner' : ''}">
      <div>
        <strong>${isPuzzleWin ? '残局破解成功！' : isOnline ? '在线对局结束' : 'AI 对局结束'}</strong>
        <div class="muted">${game.resultText || game.terminationReason || '本局已结束'}${game.hintUsed ? ` · 用了 ${game.hintUsed} 次提示` : ''} · 共 ${game.moveCount || 0} 步</div>
      </div>
      <span class="pill">${game.status}</span>
    </div>
  `;
}

function renderFinishedActions(game, isOnline) {
  if (isOnline) {
    return `
      <div class="roomRow endgameActions">
        <button class="btn" data-action="play-online-again">再来一局</button>
        <button class="ghost" data-nav="analysis/${game.gameId}">复盘分析</button>
        <button class="ghost" data-nav="room/${game.roomId}">回到房间</button>
        <button class="ghost" data-nav="home">返回首页</button>
      </div>
    `;
  }
  return `
    <div class="roomRow endgameActions">
      <button class="btn" data-action="play-practice-again">再来一局</button>
      ${game.endgameName ? '<button class="ghost" data-nav="learn">返回残局列表</button>' : ''}
      <button class="ghost" data-nav="analysis/${game.gameId}">复盘分析</button>
      <button class="ghost" data-nav="home">返回首页</button>
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
        ${renderReadonlyBoard(analysis.gameType, board)}
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

function renderAuthOverlay(dismissible) {
  return `
    <div class="authOverlay">
      <div class="authCard">
        <div class="meta">Authentication</div>
        <h2 class="sectionTitle">登录后才能创建或加入在线房间</h2>
        ${dismissible ? '<button class="ghost authClose" data-action="close-auth">稍后再说</button>' : ''}
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
  return renderXiangqiBoardState(game.board || [], {
    interactive: true,
    disabled: !canInteractWithBoard(game),
    selectedFrom: state.selectedFrom,
    hintFrom: state.hintHighlight,
    hintTo: state.hintHighlight
  });
}

function renderGomokuBoard(game) {
  return renderGomokuBoardState(game.board || [], {
    interactive: true,
    disabled: !canInteractWithBoard(game)
  });
}

function renderReadonlyBoard(gameType, board) {
  return gameType === 'GOMOKU'
    ? renderGomokuBoardState(board, { interactive: false, disabled: false })
    : renderXiangqiBoardState(board, { interactive: false, disabled: false, selectedFrom: null });
}

function renderXiangqiBoardState(board, options = {}) {
  const rows = normalizeBoard(board);
  const interactive = options.interactive === true;
  const disabled = options.disabled === true;
  const selectedFrom = options.selectedFrom || null;
  const hintFrom = options.hintFrom || null;
  const hintTo = options.hintTo || null;
  return `<div class="xiangqiBoard">
    ${rows.map((row, r) => row.map((cell, c) => {
      const cls = ['xiangqiCell'];
      if (cell && !isRedPiece(cell)) cls.push('is-black');
      if (selectedFrom && selectedFrom.row === r && selectedFrom.col === c) cls.push('is-selected');
      if (hintFrom && hintFrom.fromRow === r && hintFrom.fromCol === c) cls.push('is-hint-from');
      if (hintTo && hintTo.toRow === r && hintTo.toCol === c) cls.push('is-hint-to');
      const attrs = interactive
        ? ` data-board="xiangqi" data-row="${r}" data-col="${c}"${disabled ? ' disabled' : ''}`
        : '';
      return `<button class="${cls.join(' ')}"${attrs}>${cell || ''}</button>`;
    }).join('')).join('')}
  </div>`;
}

function renderGomokuBoardState(board, options = {}) {
  const rows = normalizeBoard(board);
  const interactive = options.interactive === true;
  const disabled = options.disabled === true;
  return `<div class="gomokuBoard">
    ${rows.map((row, r) => row.map((cell, c) => {
      const cls = ['gomokuCell'];
      if (cell === 'BLACK') cls.push('is-black');
      if (cell === 'WHITE') cls.push('is-white');
      const attrs = interactive
        ? ` data-board="gomoku" data-row="${r}" data-col="${c}"${disabled ? ' disabled' : ''}`
        : '';
      return `<button class="${cls.join(' ')}"${attrs}></button>`;
    }).join('')).join('')}
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
  on('[data-action="toggle-sound"]', toggleSound);
  on('[data-action="show-auth"]', showAuthDialog);
  on('[data-action="close-auth"]', closeAuthDialog);
  on('[data-action="logout"]', logout);
  on('[data-action="go-home-main"]', () => navTo('home'));
  on('[data-action="go-home-ai"]', () => navTo('ai'));
  on('[data-action="submit-auth"]', submitAuth);
  on('[data-action="create-room"]', createRoom);
  on('[data-action="join-by-code"]', joinByCode);
  on('[data-action="join-room"]', joinCurrentRoom);
  on('[data-action="toggle-ready"]', toggleReady);
  on('[data-action="create-practice"]', createPracticeGame);
  on('[data-action="request-hint"]', requestHint);
  document.querySelectorAll('[data-endgame-id]').forEach(el => {
    el.addEventListener('click', () => startEndgamePractice(el.getAttribute('data-endgame-id')));
  });
  document.querySelectorAll('.learnFilterBtn').forEach(btn => {
    btn.addEventListener('click', () => {
      const filter = btn.getAttribute('data-filter');
      document.querySelectorAll('.learnGroup').forEach(g => {
        g.style.display = (filter === 'all' || g.getAttribute('data-difficulty') === filter) ? '' : 'none';
      });
      document.querySelectorAll('.learnFilterBtn').forEach(b => b.classList.toggle('is-active', b === btn));
    });
  });
  on('[data-action="play-practice-again"]', playPracticeAgain);
  on('[data-action="play-online-again"]', playOnlineAgain);
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

function toggleSound() {
  soundEnabled = !soundEnabled;
  localStorage.setItem('xq_online_sound_enabled', soundEnabled ? '1' : '0');
  render();
}

function unlockAudio() {
  if (audioUnlocked) return;
  audioUnlocked = true;
  [moveAudio, resultAudio].forEach(audio => {
    const pending = audio.play();
    if (pending && pending.catch) {
      pending.then(() => {
        audio.pause();
        audio.currentTime = 0;
      }).catch(() => {});
    } else {
      audio.pause();
      audio.currentTime = 0;
    }
  });
}

function playSound(audio) {
  if (!soundEnabled || !audioUnlocked) return;
  try {
    audio.pause();
    audio.currentTime = 0;
    const pending = audio.play();
    if (pending && pending.catch) pending.catch(() => {});
  } catch (_error) {}
}

function syncAudioFeedback() {
  const game = state.game;
  if (!game) return;
  const moveCount = Array.isArray(game.moves) ? game.moves.length : 0;
  const moveKey = `${game.gameId || ''}:${moveCount}:${game.status}`;
  if (moveCount > 0 && game.status === 'PLAYING' && moveKey !== lastMoveSoundKey) {
    lastMoveSoundKey = moveKey;
    playSound(moveAudio);
  }
  const resultKey = `${game.gameId || ''}:${game.status}:${game.resultText || ''}:${game.terminationReason || ''}`;
  if (game.status !== 'PLAYING' && resultKey !== lastResultSoundKey) {
    lastResultSoundKey = resultKey;
    playSound(resultAudio);
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

function showAuthDialog() {
  state.authDialogOpen = true;
  render();
}

function closeAuthDialog() {
  state.authDialogOpen = false;
  render();
}

async function submitAuth() {
  const username = document.getElementById('authUsername').value.trim();
  const password = document.getElementById('authPassword').value;
  state.authError = '';
  try {
    const url = state.authMode === 'login' ? `${API_BASE}/auth/login` : `${API_BASE}/auth/register`;
    const data = await fetchJson(url, { method: 'POST', body: JSON.stringify({ username, password }) });
    state.me = data.user;
    state.authDialogOpen = false;
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

async function playPracticeAgain() {
  if (state.game) {
    state.learnConfig = {
      gameType: state.game.gameType || 'XIANGQI',
      difficulty: state.game.difficulty || 'MEDIUM',
      humanFirst: (state.game.viewerSide || inferViewerSide(state.game) || 'RED') === (state.game.gameType === 'GOMOKU' ? 'BLACK' : 'RED'),
      preferredEngine: state.game.aiEngine || 'BUILTIN'
    };
  }
  await createPracticeGame();
}

function playOnlineAgain() {
  if (state.game && state.game.roomId) {
    navTo(`room/${state.game.roomId}`);
    return;
  }
  navTo('play');
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
  state.hintHighlight = null;
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
