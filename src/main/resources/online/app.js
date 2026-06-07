const state = {
  bootstrap: null,
  me: null,
  lobby: null,
  room: null,
  game: null,
  profile: null,
  analysis: null,
  analysisStep: 0,
  learnContent: null,
  learnProgress: null,
  watchOverview: null,
  communityLeaderboard: null,
  lobbySearch: {
    query: '',
    rooms: [],
    players: [],
    loading: false,
    error: ''
  },
  lobbySearchTimer: 0,
  lobbySearchRequestId: 0,
  leaderboardGameType: 'XIANGQI',
  watchFilters: {
    gameType: 'ALL',
    status: 'ALL'
  },
  communityTab: 'win',
  watchUpdatedAt: 0,
  authMode: 'login',
  authError: '',
  showAuthModal: false,
  status: '',
  selectedFrom: null,
  pendingMoveMarker: null,
  pendingMoveGameId: '',
  boardFitKey: '',
  boardRefitTimer: 0,
  learnPuzzleTheme: 'ALL',
  boardPaneTab: 'board',
  moveInFlight: false,
  moveRequestToken: 0,
  aiMoveHintText: '',
  aiMoveHintExpireAt: 0,
  aiMoveHintGameId: '',
  aiMoveHintMoveIndex: 0,
  practicePollTimeout: null,
  practicePollGameId: '',
  practicePollStartedAtMs: 0,
  practicePollInFlight: false,
  ws: null,
  wsRoomId: '',
  endGameModal: null,
  endGameModalShownKey: '',
  soundEnabled: readOnlineSoundEnabled(),
  audioUnlocked: false,
  lastMoveSoundGameId: '',
  lastMoveSoundIndex: 0,
  lastFinishSoundKey: '',
  learnConfig: {
    gameType: 'XIANGQI',
    difficulty: 'MEDIUM',
    humanFirst: true,
    preferredEngine: 'BUILTIN'
  }
};

const API_BASE = '/online/api';
const WS_BASE = '/online/ws';
const WATCH_POLL_INTERVAL_MS = 10000;
const LEARN_SUB_ROUTES = ['tutorials', 'puzzles', 'practice'];
const PUZZLE_THEMES = ['ALL', 'TACTIC', 'MATE', 'POSITION', 'ENDGAME_FEN'];
const PRACTICE_POLL_FAST_MS = 250;
const PRACTICE_POLL_SLOW_MS = 500;
const PRACTICE_POLL_FAST_WINDOW_MS = 2000;
const XIANGQI_ROWS = 10;
const XIANGQI_COLS = 9;
const GOMOKU_SIZE = 15;
const XIANGQI_MIN_CELL = 12;
const GOMOKU_MIN_CELL = 10;

const ONLINE_AUDIO_ASSET_VERSION = '20260515f';
const onlineMoveAudio = createOnlineAudio(`/assets/audio/move.wav?v=${ONLINE_AUDIO_ASSET_VERSION}`);
const onlineMateAudio = createOnlineAudio(`/assets/audio/mate.wav?v=${ONLINE_AUDIO_ASSET_VERSION}`);

const app = document.getElementById('app');
const routes = ['home', 'play', 'room', 'game', 'practice', 'analysis', 'learn', 'watch', 'community', 'me'];

window.addEventListener('hashchange', render);
window.addEventListener('load', boot);
window.addEventListener('resize', () => fitBoardToViewport(currentRoute(), true));
window.setInterval(() => {
  const route = currentRoute();
  tickLiveGameClock(route);
  if (route.page === 'watch') {
    maybePollWatchOverview();
  }
}, 250);

async function boot() {
  initOnlineAudio();
  await Promise.all([loadBootstrap(), loadMe()]);
  render();
}

async function loadBootstrap() {
  state.bootstrap = await fetchJson(`${API_BASE}/site/bootstrap`).catch(() => null);
}

async function loadMe() {
  state.me = await fetchJson(`${API_BASE}/auth/me`).catch(() => null);
  if (!state.me) {
    state.learnProgress = null;
  }
}

async function loadLearnContent() {
  state.learnContent = await fetchJson(`${API_BASE}/learn/content`).catch(() => ({ tutorials: [], puzzles: [], recommendedPractice: [] }));
  render();
}

async function loadLearnProgress() {
  if (!state.me) {
    state.learnProgress = null;
    render();
    return;
  }
  state.learnProgress = await fetchJson(`${API_BASE}/learn/progress`).catch(() => ({ tutorialsCompleted: [], puzzlesCompleted: [] }));
  render();
}

async function loadWatchOverview(renderAfter = true) {
  state.watchOverview = await fetchJson(`${API_BASE}/watch/overview`).catch(() => ({ publicRooms: [], archivedGames: [] }));
  state.watchUpdatedAt = Date.now();
  if (renderAfter) {
    render();
  }
}

async function loadCommunityLeaderboard() {
  state.communityLeaderboard = await fetchJson(`${API_BASE}/community/leaderboard`).catch(() => ({ winBoard: [], activityBoard: [] }));
  render();
}

function resetLobbySearch(renderAfter = false) {
  state.lobbySearch = {
    query: '',
    rooms: [],
    players: [],
    loading: false,
    error: ''
  };
  state.lobbySearchRequestId += 1;
  if (state.lobbySearchTimer) {
    window.clearTimeout(state.lobbySearchTimer);
    state.lobbySearchTimer = 0;
  }
  if (renderAfter) {
    render();
  }
}

async function loadLobbySearch(query, renderAfter = true) {
  const normalized = String(query || '').trim();
  if (!normalized) {
    resetLobbySearch(renderAfter);
    return;
  }
  const requestId = state.lobbySearchRequestId + 1;
  state.lobbySearchRequestId = requestId;
  state.lobbySearch = {
    query: normalized,
    rooms: state.lobbySearch.rooms || [],
    players: state.lobbySearch.players || [],
    loading: true,
    error: ''
  };
  if (renderAfter) {
    render();
  }
  try {
    const res = await fetchJson(`${API_BASE}/lobby/search?q=${encodeURIComponent(normalized)}`);
    if (state.lobbySearchRequestId === requestId) {
      state.lobbySearch.rooms = res.rooms || [];
      state.lobbySearch.players = res.players || [];
      state.lobbySearch.loading = false;
      if (renderAfter) {
        render();
      }
    }
  } catch (err) {
    if (state.lobbySearchRequestId === requestId) {
      state.lobbySearch.loading = false;
      state.lobbySearch.error = '搜索失败，请稍后重试。';
      if (renderAfter) {
        render();
      }
    }
  }
}

function maybePollWatchOverview() {
  const now = Date.now();
  if (now - (state.watchUpdatedAt || 0) < WATCH_POLL_INTERVAL_MS) {
    return;
  }
  loadWatchOverview(true);
}

function currentRoute() {
  const raw = location.hash.replace(/^#\/?/, '');
  if (!raw) return { page: 'home', id: '', leaf: '', learnTab: '', puzzleTheme: resolvePuzzleTheme(state.learnPuzzleTheme) };
  const parts = raw.split('/');
  const page = routes.includes(parts[0]) ? parts[0] : 'home';
  let id = parts[1] || '';
  if (page === 'learn' && !id) {
    id = 'puzzles';
  }
  const learnTab = page === 'learn' ? resolveLearnSubRoute(id || 'puzzles') : '';
  const puzzleTheme = page === 'learn' && learnTab === 'puzzles'
    ? resolvePuzzleTheme(parts[2] || 'ALL')
    : resolvePuzzleTheme(state.learnPuzzleTheme);
  return { page: page, id: id, leaf: parts[2] || '', learnTab: learnTab, puzzleTheme: puzzleTheme };
}

function navTo(path) {
  location.hash = path;
}

function render() {
  const route = currentRoute();
  const isBoardRoute = isBoardRoutePage(route.page);
  if (!isBoardRoute && state.boardPaneTab !== 'board') {
    state.boardPaneTab = 'board';
  }
  const siteClasses = ['site', `route-${route.page}`];
  if (isBoardRoute) {
    siteClasses.push('is-board-route', `mobile-pane-${state.boardPaneTab}`);
  }
  if (route.page === 'practice') {
    siteClasses.push('route-practice-locked');
  }
  app.innerHTML = `
    <div class="${siteClasses.join(' ')}">
      ${renderTopbar(route.page)}
      <main class="shell">
        ${renderPage(route)}
      </main>
      ${renderGameEndModal()}
      ${shouldShowAuthOverlay(route) ? renderAuthOverlay() : ''}
    </div>
  `;
  bindCommon(route);
  syncRealtime(route);
  syncPracticePolling(route);
  fitBoardToViewport(route, false);
}

function fitBoardToViewport(route = currentRoute(), force = false) {
  if (!isBoardFitRoute(route)) {
    state.boardFitKey = '';
    return;
  }
  window.requestAnimationFrame(() => {
    const host = document.querySelector('.boardPane .boardHost');
    const board = host ? host.querySelector('.xiangqiBoard, .gomokuBoard') : null;
    if (!host || !board) {
      state.boardFitKey = '';
      queueBoardRefit(route);
      return;
    }
    const measured = measureBoardHostSpace(host);
    const availableWidth = Math.floor(measured.availableWidth);
    const availableHeight = Math.floor(measured.availableHeight);
    if (availableWidth <= 0 || availableHeight <= 0) {
      state.boardFitKey = '';
      queueBoardRefit(route);
      return;
    }
    const boardType = board.classList.contains('xiangqiBoard') ? 'XIANGQI' : 'GOMOKU';
    const contextId = route.page === 'analysis'
      ? ((state.analysis && state.analysis.gameId) || route.id || '')
      : ((state.game && state.game.gameId) || route.id || '');
    const fitKey = `${route.page}|${contextId}|${boardType}|${window.innerWidth}x${window.innerHeight}|${availableWidth}x${availableHeight}|${state.boardPaneTab}`;
    const boardAppliedKey = board.getAttribute('data-fit-key') || '';
    if (!force && fitKey === state.boardFitKey && boardAppliedKey === fitKey) {
      return;
    }
    if (boardType === 'XIANGQI') {
      fitXiangqiBoardToHost(board, host, measured);
    } else {
      fitGomokuBoardToHost(board, host, measured);
    }
    board.setAttribute('data-fit-key', fitKey);
    state.boardFitKey = fitKey;
  });
}

function queueBoardRefit(route) {
  if (state.boardRefitTimer) {
    return;
  }
  if (!isBoardFitRoute(route)) {
    return;
  }
  const routeKey = `${route.page}|${route.id || ''}|${route.leaf || ''}|${state.boardPaneTab}`;
  state.boardRefitTimer = window.setTimeout(() => {
    state.boardRefitTimer = 0;
    const latest = currentRoute();
    const latestKey = `${latest.page}|${latest.id || ''}|${latest.leaf || ''}|${state.boardPaneTab}`;
    if (latestKey !== routeKey || !isBoardFitRoute(latest)) {
      return;
    }
    fitBoardToViewport(latest, true);
  }, 140);
}

function isBoardFitRoute(route) {
  return !!route && (route.page === 'game' || route.page === 'practice' || route.page === 'analysis');
}

function fitXiangqiBoardToHost(board, host, measured = null) {
  fitBoardToHost({
    board,
    host,
    measured,
    cssVar: '--xi-cell-size',
    cols: XIANGQI_COLS,
    rows: XIANGQI_ROWS,
    fallbackCell: 36,
    minCell: XIANGQI_MIN_CELL
  });
}

function fitGomokuBoardToHost(board, host, measured = null) {
  fitBoardToHost({
    board,
    host,
    measured,
    cssVar: '--go-cell-size',
    cols: GOMOKU_SIZE,
    rows: GOMOKU_SIZE,
    fallbackCell: 24,
    minCell: GOMOKU_MIN_CELL
  });
}

function fitBoardToHost({ board, host, measured, cssVar, cols, rows, fallbackCell, minCell }) {
  board.style.removeProperty(cssVar);
  const baseCell = readBoardCellBase(board, cssVar, fallbackCell);
  const chrome = boardChromeSize(board);
  const availableWidth = Math.max(0, measured && Number.isFinite(measured.availableWidth)
    ? measured.availableWidth
    : (host.clientWidth - 2));
  const availableHeight = Math.max(0, measured && Number.isFinite(measured.availableHeight)
    ? measured.availableHeight
    : (host.clientHeight - 2));
  const byWidth = Math.floor((availableWidth - chrome.width) / cols);
  const byHeight = Math.floor((availableHeight - chrome.height) / rows);
  const targetCell = Math.max(minCell, Math.min(Math.floor(baseCell), byWidth, byHeight));
  const initialCell = Number.isFinite(targetCell) && targetCell > 0 ? targetCell : minCell;
  board.style.setProperty(cssVar, `${initialCell}px`, 'important');
  shrinkBoardToHost(board, cssVar, minCell, initialCell, availableWidth, availableHeight);
}

function readBoardCellBase(board, cssVar, fallbackCell) {
  const inlineValue = parseFloat(board.style.getPropertyValue(cssVar));
  if (Number.isFinite(inlineValue) && inlineValue > 0) {
    return inlineValue;
  }
  const computedValue = parseFloat(getComputedStyle(board).getPropertyValue(cssVar));
  if (Number.isFinite(computedValue) && computedValue > 0) {
    return computedValue;
  }
  return fallbackCell;
}

function boardChromeSize(board) {
  const styles = getComputedStyle(board);
  return {
    width: px(styles.paddingLeft) + px(styles.paddingRight) + px(styles.borderLeftWidth) + px(styles.borderRightWidth),
    height: px(styles.paddingTop) + px(styles.paddingBottom) + px(styles.borderTopWidth) + px(styles.borderBottomWidth)
  };
}

function px(value) {
  const num = parseFloat(value);
  return Number.isFinite(num) ? num : 0;
}

function shrinkBoardToHost(board, cssVar, minCell, startCell, limitWidth, limitHeight) {
  let cell = startCell;
  while (cell > minCell && (board.offsetWidth > limitWidth || board.offsetHeight > limitHeight)) {
    cell -= 1;
    board.style.setProperty(cssVar, `${cell}px`, 'important');
  }
}

function measureBoardHostSpace(host) {
  const fallbackRect = host.getBoundingClientRect();
  const fallbackWidth = Math.max(0, (host.clientWidth || fallbackRect.width) - 2);
  const fallbackHeight = Math.max(0, (host.clientHeight || fallbackRect.height) - 2);
  const pane = host.closest('.boardPane');
  const shell = host.closest('.shell') || document.querySelector('.shell');
  const paneRect = pane ? pane.getBoundingClientRect() : null;
  const shellRect = shell ? shell.getBoundingClientRect() : null;
  const viewportHeight = (window.visualViewport && Number.isFinite(window.visualViewport.height))
    ? window.visualViewport.height
    : window.innerHeight;
  const viewportWidth = (window.visualViewport && Number.isFinite(window.visualViewport.width))
    ? window.visualViewport.width
    : window.innerWidth;
  const visibleBottom = tightestPositive([
    viewportHeight,
    paneRect ? paneRect.bottom : 0,
    shellRect ? shellRect.bottom : 0
  ], viewportHeight);
  const visibleRight = tightestPositive([
    viewportWidth,
    paneRect ? paneRect.right : 0,
    shellRect ? shellRect.right : 0
  ], viewportWidth);
  const viewportLimitedHeight = Math.max(0, visibleBottom - Math.max(0, fallbackRect.top) - 2);
  const viewportLimitedWidth = Math.max(0, visibleRight - Math.max(0, fallbackRect.left) - 2);

  let paneRowHeight = 0;
  let preferredWidth = host.clientWidth || fallbackRect.width;
  if (pane) {
    const paneStyle = getComputedStyle(pane);
    const rowGap = parseFloat(paneStyle.rowGap || paneStyle.gap) || 0;
    const siblings = Array.from(pane.children).filter((child) => child !== host);
    const occupiedHeight = siblings.reduce((sum, child) => sum + child.getBoundingClientRect().height, 0);
    const totalGaps = Math.max(0, (pane.children.length - 1) * rowGap);
    paneRowHeight = pane.clientHeight - occupiedHeight - totalGaps;
    preferredWidth = host.clientWidth || pane.clientWidth || fallbackRect.width;
  }
  const paneLimitedHeight = Math.max(0, paneRowHeight - 2);
  const paneLimitedWidth = Math.max(0, preferredWidth - 2);
  return {
    availableWidth: tightestPositive([paneLimitedWidth, viewportLimitedWidth], fallbackWidth),
    availableHeight: tightestPositive([paneLimitedHeight, viewportLimitedHeight], fallbackHeight)
  };
}

function tightestPositive(values, fallback) {
  const valid = values.filter((value) => Number.isFinite(value) && value > 0);
  if (!valid.length) {
    return Math.max(0, fallback);
  }
  return Math.max(0, Math.min(...valid));
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
        <button class="ghost" data-action="toggle-sound">音效：${state.soundEnabled ? '开' : '关'}</button>
        <span class="muted">${me ? `@${me.username}` : '未登录'}</span>
        ${me ? '<button class="ghost" data-action="logout">退出</button>' : '<button class="btn" data-action="show-auth">登录 / 注册</button>'}
      </div>
    </header>
  `;
}

function renderGameEndModal() {
  const route = currentRoute();
  if (!state.endGameModal || (route.page !== 'game' && route.page !== 'practice')) {
    return '';
  }
  const modal = state.endGameModal;
  const game = modal.game || {};
  const isPractice = !!game.isTraining;
  const winner = game.winnerSide ? sideLabel(game.gameType, game.winnerSide) : '无';
  const resultText = escapeHtml(game.resultText || '-');
  const reason = escapeHtml(game.terminationReason || '-');
  const analysisHref = `analysis/${escapeHtml(game.gameId || '')}`;
  const roomHref = `room/${escapeHtml(game.roomId || '')}`;
  return `
    <div class="endGameOverlay" data-action="close-end-modal">
      <div class="endGameCard" role="dialog" aria-modal="true" aria-label="对局结束" data-end-game-card>
        <h3>对局结束</h3>
        <p class="muted">胜方：${escapeHtml(winner)}</p>
        <p>${resultText}</p>
        <p class="muted">结束原因：${reason}</p>
        <div class="roomRow">
          <button class="btn" data-nav="${analysisHref}">进入分析</button>
          ${isPractice ? '<button class="ghost" data-action="practice-rematch">再开一局</button><button class="ghost" data-nav="learn/puzzles/ALL">返回学习</button>' : `<button class="ghost" data-nav="${roomHref}">回到房间</button>`}
          <button class="ghost" data-action="close-end-modal">关闭</button>
        </div>
      </div>
    </div>
  `;
}

function navLink(page, label, active) {
  const href = page === 'learn'
    ? `#/learn/puzzles/${resolvePuzzleTheme(state.learnPuzzleTheme)}`
    : `#/${page}`;
  return `<a class="${active === page ? 'is-active' : ''}" href="${href}">${label}</a>`;
}

const pageRegistry = {
  home: renderHomePage,
  play: route => renderPlay(route),
  room: route => renderRoom(route.id),
  game: route => renderGame(route.id),
  practice: route => renderPractice(route.id),
  analysis: route => renderAnalysis(route.id),
  learn: route => renderLearnPage(route),
  watch: renderWatchPage,
  community: renderCommunityPage,
  me: renderProfilePage
};

function renderPage(route) {
  const renderer = pageRegistry[route.page] || pageRegistry.home;
  return renderer(route);
}

function resolveLearnSubRoute(value) {
  return LEARN_SUB_ROUTES.includes(value) ? value : 'puzzles';
}

function resolvePuzzleTheme(value) {
  const normalized = String(value || '').trim().toUpperCase();
  return PUZZLE_THEMES.includes(normalized) ? normalized : 'ALL';
}

function isBoardRoutePage(page) {
  return page === 'game' || page === 'practice' || page === 'analysis';
}

function boardSizeByType(gameType) {
  if (gameType === 'XIANGQI') {
    return { rows: XIANGQI_ROWS, cols: XIANGQI_COLS };
  }
  return { rows: GOMOKU_SIZE, cols: GOMOKU_SIZE };
}

function shouldFlipOnlineBoardForViewer(game) {
  if (!game || game.isTraining) {
    return false;
  }
  const viewerSide = game.viewerSide || inferViewerSide(game);
  if (!viewerSide) {
    return false;
  }
  if (game.gameType === 'XIANGQI') {
    return viewerSide === 'BLACK';
  }
  if (game.gameType === 'GOMOKU') {
    return viewerSide === 'WHITE';
  }
  return false;
}

function mapDisplayToBoardPosition(gameType, displayRow, displayCol, flipped) {
  if (!flipped) {
    return { row: displayRow, col: displayCol };
  }
  const size = boardSizeByType(gameType);
  return {
    row: size.rows - 1 - displayRow,
    col: size.cols - 1 - displayCol
  };
}

function mapBoardToDisplayPosition(gameType, row, col, flipped) {
  if (!flipped) {
    return { row: row, col: col };
  }
  const size = boardSizeByType(gameType);
  return {
    row: size.rows - 1 - row,
    col: size.cols - 1 - col
  };
}

function resolveBoardRenderOptions(game, route) {
  if (!game) {
    return { flipped: false, riverText: '楚河　汉界' };
  }
  const page = route && route.page ? route.page : String(route || '');
  const onlineGameRoute = page === 'game' && !game.isTraining;
  const flipped = onlineGameRoute && shouldFlipOnlineBoardForViewer(game);
  return {
    flipped: !!flipped,
    riverText: flipped ? '汉界　楚河' : '楚河　汉界'
  };
}

function sideLabel(gameType, side) {
  if (!side) {
    return '-';
  }
  if (gameType === 'XIANGQI') {
    if (side === 'RED') return '红棋';
    if (side === 'BLACK') return '黑棋';
  } else if (gameType === 'GOMOKU') {
    if (side === 'BLACK') return '黑棋';
    if (side === 'WHITE') return '白棋';
  }
  return side;
}

function resolveOpponentSide(game, viewerSide) {
  if (!game || !viewerSide || !game.players) {
    return '';
  }
  const first = game.players.first || {};
  const second = game.players.second || {};
  if (first.side === viewerSide) {
    return second.side || '';
  }
  if (second.side === viewerSide) {
    return first.side || '';
  }
  return '';
}

function turnTextForViewer(game, viewerSide) {
  const turn = game ? game.currentTurn : '';
  if (!turn) {
    return '-';
  }
  const actor = viewerSide && turn === viewerSide ? '你' : '对手';
  return `${actor}（${sideLabel(game.gameType, turn)}）`;
}

function onlineGameStatusText(game) {
  if (state.status) {
    return state.status;
  }
  return (game && game.resultText) || '在线对局进行中';
}

function shouldShowAuthOverlay(route) {
  if (state.me) return false;
  if (state.showAuthModal) return true;
  return ['play', 'room', 'game', 'practice', 'me'].includes(route.page);
}

function renderHomePage() {
  const b = state.bootstrap || { recentGames: [], activeRooms: 0, totalUsers: 0, totalGames: 0, activity: {} };
  const activity = b.activity || {};
  const activeRoom = activity.room;
  const activeGame = activity.game;
  return `
    <section class="hero">
      <div class="meta">Online Lobby</div>
      <h1>这里专注在线房间对局</h1>
      <p>中国象棋与五子棋都可以通过房间码或公开房间开始在线对战，AI 练习也保持在 Online 内完成。</p>
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
          <div class="card">
            <h3>在线 AI 练习</h3>
            <p>点击后直接进入中国象棋中等难度对局，或选择进入五子棋练习。</p>
            <div style="display:flex; flex-direction:column; gap:6px;">
              <button class="btn" data-action="quick-start-ai-practice">进入象棋 AI 对局</button>
              <button class="btn" data-action="quick-start-gomoku-practice">进入五子棋 AI 对局</button>
            </div>
          </div>
          <div class="card"><h3>围棋</h3><p>统一入口已保留，在线对战和 AI 练习都将在后续补齐。</p><button class="ghost" disabled>即将开放</button></div>
        </div>
        ${activeRoom || activeGame ? renderActivityBanner(activeRoom, activeGame) : ''}
      </section>
      <section class="panel">
        <h2 class="sectionTitle">最近对局</h2>
        <div class="moves">
          ${(b.recentGames || []).length ? b.recentGames.map(renderRecentGameCard).join('') : '<div class="banner">还没有归档对局，先去大厅或学习页开始一局。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderLearnPage(route) {
  if (!state.learnContent) {
    loadLearnContent();
    return '<section class="panel"><h2 class="sectionTitle">学习内容加载中</h2></section>';
  }
  if (state.me && !state.learnProgress) {
    loadLearnProgress();
  }
  const content = state.learnContent || { tutorials: [], puzzles: [], recommendedPractice: [] };
  const progress = state.learnProgress || { tutorialsCompleted: [], puzzlesCompleted: [] };
  const activeTab = route && route.learnTab ? route.learnTab : resolveLearnSubRoute('');
  const puzzleTheme = activeTab === 'puzzles'
    ? resolvePuzzleTheme(route && route.puzzleTheme ? route.puzzleTheme : state.learnPuzzleTheme)
    : resolvePuzzleTheme(state.learnPuzzleTheme);
  if (activeTab === 'puzzles') {
    state.learnPuzzleTheme = puzzleTheme;
  }
  const tabTitle = activeTab === 'tutorials' ? '教程' : (activeTab === 'practice' ? '推荐练习' : '题库 / 残局');
  const tabDesc = activeTab === 'tutorials'
    ? '从基础规则、战术主题到完整例线，按主题系统学习。'
    : (activeTab === 'practice'
      ? '直接创建 AI 练习局，或按推荐配置一键进入对弈。'
      : '默认题库视图，支持按主题切换和“按此题开局”快速进入对应局面。');
  return `
    <section class="hero">
      <div class="meta">Learn</div>
      <h1>学习模块</h1>
      <p>游客可浏览全部内容；登录后可记录进度并直接发起 AI 练习。默认进入题库视图，支持深链接访问。</p>
      <div class="grid cards">
        <div class="card"><div class="meta">教程</div><h3>${(content.tutorials || []).length}</h3><p>基础与进阶学习清单</p></div>
        <div class="card"><div class="meta">题库</div><h3>${(content.puzzles || []).length}</h3><p>残局与战术训练</p></div>
        <div class="card"><div class="meta">进度</div><h3>${progressCount(progress)}</h3><p>${state.me ? '已完成项目总数' : '登录后可记录学习进度'}</p></div>
      </div>
      ${renderLearnSubTabs(activeTab, state.learnPuzzleTheme)}
    </section>
    <section class="panel" style="margin-top:18px">
      <h2 class="sectionTitle">${tabTitle}</h2>
      <p class="muted" style="margin:0 0 12px">${tabDesc}</p>
      ${renderLearnTabContent(activeTab, content, progress, puzzleTheme)}
    </section>
  `;
}

function renderWatchPage() {
  if (!state.watchOverview) {
    loadWatchOverview();
    return '<section class="panel"><h2 class="sectionTitle">观战数据加载中</h2></section>';
  }
  const rooms = filterWatchRooms((state.watchOverview.publicRooms || []), state.watchFilters);
  const games = filterWatchGames((state.watchOverview.archivedGames || []), state.watchFilters);
  return `
    <section class="hero">
      <div class="meta">Watch</div>
      <h1>公开观战入口</h1>
      <p>先提供稳定轮询刷新（${Math.floor(WATCH_POLL_INTERVAL_MS / 1000)} 秒）。可按棋种和状态过滤，并快速跳转分析页。</p>
    </section>
    <section class="panel" style="margin-top:18px">
      <div class="roomRow" style="margin-bottom:12px">
        <select data-watch-filter="gameType">
          <option value="ALL" ${state.watchFilters.gameType === 'ALL' ? 'selected' : ''}>全部棋种</option>
          <option value="XIANGQI" ${state.watchFilters.gameType === 'XIANGQI' ? 'selected' : ''}>中国象棋</option>
          <option value="GOMOKU" ${state.watchFilters.gameType === 'GOMOKU' ? 'selected' : ''}>五子棋</option>
        </select>
        <select data-watch-filter="status">
          <option value="ALL" ${state.watchFilters.status === 'ALL' ? 'selected' : ''}>全部状态</option>
          <option value="PLAYING" ${state.watchFilters.status === 'PLAYING' ? 'selected' : ''}>进行中</option>
          <option value="FINISHED" ${state.watchFilters.status === 'FINISHED' ? 'selected' : ''}>已结束</option>
        </select>
        <button class="ghost" data-action="refresh-watch">手动刷新</button>
      </div>
      <div class="split">
        <section class="panel">
          <h3>公开房间</h3>
          <div class="moves">${renderWatchRooms(rooms)}</div>
        </section>
        <section class="panel">
          <h3>可观战归档对局</h3>
          <div class="moves">${renderWatchGames(games)}</div>
        </section>
      </div>
    </section>
  `;
}

function renderCommunityPage() {
  if (!state.communityLeaderboard) {
    loadCommunityLeaderboard();
    return '<section class="panel"><h2 class="sectionTitle">社区榜单加载中</h2></section>';
  }
  const board = state.communityLeaderboard || { winBoard: [], activityBoard: [], byGameType: {} };
  const isWin = state.communityTab === 'win';
  const gameData = (board.byGameType && board.byGameType[state.leaderboardGameType]) || { winBoard: [], activityBoard: [] };
  const items = isWin ? (gameData.winBoard || []) : (gameData.activityBoard || []);
  const title = isWin ? '胜局榜' : '活跃榜';
  const quickEntry = state.me
    ? '<div class="roomRow" style="margin-top:12px"><button class="btn" data-nav="me">查看我的主页</button><button class="ghost" data-nav="play">进入对局大厅</button></div>'
    : '<div class="banner" style="margin-top:12px">登录后会高亮你的榜单位置，并提供个人主页快捷入口。</div>';
  return `
    <section class="hero">
      <div class="meta">Community</div>
      <h1>排行榜与活跃榜</h1>
      <p>默认按最近 ${board.windowDaysUsed || board.requestedWindowDays || 30} 天统计。若样本不足会自动回退到全量历史。</p>
      ${quickEntry}
    </section>
    <section class="panel" style="margin-top:18px">
      <div class="roomRow" style="margin-bottom:12px; justify-content: space-between; align-items: center;">
        <div style="display:flex; gap:10px;">
          <button class="${isWin ? 'btn' : 'ghost'}" data-community-tab="win">胜局榜</button>
          <button class="${!isWin ? 'btn' : 'ghost'}" data-community-tab="activity">活跃榜</button>
        </div>
        <div style="display:flex; gap:10px;">
          <button class="${state.leaderboardGameType === 'XIANGQI' ? 'btn' : 'ghost'}" data-community-game-type="XIANGQI">象棋</button>
          <button class="${state.leaderboardGameType === 'GOMOKU' ? 'btn' : 'ghost'}" data-community-game-type="GOMOKU">五子棋</button>
        </div>
      </div>
      ${board.fallbackToAllTime ? '<div class="banner">近 30 天样本较少，当前展示全量历史榜单。</div>' : ''}
      <h2 class="sectionTitle">${title}</h2>
      <div class="moves">${renderCommunityItems(items, isWin)}</div>
    </section>
  `;
}

function renderProfilePage() {
  return renderProfile();
}

function progressCount(progress) {
  const tutorials = (progress && progress.tutorialsCompleted) || [];
  const puzzles = (progress && progress.puzzlesCompleted) || [];
  return tutorials.length + puzzles.length;
}

function renderLearnSubTabs(activeTab, puzzleTheme) {
  const theme = resolvePuzzleTheme(puzzleTheme);
  return `
    <div class="learnTabs">
      <a href="#/learn/puzzles/${theme}" class="${activeTab === 'puzzles' ? 'is-active' : ''}">题库</a>
      <a href="#/learn/tutorials" class="${activeTab === 'tutorials' ? 'is-active' : ''}">教程</a>
      <a href="#/learn/practice" class="${activeTab === 'practice' ? 'is-active' : ''}">练习</a>
    </div>
  `;
}

function renderLearnTabContent(activeTab, content, progress, puzzleTheme) {
  if (activeTab === 'tutorials') {
    return `<div class="moves">${renderLearnTutorials(content.tutorials || [], progress.tutorialsCompleted || [])}</div>`;
  }
  if (activeTab === 'practice') {
    return renderLearnPracticeTab(content);
  }
  return `
    ${renderPuzzleThemeTiles(puzzleTheme)}
    <div class="moves">${renderLearnPuzzles(content.puzzles || [], progress.puzzlesCompleted || [], puzzleTheme)}</div>
  `;
}

function renderPuzzleThemeTiles(activeTheme) {
  const resolved = resolvePuzzleTheme(activeTheme);
  return `
    <div class="learnThemeTiles">
      ${PUZZLE_THEMES.map(theme => `<a class="learnThemeTile ${resolved === theme ? 'is-active' : ''}" href="#/learn/puzzles/${theme}">${escapeHtml(puzzleThemeLabel(theme))}</a>`).join('')}
    </div>
  `;
}

function puzzleThemeLabel(theme) {
  if (theme === 'ALL') return '全部';
  if (theme === 'TACTIC') return '战术';
  if (theme === 'MATE') return '杀法';
  if (theme === 'POSITION') return '局面';
  if (theme === 'ENDGAME_FEN') return '残局';
  return theme;
}

function renderLearnPracticeTab(content) {
  const engines = engineOptions(state.learnConfig.gameType);
  return `
    <div class="split">
      <section class="panel">
        <h3 style="margin:0 0 12px">快速创建 AI 练习</h3>
        <div class="stack">
          <div class="field">
            <label>棋种</label>
            <select data-learn-field="gameType">
              <option value="XIANGQI" ${state.learnConfig.gameType === 'XIANGQI' ? 'selected' : ''}>中国象棋</option>
              <option value="GOMOKU" ${state.learnConfig.gameType === 'GOMOKU' ? 'selected' : ''}>五子棋</option>
            </select>
          </div>
          <div class="field">
            <label>难度</label>
            <select data-learn-field="difficulty">
              <option value="EASY" ${state.learnConfig.difficulty === 'EASY' ? 'selected' : ''}>简单</option>
              <option value="MEDIUM" ${state.learnConfig.difficulty === 'MEDIUM' ? 'selected' : ''}>中等</option>
              <option value="HARD" ${state.learnConfig.difficulty === 'HARD' ? 'selected' : ''}>困难</option>
            </select>
          </div>
          <div class="field">
            <label>先后手</label>
            <select data-learn-field="humanFirst">
              <option value="true" ${state.learnConfig.humanFirst ? 'selected' : ''}>我先手</option>
              <option value="false" ${!state.learnConfig.humanFirst ? 'selected' : ''}>AI 先手</option>
            </select>
          </div>
          <div class="field">
            <label>引擎</label>
            <select data-learn-field="preferredEngine">
              ${engines.map(item => `<option value="${item.value}" ${state.learnConfig.preferredEngine === item.value ? 'selected' : ''}>${item.label}</option>`).join('')}
            </select>
          </div>
          <button class="btn" data-action="create-practice">直接进入 AI 对局</button>
        </div>
      </section>
      <section class="panel">
        <h3 style="margin:0 0 12px">推荐练习配置</h3>
        <div class="grid cards">
          ${(content.recommendedPractice || []).length
            ? (content.recommendedPractice || []).map(renderPracticePresetCard).join('')
            : '<div class="banner">暂无推荐练习配置。</div>'}
        </div>
      </section>
    </div>
  `;
}

function renderLearnTutorials(items, completedIds) {
  const completed = new Set(completedIds || []);
  if (!items.length) {
    return '<div class="banner">暂无教程内容。</div>';
  }
  return items.map(item => `
    <div class="move">
      <div class="learnMain">
        <strong>${escapeHtml(item.title || '')}</strong>
        <div class="muted">${escapeHtml(item.gameType || '')} · ${escapeHtml(item.difficulty || '')} · ${escapeHtml(String(item.minutes || '-'))} 分钟</div>
        <div class="muted">${escapeHtml(item.summary || '')}</div>
        <div class="learnDetail">
          ${renderLearnTextBlock('学习目标', item.objective)}
          ${renderLearnListBlock('关键要点', item.keyPoints)}
          ${renderLearnListBlock('示例走法', item.exampleLine)}
          ${renderLearnListBlock('练习清单', item.practiceChecklist)}
        </div>
      </div>
      <div class="learnAction">${renderProgressAction('tutorial', item.id, completed.has(item.id))}</div>
    </div>
  `).join('');
}

function renderLearnPuzzles(items, completedIds, puzzleTheme) {
  const completed = new Set(completedIds || []);
  const resolvedTheme = resolvePuzzleTheme(puzzleTheme);
  const filteredItems = resolvedTheme === 'ALL'
    ? (items || [])
    : (items || []).filter(item => resolvePuzzleTheme(item.theme) === resolvedTheme);
  if (!filteredItems.length) {
    return '<div class="banner">暂无题库内容。</div>';
  }
  return filteredItems.map(item => `
    <div class="move">
      <div class="learnMain">
        <strong>${escapeHtml(item.title || '')}</strong>
        <div class="muted">${escapeHtml(item.gameType || '')} · ${escapeHtml(item.difficulty || '')} · ${escapeHtml(puzzleThemeLabel(resolvePuzzleTheme(item.theme)))}</div>
        <div class="muted learnSummaryOneLine">${escapeHtml(item.summary || '暂无题目摘要')}</div>
      </div>
      <div class="learnAction">${renderPuzzleActions(item, completed.has(item.id))}</div>
    </div>
  `).join('');
}

function renderPuzzleActions(item, completed) {
  const completeAction = renderProgressAction('puzzle', item.id, completed);
  if (!canStartPuzzlePractice(item)) {
    return `
      <div class="learnActionGroup">
        ${completeAction}
        <button class="ghost" disabled>FEN 待补全</button>
      </div>
    `;
  }
  return `
    <div class="learnActionGroup">
      ${completeAction}
      <button class="btn" data-action="start-puzzle-practice" data-puzzle-id="${escapeHtml(item.id || '')}">按此题开局</button>
    </div>
  `;
}

function renderProgressAction(kind, id, completed) {
  if (completed) {
    return '<span class="pill">已完成</span>';
  }
  if (!state.me) {
    return '<button class="ghost" disabled>登录后记录</button>';
  }
  return `<button class="btn" data-learn-complete="${kind}" data-id="${escapeHtml(id || '')}">标记完成</button>`;
}

function renderLearnTextBlock(label, value) {
  if (!value) {
    return '';
  }
  return `
    <div class="learnBlock">
      <div class="learnLabel">${escapeHtml(label)}</div>
      <div class="learnText">${escapeHtml(value)}</div>
    </div>
  `;
}

function renderLearnFenBlock(fen) {
  if (!fen) {
    return '';
  }
  return `
    <div class="learnBlock">
      <div class="learnLabel">FEN</div>
      <div class="learnText learnText--fen">${escapeHtml(fen)}</div>
    </div>
  `;
}

function canStartPuzzlePractice(item) {
  return !!(item
    && item.gameType === 'XIANGQI'
    && isValidXiangqiInitialFen(item.fen));
}

function normalizeFenText(fen) {
  return String(fen || '').trim().replace(/\s+/g, ' ');
}

function isValidXiangqiInitialFen(fen) {
  const normalized = normalizeFenText(fen);
  if (!normalized) {
    return false;
  }
  const parts = normalized.split(' ');
  const boardPart = parts[0] || '';
  const rows = boardPart.split('/');
  if (rows.length !== 10) {
    return false;
  }
  const allowedPieces = new Set(['k', 'a', 'b', 'n', 'r', 'c', 'p', 'K', 'A', 'B', 'N', 'R', 'C', 'P']);
  let redGeneral = 0;
  let blackGeneral = 0;
  for (const row of rows) {
    let col = 0;
    for (const ch of row) {
      if (ch >= '1' && ch <= '9') {
        col += Number(ch);
      } else if (allowedPieces.has(ch)) {
        col += 1;
        if (ch === 'K') redGeneral += 1;
        if (ch === 'k') blackGeneral += 1;
      } else {
        return false;
      }
      if (col > 9) {
        return false;
      }
    }
    if (col !== 9) {
      return false;
    }
  }
  if (redGeneral !== 1 || blackGeneral !== 1) {
    return false;
  }
  if (parts.length > 1 && parts[1] !== 'w' && parts[1] !== 'b') {
    return false;
  }
  return true;
}

function renderLearnListBlock(label, values) {
  if (!Array.isArray(values) || !values.length) {
    return '';
  }
  return `
    <div class="learnBlock">
      <div class="learnLabel">${escapeHtml(label)}</div>
      <ul class="learnList">
        ${values.map(item => `<li>${escapeHtml(item)}</li>`).join('')}
      </ul>
    </div>
  `;
}

function renderPracticePresetCard(item) {
  return `
    <div class="card">
      <h3>${escapeHtml(item.title || '')}</h3>
      <p>${escapeHtml(item.description || '')}</p>
      <div class="muted">${escapeHtml(item.gameType || '')} · ${escapeHtml(item.difficulty || '')}</div>
      <button class="btn" style="margin-top:10px" data-action="start-practice-preset" data-preset-id="${escapeHtml(item.id || '')}">立即练习</button>
    </div>
  `;
}

function filterWatchRooms(items, filters) {
  return (items || []).filter(item => {
    if (filters.gameType !== 'ALL' && item.gameType !== filters.gameType) return false;
    if (filters.status !== 'ALL' && item.status !== filters.status) return false;
    return true;
  });
}

function filterWatchGames(items, filters) {
  return (items || []).filter(item => {
    if (filters.gameType !== 'ALL' && item.gameType !== filters.gameType) return false;
    if (filters.status !== 'ALL' && item.status !== filters.status) return false;
    return true;
  });
}

function renderWatchRooms(items) {
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
}

function renderWatchGames(items) {
  if (!items.length) {
    return '<div class="banner">暂无可观战归档对局。</div>';
  }
  return items.map(item => `
    <div class="move">
      <div>
        <strong>${escapeHtml(item.gameType || '')}</strong>
        <div class="muted">${escapeHtml(item.players && item.players.first ? item.players.first.username : '')} vs ${escapeHtml(item.players && item.players.second ? item.players.second.username : '')}</div>
        <div class="muted">状态：${escapeHtml(item.status || '')} · 手数：${escapeHtml(String(item.moveCount || 0))}</div>
      </div>
      <button class="ghost" data-nav="analysis/${escapeHtml(item.gameId || '')}">${item.status === 'PLAYING' ? '观战' : '查看'}</button>
    </div>
  `).join('');
}

function renderCommunityItems(items, isWinBoard) {
  if (!items.length) {
    return '<div class="banner">当前暂无榜单数据。</div>';
  }
  return items.map(item => {
    const self = state.me && state.me.id === item.userId;
    const metrics = isWinBoard
      ? `胜 ${item.wins || 0} / 总 ${item.totalGames || 0} / 胜率 ${(Number(item.winRate || 0) * 100).toFixed(1)}%`
      : `活跃局数 ${item.activityGames || 0} / 胜局 ${item.wins || 0}`;
    return `
      <div class="move ${self ? 'is-current' : ''}">
        <div>
          <strong>#${item.rank || '-'} ${escapeHtml(item.username || '')}${self ? '（你）' : ''}</strong>
          <div class="muted">${metrics}</div>
        </div>
      </div>
    `;
  }).join('');
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
          
          <div class="field" style="margin-top:12px;">
            <label>搜索大厅</label>
            <input id="lobbySearchInput" placeholder="输入房间ID或用户名" value="${escapeHtml(state.lobbySearch.query)}" />
          </div>

          <h3>${state.lobbySearch.query ? '搜索结果' : '公开房间'}</h3>
          <div class="moves">
            ${state.lobbySearch.query ? (
              state.lobbySearch.loading ? '<div class="banner">正在搜索中...</div>' : (
                state.lobbySearch.error ? `<div class="banner error">${escapeHtml(state.lobbySearch.error)}</div>` : (
                  (state.lobbySearch.rooms.length || state.lobbySearch.players.length) ? (
                    [
                      ...state.lobbySearch.rooms.map(room => `
                        <div class="move">
                          <div>
                            <strong>${escapeHtml(room.gameType)} (房间)</strong>
                            <div class="muted">${escapeHtml(room.hostUsername)}${room.guestUsername ? ` vs ${escapeHtml(room.guestUsername)}` : ' · 等待加入'}</div>
                          </div>
                          <button class="ghost" data-nav="room/${escapeHtml(room.roomId)}">查看</button>
                        </div>
                      `),
                      ...state.lobbySearch.players.map(player => `
                        <div class="move">
                          <div>
                            <strong>${escapeHtml(player.username)} (玩家)</strong>
                            <div class="muted">当前在线</div>
                          </div>
                          <span class="pill">在线</span>
                        </div>
                      `)
                    ].join('')
                  ) : '<div class="banner">未找到匹配的房间或玩家。</div>'
                )
              )
            ) : (
              rooms.length ? rooms.map(room => `
                <div class="move">
                  <div>
                    <strong>${escapeHtml(room.gameType)}</strong>
                    <div class="muted">${escapeHtml(room.hostUsername)}${room.guestUsername ? ` vs ${escapeHtml(room.guestUsername)}` : ' · 等待加入'}</div>
                  </div>
                  <button class="ghost" data-nav="room/${escapeHtml(room.roomId)}">查看</button>
                </div>`).join('') : '<div class="banner">当前没有公开房间，创建一个新的也可以。</div>'
            )}
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
  const board = renderPlayableBoardByGameType(game, resolveBoardRenderOptions(game, { page: 'game' }));
  const drawOffer = game.drawOffer;
  const viewerSide = game.viewerSide || inferViewerSide(game);
  const opponentSide = resolveOpponentSide(game, viewerSide);
  const canRespondDraw = drawOffer && drawOffer.side !== viewerSide;
  const canOfferDraw = game.status === 'PLAYING' && !drawOffer;
  return `
    <div class="boardPage">
      ${renderBoardPaneTabs()}
      <div class="split boardSplit">
        <section class="boardWrap boardPane boardPane--game">
          <div class="gameMetaRow">
            <span class="pill">${game.gameType}</span>
            <span class="pill" data-live-side-self>你执 ${sideLabel(game.gameType, viewerSide)}</span>
            <span class="pill" data-live-side-opponent>对手执 ${sideLabel(game.gameType, opponentSide)}</span>
            <span class="pill" data-live-turn>轮到 ${turnTextForViewer(game, viewerSide)}</span>
            <span class="pill" data-live-game-status>${game.status}</span>
            <span class="pill" data-live-game-termination>${game.terminationReason || 'LIVE'}</span>
          </div>
          <div class="clockGrid" data-live-clock-grid>
            ${renderClockCard(game, 'first')}
            ${renderClockCard(game, 'second')}
          </div>
          <div class="status" data-live-status>${onlineGameStatusText(game)}</div>
          <div data-live-draw-offer>${drawOffer ? renderDrawOfferBanner(drawOffer, canRespondDraw) : ''}</div>
          <div class="boardHost" data-live-board-host>${board}</div>
          <div class="roomRow" data-live-game-actions>
            ${renderOnlineGameActions(game, canOfferDraw)}
          </div>
        </section>
        <section class="panel recordPane">
          <h2 class="sectionTitle">走子记录</h2>
          <div class="moves" data-live-moves>
            ${(game.moves || []).length ? game.moves.map(renderMoveRow).join('') : '<div class="banner">等待第一步落子。</div>'}
          </div>
        </section>
      </div>
    </div>
  `;
}

function renderOnlineGameActions(game, canOfferDraw) {
  return `
    <button class="ghost" data-nav="room/${game.roomId}">回到房间</button>
    <button class="ghost" data-nav="analysis/${game.gameId}">进入分析</button>
    ${canOfferDraw ? '<button class="ghost" data-action="offer-draw">求和</button>' : ''}
    ${game.status === 'PLAYING' ? '<button class="danger" data-action="resign">认输</button>' : ''}
  `;
}

function renderPracticeView(game) {
  const board = renderPlayableBoardByGameType(game, resolveBoardRenderOptions(game, { page: 'practice' }));
  const ai = practiceAiMeta(game);
  const undoDisabledReason = practiceUndoDisabledReason(game);
  const undoDisabled = !!undoDisabledReason;
  return `
    <div class="boardPage boardPage--practice">
      ${renderBoardPaneTabs()}
      <div class="split boardSplit">
        <section class="boardWrap boardPane boardPane--practice">
          <div class="practiceMetaLine">
            <span class="pill">AI 练习</span>
            <span class="pill">${game.gameType}</span>
            <span class="pill">${game.viewerSide || inferViewerSide(game)}</span>
            <span class="pill">轮到 ${game.currentTurn || '-'}</span>
            <span class="pill">${game.status}</span>
            <span class="pill">AI ${escapeHtml(ai.engineText || '-')}</span>
            <span class="pill">引擎 ${escapeHtml(ai.engineId || '-')}</span>
            <span class="pill">难度 ${escapeHtml(ai.difficulty || '-')}</span>
            <span class="pill">对手 ${escapeHtml(practiceOpponent(game))}</span>
            <span class="pill">AI 方 ${escapeHtml(game.aiSide || ai.side || '-')}</span>
          </div>
          <div class="status" data-live-status>${practiceStatusText(game)}</div>
          <div class="boardHost" data-live-board-host>${board}</div>
          <div class="roomRow">
            <button class="ghost" data-nav="learn/practice">返回学习页</button>
            <button class="ghost" data-nav="analysis/${game.gameId}">进入分析</button>
            ${game.status === 'PLAYING'
              ? `<button class="ghost" data-action="undo-practice" ${undoDisabled ? 'disabled' : ''} title="${escapeHtml(undoDisabledReason || '回合悔棋：撤销你最近一步及其后续 AI 应手')}">悔棋</button><button class="danger" data-action="resign">认输</button>`
              : '<button class="btn" data-action="practice-rematch">再开一局</button>'}
          </div>
        </section>
        <section class="panel recordPane">
          <h2 class="sectionTitle">练习记录</h2>
          <div class="moves">
            ${(game.moves || []).length ? game.moves.map(renderMoveRow).join('') : '<div class="banner">等待第一步落子。</div>'}
          </div>
        </section>
      </div>
    </div>
  `;
}

function renderClockCard(game, slot) {
  const player = slot === 'first' ? game.players.first : game.players.second;
  const side = player.side;
  const active = game.status === 'PLAYING' && game.currentTurn === side;
  const remaining = slot === 'first' ? effectiveRemaining(game, game.firstRemainingSeconds, side) : effectiveRemaining(game, game.secondRemainingSeconds, side);
  const baseRemaining = slot === 'first' ? (game.firstRemainingSeconds || 0) : (game.secondRemainingSeconds || 0);
  return `
    <div class="clockCard ${active ? 'is-active' : ''}" data-clock-card="${side}">
      <div class="meta">${side}</div>
      <strong>${player.username}</strong>
      <div class="clockValue" data-clock-value="${side}" data-remaining-base="${baseRemaining}">${formatClock(remaining)}</div>
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
  const marker = createMoveMarker(analysis.gameType, move, analysis.viewerSide || '');
  return `
    <div class="boardPage">
      ${renderBoardPaneTabs()}
      <div class="split boardSplit">
        <section class="boardWrap boardPane boardPane--analysis">
          <div class="gameMetaRow">
            <span class="pill">${analysis.gameType}</span>
            ${analysis.isTraining ? '<span class="pill">AI 练习</span>' : ''}
            <span class="pill">${analysis.status}</span>
            <span class="pill">步数 ${step}/${Math.max(0, boards.length - 1)}</span>
          </div>
          <div class="status">${analysis.isTraining ? `${practiceOpponent(analysis)} · ${analysis.aiEngine || '-'} · ${analysis.difficulty || '-'}` : '归档对局回放'}${move ? ` · ${move.side} ${move.notation}` : step === 0 ? ' · 开局局面' : ''}</div>
          <div class="boardHost" data-analysis-board-host>${renderAnalysisBoardByGameType(analysis.gameType, board, marker)}</div>
          <div class="roomRow">
            <button class="ghost" data-analysis-step="0">开局</button>
            <button class="ghost" data-analysis-step="${Math.max(0, step - 1)}">上一步</button>
            <button class="ghost" data-analysis-step="${Math.min(boards.length - 1, step + 1)}">下一步</button>
            <button class="ghost" data-analysis-step="${boards.length - 1}">终局</button>
          </div>
        </section>
        <section class="panel recordPane">
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
    </div>
  `;
}

function renderBoardPaneTabs() {
  return `
    <div class="boardMobileTabs">
      <button class="${state.boardPaneTab === 'board' ? 'btn' : 'ghost'}" data-board-pane="board">棋盘</button>
      <button class="${state.boardPaneTab === 'moves' ? 'btn' : 'ghost'}" data-board-pane="moves">记录</button>
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
        <h2 class="sectionTitle">登录后可进行房间对局与学习进度互动</h2>
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

function renderXiangqiBoard(game, options = {}) {
  const rows = game.board || [];
  const disabled = !canInteractWithBoard(game);
  const marker = currentMoveMarker(game);
  const flipped = !!options.flipped;
  const riverText = options.riverText || (flipped ? '汉界　楚河' : '楚河　汉界');
  const cells = [];
  for (let displayRow = 0; displayRow < XIANGQI_ROWS; displayRow += 1) {
    for (let displayCol = 0; displayCol < XIANGQI_COLS; displayCol += 1) {
      const mapped = mapDisplayToBoardPosition('XIANGQI', displayRow, displayCol, flipped);
      const r = mapped.row;
      const c = mapped.col;
      const boardRow = Array.isArray(rows[r]) ? rows[r] : [];
      const cell = Array.isArray(boardRow) ? boardRow[c] : '';
      const piece = normalizeXiangqiPiece(cell);
      const redPiece = isRedPiece(cell);
      const cls = ['xiangqiCell'];
      if (piece) cls.push('has-piece');
      if (piece && !redPiece) cls.push('is-black');
      if (state.selectedFrom && state.selectedFrom.row === r && state.selectedFrom.col === c) cls.push('is-selected');
      cells.push(`<button class="${cls.join(' ')}" data-board="xiangqi" data-row="${r}" data-col="${c}" data-display-row="${displayRow}" data-display-col="${displayCol}" ${disabled ? 'disabled' : ''}>${renderXiangqiCellLines(displayRow, displayCol)}${renderXiangqiMarkerGlyph(displayRow, displayCol)}${renderXiangqiLastMoveMarker(marker, r, c)}${piece ? `<span class="piece">${escapeHtml(piece)}</span>` : ''}</button>`);
    }
  }
  return `<div class="xiangqiBoard ${flipped ? 'xiangqiBoard--flipped' : ''}">
    <div class="xiangqiBoardRiver" data-river-layer="under-piece">${riverText}</div>
    <div class="xiangqiBoardGrid">
      ${renderXiangqiPalaceLayer()}
      ${cells.join('')}
    </div>
  </div>`;
}

function renderStaticXiangqiBoard(board, marker = null) {
  const rows = normalizeBoard(board);
  return `<div class="xiangqiBoard xiangqiBoard--static">
    <div class="xiangqiBoardRiver">楚河　汉界</div>
    <div class="xiangqiBoardGrid">
      ${renderXiangqiPalaceLayer()}
      ${rows.map((row, r) => row.map((cell, c) => {
        const piece = normalizeXiangqiPiece(cell);
        const redPiece = isRedPiece(cell);
        return `<button class="xiangqiCell xiangqiCell--static ${piece ? 'has-piece' : ''} ${piece && !redPiece ? 'is-black' : ''}" disabled>${renderXiangqiCellLines(r, c)}${renderXiangqiMarkerGlyph(r, c)}${renderXiangqiLastMoveMarker(marker, r, c)}${piece ? `<span class="piece">${escapeHtml(piece)}</span>` : ''}</button>`;
      }).join('')).join('')}
    </div>
  </div>`;
}

function renderGomokuBoard(game, options = {}) {
  const rows = game.board || [];
  const disabled = !canInteractWithBoard(game);
  const marker = currentMoveMarker(game);
  const flipped = !!options.flipped;
  const cells = [];
  for (let displayRow = 0; displayRow < GOMOKU_SIZE; displayRow += 1) {
    for (let displayCol = 0; displayCol < GOMOKU_SIZE; displayCol += 1) {
      const mapped = mapDisplayToBoardPosition('GOMOKU', displayRow, displayCol, flipped);
      const r = mapped.row;
      const c = mapped.col;
      const boardRow = Array.isArray(rows[r]) ? rows[r] : [];
      const cell = Array.isArray(boardRow) ? boardRow[c] : '';
      const cls = ['gomokuCell'];
      if (isGomokuStarPoint(displayRow, displayCol)) cls.push('is-star');
      if (cell === 'BLACK') cls.push('is-black');
      if (cell === 'WHITE') cls.push('is-white');
      cells.push(`<button class="${cls.join(' ')}" data-board="gomoku" data-row="${r}" data-col="${c}" data-display-row="${displayRow}" data-display-col="${displayCol}" ${disabled ? 'disabled' : ''}><span class="gomokuStone"></span>${renderGomokuLastMoveMarker(marker, r, c)}</button>`);
    }
  }
  return `<div class="gomokuBoard">
    ${cells.join('')}
  </div>`;
}

function renderStaticGomokuBoard(board, marker = null) {
  const rows = normalizeBoard(board);
  return `<div class="gomokuBoard">
    ${rows.map((row, r) => row.map((cell, c) => `<button class="gomokuCell ${isGomokuStarPoint(r, c) ? 'is-star' : ''} ${cell === 'BLACK' ? 'is-black' : ''} ${cell === 'WHITE' ? 'is-white' : ''}" disabled><span class="gomokuStone"></span>${renderGomokuLastMoveMarker(marker, r, c)}</button>`).join('')).join('')}
  </div>`;
}

function renderPlayableBoardByGameType(game, options = {}) {
  if (!game || !isSupportedGameType(game.gameType)) {
    return renderUnsupportedBoard(game ? game.gameType : '', 'game');
  }
  if (game.gameType === 'XIANGQI') {
    return renderXiangqiBoard(game, options);
  }
  return renderGomokuBoard(game, options);
}

function renderAnalysisBoardByGameType(gameType, board, marker = null) {
  if (gameType === 'XIANGQI') {
    return renderStaticXiangqiBoard(board, marker);
  }
  if (gameType === 'GOMOKU') {
    return renderStaticGomokuBoard(board, marker);
  }
  return renderUnsupportedBoard(gameType, 'analysis');
}

function renderUnsupportedBoard(gameType, surface) {
  const typeText = escapeHtml(gameType || 'UNKNOWN');
  const sceneText = surface === 'analysis' ? '分析页' : '对局页';
  return `
    <div class="boardUnsupported" data-unsupported-board="${surface}">
      <strong>${sceneText}暂不支持该棋种渲染</strong>
      <div class="muted">gameType: ${typeText}</div>
    </div>
  `;
}

function normalizeBoard(board) {
  if (!Array.isArray(board)) return [];
  return board.map(row => Array.isArray(row) ? row : []);
}

function isGomokuStarPoint(row, col) {
  return (row === 3 && col === 3)
    || (row === 3 && col === 11)
    || (row === 7 && col === 7)
    || (row === 11 && col === 3)
    || (row === 11 && col === 11);
}

function isXiangqiMarkerPoint(row, col) {
  return (row === 2 && (col === 1 || col === 7))
    || (row === 7 && (col === 1 || col === 7))
    || (row === 3 && [0, 2, 4, 6, 8].includes(col))
    || (row === 6 && [0, 2, 4, 6, 8].includes(col));
}

function renderXiangqiPalaceLayer() {
  return `
    <div class="xiangqiPalaceLayer" aria-hidden="true">
      <span class="xiangqiPalaceLine xiangqiPalaceLine--top-a"></span>
      <span class="xiangqiPalaceLine xiangqiPalaceLine--top-b"></span>
      <span class="xiangqiPalaceLine xiangqiPalaceLine--bottom-a"></span>
      <span class="xiangqiPalaceLine xiangqiPalaceLine--bottom-b"></span>
    </div>
  `;
}

function renderXiangqiCellLines(row, col) {
  const hasUp = row > 0 && !(row === 5 && col >= 1 && col <= 7);
  const hasDown = row < 9 && !(row === 4 && col >= 1 && col <= 7);
  const hasLeft = col > 0;
  const hasRight = col < 8;
  return `
    <span class="xiangqiLines" aria-hidden="true">
      ${hasUp ? '<span class="xiangqiLine xiangqiLine--up"></span>' : ''}
      ${hasDown ? '<span class="xiangqiLine xiangqiLine--down"></span>' : ''}
      ${hasLeft ? '<span class="xiangqiLine xiangqiLine--left"></span>' : ''}
      ${hasRight ? '<span class="xiangqiLine xiangqiLine--right"></span>' : ''}
    </span>
  `;
}

function renderXiangqiMarkerGlyph(row, col) {
  if (!isXiangqiMarkerPoint(row, col)) {
    return '';
  }
  const hasLeft = col > 0;
  const hasRight = col < 8;
  return `
    <span class="xiangqiMarker" aria-hidden="true">
      ${hasLeft ? '<span class="xiangqiMarkerCorner xiangqiMarkerCorner--lt"></span><span class="xiangqiMarkerCorner xiangqiMarkerCorner--lb"></span>' : ''}
      ${hasRight ? '<span class="xiangqiMarkerCorner xiangqiMarkerCorner--rt"></span><span class="xiangqiMarkerCorner xiangqiMarkerCorner--rb"></span>' : ''}
    </span>
  `;
}

function getLastMove(game) {
  if (!game || !Array.isArray(game.moves) || !game.moves.length) {
    return null;
  }
  return game.moves[game.moves.length - 1];
}

function currentMoveMarker(game) {
  if (state.moveInFlight
    && state.pendingMoveMarker
    && game
    && game.gameId
    && state.pendingMoveGameId === game.gameId) {
    return state.pendingMoveMarker;
  }
  return createMoveMarker(game.gameType, getLastMove(game), game.viewerSide || '');
}

function createPendingMoveMarker(game, payload) {
  if (!game || !payload) {
    return null;
  }
  if (game.gameType === 'XIANGQI' && isXiangqiMovePayload(payload)) {
    return {
      gameType: 'XIANGQI',
      fromRow: payload.fromRow,
      fromCol: payload.fromCol,
      toRow: payload.toRow,
      toCol: payload.toCol,
      owner: 'self',
      pending: true
    };
  }
  if (game.gameType === 'GOMOKU' && isGomokuMovePayload(payload)) {
    return {
      gameType: 'GOMOKU',
      row: payload.row,
      col: payload.col,
      owner: 'self',
      pending: true
    };
  }
  return null;
}

function clearPendingMoveMarker() {
  state.pendingMoveMarker = null;
  state.pendingMoveGameId = '';
}

function applyOptimisticPracticeMove(game, payload) {
  if (!game || !game.isTraining || !payload) {
    return null;
  }
  const optimistic = JSON.parse(JSON.stringify(game));
  const board = normalizeBoard(optimistic.board).map(row => row.slice());
  const viewerSide = optimistic.viewerSide || inferViewerSide(optimistic);
  if (game.gameType === 'XIANGQI' && isXiangqiMovePayload(payload)) {
    const fromRow = payload.fromRow;
    const fromCol = payload.fromCol;
    const toRow = payload.toRow;
    const toCol = payload.toCol;
    const sourceRow = Array.isArray(board[fromRow]) ? board[fromRow].slice() : [];
    const targetRow = fromRow === toRow
      ? sourceRow
      : (Array.isArray(board[toRow]) ? board[toRow].slice() : []);
    const movingPiece = sourceRow[fromCol];
    if (!movingPiece) {
      return null;
    }
    sourceRow[fromCol] = '';
    targetRow[toCol] = movingPiece;
    board[fromRow] = sourceRow;
    board[toRow] = targetRow;
  } else if (game.gameType === 'GOMOKU' && isGomokuMovePayload(payload)) {
    const row = payload.row;
    const col = payload.col;
    const stone = viewerSide === 'WHITE' ? 'WHITE' : 'BLACK';
    const targetRow = Array.isArray(board[row]) ? board[row].slice() : [];
    targetRow[col] = stone;
    board[row] = targetRow;
  } else {
    return null;
  }
  optimistic.board = board;
  optimistic.updatedAt = new Date().toISOString();
  optimistic.aiPending = false;
  if (Array.isArray(optimistic.moves)) {
    const nextIndex = optimistic.moves.length + 1;
    optimistic.moves = optimistic.moves.slice();
    optimistic.moves.push({
      index: nextIndex,
      side: viewerSide || optimistic.currentTurn || '',
      notation: optimisticMoveNotation(game.gameType, payload),
      payload: Object.assign({}, payload, viewerSide ? { side: viewerSide } : {}),
      actorUserId: state.me && state.me.id ? state.me.id : '',
      createdAt: optimistic.updatedAt
    });
    optimistic.moveCount = optimistic.moves.length;
  } else {
    optimistic.moves = [];
    optimistic.moveCount = 0;
  }
  const opponentSide = resolveOpponentSide(optimistic, viewerSide);
  if (opponentSide) {
    optimistic.currentTurn = opponentSide;
  }
  return enrichGame(optimistic);
}

function playOptimisticPracticeMoveSound(game) {
  if (!game || !game.gameId || !game.isTraining) {
    return;
  }
  const latestIndex = Number(game.moveCount || (Array.isArray(game.moves) ? game.moves.length : 0));
  if (!Number.isFinite(latestIndex) || latestIndex <= 0) {
    return;
  }
  state.lastMoveSoundGameId = game.gameId;
  state.lastMoveSoundIndex = latestIndex;
  playOnlineSound(onlineMoveAudio);
}

function optimisticMoveNotation(gameType, payload) {
  if (gameType === 'XIANGQI' && isXiangqiMovePayload(payload)) {
    return `${payload.fromRow},${payload.fromCol} -> ${payload.toRow},${payload.toCol}`;
  }
  if (gameType === 'GOMOKU' && isGomokuMovePayload(payload)) {
    return `${payload.row},${payload.col}`;
  }
  return 'pending';
}

function createMoveMarker(gameType, move, viewerSide) {
  if (!move || !move.payload) {
    return null;
  }
  const payload = move.payload;
  const side = String(move.side || payload.side || '').trim();
  const owner = moveOwnerClass(side, viewerSide);
  if (gameType === 'XIANGQI' && isXiangqiMovePayload(payload)) {
    return {
      gameType: 'XIANGQI',
      fromRow: payload.fromRow,
      fromCol: payload.fromCol,
      toRow: payload.toRow,
      toCol: payload.toCol,
      owner: owner
    };
  }
  if (gameType === 'GOMOKU' && isGomokuMovePayload(payload)) {
    return {
      gameType: 'GOMOKU',
      row: payload.row,
      col: payload.col,
      owner: owner
    };
  }
  return null;
}

function moveOwnerClass(side, viewerSide) {
  if (viewerSide && side && side === viewerSide) {
    return 'self';
  }
  if (side) {
    return 'opponent';
  }
  return 'neutral';
}

function renderXiangqiLastMoveMarker(marker, row, col) {
  if (!marker || marker.gameType !== 'XIANGQI') {
    return '';
  }
  const owner = marker.owner || 'neutral';
  const pendingClass = marker.pending ? ' xiangqiLastMove--pending' : '';
  let html = '';
  if (marker.fromRow === row && marker.fromCol === col) {
    html += `<span class="xiangqiLastMove xiangqiLastMove--from xiangqiLastMove--${owner}${pendingClass}"></span>`;
  }
  if (marker.toRow === row && marker.toCol === col) {
    html += `<span class="xiangqiLastMove xiangqiLastMove--to xiangqiLastMove--${owner}${pendingClass}"></span>`;
  }
  return html;
}

function renderGomokuLastMoveMarker(marker, row, col) {
  if (!marker || marker.gameType !== 'GOMOKU') {
    return '';
  }
  if (marker.row !== row || marker.col !== col) {
    return '';
  }
  const owner = marker.owner || 'neutral';
  const pendingClass = marker.pending ? ' gomokuLastMove--pending' : '';
  return `<span class="gomokuLastMove gomokuLastMove--${owner}${pendingClass}"></span>`;
}

function normalizeXiangqiPiece(cell) {
  const raw = String(cell || '').trim();
  if (!raw) return '';
  if (isRedPiece(raw)) {
    if (raw === '帥') return '帅';
    if (raw === '馬') return '马';
    if (raw === '車') return '车';
    if (raw === '砲') return '炮';
    if (raw === '卒') return '兵';
    return raw;
  }
  if (raw === '兵') return '卒';
  if (raw === '馬') return '马';
  if (raw === '車') return '车';
  if (raw === '砲') return '炮';
  return raw;
}

function isRedPiece(text) {
  return ['帥', '帅', '仕', '相', '馬', '車', '砲', '卒'].includes(String(text || '').trim());
}

function isViewerOwnXiangqiPiece(piece, viewerSide) {
  const normalized = String(piece || '').trim();
  if (!normalized) {
    return false;
  }
  const redPiece = isRedPiece(normalized);
  if (viewerSide === 'RED') {
    return redPiece;
  }
  if (viewerSide === 'BLACK') {
    return !redPiece;
  }
  return false;
}

function bindCommon(route) {
  bindNavClicks();
  document.querySelectorAll('[data-auth-mode]').forEach(el => el.addEventListener('click', () => {
    state.authMode = el.getAttribute('data-auth-mode');
    state.authError = '';
    state.showAuthModal = true;
    render();
  }));
  document.querySelectorAll('[data-learn-field]').forEach(el => el.addEventListener('change', event => updateLearnConfig(event.currentTarget)));
  document.querySelectorAll('[data-watch-filter]').forEach(el => el.addEventListener('change', event => {
    const field = event.currentTarget.getAttribute('data-watch-filter');
    state.watchFilters[field] = event.currentTarget.value;
    render();
  }));
  document.querySelectorAll('[data-community-tab]').forEach(el => el.addEventListener('click', () => {
    state.communityTab = el.getAttribute('data-community-tab');
    render();
  }));
  document.querySelectorAll('[data-community-game-type]').forEach(el => el.addEventListener('click', () => {
    state.leaderboardGameType = el.getAttribute('data-community-game-type');
    render();
  }));
  document.querySelectorAll('[data-learn-complete]').forEach(el => el.addEventListener('click', () => {
    markLearnCompleted(el.getAttribute('data-learn-complete'), el.getAttribute('data-id'));
  }));
  document.querySelectorAll('[data-action="start-practice-preset"]').forEach(el => el.addEventListener('click', startPracticeFromPreset));
  document.querySelectorAll('[data-action="start-puzzle-practice"]').forEach(el => el.addEventListener('click', startPracticeFromPuzzle));
  on('[data-action="logout"]', logout);
  on('[data-action="show-auth"]', () => {
    state.showAuthModal = true;
    state.authError = '';
    render();
  });
  on('[data-action="toggle-sound"]', toggleOnlineSound);
  on('[data-action="submit-auth"]', submitAuth);
  on('[data-action="quick-start-ai-practice"]', quickStartAiPractice);
  on('[data-action="quick-start-gomoku-practice"]', quickStartGomokuPractice);
  on('[data-action="create-room"]', createRoom);
  on('[data-action="join-by-code"]', joinByCode);
  on('[data-action="join-room"]', joinCurrentRoom);
  on('[data-action="toggle-ready"]', toggleReady);
  on('[data-action="create-practice"]', createPracticeGame);
  on('[data-action="refresh-watch"]', () => loadWatchOverview(true));
  
  const searchInput = document.getElementById('lobbySearchInput');
  if (searchInput) {
    searchInput.addEventListener('input', event => {
      const value = event.target.value;
      if (state.lobbySearchTimer) {
        window.clearTimeout(state.lobbySearchTimer);
      }
      state.lobbySearchTimer = window.setTimeout(() => {
        loadLobbySearch(value);
      }, 300);
    });
    if (state.lobbySearch.query) {
      searchInput.focus();
      const len = searchInput.value.length;
      searchInput.setSelectionRange(len, len);
    }
  }

  document.querySelectorAll('[data-board-pane]').forEach(el => el.addEventListener('click', () => {
    const pane = el.getAttribute('data-board-pane');
    if (pane !== 'board' && pane !== 'moves') {
      return;
    }
    if (state.boardPaneTab === pane) {
      return;
    }
    state.boardPaneTab = pane;
    render();
  }));
  bindBoardCellEvents();
  bindInlineOnlineGameActions();
  document.querySelectorAll('[data-action="practice-rematch"]').forEach(el => {
    if (el.dataset.boundPracticeRematch === '1') {
      return;
    }
    el.dataset.boundPracticeRematch = '1';
    el.addEventListener('click', startPracticeRematch);
  });
  document.querySelectorAll('[data-action="undo-practice"]').forEach(el => {
    if (el.dataset.boundPracticeUndo === '1') {
      return;
    }
    el.dataset.boundPracticeUndo = '1';
    el.addEventListener('click', undoPracticeMove);
  });
  document.querySelectorAll('[data-action="close-end-modal"]').forEach(el => {
    if (el.dataset.boundCloseModal === '1') {
      return;
    }
    el.dataset.boundCloseModal = '1';
    el.addEventListener('click', closeEndGameModal);
  });
  document.querySelectorAll('[data-end-game-card]').forEach(el => el.addEventListener('click', event => event.stopPropagation()));
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
    state.showAuthModal = false;
    await refreshBootstrapAndProfile();
    await loadLearnProgress();
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
  state.moveInFlight = false;
  stopPracticePolling();
  state.analysis = null;
  state.learnProgress = null;
  state.bootstrap = null;
  state.showAuthModal = false;
  state.selectedFrom = null;
  state.endGameModal = null;
  state.endGameModalShownKey = '';
  state.lastMoveSoundGameId = '';
  state.lastMoveSoundIndex = 0;
  state.lastFinishSoundKey = '';
  clearAiMoveHint();
  state.status = '';
  await loadBootstrap();
  render();
}

function bindNavClicks(root = document) {
  root.querySelectorAll('[data-nav]').forEach(el => {
    if (el.dataset.boundNav === '1') {
      return;
    }
    el.dataset.boundNav = '1';
    el.addEventListener('click', () => navTo(el.getAttribute('data-nav')));
  });
}

function bindBoardCellEvents(root = document) {
  root.querySelectorAll('[data-board="xiangqi"]').forEach(el => {
    if (el.dataset.boundBoard === '1') {
      return;
    }
    el.dataset.boundBoard = '1';
    el.addEventListener('click', onXiangqiCellClick);
  });
  root.querySelectorAll('[data-board="gomoku"]').forEach(el => {
    if (el.dataset.boundBoard === '1') {
      return;
    }
    el.dataset.boundBoard = '1';
    el.addEventListener('click', onGomokuCellClick);
  });
}

function bindInlineOnlineGameActions(root = document) {
  root.querySelectorAll('[data-action="offer-draw"]').forEach(el => {
    if (el.dataset.boundInlineAction === '1') {
      return;
    }
    el.dataset.boundInlineAction = '1';
    el.addEventListener('click', offerDraw);
  });
  root.querySelectorAll('[data-action="accept-draw"]').forEach(el => {
    if (el.dataset.boundInlineAction === '1') {
      return;
    }
    el.dataset.boundInlineAction = '1';
    el.addEventListener('click', () => respondDraw(true));
  });
  root.querySelectorAll('[data-action="reject-draw"]').forEach(el => {
    if (el.dataset.boundInlineAction === '1') {
      return;
    }
    el.dataset.boundInlineAction = '1';
    el.addEventListener('click', () => respondDraw(false));
  });
  root.querySelectorAll('[data-action="resign"]').forEach(el => {
    if (el.dataset.boundInlineAction === '1') {
      return;
    }
    el.dataset.boundInlineAction = '1';
    el.addEventListener('click', resignGame);
  });
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

async function createPracticeGame(overrideConfig = null) {
  const payload = overrideConfig ? { ...state.learnConfig, ...overrideConfig } : state.learnConfig;
  try {
    state.status = '';
    stopPracticePolling();
    const game = await fetchJson(`${API_BASE}/learn/practice-games`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    state.game = applyServerGameSnapshot(game);
    await refreshBootstrapAndProfile();
    navTo(`practice/${game.gameId}`);
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function quickStartAiPractice() {
  if (!state.me) {
    state.showAuthModal = true;
    state.authError = '请先登录，再直接进入 AI 对局。';
    render();
    return;
  }
  state.learnConfig = {
    gameType: 'XIANGQI',
    difficulty: 'MEDIUM',
    humanFirst: true,
    preferredEngine: 'BUILTIN'
  };
  await createPracticeGame();
}

async function quickStartGomokuPractice() {
  if (!state.me) {
    state.showAuthModal = true;
    state.authError = '请先登录，再直接进入五子棋 AI 对局。';
    render();
    return;
  }
  state.learnConfig = {
    gameType: 'GOMOKU',
    difficulty: 'MEDIUM',
    humanFirst: true,
    preferredEngine: 'BUILTIN'
  };
  await createPracticeGame();
}

async function startPracticeFromPreset(event) {
  if (!state.me) {
    state.showAuthModal = true;
    state.authError = '登录后可按推荐配置开始练习';
    render();
    return;
  }
  const presetId = event.currentTarget.getAttribute('data-preset-id');
  const presets = (state.learnContent && state.learnContent.recommendedPractice) || [];
  const preset = presets.find(item => item.id === presetId);
  if (!preset) {
    state.status = '未找到练习配置。';
    render();
    return;
  }
  state.learnConfig = {
    gameType: preset.gameType || 'XIANGQI',
    difficulty: preset.difficulty || 'MEDIUM',
    humanFirst: preset.humanFirst !== false,
    preferredEngine: preset.preferredEngine || 'BUILTIN'
  };
  await createPracticeGame();
}

async function startPracticeFromPuzzle(event) {
  if (!state.me) {
    state.showAuthModal = true;
    state.authError = '登录后可按题目局面直接开始练习';
    render();
    return;
  }
  const puzzleId = event.currentTarget.getAttribute('data-puzzle-id');
  const puzzles = (state.learnContent && state.learnContent.puzzles) || [];
  const puzzle = puzzles.find(item => item.id === puzzleId);
  if (!puzzle) {
    state.status = '未找到对应题目。';
    render();
    return;
  }
  const fen = normalizeFenText(puzzle.fen);
  if (!canStartPuzzlePractice(puzzle)) {
    state.status = '该题目 FEN 暂不可用于在线开局，请先选择其他题目或推荐练习。';
    render();
    return;
  }
  state.learnConfig = {
    gameType: 'XIANGQI',
    difficulty: puzzle.difficulty || 'MEDIUM',
    humanFirst: true,
    preferredEngine: 'BUILTIN'
  };
  await createPracticeGame({ gameType: 'XIANGQI', initialFen: fen });
}

async function markLearnCompleted(kind, id) {
  if (!state.me) {
    state.showAuthModal = true;
    state.authError = '登录后才能记录学习进度';
    render();
    return;
  }
  const normalizedKind = (kind || '').toLowerCase();
  const endpoint = normalizedKind === 'tutorial'
    ? `${API_BASE}/learn/tutorials/${id}/complete`
    : `${API_BASE}/learn/puzzles/${id}/complete`;
  try {
    await fetchJson(endpoint, { method: 'POST', body: '{}' });
    await loadLearnProgress();
  } catch (error) {
    state.status = error.message;
    render();
  }
}

async function startPracticeRematch() {
  const game = state.game;
  if (!game || !game.isTraining) {
    return;
  }
  closeEndGameModal();
  const ai = practiceAiMeta(game);
  const rematch = {
    gameType: game.gameType || state.learnConfig.gameType || 'XIANGQI',
    difficulty: game.difficulty || ai.difficulty || state.learnConfig.difficulty || 'MEDIUM',
    humanFirst: inferPracticeHumanFirst(game),
    preferredEngine: ai.preferredEngine || ai.engineId || game.aiEngine || state.learnConfig.preferredEngine || 'BUILTIN'
  };
  if (rematch.gameType === 'XIANGQI' && game.initialFen) {
    rematch.initialFen = game.initialFen;
  }
  await createPracticeGame(rematch);
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

async function undoPracticeMove() {
  const game = state.game;
  const reason = practiceUndoDisabledReason(game);
  if (reason) {
    state.status = reason;
    if (!refreshLiveStatusLine(currentRoute())) {
      render();
    }
    return;
  }
  await sendGameAction(`${API_BASE}/learn/practice-games/${game.gameId}/undo`, {});
}

async function resignGame() {
  if (!state.game || state.game.status !== 'PLAYING') {
    return;
  }
  const prompt = state.game.isTraining ? '确定要在当前 AI 练习局认输吗？' : '确定要在当前在线对局认输吗？';
  if (!window.confirm(prompt)) {
    return;
  }
  await sendGameAction(`${gameActionBase(state.game)}/resign`, {});
}

async function sendGameAction(url, body) {
  try {
    state.status = '';
    state.game = applyServerGameSnapshot(await fetchJson(url, { method: 'POST', body: JSON.stringify(body) }));
    if (!isPracticeAiPending(state.game) || state.game.status !== 'PLAYING') {
      stopPracticePolling();
    } else {
      startPracticePolling(state.game.gameId, false);
    }
    render();
    refreshBootstrapAndProfile().catch(() => null);
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
  stopPracticePolling();
  state.game = applyServerGameSnapshot(await fetchJson(`${API_BASE}/games/${gameId}`).catch(() => null));
  render();
}

async function loadPractice(gameId) {
  state.game = applyServerGameSnapshot(await fetchJson(`${API_BASE}/learn/practice-games/${gameId}`).catch(() => null));
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

function refreshLiveBoardSurface(route = currentRoute()) {
  if (!state.game || !isBoardRoutePage(route.page)) {
    return false;
  }
  const host = document.querySelector('[data-live-board-host]');
  if (!host) {
    return false;
  }
  const previousBoard = host.querySelector('.xiangqiBoard, .gomokuBoard');
  const previousXiCell = previousBoard ? parseFloat(getComputedStyle(previousBoard).getPropertyValue('--xi-cell-size')) : 0;
  const previousGoCell = previousBoard ? parseFloat(getComputedStyle(previousBoard).getPropertyValue('--go-cell-size')) : 0;
  host.innerHTML = renderPlayableBoardByGameType(state.game, resolveBoardRenderOptions(state.game, route));
  const board = host.querySelector('.xiangqiBoard, .gomokuBoard');
  if (route.page === 'practice' && board) {
    if (board.classList.contains('xiangqiBoard') && previousXiCell > 0) {
      board.style.setProperty('--xi-cell-size', `${previousXiCell}px`);
    }
    if (board.classList.contains('gomokuBoard') && previousGoCell > 0) {
      board.style.setProperty('--go-cell-size', `${previousGoCell}px`);
    }
  }
  if (board && isBoardFitRoute(route)) {
    fitBoardToViewport(route, true);
  }
  bindBoardCellEvents(host);
  return true;
}

function refreshLiveStatusLine(route = currentRoute()) {
  if (!state.game || !isBoardRoutePage(route.page)) {
    return false;
  }
  const statusEl = document.querySelector('[data-live-status]');
  if (!statusEl) {
    return false;
  }
  if (route.page === 'game' && !state.game.isTraining) {
    statusEl.textContent = onlineGameStatusText(state.game);
    return true;
  }
  if (route.page === 'practice' || (route.page === 'game' && state.game.isTraining)) {
    statusEl.textContent = practiceStatusText(state.game);
    return true;
  }
  return false;
}

function refreshOnlineGameMetaPills() {
  const route = currentRoute();
  if (route.page !== 'game' || !state.game || state.game.isTraining) {
    return false;
  }
  const viewerSide = state.game.viewerSide || inferViewerSide(state.game);
  const opponentSide = resolveOpponentSide(state.game, viewerSide);
  const sideSelf = document.querySelector('[data-live-side-self]');
  const sideOpponent = document.querySelector('[data-live-side-opponent]');
  const turn = document.querySelector('[data-live-turn]');
  const status = document.querySelector('[data-live-game-status]');
  const termination = document.querySelector('[data-live-game-termination]');
  if (sideSelf) sideSelf.textContent = `你执 ${sideLabel(state.game.gameType, viewerSide)}`;
  if (sideOpponent) sideOpponent.textContent = `对手执 ${sideLabel(state.game.gameType, opponentSide)}`;
  if (turn) turn.textContent = `轮到 ${turnTextForViewer(state.game, viewerSide)}`;
  if (status) status.textContent = state.game.status || '-';
  if (termination) termination.textContent = state.game.terminationReason || 'LIVE';
  return !!(sideSelf || sideOpponent || turn || status || termination);
}

function refreshGameInteractionUi(route = currentRoute()) {
  const board = refreshLiveBoardSurface(route);
  const status = refreshLiveStatusLine(route);
  const meta = refreshOnlineGameMetaPills();
  return board || status || meta;
}

function patchOnlineGameRealtimeView() {
  const route = currentRoute();
  if (route.page !== 'game' || !state.game || state.game.isTraining) {
    return false;
  }
  let patched = refreshGameInteractionUi(route);
  const clockHost = document.querySelector('[data-live-clock-grid]');
  if (clockHost) {
    clockHost.innerHTML = `${renderClockCard(state.game, 'first')}${renderClockCard(state.game, 'second')}`;
    patched = true;
  }
  const movesHost = document.querySelector('[data-live-moves]');
  if (movesHost) {
    movesHost.innerHTML = (state.game.moves || []).length
      ? state.game.moves.map(renderMoveRow).join('')
      : '<div class="banner">等待第一步落子。</div>';
    patched = true;
  }
  const viewerSide = state.game.viewerSide || inferViewerSide(state.game);
  const drawOffer = state.game.drawOffer;
  const canRespondDraw = drawOffer && drawOffer.side !== viewerSide;
  const drawHost = document.querySelector('[data-live-draw-offer]');
  if (drawHost) {
    drawHost.innerHTML = drawOffer ? renderDrawOfferBanner(drawOffer, canRespondDraw) : '';
    patched = true;
  }
  const actionsHost = document.querySelector('[data-live-game-actions]');
  if (actionsHost) {
    const canOfferDraw = state.game.status === 'PLAYING' && !drawOffer;
    actionsHost.innerHTML = renderOnlineGameActions(state.game, canOfferDraw);
    patched = true;
  }
  if (patched) {
    bindNavClicks();
    bindInlineOnlineGameActions();
  }
  return patched;
}

function onXiangqiCellClick(event) {
  if (!canInteractWithBoard(state.game)) return;
  markBoardTap(event.currentTarget);
  if (!state.game || state.game.gameType !== 'XIANGQI') {
    state.status = '当前对局并非中国象棋，无法使用象棋落子。';
    if (!refreshLiveStatusLine()) {
      render();
    }
    return;
  }
  const row = Number(event.currentTarget.dataset.row);
  const col = Number(event.currentTarget.dataset.col);
  const boardRow = Array.isArray(state.game.board) ? state.game.board[row] : [];
  const clickedCell = Array.isArray(boardRow) ? boardRow[col] : '';
  if (!state.selectedFrom) {
    if (!clickedCell) {
      return;
    }
    if (!isViewerOwnXiangqiPiece(clickedCell, state.game.viewerSide)) {
      state.status = '请先选择己方棋子。';
      if (!refreshLiveStatusLine()) {
        render();
      }
      return;
    }
    state.status = '';
    state.selectedFrom = { row, col };
    if (!refreshGameInteractionUi()) {
      render();
    }
    return;
  }
  if (state.selectedFrom.row === row && state.selectedFrom.col === col) {
    state.status = '';
    state.selectedFrom = null;
    if (!refreshGameInteractionUi()) {
      render();
    }
    return;
  }
  if (clickedCell && isViewerOwnXiangqiPiece(clickedCell, state.game.viewerSide)) {
    state.status = '';
    state.selectedFrom = { row, col };
    if (!refreshGameInteractionUi()) {
      render();
    }
    return;
  }
  const move = { fromRow: state.selectedFrom.row, fromCol: state.selectedFrom.col, toRow: row, toCol: col };
  state.status = '';
  state.selectedFrom = null;
  sendMove(move);
}

function onGomokuCellClick(event) {
  if (!canInteractWithBoard(state.game)) return;
  markBoardTap(event.currentTarget);
  if (!state.game || state.game.gameType !== 'GOMOKU') {
    state.status = '当前对局并非五子棋，无法使用五子棋落子。';
    if (!refreshLiveStatusLine()) {
      render();
    }
    return;
  }
  state.status = '';
  sendMove({ row: Number(event.currentTarget.dataset.row), col: Number(event.currentTarget.dataset.col) });
}

async function sendMove(payload) {
  const gameBeforeMove = state.game;
  const routeBeforeMove = currentRoute();
  const optimisticGame = applyOptimisticPracticeMove(gameBeforeMove, payload);
  if (state.moveInFlight) {
    state.status = '正在提交走子，请稍候...';
    if (!refreshLiveStatusLine(routeBeforeMove)) {
      render();
    }
    return;
  }
  const moveUrl = `${gameActionBase(gameBeforeMove)}/move`;
  const payloadError = validateMovePayload(gameBeforeMove, payload);
  if (payloadError) {
    state.status = payloadError;
    if (!refreshLiveStatusLine(routeBeforeMove)) {
      render();
    }
    return;
  }
  const requestToken = ++state.moveRequestToken;
  state.moveInFlight = true;
  state.status = '';
  state.pendingMoveGameId = gameBeforeMove && gameBeforeMove.gameId ? gameBeforeMove.gameId : '';
  state.pendingMoveMarker = createPendingMoveMarker(gameBeforeMove, payload);
  if (optimisticGame) {
    state.game = optimisticGame;
    playOptimisticPracticeMoveSound(optimisticGame);
  }
  if (!refreshGameInteractionUi(routeBeforeMove)) {
    render();
  }
  try {
    const snapshot = applyServerGameSnapshot(await fetchJson(moveUrl, { method: 'POST', body: JSON.stringify(payload) }));
    if (requestToken !== state.moveRequestToken) {
      return;
    }
    state.game = snapshot;
    state.moveInFlight = false;
    clearPendingMoveMarker();
    if (isPracticeAiPending(state.game)) {
      startPracticePolling(state.game.gameId, false);
    } else {
      stopPracticePolling();
    }
    render();
    refreshBootstrapAndProfile().catch(() => null);
  } catch (error) {
    if (requestToken !== state.moveRequestToken) {
      return;
    }
    if (optimisticGame) {
      state.game = gameBeforeMove;
    }
    state.status = error.message;
    state.moveInFlight = false;
    clearPendingMoveMarker();
    if (!refreshGameInteractionUi(currentRoute())) {
      render();
    }
  } finally {
    if (requestToken === state.moveRequestToken && state.moveInFlight) {
      state.moveInFlight = false;
      clearPendingMoveMarker();
      refreshGameInteractionUi(currentRoute());
    }
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
    const previousGame = state.game;
    if (data.room) state.room = data.room;
    if (data.game) {
      const incomingGame = data.game;
      if (previousGame && incomingGame && previousGame.gameId === incomingGame.gameId) {
        const currentStateId = Number(previousGame.stateId || 0);
        const incomingStateId = Number(incomingGame.stateId || 0);
        if (incomingStateId > 0 && currentStateId > 0) {
          if (incomingStateId <= currentStateId) {
            return;
          }
          if (incomingStateId > currentStateId + 1) {
            try {
              state.game = applyServerGameSnapshot(await fetchJson(`${API_BASE}/games/${incomingGame.gameId}`));
            } catch (error) {
              state.game = applyServerGameSnapshot(incomingGame);
            }
          } else {
            state.game = applyServerGameSnapshot(incomingGame);
          }
        } else {
          state.game = applyServerGameSnapshot(incomingGame);
        }
      } else {
        state.game = applyServerGameSnapshot(incomingGame);
      }
      const routeNow = currentRoute();
      if (previousGame
        && state.game
        && previousGame.gameId === state.game.gameId
        && routeNow.page === 'game'
        && !state.game.isTraining
        && patchOnlineGameRealtimeView()) {
        return;
      }
    }
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

function syncGameTransitionFeedback(previousGame, nextGame) {
  if (!nextGame || !nextGame.gameId) {
    return;
  }
  if (state.endGameModal
    && state.endGameModal.game
    && state.endGameModal.game.gameId === nextGame.gameId
    && nextGame.status !== 'FINISHED') {
    state.endGameModal = null;
  }
  maybePlayMoveSound(previousGame, nextGame);
  maybePlayFinishSound(previousGame, nextGame);
  maybeOpenEndGameModal(nextGame);
}

function maybePlayMoveSound(previousGame, nextGame) {
  if (!nextGame || !Array.isArray(nextGame.moves) || !nextGame.moves.length) {
    return;
  }
  const latest = getLastMove(nextGame);
  const latestIndex = Number(latest && latest.index ? latest.index : nextGame.moves.length);
  if (!Number.isFinite(latestIndex) || latestIndex <= 0) {
    return;
  }
  if (!previousGame || previousGame.gameId !== nextGame.gameId) {
    state.lastMoveSoundGameId = nextGame.gameId;
    state.lastMoveSoundIndex = latestIndex;
    return;
  }
  const prevLast = getLastMove(previousGame);
  const prevIndex = Number(prevLast && prevLast.index ? prevLast.index : (previousGame.moves || []).length);
  if (latestIndex <= prevIndex) {
    state.lastMoveSoundGameId = nextGame.gameId;
    state.lastMoveSoundIndex = latestIndex;
    return;
  }
  if (state.lastMoveSoundGameId === nextGame.gameId && state.lastMoveSoundIndex === latestIndex) {
    return;
  }
  state.lastMoveSoundGameId = nextGame.gameId;
  state.lastMoveSoundIndex = latestIndex;
  playOnlineSound(onlineMoveAudio);
}

function maybePlayFinishSound(previousGame, nextGame) {
  if (!nextGame || nextGame.status !== 'FINISHED') {
    return;
  }
  const key = endModalGameKey(nextGame);
  if (!key || state.lastFinishSoundKey === key) {
    return;
  }
  const becameFinished = !previousGame
    || previousGame.gameId !== nextGame.gameId
    || previousGame.status !== 'FINISHED';
  state.lastFinishSoundKey = key;
  if (becameFinished) {
    playOnlineSound(onlineMateAudio);
  }
}

function maybeOpenEndGameModal(game) {
  const route = currentRoute();
  if (!game || game.status !== 'FINISHED') {
    return;
  }
  if (route.page !== 'game' && route.page !== 'practice') {
    return;
  }
  const key = endModalGameKey(game);
  if (!key || state.endGameModalShownKey === key) {
    return;
  }
  state.endGameModalShownKey = key;
  state.endGameModal = { key: key, game: game };
}

function endModalGameKey(game) {
  if (!game || !game.gameId) {
    return '';
  }
  return `${game.gameId}|${game.status || ''}|${game.moveCount || 0}|${game.winnerSide || ''}|${game.terminationReason || ''}|${game.resultText || ''}`;
}

function closeEndGameModal() {
  state.endGameModal = null;
  render();
}

function initOnlineAudio() {
  window.addEventListener('pointerdown', unlockOnlineAudio, { once: true, capture: true });
  window.addEventListener('keydown', unlockOnlineAudio, { once: true, capture: true });
}

function readOnlineSoundEnabled() {
  try {
    return (localStorage.getItem('xq_online_sound_enabled') ?? '1') !== '0';
  } catch (error) {
    return true;
  }
}

function persistOnlineSoundEnabled(value) {
  try {
    localStorage.setItem('xq_online_sound_enabled', value ? '1' : '0');
  } catch (error) {
    // ignore storage write failures
  }
}

function toggleOnlineSound() {
  state.soundEnabled = !state.soundEnabled;
  persistOnlineSoundEnabled(state.soundEnabled);
  render();
}

function createOnlineAudio(url) {
  try {
    const audio = new Audio(url);
    audio.preload = 'auto';
    return audio;
  } catch (error) {
    return null;
  }
}

function unlockOnlineAudio() {
  if (state.audioUnlocked) {
    return;
  }
  state.audioUnlocked = true;
  [onlineMoveAudio, onlineMateAudio].forEach(audio => {
    if (!audio) {
      return;
    }
    try {
      const pending = audio.play();
      if (pending && pending.then) {
        pending.then(() => {
          audio.pause();
          audio.currentTime = 0;
        }).catch(() => null);
      } else {
        audio.pause();
        audio.currentTime = 0;
      }
    } catch (error) {
      // ignore
    }
  });
}

function playOnlineSound(audio) {
  if (!audio || !state.soundEnabled || !state.audioUnlocked) {
    return;
  }
  try {
    audio.pause();
    audio.currentTime = 0;
    const pending = audio.play();
    if (pending && pending.catch) {
      pending.catch(() => null);
    }
  } catch (error) {
    // ignore
  }
}

function applyServerGameSnapshot(game) {
  const previousGame = state.game;
  state.selectedFrom = null;
  if (!state.moveInFlight) {
    clearPendingMoveMarker();
  }
  const snapshot = enrichGame(game);
  syncGameTransitionFeedback(previousGame, snapshot);
  maybeRememberAiMove(previousGame, snapshot);
  if (!snapshot || snapshot.gameId !== state.aiMoveHintGameId) {
    clearAiMoveHint();
  }
  return snapshot;
}

function syncPracticePolling(route) {
  if (!state.me || !isPracticeRouteActive(route)) {
    stopPracticePolling();
    return;
  }
  if (!state.game
    || !state.game.isTraining
    || state.game.status !== 'PLAYING'
    || !state.game.aiPending) {
    stopPracticePolling();
    return;
  }
  startPracticePolling(state.game.gameId, false);
}

function startPracticePolling(gameId, immediate) {
  if (!gameId) {
    return;
  }
  if (state.practicePollGameId !== gameId) {
    stopPracticePolling();
  }
  state.practicePollGameId = gameId;
  if (!state.practicePollStartedAtMs) {
    state.practicePollStartedAtMs = Date.now();
  }
  if (state.practicePollTimeout || state.practicePollInFlight) {
    return;
  }
  schedulePracticePoll(gameId, !!immediate);
}

function stopPracticePolling() {
  if (state.practicePollTimeout) {
    window.clearTimeout(state.practicePollTimeout);
  }
  state.practicePollTimeout = null;
  state.practicePollGameId = '';
  state.practicePollStartedAtMs = 0;
  state.practicePollInFlight = false;
}

function schedulePracticePoll(gameId, immediate) {
  if (!gameId) {
    return;
  }
  if (state.practicePollTimeout) {
    window.clearTimeout(state.practicePollTimeout);
    state.practicePollTimeout = null;
  }
  const elapsed = Math.max(0, Date.now() - (state.practicePollStartedAtMs || Date.now()));
  const delay = immediate
    ? 0
    : (elapsed < PRACTICE_POLL_FAST_WINDOW_MS ? PRACTICE_POLL_FAST_MS : PRACTICE_POLL_SLOW_MS);
  state.practicePollTimeout = window.setTimeout(() => {
    state.practicePollTimeout = null;
    pollPracticeGame(gameId);
  }, delay);
}

async function pollPracticeGame(gameId) {
  if (state.practicePollInFlight || !gameId || state.practicePollGameId !== gameId) {
    return;
  }
  const route = currentRoute();
  if (!isPracticeRouteActive(route) || !state.game || state.game.gameId !== gameId) {
    stopPracticePolling();
    return;
  }
  state.practicePollInFlight = true;
  try {
    const snapshot = applyServerGameSnapshot(await fetchJson(`${API_BASE}/learn/practice-games/${gameId}`));
    if (!snapshot || snapshot.gameId !== gameId) {
      stopPracticePolling();
      return;
    }
    state.game = snapshot;
    if (!snapshot.aiPending || snapshot.status !== 'PLAYING') {
      stopPracticePolling();
    } else {
      schedulePracticePoll(gameId, false);
    }
    render();
  } catch (error) {
    state.status = error.message;
    render();
    if (state.practicePollGameId === gameId && isPracticeRouteActive(currentRoute())) {
      schedulePracticePoll(gameId, false);
    }
  } finally {
    state.practicePollInFlight = false;
  }
}

function isPracticeRouteActive(route) {
  if (!route || !state.game) {
    return false;
  }
  if (route.page === 'practice') {
    return true;
  }
  return route.page === 'game' && state.game.isTraining === true;
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

function tickLiveGameClock(route) {
  if (!route || route.page !== 'game') {
    return;
  }
  const game = state.game;
  if (!game || game.isTraining || game.status !== 'PLAYING' || game.clockState !== 'RUNNING') {
    return;
  }
  updateClockValue(game, 'first');
  updateClockValue(game, 'second');
}

function updateClockValue(game, slot) {
  if (!game || !game.players) {
    return;
  }
  const player = slot === 'first' ? game.players.first : game.players.second;
  if (!player || !player.side) {
    return;
  }
  const side = player.side;
  const remaining = slot === 'first'
    ? effectiveRemaining(game, game.firstRemainingSeconds, side)
    : effectiveRemaining(game, game.secondRemainingSeconds, side);
  const target = document.querySelector(`[data-clock-value="${side}"]`);
  if (target) {
    target.textContent = formatClock(remaining);
  }
}

function markBoardTap(target) {
  if (!target) {
    return;
  }
  target.classList.add('is-tapped');
  window.setTimeout(() => {
    target.classList.remove('is-tapped');
  }, 110);
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

function inferPracticeHumanFirst(game) {
  const viewerSide = game && game.viewerSide ? game.viewerSide : inferViewerSide(game);
  if (game && game.gameType === 'GOMOKU') {
    return viewerSide === 'BLACK';
  }
  return viewerSide !== 'BLACK';
}

function latestHumanMoveIndex(game) {
  if (!game || !Array.isArray(game.moves) || !game.moves.length) {
    return -1;
  }
  const viewerSide = game.viewerSide || inferViewerSide(game);
  for (let idx = game.moves.length - 1; idx >= 0; idx -= 1) {
    const move = game.moves[idx] || {};
    const side = String(move.side || (move.payload && move.payload.side) || '').trim();
    if (side && side === viewerSide) {
      return idx;
    }
  }
  return -1;
}

function practiceUndoDisabledReason(game) {
  if (!game || !game.isTraining) {
    return '仅 AI 练习局支持悔棋。';
  }
  if (game.status !== 'PLAYING') {
    return '对局结束后不可悔棋。';
  }
  if (state.moveInFlight) {
    return '正在提交走子，请稍候。';
  }
  if (isPracticeAiPending(game)) {
    return 'AI 思考中不可悔棋。';
  }
  if (latestHumanMoveIndex(game) < 0) {
    return '至少完成一步走子后可悔棋。';
  }
  return '';
}

function canInteractWithBoard(game) {
  return !!(game
    && isSupportedGameType(game.gameType)
    && game.status === 'PLAYING'
    && game.viewerSide
    && game.currentTurn === game.viewerSide
    && !state.moveInFlight
    && !isPracticeAiPending(game));
}

function isSupportedGameType(gameType) {
  return gameType === 'XIANGQI' || gameType === 'GOMOKU';
}

function practiceStatusText(game) {
  if (state.moveInFlight) {
    if (game && game.isTraining && state.pendingMoveGameId && game.gameId === state.pendingMoveGameId) {
      return '你的落子已落下，等待进入 AI 思考...';
    }
    return '正在提交走子...';
  }
  if (state.status) {
    return state.status;
  }
  const hint = activeAiMoveHint(game);
  if (hint) {
    return hint;
  }
  if (isPracticeAiPending(game)) {
    return 'AI 思考中...';
  }
  return (game && game.resultText) || '你落子后，AI 会思考并自动应手。';
}

function isPracticeAiPending(game) {
  return !!(game && game.isTraining && game.aiPending);
}

function activeAiMoveHint(game) {
  if (!game || !state.aiMoveHintText) {
    return '';
  }
  if (game.gameId !== state.aiMoveHintGameId) {
    return '';
  }
  if (Date.now() > state.aiMoveHintExpireAt) {
    clearAiMoveHint();
    return '';
  }
  return state.aiMoveHintText;
}

function clearAiMoveHint() {
  state.aiMoveHintText = '';
  state.aiMoveHintExpireAt = 0;
  state.aiMoveHintGameId = '';
  state.aiMoveHintMoveIndex = 0;
}

function maybeRememberAiMove(previousGame, nextGame) {
  if (!nextGame || !nextGame.gameId || !Array.isArray(nextGame.moves) || !nextGame.moves.length) {
    return;
  }
  const move = getLastMove(nextGame);
  if (!move) {
    return;
  }
  const previousLast = getLastMove(previousGame);
  const previousIndex = previousLast ? Number(previousLast.index || (previousGame.moves || []).length || 0) : 0;
  const nextIndex = Number(move.index || nextGame.moves.length || 0);
  if (previousGame
    && previousGame.gameId === nextGame.gameId
    && previousIndex === nextIndex
    && state.aiMoveHintMoveIndex === nextIndex) {
    return;
  }
  const viewerSide = nextGame.viewerSide || inferViewerSide(nextGame);
  const moveSide = String(move.side || (move.payload && move.payload.side) || '').trim();
  if (!moveSide || !viewerSide || moveSide === viewerSide) {
    return;
  }
  if (!nextGame.isTraining && !gameHasAiOpponent(nextGame)) {
    return;
  }
  const notation = String(move.notation || '').trim() || fallbackMoveNotation(nextGame.gameType, move.payload);
  state.aiMoveHintText = notation ? `AI 已落子：${notation}` : 'AI 已落子';
  state.aiMoveHintExpireAt = Date.now() + 2600;
  state.aiMoveHintGameId = nextGame.gameId;
  state.aiMoveHintMoveIndex = nextIndex;
}

function gameHasAiOpponent(game) {
  if (!game || !game.players) {
    return false;
  }
  const first = game.players.first;
  const second = game.players.second;
  return !!((first && first.opponentType === 'AI') || (second && second.opponentType === 'AI'));
}

function fallbackMoveNotation(gameType, payload) {
  if (!payload) {
    return '';
  }
  if (gameType === 'XIANGQI' && isXiangqiMovePayload(payload)) {
    return `${payload.fromRow},${payload.fromCol}→${payload.toRow},${payload.toCol}`;
  }
  if (gameType === 'GOMOKU' && isGomokuMovePayload(payload)) {
    return `${payload.row},${payload.col}`;
  }
  return '';
}

function validateMovePayload(game, payload) {
  if (!game) {
    return '当前对局不存在，无法落子。';
  }
  if (!isSupportedGameType(game.gameType)) {
    return `暂不支持 ${game.gameType || 'UNKNOWN'} 的落子操作。`;
  }
  if (game.gameType === 'XIANGQI') {
    return isXiangqiMovePayload(payload) ? '' : '象棋走法参数不合法，必须包含 fromRow/fromCol/toRow/toCol。';
  }
  return isGomokuMovePayload(payload) ? '' : '五子棋走法参数不合法，必须包含 row/col。';
}

function isXiangqiMovePayload(payload) {
  return !!(payload
    && Number.isInteger(payload.fromRow)
    && Number.isInteger(payload.fromCol)
    && Number.isInteger(payload.toRow)
    && Number.isInteger(payload.toCol));
}

function isGomokuMovePayload(payload) {
  return !!(payload
    && Number.isInteger(payload.row)
    && Number.isInteger(payload.col));
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
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
