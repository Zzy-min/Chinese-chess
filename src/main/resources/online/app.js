const state = {
  bootstrap: null,
  me: null,
  lobby: null,
  room: null,
  game: null,
  profile: null,
  profileDashboard: null,
  analysis: null,
  analysisStep: 0,
  learnContent: null,
  learnProgress: null,
  watchOverview: null,
  communityLeaderboard: null,
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
  boardPaneTab: 'board',
  boardTheme: 'wood',
  boardFlipped: false,
  expandedTutorialId: '',
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
  lastOpponentMoveNoticeGameId: '',
  lastOpponentMoveNoticeIndex: 0,
  lastFinishSoundKey: '',
  learnConfig: {
    gameType: 'XIANGQI',
    difficulty: 'MEDIUM',
    humanFirst: true,
    preferredEngine: 'BUILTIN'
  },
  learnFilter: 'all',
  learnSearchQuery: '',
  learnVisibleLimit: 0,
  learnVisibleKey: '',
  toasts: [],
  toastSeq: 0,
  actionBusy: '',
  actionBusyLabel: '',
  mobileQuickStartOpen: false,
  mobileQuickStartGameType: 'XIANGQI',
  nativeGameStateKey: ''
};

const API_BASE = '/online/api';
const WS_BASE = '/online/ws';
const WATCH_POLL_INTERVAL_MS = 10000;
const LEARN_SUB_ROUTES = ['tutorials', 'puzzles', 'practice'];
const ME_TABS = ['overview', 'records', 'study', 'inbox', 'achievements', 'settings', 'help'];
const PUZZLE_THEMES = ['ALL', 'TACTIC', 'MATE', 'POSITION', 'ENDGAME_FEN'];
const LEARN_PAGE_SIZE_MOBILE = 12;
const LEARN_PAGE_SIZE_DESKTOP = 24;
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
const routes = ['welcome', 'home', 'play', 'room', 'game', 'practice', 'analysis', 'learn', 'watch', 'community', 'help', 'me'];
const mobileLayoutMedia = window.matchMedia('(max-width: 768px)');

window.addEventListener('hashchange', render);
window.addEventListener('load', boot);
window.addEventListener('resize', () => fitBoardToViewport(currentRoute(), true));
mobileLayoutMedia.addEventListener('change', render);
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
  if (state.me) {
    await Promise.all([loadProfilePreferences(), loadProfileDashboard(false)]);
  }
  render();
  notifyNative('appReady', { route: currentRoute().page });
}

function notifyNative(type, payload = {}) {
  try {
    if (window.QingQijuApp && typeof window.QingQijuApp.postMessage === 'function') {
      window.QingQijuApp.postMessage(JSON.stringify({ type, payload }));
      return true;
    }
  } catch (_) {
    // The web experience remains fully usable outside the Android shell.
  }
  return false;
}

function notifyNativeGameState(route) {
  const game = (route.page === 'game' || route.page === 'practice') ? state.game : null;
  const key = `${route.page}:${route.id || ''}:${game && game.status ? game.status : ''}`;
  if (key === state.nativeGameStateKey) return;
  state.nativeGameStateKey = key;
  notifyNative('gameStateChanged', {
    route: route.page,
    gameId: route.id || '',
    status: game && game.status ? game.status : ''
  });
}

async function loadBootstrap() {
  state.bootstrap = await fetchJson(`${API_BASE}/site/bootstrap`).catch(() => null);
}

async function loadMe() {
  state.me = await fetchJson(`${API_BASE}/auth/me`).catch(() => null);
  if (!state.me) {
    state.learnProgress = null;
    state.profileDashboard = null;
    state.profile = null;
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
  if (state._loadingLeaderboard) {
    return;
  }
  state._loadingLeaderboard = true;
  try {
    state.communityLeaderboard = await fetchJson(`${API_BASE}/community/leaderboard`).catch(() => ({
      winBoard: [],
      activityBoard: [],
      byGameType: {}
    }));
  } finally {
    state._loadingLeaderboard = false;
  }
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
  query = (query || '').trim();
  state.lobbySearch.query = query;
  if (!query) {
    resetLobbySearch(renderAfter);
    return;
  }
  const requestId = state.lobbySearchRequestId + 1;
  state.lobbySearchRequestId = requestId;
  state.lobbySearch.loading = true;
  state.lobbySearch.error = '';
  if (renderAfter) {
    render();
  }
  try {
    const res = await fetchJson(`${API_BASE}/lobby/search?q=${encodeURIComponent(query)}`);
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
  if (!raw) {
    return {
      page: 'home',
      id: '',
      leaf: '',
      learnTab: '',
      meTab: '',
      puzzleTheme: resolvePuzzleTheme(state.learnPuzzleTheme)
    };
  }
  const parts = raw.split('/');
  const page = routes.includes(parts[0]) ? parts[0] : 'home';
  let id = parts[1] || '';
  if (page === 'learn' && !id) {
    id = 'puzzles';
  }
  const learnTab = page === 'learn' ? resolveLearnSubRoute(id || 'puzzles') : '';
  const meTab = page === 'me' ? resolveMeTab(id || 'overview') : '';
  if (page === 'me' && !id) {
    id = 'overview';
  }
  const puzzleTheme = page === 'learn' && learnTab === 'puzzles'
    ? resolvePuzzleTheme(parts[2] || 'ALL')
    : resolvePuzzleTheme(state.learnPuzzleTheme);
  return {
    page: page,
    id: id,
    leaf: parts[2] || '',
    learnTab: learnTab,
    meTab: meTab,
    puzzleTheme: puzzleTheme
  };
}

function resolveMeTab(value) {
  const normalized = String(value || '').trim().toLowerCase();
  return ME_TABS.includes(normalized) ? normalized : 'overview';
}

function navTo(path) {
  location.hash = path;
}

function showToast(message, type = 'info', durationMs = 2800) {
  const text = String(message || '').trim();
  if (!text) return;
  const id = ++state.toastSeq;
  state.toasts = [...(state.toasts || []).slice(-4), { id, text, type: type || 'info' }];
  patchToastHost();
  window.setTimeout(() => {
    state.toasts = (state.toasts || []).filter(item => item.id !== id);
    patchToastHost();
  }, Math.max(1200, durationMs || 2800));
}

function patchToastHost() {
  let host = document.getElementById('toastHost');
  if (!host) {
    host = document.createElement('div');
    host.id = 'toastHost';
    host.className = 'toastHost';
    host.setAttribute('aria-live', 'polite');
    host.setAttribute('aria-relevant', 'additions text');
    document.body.appendChild(host);
  }
  host.innerHTML = (state.toasts || []).map(item => `
    <div class="toast toast--${escapeHtml(item.type || 'info')}" role="status">${escapeHtml(item.text)}</div>
  `).join('');
}

function isActionBusy(key) {
  return !!state.actionBusy && (!key || state.actionBusy === key);
}

async function withActionBusy(key, label, work) {
  if (state.actionBusy) {
    showToast(state.actionBusyLabel ? `请稍候：${state.actionBusyLabel}` : '操作进行中，请稍候…', 'info', 1600);
    return null;
  }
  state.actionBusy = key;
  state.actionBusyLabel = label || '处理中';
  patchBusyButtons();
  try {
    return await work();
  } finally {
    if (state.actionBusy === key) {
      state.actionBusy = '';
      state.actionBusyLabel = '';
      patchBusyButtons();
    }
  }
}

function patchBusyButtons() {
  document.querySelectorAll('[data-action]').forEach(el => {
    const action = el.getAttribute('data-action') || '';
    const busyKeys = (el.getAttribute('data-busy-key') || action).split(',').map(s => s.trim()).filter(Boolean);
    const busy = state.actionBusy && busyKeys.includes(state.actionBusy);
    if (busy) {
      el.classList.add('is-busy');
      el.setAttribute('aria-busy', 'true');
      if ('disabled' in el) el.disabled = true;
    } else if (el.classList.contains('is-busy')) {
      el.classList.remove('is-busy');
      el.removeAttribute('aria-busy');
      if ('disabled' in el && !el.hasAttribute('data-keep-disabled')) el.disabled = false;
    }
  });
  const bar = document.getElementById('actionBusyBar');
  if (state.actionBusy && state.actionBusyLabel) {
    if (!bar) {
      const next = document.createElement('div');
      next.id = 'actionBusyBar';
      next.className = 'actionBusyBar';
      next.setAttribute('role', 'status');
      next.setAttribute('aria-live', 'polite');
      next.textContent = state.actionBusyLabel;
      document.body.appendChild(next);
    } else {
      bar.textContent = state.actionBusyLabel;
      bar.hidden = false;
    }
  } else if (bar) {
    bar.hidden = true;
  }
}

function openAuthModal(message) {
  state.showAuthModal = true;
  state.authError = message || state.authError || '';
  render();
  window.requestAnimationFrame(() => {
    const input = document.getElementById('authUsername');
    if (input) input.focus();
  });
}

function closeAuthModal() {
  if (!state.showAuthModal) return;
  state.showAuthModal = false;
  state.authError = '';
  render();
}

function render() {
  const route = currentRoute();
  const isBoardRoute = isBoardRoutePage(route.page);
  const isMobileBoardRoute = isBoardRoute && isMobileLayout();
  document.body.classList.toggle('mobile-board-route', isMobileBoardRoute);
  document.documentElement.classList.toggle('mobile-board-route', isMobileBoardRoute);
  if (!isBoardRoute && state.boardPaneTab !== 'board') {
    state.boardPaneTab = 'board';
  }
  const siteClasses = ['site', `route-${route.page}`];
  if (isMobileLayout()) {
    siteClasses.push('is-mobile-layout');
  }
  if (isBoardRoute) {
    siteClasses.push('is-board-route', `mobile-pane-${state.boardPaneTab}`);
  }
  if (route.page === 'practice') {
    siteClasses.push('route-practice-locked');
  }
  if (state.actionBusy) {
    siteClasses.push('is-action-busy');
  }
  app.innerHTML = `
    <div class="${siteClasses.join(' ')}">
      ${renderTopbar(route.page)}
      <main class="shell" id="mainContent">
        ${renderPage(route)}
      </main>
      ${renderBottomNav(route.page)}
      ${renderGameEndModal()}
      ${shouldShowAuthOverlay(route) ? renderAuthOverlay() : ''}
    </div>
  `;
  bindCommon(route);
  notifyNativeGameState(route);
  syncRealtime(route);
  syncPracticePolling(route);
  fitBoardToViewport(route, false);
  patchToastHost();
  patchBusyButtons();
  if (shouldShowAuthOverlay(route)) {
    window.requestAnimationFrame(() => {
      const input = document.getElementById('authUsername');
      if (input && document.activeElement === document.body) input.focus();
    });
  }
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
          <strong>轻棋局</strong>
          <span>在线对弈，AI 棋桌与复盘分析</span>
        </div>
      </div>
      <nav class="nav">
        ${navLink('home', '首页', active)}
        ${navLink('play', '对局', active)}
        ${navLink('learn', '棋谱', active)}
        ${navLink('community', '排行榜', active)}
        ${navLink('watch', '观战', active)}
        ${navLink('help', '帮助', active)}
      </nav>
      <div class="userBar">
        <button class="ghost" data-action="toggle-sound">音效：${state.soundEnabled ? '开' : '关'}</button>
        ${me ? `<button class="ghost topbarProfile" data-nav="me">@${me.username}</button>` : '<span class="muted">未登录</span>'}
        ${me ? '<button class="ghost" data-action="logout">退出</button>' : '<button class="ghost" data-auth-mode="login">登录</button><button class="btn" data-auth-mode="register">注册</button>'}
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
  welcome: renderWelcomePage,
  home: renderHomePageGuofeng,
  play: route => renderPlay(route),
  room: route => renderRoom(route.id),
  game: route => renderGame(route.id),
  practice: route => renderPractice(route.id),
  analysis: route => renderAnalysis(route.id),
  learn: route => renderLearnPage(route),
  watch: renderWatchPage,
  community: renderCommunityPage,
  help: renderHelpPage,
  me: renderProfilePage
};

function renderPage(route) {
  if (isMobileLayout()) {
    if (route.page === 'home') return renderMobileHomePage();
    if (route.page === 'play' && !route.id) return renderMobileLobby();
    if (route.page === 'play') return renderMobileModePage(route);
    if (!isBoardRoutePage(route.page) && route.page !== 'welcome') {
      const renderer = pageRegistry[route.page] || pageRegistry.home;
      return renderMobileContentPage(route, renderer(route));
    }
  }
  const renderer = pageRegistry[route.page] || pageRegistry.home;
  return renderer(route);
}

function isMobileLayout() {
  return mobileLayoutMedia.matches;
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
  let flipped = onlineGameRoute && shouldFlipOnlineBoardForViewer(game);
  if (state.boardFlipped) {
    flipped = !flipped;
  }
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
  // Mode showcase pages remain browsable without login.
  if (route.page === 'play' && (route.id === 'xiangqi' || route.id === 'gomoku')) {
    return false;
  }
  // Lobby can be browsed; only force auth for protected surfaces.
  if (route.page === 'play' && !route.id) {
    return false;
  }
  return ['room', 'game', 'practice', 'me'].includes(route.page);
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
          <div class="card"><h3>在线 AI 练习</h3><p>默认按中国象棋中等难度开局，点击后直接进入 AI 对局。</p><button class="btn" data-action="quick-start-ai-practice">直接进入 AI 对局</button></div>
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

function renderLearnItemCard(item, completedSet) {
  const isCompleted = completedSet.has(item.id);
  const isTutorial = !!item.isTutorial;
  const kind = isTutorial ? 'tutorial' : 'puzzle';
  
  const isXiangqi = item.gameType === 'XIANGQI';
  const badgeBg = isXiangqi ? '#8c2e21' : '#2f5f4a';
  let badgeChar = '棋';
  if (isXiangqi) {
    if (item.title && item.title.includes('飞相')) badgeChar = '飞';
    else if (item.title && (item.title.includes('中局') || item.title.includes('战术') || item.title.includes('杀网'))) badgeChar = '中';
    else if (item.title && (item.title.includes('开局') || item.title.includes('布阵'))) badgeChar = '开';
    else if (item.theme === 'TACTIC') badgeChar = '战';
    else if (item.theme === 'MATE') badgeChar = '杀';
    else if (item.theme === 'POSITION') badgeChar = '局';
    else if (item.theme === 'ENDGAME_FEN') badgeChar = '残';
  } else {
    badgeChar = '五';
  }
  
  const themeLabel = isTutorial ? '教程' : puzzleThemeLabel(resolvePuzzleTheme(item.theme));
  const gameTypeLabel = isXiangqi ? '中国象棋' : '五子棋';
  const difficultyLabel = { EASY: '简单', MEDIUM: '中等', HARD: '困难', EXPERT: '专家' }[item.difficulty || 'MEDIUM'] || item.difficulty || '中等';
  const metaText = `${gameTypeLabel} · ${difficultyLabel} · ${themeLabel}`;
  
  let completeBtn = '';
  if (isCompleted) {
    completeBtn = '<span class="pill" style="background:var(--pane-bg); color:var(--text-muted); padding:6px 14px; border-radius:4px; font-size:12px; font-weight:bold;">已完成</span>';
  } else if (!state.me) {
    completeBtn = '<button class="ghost" disabled style="padding:6px 14px; font-size:12px;">登录后记录</button>';
  } else {
    completeBtn = `<button class="btn btn-red btn-small" data-learn-complete="${kind}" data-id="${escapeHtml(item.id || '')}" style="padding:6px 14px; font-size:12px; background:#8c2e21; color:#fff; border:none; border-radius:4px; cursor:pointer;">标记完成</button>`;
  }
  
  let actionBtn = '';
  const expandId = item.id || '';
  const expanded = !!expandId && state.expandedTutorialId === expandId;
  if (isTutorial) {
    actionBtn = `<button class="ghost" data-action="view-tutorial-detail" data-id="${escapeHtml(expandId)}" style="margin-top:6px; padding:4px 12px; font-size:11px; border-color:var(--border-color); color:var(--text-muted);">${expanded ? '收起详情' : '查看详情'}</button>`;
  } else {
    const detailBtn = `<button class="ghost" data-action="view-tutorial-detail" data-id="${escapeHtml(expandId)}" style="margin-top:6px; padding:4px 12px; font-size:11px; border-color:var(--border-color); color:var(--text-muted);">${expanded ? '收起参考' : '参考着法'}</button>`;
    if (canStartPuzzlePractice(item)) {
      actionBtn = `${detailBtn}<button class="ghost" data-action="start-puzzle-practice" data-puzzle-id="${escapeHtml(item.id || '')}" style="margin-top:6px; padding:4px 12px; font-size:11px; border-color:var(--border-color); color:var(--text-muted);">开始研究</button>`;
    } else {
      actionBtn = `${detailBtn}<button class="ghost" disabled style="margin-top:6px; padding:4px 12px; font-size:11px; color:var(--text-muted); border-color:transparent; background:transparent;">FEN 待补全</button>`;
    }
  }

  const solutionLine = Array.isArray(item.solutionLine) ? item.solutionLine : [];
  const solutionText = Array.isArray(item.solution) ? item.solution : [];
  const hasEngineLine = solutionLine.length > 0;
  const detailHtml = expanded ? `
    <div class="learnTutorialDetail" style="margin-top:12px; padding-top:12px; border-top:1px dashed var(--border-color); text-align:left; width:100%;">
      ${item.objective ? `<div class="muted" style="margin-bottom:8px;"><strong>目标：</strong>${escapeHtml(item.objective)}</div>` : ''}
      ${item.goal ? `<div class="muted" style="margin-bottom:8px;"><strong>任务：</strong>${escapeHtml(item.goal)}</div>` : ''}
      ${renderLearnListBlock('要点', item.keyPoints || item.hints || [])}
      ${renderLearnListBlock('示例着法', item.exampleLine || [])}
      ${renderLearnListBlock(hasEngineLine ? '引擎参考着法' : '参考说明', hasEngineLine ? solutionLine.map((mv, i) => `${i + 1}. ${mv}`) : solutionText)}
      ${item.solver || item.endedBy ? `<div class="muted" style="margin-top:8px;font-size:12px;">求解：${escapeHtml(item.solver || '-')} · 终止：${escapeHtml(item.endedBy || '-')} · 半步：${escapeHtml(String(item.solutionPlies || solutionLine.length || 0))}</div>` : ''}
      ${item.fen ? renderLearnFenBlock(item.fen) : ''}
      ${renderLearnListBlock('练习清单', item.practiceChecklist || [])}
    </div>
  ` : '';
  
  return `
    <div class="panel learnCard" style="display:flex; flex-direction:column; margin-bottom:12px; padding:16px; border-radius:8px; box-shadow:0 2px 8px rgba(0,0,0,0.02); background:var(--pane-bg); border:1px solid var(--border-color);">
      <div style="display:flex; align-items:center; width:100%;">
      <div class="learnCardLeft" style="display:flex; align-items:center; flex:1;">
        <span class="learnCardBadge" style="background:${badgeBg}; color:#fff; width:40px; height:40px; display:flex; align-items:center; justify-content:center; border-radius:50%; font-weight:bold; font-size:18px; flex-shrink:0;">
          ${badgeChar}
        </span>
        <div class="learnCardInfo" style="margin-left:16px; text-align:left;">
          <h3 style="margin:0 0 4px; font-size:16px; font-weight:bold; color:var(--text-color);">${escapeHtml(item.title || '')}${hasEngineLine ? ' <span class="pill" style="font-size:10px;padding:2px 6px;">有参考着法</span>' : ''}</h3>
          <div class="muted" style="margin-bottom:4px; font-size:12px; color:var(--text-muted); font-weight:500;">${escapeHtml(metaText)}</div>
          <div class="muted learnSummaryOneLine" style="font-size:13px; color:var(--text-muted);">${escapeHtml(item.summary || '暂无详细介绍')}</div>
        </div>
      </div>
      <div class="learnAction" style="display:flex; flex-direction:column; align-items:flex-end; margin-left:16px; flex-shrink:0;">
        ${completeBtn}
        ${actionBtn}
      </div>
      </div>
      ${detailHtml}
    </div>
  `;
}

function learnPageSize() {
  return isMobileLayout() ? LEARN_PAGE_SIZE_MOBILE : LEARN_PAGE_SIZE_DESKTOP;
}

function renderLearnPage(route) {
  if (!state.learnContent) {
    loadLearnContent();
    if (state.me && !state.learnProgress) {
      loadLearnProgress();
    }
    return '<section class="panel"><h2 class="sectionTitle">学习内容加载中</h2></section>';
  }

  const isPracticeTab = route && route.learnTab === 'practice';
  const isEndgameRoute = route
    && route.learnTab === 'puzzles'
    && resolvePuzzleTheme(route.puzzleTheme) === 'ENDGAME_FEN';
  const filter = isPracticeTab ? 'practice' : (isEndgameRoute ? 'endgames' : (state.learnFilter || 'all'));
  const puzzles = state.learnContent.puzzles || [];
  const tutorials = state.learnContent.tutorials || [];
  
  let mainContentHtml = '';
  if (isPracticeTab) {
    mainContentHtml = renderLearnPracticeTab(state.learnContent);
  } else {
    const progress = state.learnProgress || { tutorialsCompleted: [], puzzlesCompleted: [] };
    let items = [];
    if (filter === 'all') {
      items = [...tutorials.map(t => ({...t, isTutorial: true})), ...puzzles];
    } else if (filter === 'xiangqi') {
      items = [
        ...tutorials.filter(t => t.gameType === 'XIANGQI').map(t => ({...t, isTutorial: true})),
        ...puzzles.filter(p => p.gameType === 'XIANGQI')
      ];
    } else if (filter === 'gomoku') {
      items = [
        ...tutorials.filter(t => t.gameType === 'GOMOKU').map(t => ({...t, isTutorial: true})),
        ...puzzles.filter(p => p.gameType === 'GOMOKU')
      ];
    } else if (filter === 'featured') {
      items = tutorials.map(t => ({...t, isTutorial: true}));
    } else if (filter === 'puzzles') {
      items = puzzles;
    } else if (filter === 'endgames') {
      items = puzzles.filter(p => resolvePuzzleTheme(p.theme) === 'ENDGAME_FEN');
    } else if (filter === 'openings') {
      items = [
        ...tutorials.filter(t => String(t.title || '').includes('开局') || String(t.title || '').includes('布局') || String(t.id || '').includes('opening')).map(t => ({...t, isTutorial: true})),
        ...puzzles.filter(p => String(p.title || '').includes('开局') || String(p.title || '').includes('布局') || String(p.id || '').includes('opening'))
      ];
    }

    const query = (state.learnSearchQuery || '').trim().toLowerCase();
    if (query) {
      items = items.filter(item => 
        String(item.title || '').toLowerCase().includes(query) ||
        String(item.summary || '').toLowerCase().includes(query)
      );
    }

    const visibleKey = `${filter}|${query}`;
    if (state.learnVisibleKey !== visibleKey) {
      state.learnVisibleKey = visibleKey;
      state.learnVisibleLimit = learnPageSize();
    }
    const visibleLimit = Math.max(learnPageSize(), Number(state.learnVisibleLimit) || 0);
    const visibleItems = items.slice(0, visibleLimit);
    const remainingItems = Math.max(0, items.length - visibleItems.length);
    const completedTutorials = (progress.tutorialsCompleted || []);
    const completedPuzzles = (progress.puzzlesCompleted || []);
    const completedSet = new Set([...completedTutorials, ...completedPuzzles]);
    
    if (!items.length) {
      mainContentHtml = '<div class="banner" style="padding:40px; text-align:center; color:var(--text-muted);">未找到符合条件的棋谱或题目。</div>';
    } else {
      mainContentHtml = visibleItems.map(item => renderLearnItemCard(item, completedSet)).join('')
        + (remainingItems > 0
          ? `<button class="ghost learnLoadMore" data-action="load-more-learn">再加载 ${Math.min(learnPageSize(), remainingItems)} 条<span> · 余 ${remainingItems} 条</span></button>`
          : '');
    }
  }

  return `
    <div class="learnPage">
      <section class="hero" style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:16px; margin-bottom:24px;">
        <div class="heroLeft" style="flex:1; min-width:300px; text-align:left;">
          <span class="pill" style="background:#e85a4f; color:#fff; font-size:11px; padding:3px 8px; border-radius:10px; font-weight:bold; vertical-align:middle;">练习</span>
          <h1 style="margin:8px 0; font-size:28px; font-weight:bold;">棋谱库</h1>
          <p style="margin:0; font-size:14px; color:var(--text-muted);">从残局题库、教程复盘到 AI 对战，当前网页端的学习能力全部保持免费可用。</p>
        </div>
        <div class="heroRight learnSearchWrap">
          <button class="btn learnEndgameCta" data-nav="learn/puzzles/ENDGAME_FEN">进入残局挑战</button>
          <label class="searchFieldLabel" for="learnSearchInput">搜索棋谱</label>
          <div class="searchBar searchBar--learn">
            <span class="searchIcon" aria-hidden="true">🔍</span>
            <input type="search" id="learnSearchInput" name="learnSearch" placeholder="搜索棋谱、棋手、赛事..." value="${escapeHtml(state.learnSearchQuery || '')}" autocomplete="off" />
          </div>
        </div>
      </section>

      <div class="learnTabs" style="display:flex; gap:10px; margin-bottom:20px; overflow-x:auto; padding-bottom:4px;">
        <button class="pill ${filter === 'all' ? 'is-active' : ''}" data-learn-filter="all">全部</button>
        <button class="pill ${filter === 'xiangqi' ? 'is-active' : ''}" data-learn-filter="xiangqi">象棋</button>
        <button class="pill ${filter === 'gomoku' ? 'is-active' : ''}" data-learn-filter="gomoku">五子棋</button>
        <button class="pill ${filter === 'featured' ? 'is-active' : ''}" data-learn-filter="featured">精彩对局</button>
        <button class="pill ${filter === 'endgames' ? 'is-active' : ''}" data-nav="learn/puzzles/ENDGAME_FEN">残局挑战</button>
        <button class="pill ${filter === 'puzzles' ? 'is-active' : ''}" data-learn-filter="puzzles">全部题库</button>
        <button class="pill ${filter === 'openings' ? 'is-active' : ''}" data-learn-filter="openings">布局大全</button>
        <button class="pill ${filter === 'practice' ? 'is-active' : ''}" data-nav="learn/practice">AI 练习</button>
      </div>

      <div class="learnMainContent" style="margin-top:18px;">
        ${mainContentHtml}
      </div>
    </div>
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
  const board = state.communityLeaderboard || { winBoard: [], activityBoard: [] };
  const gameTypeData = (board.byGameType && board.byGameType[state.leaderboardGameType]) || board;
  const isWin = state.communityTab === 'win';
  const items = isWin ? (gameTypeData.winBoard || []) : (gameTypeData.activityBoard || []);
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
      <div class="roomRow" style="margin-bottom:12px">
        <button class="${isWin ? 'btn' : 'ghost'}" data-community-tab="win">胜局榜</button>
        <button class="${!isWin ? 'btn' : 'ghost'}" data-community-tab="activity">活跃榜</button>
      </div>
      <div class="roomRow" style="margin-bottom:12px; display: flex; gap: 8px;">
        <button class="${state.leaderboardGameType === 'XIANGQI' ? 'btn' : 'ghost'}" data-community-game-type="XIANGQI">象棋</button>
        <button class="${state.leaderboardGameType === 'GOMOKU' ? 'btn' : 'ghost'}" data-community-game-type="GOMOKU">五子棋</button>
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

function watchActionButton(item, roomFallbackId) {
  const status = String(item.status || '').toUpperCase();
  const gameId = item.gameId || '';
  const roomId = item.roomId || roomFallbackId || '';
  if (gameId && status === 'PLAYING') {
    return `<button class="ghost" data-nav="game/${escapeHtml(gameId)}">实时观战</button>`;
  }
  if (gameId) {
    return `<button class="ghost" data-nav="analysis/${escapeHtml(gameId)}">复盘分析</button>`;
  }
  if (roomId) {
    return `<button class="ghost" data-nav="room/${escapeHtml(roomId)}">进入房间</button>`;
  }
  return '<span class="pill">等待开局</span>';
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
      ${watchActionButton(item)}
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
      ${watchActionButton(item)}
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

function renderPlay(route) {
  if (route && route.id === 'xiangqi') {
    return renderPlayXiangqi();
  }
  if (route && route.id === 'gomoku') {
    return renderPlayGomoku();
  }
  return renderPlayLobbyDesk();
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
        <button class="ghost" data-action="share-room" data-room-code="${escapeHtml(room.roomCode || '')}">分享房间</button>
        <button class="btn" data-action="toggle-ready">${isViewerReady(room) ? '取消准备' : '我已准备'}</button>
        ${room.gameId ? `<button class="btn" data-nav="game/${room.gameId}">进入对局</button>` : ''}
        ${isRoomHost(room) && room.status !== 'PLAYING' ? '<button class="ghost danger" data-action="close-room">关闭房间</button>' : ''}
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

function renderPlaybackControls(step, totalSteps) {
  return `
    <div class="playbackControls">
      <button class="playback-btn" data-analysis-step="0" title="开局">⏮️ 第一步</button>
      <button class="playback-btn" data-analysis-step="${Math.max(0, step - 1)}" title="上一步">◀️ 上一步</button>
      <button class="playback-btn-center" disabled>第 ${step} / ${totalSteps} 步</button>
      <button class="playback-btn" data-analysis-step="${Math.min(totalSteps, step + 1)}" title="下一步">下一步 ▶️</button>
      <button class="playback-btn" data-analysis-step="${totalSteps}" title="终局">终局 ⏭️</button>
    </div>
  `;
}

function renderRightSidebar(game, isAnalysis = false) {
  const activeTab = state.gameRightTab || 'moves';
  
  // Tab headers
  const tabs = `
    <div class="gameSidebarTabs">
      <button class="tab-btn ${activeTab === 'status' ? 'active' : ''}" data-game-tab="status">局势</button>
      <button class="tab-btn ${activeTab === 'moves' ? 'active' : ''}" data-game-tab="moves">棋谱</button>
      <button class="tab-btn ${activeTab === 'analysis' ? 'active' : ''}" data-game-tab="analysis">分析</button>
      <button class="tab-btn ${activeTab === 'settings' ? 'active' : ''}" data-game-tab="settings">设置</button>
    </div>
  `;
  
  let contentHtml = '';
  if (activeTab === 'status') {
    if (isAnalysis) {
      const step = Math.max(0, Math.min(state.analysisStep, (game.historyBoards || []).length - 1));
      contentHtml = `
        <div class="tabContent statusContent">
          <div class="statusField">
            <span class="label">当前类型</span>
            <span class="val">${game.gameType === 'XIANGQI' ? '中国象棋' : '五子棋'}</span>
          </div>
          <div class="statusField">
            <span class="label">对局状态</span>
            <span class="val">${escapeHtml(game.status || '已归档')}</span>
          </div>
          <div class="statusField">
            <span class="label">当前步数</span>
            <span class="val">${step} / ${Math.max(0, (game.historyBoards || []).length - 1)} 步</span>
          </div>
          <div class="statusField">
            <span class="label">对弈形式</span>
            <span class="val">${game.isTraining ? 'AI 练习局' : '真人联机对局'}</span>
          </div>
        </div>
      `;
    } else {
      contentHtml = `
        <div class="tabContent statusContent">
          <div class="statusField">
            <span class="label">对局类型</span>
            <span class="val">${game.gameType === 'XIANGQI' ? '中国象棋' : '五子棋'}</span>
          </div>
          <div class="statusField">
            <span class="label">当前状态</span>
            <span class="val">${game.status === 'PLAYING' ? '激烈对弈中' : '对局已结束'}</span>
          </div>
          <div class="statusField">
            <span class="label">总局时</span>
            <span class="val">${game.initialTimeSeconds ? formatClock(game.initialTimeSeconds) : '-'}</span>
          </div>
          <div class="statusField">
            <span class="label">当前剩余</span>
            <span class="val">${formatClock(Math.max(game.firstRemainingSeconds || 0, game.secondRemainingSeconds || 0))}</span>
          </div>
          <div class="statusField">
            <span class="label">红方选手</span>
            <span class="val">${escapeHtml((game.players && game.players.first && game.players.first.username) || (game.viewerSide === 'RED' ? (state.me ? state.me.username : '玩家') : '内置AI'))}</span>
          </div>
          <div class="statusField">
            <span class="label">黑方选手</span>
            <span class="val">${escapeHtml((game.players && game.players.second && game.players.second.username) || (game.viewerSide === 'BLACK' ? (state.me ? state.me.username : '玩家') : '内置AI'))}</span>
          </div>
        </div>
      `;
    }
  } else if (activeTab === 'analysis') {
    const hint = isAnalysis
      ? '复盘分析中，支持按步回看局面。引擎形势评估尚未接入。'
      : (game.gameType === 'XIANGQI'
        ? '棋虽百变，理归一贯。当前仅提供着法记录与复盘入口，无实时引擎评分。'
        : '五子连珠，守中带攻。当前仅提供着法记录与复盘入口，无实时引擎评分。');
    contentHtml = `
      <div class="tabContent analysisContent">
        <div class="analysisBanner">
          <span class="icon">💡</span>
          <p>${hint}</p>
        </div>
        <div class="banner muted">复盘回放可用；局面形势评估功能尚未接入，不展示模拟评分。</div>
      </div>
    `;
  } else if (activeTab === 'settings') {
    const soundOn = state.soundEnabled !== false;
    const theme = state.boardTheme || 'wood';
    contentHtml = `
      <div class="tabContent settingsContent">
        <div class="settingRow">
          <span>对局落子音效</span>
          <button class="btn btn-small ${soundOn ? 'btn-red' : 'ghost'}" data-action="toggle-sound">
            ${soundOn ? '开启' : '关闭'}
          </button>
        </div>
        <div class="settingRow">
          <span>视觉背景主题</span>
          <button class="btn btn-small btn-red" data-action="toggle-theme">${theme === 'wood' ? '古雅木纹' : '清雅水墨'}</button>
        </div>
        <div class="settingRow">
          <span>翻转对局棋盘</span>
          <button class="btn btn-small ghost" data-action="flip-board">翻转</button>
        </div>
      </div>
    `;
  } else {
    if (isAnalysis) {
      const step = Math.max(0, Math.min(state.analysisStep, (game.historyBoards || []).length - 1));
      contentHtml = `
        <div class="tabContent movesContent">
          <div class="moves scrollable">
            ${(game.moves || []).length ? game.moves.map(moveItem => `
              <button class="move ${step === moveItem.index ? 'is-current' : ''}" data-analysis-step="${moveItem.index}">
                <div><strong>#${moveItem.index}</strong><div class="muted">${moveItem.side}</div></div>
                <div>${moveItem.notation}</div>
              </button>`).join('') : '<div class="banner">当前没有可回放着法。</div>'}
          </div>
        </div>
      `;
    } else {
      contentHtml = `
        <div class="tabContent movesContent">
          <div class="moves scrollable" data-live-moves>
            ${(game.moves || []).length ? game.moves.map(renderMoveRow).join('') : '<div class="banner">等待第一步落子。</div>'}
          </div>
        </div>
      `;
    }
  }

  return `
    <section class="panel recordPane boardSidebar">
      ${tabs}
      <div class="tabContainer">
        ${contentHtml}
      </div>
    </section>
  `;
}

function renderOnlineGameView(game) {
  const board = renderPlayableBoardByGameType(game, resolveBoardRenderOptions(game, { page: 'game' }));
  const drawOffer = game.drawOffer;
  const viewerSide = game.viewerSide || inferViewerSide(game);
  const opponentSide = resolveOpponentSide(game, viewerSide);
  const canRespondDraw = drawOffer && drawOffer.side !== viewerSide;
  const canOfferDraw = game.status === 'PLAYING' && !drawOffer;
  const firstPlayer = (game.players && game.players.first) || {};
  const secondPlayer = (game.players && game.players.second) || {};

  const themeClass = (game.gameType === 'XIANGQI' ? 'xiangqiTheme' : 'gomokuTheme') + ' ' + (state.boardTheme === 'ink' ? 'theme-ink' : 'theme-wood');

  const isViewerFirst = viewerSide ? (viewerSide === firstPlayer.side) : true;

  return `
    <div class="boardPage boardPage--desk ${themeClass}">
      ${renderBoardPaneTabs()}
      <div class="boardDesk boardDesk--game">
        <!-- 左栏 (玩家卡片栏) -->
        <aside class="panel boardRail boardRail--players">
          <div class="boardRailHeader">
            <div>
              <div class="meta">在线对局</div>
              <h2 class="sectionTitle">对局桌</h2>
            </div>
            <span class="pill">${game.gameType === 'XIANGQI' ? '象棋' : '五子棋'}</span>
          </div>

          <div class="clockGrid" data-live-clock-grid>
            ${renderClockCard(game, 'second')}
            ${renderClockCard(game, 'first')}
          </div>

          <div class="boardRailNote">
            <div>局时: <strong>15:00</strong></div>
            <div>步时: <strong>01:30</strong></div>
            <div>你执: <strong>${sideLabel(game.gameType, viewerSide)}</strong></div>
            <div>当前状态: <strong>${game.status === 'PLAYING' ? '对局中' : '已结束'}</strong></div>
          </div>
        </aside>

        <!-- 中栏 (自适应棋盘区) -->
        <section class="boardWrap boardPane boardPane--game boardStage">
          <div class="gameMetaRow">
            <span class="pill">${game.gameType}</span>
            <span class="pill" data-live-side-self>你执 ${sideLabel(game.gameType, viewerSide)}</span>
            <span class="pill" data-live-side-opponent>对手执 ${sideLabel(game.gameType, opponentSide)}</span>
            <span class="pill" data-live-turn>轮到 ${turnTextForViewer(game, viewerSide)}</span>
            <span class="pill" data-live-game-status>${game.status}</span>
            <span class="pill" data-live-game-termination>${game.terminationReason || 'LIVE'}</span>
          </div>
          <div class="status" data-live-status>${onlineGameStatusText(game)}</div>
          <div data-live-draw-offer>${drawOffer ? renderDrawOfferBanner(drawOffer, canRespondDraw) : ''}</div>
          <div class="boardHost" data-live-board-host>${board}</div>
          <div class="roomRow woodActions" data-live-game-actions>
            ${renderOnlineGameActions(game, canOfferDraw)}
          </div>
        </section>

        <!-- 右栏 -->
        ${renderRightSidebar(game, false)}
      </div>
    </div>
  `;
}

function renderOnlineGameActions(game, canOfferDraw) {
  return `
    <button class="ghost" disabled title="在线真人对局不支持单方悔棋">悔棋</button>
    ${canOfferDraw ? '<button class="ghost" data-action="offer-draw">求和</button>' : '<button class="ghost" disabled>求和</button>'}
    ${game.status === 'PLAYING' ? '<button class="danger" data-action="resign">认输</button>' : '<button class="danger" disabled>认输</button>'}
    <button class="ghost" data-nav="room/${game.roomId || ''}">离开</button>
  `;
}

function renderPracticeView(game) {
  const board = renderPlayableBoardByGameType(game, resolveBoardRenderOptions(game, { page: 'practice' }));
  const ai = practiceAiMeta(game);
  const undoDisabledReason = practiceUndoDisabledReason(game);
  const undoDisabled = !!undoDisabledReason;
  const viewerSide = game.viewerSide || inferViewerSide(game);
  const themeClass = (game.gameType === 'XIANGQI' ? 'xiangqiTheme' : 'gomokuTheme') + ' ' + (state.boardTheme === 'ink' ? 'theme-ink' : 'theme-wood');

  const getAvatar = (username, color) => {
    const char = escapeHtml((username || 'AI').slice(0, 1));
    return `data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'%3E%3Crect width='40' height='40' fill='${color}'/%3E%3Ctext x='20' y='25' text-anchor='middle' font-size='18' fill='white' font-family='KaiTi, serif' font-weight='bold'%3E${char}%3C/text%3E%3C/svg%3E`;
  };

  const aiColor = '%232e4d3e'; // 墨绿
  const playerColor = viewerSide === 'RED' ? '%238c2e21' : '%232a2720';

  const aiActive = game.status === 'PLAYING' && game.currentTurn !== viewerSide;
  const playerActive = game.status === 'PLAYING' && game.currentTurn === viewerSide;

  return `
    <div class="boardPage boardPage--practice boardPage--desk ${themeClass}">
      ${renderBoardPaneTabs()}
      <div class="boardDesk boardDesk--practice">
        <!-- 左栏 (玩家卡片栏) -->
        <aside class="panel boardRail boardRail--practice">
          <div class="boardRailHeader">
            <div>
              <div class="meta">AI 棋桌</div>
              <h2 class="sectionTitle">练习信息</h2>
            </div>
            <span class="pill">${game.gameType === 'XIANGQI' ? '象棋' : '五子棋'}</span>
          </div>

          <!-- 上方对手卡片 (AI) -->
          <div class="boardPlayerCard ${aiActive ? 'is-active' : ''}">
            <div class="boardPlayerCardTop">
              <img class="avatar" src="${getAvatar(practiceOpponent(game), aiColor)}" />
              <div class="userMeta">
                <strong>${escapeHtml(practiceOpponent(game))}</strong>
                <span class="vipBadge">${escapeHtml(ai.engineText || ai.engineId || '内置 AI')} · ${escapeHtml(ai.difficulty || '普通')}</span>
              </div>
            </div>
            ${aiActive ? '<div class="boardPlayerClock">AI思考中</div>' : '<div class="boardPlayerClock">等待中</div>'}
            ${aiActive ? '<div class="turnBadge active">AI回合</div>' : '<div class="turnBadge">等待中</div>'}
          </div>

          <!-- 下方玩家自己卡片 -->
          <div class="boardPlayerCard ${playerActive ? 'is-active' : ''}">
            <div class="boardPlayerCardTop">
              <img class="avatar" src="${getAvatar(state.me ? state.me.username : '我', playerColor)}" />
              <div class="userMeta">
                <strong>${escapeHtml(state.me ? state.me.username : '当前用户')}</strong>
                <span class="vipBadge">${viewerSide === 'RED' ? '红方' : '黑方'} · 挑战者</span>
              </div>
            </div>
            <div class="boardPlayerClock">无限制</div>
            ${playerActive ? '<div class="turnBadge active">我的回合</div>' : '<div class="turnBadge">等待中</div>'}
          </div>

          <div class="boardRailNote">
            <div>AI 难度: <strong>${escapeHtml(ai.difficulty || '普通')}</strong></div>
            <div>AI 阵营: <strong>${sideLabel(game.gameType, game.aiSide || ai.side || '')}</strong></div>
            <div>状态: <strong>${escapeHtml(practiceStatusText(game))}</strong></div>
          </div>
        </aside>

        <!-- 中栏 (自适应棋盘区) -->
        <section class="boardWrap boardPane boardPane--practice boardStage">
          <div class="status" data-live-status>${practiceStatusText(game)}</div>
          <div class="boardHost" data-live-board-host>${board}</div>
          
          <!-- 底部控制按钮组 (悔棋、认输、再来一局、离开) -->
          <div class="roomRow woodActions">
            ${game.status === 'PLAYING'
              ? `<button class="ghost" data-action="undo-practice" ${undoDisabled ? 'disabled' : ''} title="${escapeHtml(undoDisabledReason || '回合悔棋')}">悔棋</button>
                 <button class="danger" data-action="resign">认输</button>`
              : '<button class="btn" data-action="practice-rematch">再开一局</button>'}
            <button class="ghost" data-nav="learn/practice">离开</button>
          </div>
        </section>

        <!-- 右栏 -->
        ${renderRightSidebar(game, false)}
      </div>
    </div>
  `;
}

function renderClockCard(game, slot) {
  if (!game || !game.players) return '';
  const player = slot === 'first' ? game.players.first : game.players.second;
  if (!player) return '';
  const side = player.side;
  const active = game.status === 'PLAYING' && game.currentTurn === side;
  const remaining = slot === 'first' ? effectiveRemaining(game, game.firstRemainingSeconds, side) : effectiveRemaining(game, game.secondRemainingSeconds, side);
  const baseRemaining = slot === 'first' ? (game.firstRemainingSeconds || 0) : (game.secondRemainingSeconds || 0);

  const getAvatar = (username, color) => {
    const char = escapeHtml((username || '棋').slice(0, 1));
    return `data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'%3E%3Crect width='40' height='40' fill='${color}'/%3E%3Ctext x='20' y='25' text-anchor='middle' font-size='18' fill='white' font-family='KaiTi, serif' font-weight='bold'%3E${char}%3C/text%3E%3C/svg%3E`;
  };

  const color = side === 'RED' ? '%238c2e21' : '%232a2720';
  const label = side === 'RED' ? '红方' : (side === 'BLACK' ? '黑方' : (side === 'WHITE' ? '白方' : '棋手'));
  const level = side === 'RED' ? '业余6段' : '业余5段';

  return `
    <div class="boardPlayerCard ${active ? 'is-active' : ''}" data-clock-card="${side}">
      <div class="boardPlayerCardTop">
        <img class="avatar" src="${getAvatar(player.username, color)}" />
        <div class="userMeta">
          <strong>${escapeHtml(player.username || '棋手')}</strong>
          <span class="vipBadge">${label} · ${level}</span>
        </div>
      </div>
      <div class="boardPlayerClock" data-clock-value="${side}" data-remaining-base="${baseRemaining}">${formatClock(remaining)}</div>
      ${active ? `<div class="turnBadge active">${label}回合</div>` : `<div class="turnBadge">等待中</div>`}
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
  const summaryText = analysis.isTraining
    ? `${practiceOpponent(analysis)} · ${analysis.aiEngine || '-'} · ${analysis.difficulty || '-'}`
    : '归档对局回放';
  const themeClass = (analysis.gameType === 'XIANGQI' ? 'xiangqiTheme' : 'gomokuTheme') + ' ' + (state.boardTheme === 'ink' ? 'theme-ink' : 'theme-wood');
  
  return `
    <div class="boardPage boardPage--desk ${themeClass}">
      ${renderBoardPaneTabs()}
      <div class="boardDesk boardDesk--analysis">
        <aside class="panel boardRail boardRail--analysis">
          <div class="boardRailHeader">
            <div>
              <div class="meta">复盘分析</div>
              <h2 class="sectionTitle">局面概览</h2>
            </div>
            <span class="pill">${analysis.gameType === 'XIANGQI' ? '象棋' : '五子棋'}</span>
          </div>
          <div class="boardPlayerCard">
            <div class="boardPlayerMeta">状态</div>
            <strong>${escapeHtml(analysis.status || '-')}</strong>
            <div class="boardPlayerHint">${escapeHtml(summaryText)}</div>
          </div>
          <div class="boardRailNote">
            <div>当前步数 <strong>${step}/${Math.max(0, boards.length - 1)}</strong></div>
            <div>${move ? `${escapeHtml(move.side)} · ${escapeHtml(move.notation)}` : '开局局面'}</div>
          </div>
        </aside>
        <section class="boardWrap boardPane boardPane--analysis boardStage">
          <div class="gameMetaRow">
            <span class="pill">${analysis.gameType}</span>
            ${analysis.isTraining ? '<span class="pill">AI 练习</span>' : ''}
            <span class="pill">${analysis.status}</span>
            <span class="pill">步数 ${step}/${Math.max(0, boards.length - 1)}</span>
          </div>
          <div class="status">${summaryText}${move ? ` · ${move.side} ${move.notation}` : step === 0 ? ' · 开局局面' : ''}</div>
          <div class="boardHost" data-analysis-board-host>${renderAnalysisBoardByGameType(analysis.gameType, board, marker)}</div>
          
          <!-- 底部播放跳转控制的 DOM 重绘 -->
          ${renderPlaybackControls(step, boards.length - 1)}
        </section>
        
        <!-- 右栏 -->
        ${renderRightSidebar(analysis, true)}
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
  if (!state.profileDashboard && !state.profile) {
    loadProfileDashboard(true);
    return '<section class="panel"><h2 class="sectionTitle">个人摘要加载中</h2></section>';
  }
  const route = currentRoute();
  const meTab = route.meTab || 'overview';
  const dash = state.profileDashboard || {};
  const summary = dash.summary || (state.profile && state.profile.summary) || {};
  const recentGames = dash.recentGames || (state.profile && state.profile.recentGames) || [];
  const activity = dash.activity || {};
  const learnProgress = dash.learnProgress || state.learnProgress || { tutorialsCompleted: [], puzzlesCompleted: [] };
  const achievements = dash.achievements || [];
  const notifications = dash.notifications || [];
  const prefs = dash.preferences || {};
  const totalGames = summary.totalGames || 0;
  const wins = summary.wins || 0;
  const losses = summary.losses || 0;
  const winRate = totalGames ? Math.round((wins * 100) / totalGames) : 0;
  const earnedCount = achievements.filter(item => item.earned).length;
  const sidebar = [
    { tab: 'overview', label: '个人信息' },
    { tab: 'records', label: '对局记录' },
    { tab: 'study', label: '学习档案' },
    { tab: 'inbox', label: '消息通知' },
    { tab: 'achievements', label: '我的成就' },
    { tab: 'settings', label: '偏好设置' },
    { tab: 'help', label: '帮助与反馈' }
  ].map(item => `
    <button class="profileSidebarItem ${meTab === item.tab ? 'is-active' : ''}" data-nav="me/${item.tab}">${item.label}</button>
  `).join('');

  let mainHtml = '';
  if (meTab === 'records') {
    mainHtml = `
      <section class="panel">
        <h2 class="sectionTitle">对局记录</h2>
        <div class="moves">
          ${recentGames.length ? recentGames.map(renderProfileGameCard).join('') : '<div class="banner">暂无对局记录，去大厅或练习开一局吧。</div>'}
        </div>
      </section>`;
  } else if (meTab === 'study') {
    const tDone = (learnProgress.tutorialsCompleted || []).length;
    const pDone = (learnProgress.puzzlesCompleted || []).length;
    mainHtml = `
      <section class="panel">
        <h2 class="sectionTitle">学习档案</h2>
        <div class="profileStatsGrid">
          <div class="statBox"><strong>${tDone}</strong><span>已完成教程</span></div>
          <div class="statBox"><strong>${pDone}</strong><span>已完成题目</span></div>
        </div>
        <div class="roomRow" style="margin-top:12px">
          <button class="btn" data-nav="learn/puzzles/ALL">继续题库</button>
          <button class="ghost" data-nav="learn/practice">AI 练习</button>
        </div>
      </section>`;
  } else if (meTab === 'inbox') {
    mainHtml = `
      <section class="panel">
        <h2 class="sectionTitle">消息通知</h2>
        <div class="moves">
          ${notifications.length ? notifications.map(item => `
            <div class="move">
              <div>
                <strong>${escapeHtml(item.title || '')}</strong>
                <div class="muted">${escapeHtml(item.body || '')}</div>
              </div>
              ${item.path ? `<button class="ghost" data-nav="${escapeHtml(item.path)}">查看</button>` : ''}
            </div>
          `).join('') : '<div class="banner">暂无系统通知。</div>'}
        </div>
      </section>`;
  } else if (meTab === 'achievements') {
    mainHtml = `
      <section class="panel">
        <h2 class="sectionTitle">我的成就</h2>
        <div class="moves">
          ${achievements.length ? achievements.map(item => {
            const pct = item.target ? Math.min(100, Math.round((Number(item.current || 0) * 100) / Number(item.target))) : 0;
            return `
              <div class="move">
                <div style="flex:1">
                  <strong>${escapeHtml(item.title || '')}${item.earned ? ' ✓' : ''}</strong>
                  <div class="muted">${escapeHtml(item.description || '')}</div>
                  <div class="muted">${item.current || 0} / ${item.target || 0}</div>
                  <div style="height:6px;background:var(--border-color);border-radius:3px;margin-top:6px;overflow:hidden">
                    <div style="height:100%;width:${pct}%;background:var(--brand-red,#8c2e21)"></div>
                  </div>
                </div>
              </div>`;
          }).join('') : '<div class="banner">暂无成就数据。</div>'}
        </div>
      </section>`;
  } else if (meTab === 'settings') {
    const soundOn = state.soundEnabled !== false;
    const theme = state.boardTheme || prefs.boardTheme || 'wood';
    const flipped = !!state.boardFlipped;
    mainHtml = `
      <section class="panel">
        <h2 class="sectionTitle">偏好设置</h2>
        <div class="settingRow" style="display:flex;justify-content:space-between;align-items:center;margin:12px 0">
          <span>对局落子音效</span>
          <button class="btn btn-small ${soundOn ? 'btn-red' : 'ghost'}" data-action="toggle-sound">${soundOn ? '开启' : '关闭'}</button>
        </div>
        <div class="settingRow" style="display:flex;justify-content:space-between;align-items:center;margin:12px 0">
          <span>视觉背景主题</span>
          <button class="btn btn-small btn-red" data-action="toggle-theme">${theme === 'wood' ? '古雅木纹' : '清雅水墨'}</button>
        </div>
        <div class="settingRow" style="display:flex;justify-content:space-between;align-items:center;margin:12px 0">
          <span>默认翻转棋盘</span>
          <button class="btn btn-small ghost" data-action="flip-board">${flipped ? '已翻转' : '正位'}</button>
        </div>
        <button class="settingRow mobileVersionRow" data-action="mobile-version-tap" type="button">
          <span>轻棋局 Android</span><span class="muted">版本信息</span>
        </button>
        <p class="muted">登录账号下会同步到服务器，刷新后仍保留。</p>
      </section>`;
  } else if (meTab === 'help') {
    mainHtml = renderHelpPage();
  } else {
    const activeRoom = activity.room;
    const activeGame = activity.game;
    mainHtml = `
      <section class="panel profileHeaderCard">
        <div class="profileUserRow">
          <img class="avatar" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'%3E%3Crect width='40' height='40' fill='%238c2e21'/%3E%3Ctext x='20' y='25' text-anchor='middle' font-size='18' fill='white'%3E${escapeHtml((me.username || '棋').slice(0,1))}%3C/text%3E%3C/svg%3E" />
          <div class="profileUserMeta">
            <strong>${escapeHtml(me.username)}</strong>
            <span class="vipBadge">棋友 · ${escapeHtml(String(me.id || '').slice(0, 8) || '账号')}</span>
          </div>
          <button class="btn btn-red btn-small" data-nav="play">开始对局</button>
        </div>
        <div class="profileStatsGrid">
          <div class="statBox"><strong>${totalGames}</strong><span>对局数</span></div>
          <div class="statBox"><strong>${winRate}%</strong><span>胜率</span></div>
          <div class="statBox"><strong>${wins}/${losses}</strong><span>胜/负</span></div>
          <div class="statBox"><strong>${earnedCount}</strong><span>已获成就</span></div>
        </div>
      </section>
      ${activeRoom || activeGame ? renderActivityBanner(activeRoom, activeGame) : ''}
      <section class="panel" style="margin-top:12px">
        <h3 class="sectionTitle">最近对局</h3>
        <div class="moves">
          ${recentGames.slice(0, 5).length ? recentGames.slice(0, 5).map(renderProfileGameCard).join('') : '<div class="banner">暂无对局。</div>'}
        </div>
      </section>`;
  }

  return `
    <div class="profilePage">
      <aside class="panel profileSidebar">${sidebar}</aside>
      <div class="profileMain">${mainHtml}</div>
    </div>
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
  const canDismiss = !!state.showAuthModal;
  return `
    <div class="authOverlay" data-auth-overlay ${canDismiss ? 'data-action="close-auth"' : ''}>
      <div class="authCard" role="dialog" aria-modal="true" aria-labelledby="authDialogTitle" data-auth-card>
        <div class="authCardHead">
          <div>
            <div class="meta">账号</div>
            <h2 class="sectionTitle" id="authDialogTitle">登录后开启对局与学习进度</h2>
          </div>
          ${canDismiss ? '<button type="button" class="ghost authCloseBtn" data-action="close-auth" aria-label="关闭登录">×</button>' : ''}
        </div>
        <p class="muted authHint">支持房间对战、快速匹配、AI 练习与战绩同步。未登录仍可浏览大厅与棋谱。</p>
        <div class="authTabs" role="tablist">
          <button type="button" class="${state.authMode === 'login' ? 'btn' : 'ghost'}" data-auth-mode="login" role="tab" aria-selected="${state.authMode === 'login'}">登录</button>
          <button type="button" class="${state.authMode === 'register' ? 'btn' : 'ghost'}" data-auth-mode="register" role="tab" aria-selected="${state.authMode === 'register'}">注册</button>
        </div>
        <form class="stack authForm" data-auth-form>
          <div class="field">
            <label for="authUsername">用户名</label>
            <input id="authUsername" name="username" autocomplete="username" placeholder="例如 river-horse" required />
          </div>
          <div class="field">
            <label for="authPassword">密码</label>
            <input id="authPassword" name="password" type="password" autocomplete="${state.authMode === 'login' ? 'current-password' : 'new-password'}" placeholder="至少 8 位" required minlength="8" />
          </div>
          <button type="submit" class="btn ${state.actionBusy === 'auth' ? 'is-busy' : ''}" data-action="submit-auth" data-busy-key="auth" ${state.actionBusy === 'auth' ? 'disabled' : ''}>
            ${state.actionBusy === 'auth' ? '提交中…' : (state.authMode === 'login' ? '登录' : '注册并登录')}
          </button>
          <div class="status ${state.authError ? 'status--error' : ''}" role="alert">${escapeHtml(state.authError || '')}</div>
        </form>
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
  if (!window.__onlineGlobalKeysBound) {
    window.__onlineGlobalKeysBound = true;
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && state.showAuthModal) {
        event.preventDefault();
        closeAuthModal();
      }
    });
  }
  document.querySelectorAll('[data-auth-mode]').forEach(el => el.addEventListener('click', () => {
    state.authMode = el.getAttribute('data-auth-mode');
    openAuthModal('');
  }));
  const authForm = document.querySelector('[data-auth-form]');
  if (authForm && authForm.dataset.boundAuthForm !== '1') {
    authForm.dataset.boundAuthForm = '1';
    authForm.addEventListener('submit', (event) => {
      event.preventDefault();
      submitAuth();
    });
  }
  document.querySelectorAll('[data-action="close-auth"]').forEach(el => {
    if (el.dataset.boundCloseAuth === '1') return;
    el.dataset.boundCloseAuth = '1';
    el.addEventListener('click', (event) => {
      if (el.hasAttribute('data-auth-overlay') && event.target !== el) return;
      event.preventDefault();
      event.stopPropagation();
      closeAuthModal();
    });
  });
  document.querySelectorAll('[data-auth-card]').forEach(el => {
    if (el.dataset.boundAuthCard === '1') return;
    el.dataset.boundAuthCard = '1';
    el.addEventListener('click', (event) => event.stopPropagation());
  });
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
  on('[data-action="show-auth"]', () => openAuthModal(''));
  on('[data-action="toggle-sound"]', toggleOnlineSound);
  on('[data-action="open-mobile-quick-start"]', () => {
    notifyNative('haptic', { style: 'medium' });
    state.mobileQuickStartOpen = true;
    render();
  });
  on('[data-action="close-mobile-quick-start"]', (event) => {
    if (event.currentTarget.hasAttribute('data-mobile-sheet')) return;
    state.mobileQuickStartOpen = false;
    render();
  });
  document.querySelectorAll('[data-mobile-sheet]').forEach(el => el.addEventListener('click', event => event.stopPropagation()));
  on('[data-action="select-mobile-game"]', (event) => {
    state.mobileQuickStartGameType = event.currentTarget.getAttribute('data-game-type') === 'GOMOKU' ? 'GOMOKU' : 'XIANGQI';
    render();
  });
  on('[data-action="refresh-lobby"]', loadLobby);
  on('[data-action="mobile-version-tap"]', () => {
    notifyNative('versionTap');
  });
  on('[data-action="share-room"]', shareCurrentRoom);
  on('[data-action="submit-auth"]', (event) => {
    event.preventDefault();
    submitAuth();
  });
  on('[data-action="quick-start-ai-practice"]', quickStartAiPractice);
  on('[data-action="quick-start-gomoku-practice"]', quickStartGomokuPractice);
  on('[data-action="quick-start-public-match"]', (event) => {
    const gameType = (event.currentTarget && event.currentTarget.getAttribute('data-game-type')) || 'XIANGQI';
    const seconds = Number((event.currentTarget && event.currentTarget.getAttribute('data-time-seconds')) || 300);
    quickStartPublicMatch(gameType, seconds);
  });
  on('[data-action="create-room"]', createRoom);
  on('[data-action="create-room-xiangqi"]', () => createRoomWithPreset({ gameType: 'XIANGQI', initialTimeSeconds: 900, isPublic: false }));
  on('[data-action="create-room-gomoku"]', () => createRoomWithPreset({ gameType: 'GOMOKU', initialTimeSeconds: 600, isPublic: false }));
  on('[data-action="start-xiangqi-game"]', () => quickStartPublicMatch('XIANGQI', 300));
  on('[data-action="start-gomoku-game"]', () => quickStartPublicMatch('GOMOKU', 300));
  on('[data-action="join-by-code"]', joinByCode);
  document.querySelectorAll('[data-action="view-tutorial-detail"]').forEach(el => {
    if (el.dataset.boundTutorialDetail === '1') return;
    el.dataset.boundTutorialDetail = '1';
    el.addEventListener('click', () => {
      const id = el.getAttribute('data-id') || '';
      state.expandedTutorialId = state.expandedTutorialId === id ? '' : id;
      render();
    });
  });
  on('[data-action="join-room"]', joinCurrentRoom);
  on('[data-action="toggle-ready"]', toggleReady);
  on('[data-action="close-room"]', closeCurrentRoom);
  on('[data-action="create-practice"]', createPracticeGame);
  on('[data-action="refresh-watch"]', () => loadWatchOverview(true));
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
  if ((route.page === 'play' || route.page === 'home') && !state.lobby) {
    loadLobby();
  }
  if ((route.page === 'play' || route.page === 'home' || route.page === 'community') && !state.communityLeaderboard) {
    loadCommunityLeaderboard();
  }
  
  // 绑定对局右栏 Tab 切换
  document.querySelectorAll('[data-game-tab]').forEach(el => {
    if (el.dataset.boundGameTab === '1') return;
    el.dataset.boundGameTab = '1';
    el.addEventListener('click', () => {
      state.gameRightTab = el.getAttribute('data-game-tab');
      render();
    });
  });

  // 绑定对局设置选项
  document.querySelectorAll('[data-action="toggle-theme"]').forEach(el => {
    if (el.dataset.boundToggleTheme === '1') return;
    el.dataset.boundToggleTheme = '1';
    el.addEventListener('click', () => {
      state.boardTheme = state.boardTheme === 'ink' ? 'wood' : 'ink';
      saveProfilePreferences({ boardTheme: state.boardTheme }).catch(() => null);
      showToast(state.boardTheme === 'ink' ? '已切换清雅水墨' : '已切换古雅木纹', 'info', 1400);
      render();
    });
  });

  document.querySelectorAll('[data-action="flip-board"]').forEach(el => {
    if (el.dataset.boundFlipBoard === '1') return;
    el.dataset.boundFlipBoard = '1';
    el.addEventListener('click', () => {
      state.boardFlipped = !state.boardFlipped;
      saveProfilePreferences({ boardFlipped: !!state.boardFlipped }).catch(() => null);
      showToast(state.boardFlipped ? '棋盘已翻转' : '棋盘已恢复正位', 'info', 1400);
      render();
    });
  });

  const searchInput = document.getElementById('lobbySearchInput');
  if (searchInput) {
    searchInput.addEventListener('input', event => {
      const value = event.target.value;
      state.lobbySearch.query = value;
      if (state.lobbySearchTimer) {
        window.clearTimeout(state.lobbySearchTimer);
      }
      state.lobbySearchTimer = window.setTimeout(() => {
        loadLobbySearch(value);
      }, 300);
    });
    if (state.lobbySearch.query) {
      searchInput.focus();
      searchInput.setSelectionRange(searchInput.value.length, searchInput.value.length);
    }
  }

  // 绑定学习/棋谱过滤Tab
  document.querySelectorAll('[data-learn-filter]').forEach(el => {
    if (el.dataset.boundLearnFilter === '1') return;
    el.dataset.boundLearnFilter = '1';
    el.addEventListener('click', () => {
      state.learnFilter = el.getAttribute('data-learn-filter');
      state.learnVisibleLimit = learnPageSize();
      state.learnVisibleKey = '';
      if (window.location.hash.includes('/practice')) {
        window.location.hash = '#/learn/puzzles/ALL';
      } else {
        render();
      }
    });
  });

  on('[data-action="load-more-learn"]', () => {
    state.learnVisibleLimit = Math.max(learnPageSize(), Number(state.learnVisibleLimit) || 0) + learnPageSize();
    render();
  });

  // 绑定学习/棋谱页面搜索框
  const learnSearchInput = document.getElementById('learnSearchInput');
  if (learnSearchInput) {
    if (learnSearchInput.dataset.boundLearnSearch !== '1') {
      learnSearchInput.dataset.boundLearnSearch = '1';
      learnSearchInput.addEventListener('input', event => {
        state.learnSearchQuery = event.target.value;
        state.learnVisibleLimit = learnPageSize();
        state.learnVisibleKey = '';
        render();
      });
    }
    if (state.learnSearchQuery) {
      learnSearchInput.focus();
      learnSearchInput.setSelectionRange(learnSearchInput.value.length, learnSearchInput.value.length);
    }
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
  document.querySelectorAll(selector).forEach(element => {
    const boundKey = 'bound_' + selector.replace(/[^a-zA-Z0-9]/g, '_');
    if (element.dataset[boundKey] === '1') {
      return;
    }
    element.dataset[boundKey] = '1';
    element.addEventListener('click', handler);
  });
}

async function submitAuth() {
  if (isActionBusy('auth')) return;
  const usernameEl = document.getElementById('authUsername');
  const passwordEl = document.getElementById('authPassword');
  const username = usernameEl ? usernameEl.value.trim() : '';
  const password = passwordEl ? passwordEl.value : '';
  state.authError = '';
  if (!username || password.length < 8) {
    state.authError = !username ? '请输入用户名' : '密码至少 8 位';
    render();
    return;
  }
  await withActionBusy('auth', state.authMode === 'login' ? '正在登录…' : '正在注册…', async () => {
    try {
      const url = state.authMode === 'login' ? `${API_BASE}/auth/login` : `${API_BASE}/auth/register`;
      const data = await fetchJson(url, { method: 'POST', body: JSON.stringify({ username, password }) });
      state.me = data.user;
      state.showAuthModal = false;
      state.authError = '';
      await refreshBootstrapAndProfile();
      await Promise.all([loadLearnProgress(), loadProfilePreferences(), loadProfileDashboard(false)]);
      showToast(state.authMode === 'login' ? `欢迎回来，${username}` : `注册成功，已登录 ${username}`, 'success');
      render();
    } catch (error) {
      state.authError = error.message || '登录失败';
      showToast(state.authError, 'error', 3200);
      render();
    }
  });
}

async function logout() {
  await fetchJson(`${API_BASE}/auth/logout`, { method: 'POST' }).catch(() => null);
  state.me = null;
  state.profile = null;
  state.profileDashboard = null;
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
  const gameTypeEl = document.getElementById('createGameType');
  const timeEl = document.getElementById('createTime');
  const publicEl = document.getElementById('createPublic');
  if (!gameTypeEl || !timeEl || !publicEl) {
    await createRoomWithPreset({ gameType: 'XIANGQI', initialTimeSeconds: 600, isPublic: false });
    return;
  }
  try {
    state.status = '';
    const room = await fetchJson(`${API_BASE}/rooms`, {
      method: 'POST',
      body: JSON.stringify({
        gameType: gameTypeEl.value,
        initialTimeSeconds: Number(timeEl.value),
        isPublic: publicEl.value === 'true'
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

async function quickStartPublicMatch(gameType = 'XIANGQI', initialTimeSeconds = 300) {
  if (!state.me) {
    openAuthModal('请先登录，再进行真人快速匹配。');
    return;
  }
  const normalizedType = String(gameType || 'XIANGQI').toUpperCase() === 'GOMOKU' ? 'GOMOKU' : 'XIANGQI';
  await withActionBusy('quick-match', '正在匹配对手…', async () => {
    try {
      state.status = '正在匹配对手…';
      render();
      const result = await fetchJson(`${API_BASE}/rooms/quick-match`, {
        method: 'POST',
        body: JSON.stringify({
          gameType: normalizedType,
          initialTimeSeconds: Number(initialTimeSeconds) || 300
        })
      });
      const room = result.room || result;
      state.room = room;
      state.lobby = null;
      state.status = result.matched
        ? '已匹配到对手，在线对局已准备就绪。'
        : '已创建公开候场房，等待棋友加入。';
      showToast(state.status, result.matched ? 'success' : 'info', 2600);
      await refreshBootstrapAndProfile();
      if (room.gameId) {
        navTo(`game/${room.gameId}`);
      } else {
        navTo(`room/${room.roomId}`);
      }
    } catch (error) {
      state.status = error.message;
      showToast(error.message || '匹配失败', 'error');
      render();
    }
  });
}

async function createRoomWithPreset(preset) {
  if (!state.me) {
    openAuthModal('请先登录，再创建或发起对局。');
    return;
  }
  await withActionBusy('create-room', '正在创建房间…', async () => {
    try {
      state.status = '';
      const room = await fetchJson(`${API_BASE}/rooms`, {
        method: 'POST',
        body: JSON.stringify({
          gameType: preset.gameType,
          initialTimeSeconds: Number(preset.initialTimeSeconds || 600),
          isPublic: preset.isPublic !== false
        })
      });
      state.room = room;
      state.lobby = null;
      showToast('房间已创建', 'success', 1800);
      await refreshBootstrapAndProfile();
      navTo(`room/${room.roomId}`);
    } catch (error) {
      state.status = error.message;
      showToast(error.message || '创建房间失败', 'error');
      render();
    }
  });
}

async function createPracticeGame(overrideConfig = null) {
  const payload = overrideConfig ? { ...state.learnConfig, ...overrideConfig } : state.learnConfig;
  if (!state.me) {
    openAuthModal('请先登录，再进入 AI 练习。');
    return;
  }
  await withActionBusy('practice', '正在进入 AI 练习…', async () => {
    try {
      state.status = '';
      stopPracticePolling();
      const game = await fetchJson(`${API_BASE}/learn/practice-games`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      state.game = applyServerGameSnapshot(game);
      showToast('AI 练习局已开始', 'success', 1600);
      await refreshBootstrapAndProfile();
      navTo(`practice/${game.gameId}`);
    } catch (error) {
      state.status = error.message;
      showToast(error.message || '创建练习失败', 'error');
      render();
    }
  });
}

async function quickStartAiPractice() {
  if (!state.me) {
    openAuthModal('请先登录，再直接进入 AI 对局。');
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
    openAuthModal('请先登录，再直接进入 AI 对局。');
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
    openAuthModal('登录后可按推荐配置开始练习');
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
    openAuthModal('登录后可按题目局面直接开始练习');
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
    showToast('学习进度已记录', 'success', 1600);
  } catch (error) {
    state.status = error.message;
    showToast(error.message || '记录失败', 'error');
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
  if (!state.me) {
    openAuthModal('请先登录，再通过房间码加入。');
    return;
  }
  const input = document.getElementById('joinCode');
  let roomCode = input ? String(input.value || '').trim() : '';
  if (!roomCode) {
    roomCode = String(window.prompt('请输入房间码') || '').trim();
  }
  if (!roomCode) {
    state.status = '请输入房间码。';
    showToast('请输入房间码', 'error', 2200);
    if (input) {
      input.classList.add('input-invalid');
      input.focus();
      window.setTimeout(() => input.classList.remove('input-invalid'), 1200);
    }
    return;
  }
  await withActionBusy('join-code', '正在加入房间…', async () => {
    try {
      state.status = '';
      const room = await fetchJson(`${API_BASE}/rooms/join-by-code`, {
        method: 'POST',
        body: JSON.stringify({ roomCode })
      });
      state.room = room;
      showToast(`已加入房间 ${room.roomCode || roomCode}`, 'success');
      await refreshBootstrapAndProfile();
      navTo(`room/${room.roomId}`);
    } catch (error) {
      state.status = error.message;
      showToast(error.message || '加入失败', 'error');
      render();
    }
  });
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

async function closeCurrentRoom() {
  const room = state.room;
  if (!room || !isRoomHost(room)) return;
  if (!window.confirm('关闭后所有成员将退出此房间，确定关闭吗？')) return;
  try {
    await fetchJson(`${API_BASE}/rooms/${room.roomId}`, { method: 'DELETE' });
    state.room = null;
    state.game = null;
    closeSocket();
    await refreshBootstrapAndProfile();
    showToast('房间已关闭', 'success');
    navTo('play');
  } catch (error) {
    state.status = error.message;
    render();
  }
}

function isRoomHost(room) {
  return !!(room && room.host && state.me && room.host.id === state.me.id);
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
  await loadProfileDashboard(true);
}

async function loadProfileDashboard(renderAfter = true) {
  if (!state.me) {
    state.profileDashboard = null;
    state.profile = null;
    if (renderAfter) render();
    return;
  }
  const dash = await fetchJson(`${API_BASE}/profile/dashboard`).catch(() => null);
  if (dash) {
    state.profileDashboard = dash;
    state.profile = {
      user: dash.user,
      summary: dash.summary,
      recentGames: dash.recentGames,
      activity: dash.activity
    };
    if (dash.learnProgress) {
      state.learnProgress = dash.learnProgress;
    }
    applyPreferencesToState(dash.preferences || {});
  } else {
    state.profile = await fetchJson(`${API_BASE}/profile/summary`).catch(() => null);
  }
  if (renderAfter) render();
}

async function loadProfilePreferences() {
  if (!state.me) return;
  const prefs = await fetchJson(`${API_BASE}/profile/preferences`).catch(() => null);
  if (prefs) {
    applyPreferencesToState(prefs);
  }
}

function applyPreferencesToState(prefs) {
  if (!prefs || typeof prefs !== 'object') return;
  if (typeof prefs.soundEnabled === 'boolean') {
    state.soundEnabled = prefs.soundEnabled;
    persistOnlineSoundEnabled(prefs.soundEnabled);
  }
  if (prefs.boardTheme === 'ink' || prefs.boardTheme === 'wood') {
    state.boardTheme = prefs.boardTheme;
  }
  if (typeof prefs.boardFlipped === 'boolean') {
    state.boardFlipped = prefs.boardFlipped;
  }
}

async function saveProfilePreferences(patch) {
  if (!state.me || !patch) return null;
  try {
    const saved = await fetchJson(`${API_BASE}/profile/preferences`, {
      method: 'POST',
      body: JSON.stringify(patch)
    });
    applyPreferencesToState(saved);
    if (state.profileDashboard) {
      state.profileDashboard = { ...state.profileDashboard, preferences: saved };
    }
    return saved;
  } catch (error) {
    state.status = error.message || '偏好同步失败';
    return null;
  }
}

async function refreshBootstrapAndProfile() {
  await loadBootstrap();
  if (state.me) {
    await loadProfileDashboard(false);
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
  const lobbyRoute = route.page === 'play' || route.page === 'home';
  const desiredRoom = route.page === 'room'
    ? route.id
    : (route.page === 'game' && state.game && !state.game.isTraining ? state.game.roomId : '');
  const desiredSubscription = lobbyRoute ? 'lobby' : desiredRoom;
  if (!desiredSubscription) {
    closeSocket();
    return;
  }
  if (state.ws && state.wsRoomId === desiredSubscription) return;
  closeSocket();
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const socket = new WebSocket(`${protocol}//${location.host}${WS_BASE}`);
  state.ws = socket;
  state.wsRoomId = desiredSubscription;
  socket.onopen = () => socket.send(JSON.stringify(
    lobbyRoute ? { type: 'subscribe_lobby' } : { type: 'subscribe', roomId: desiredRoom }
  ));
  socket.onmessage = async event => {
    const data = JSON.parse(event.data);
    if (data.type === 'lobby' && data.lobby) {
      state.lobby = data.lobby;
      render();
      return;
    }
    if (data.type === 'room_closed') {
      state.room = null;
      state.game = null;
      closeSocket();
      await refreshBootstrapAndProfile();
      showToast(data.message || '房间已关闭', 'info');
      navTo('play');
      return;
    }
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
  socket.onclose = () => {
    if (state.ws !== socket) return;
    state.ws = null;
    state.wsRoomId = '';
    window.setTimeout(() => syncRealtime(currentRoute()), 1000);
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
  maybeNotifyOpponentMove(previousGame, nextGame);
  maybePlayMoveSound(previousGame, nextGame);
  maybePlayFinishSound(previousGame, nextGame);
  maybeOpenEndGameModal(nextGame);
}

function maybeNotifyOpponentMove(previousGame, nextGame) {
  if (!previousGame
    || !nextGame
    || previousGame.gameId !== nextGame.gameId
    || nextGame.isTraining
    || gameHasAiOpponent(nextGame)
    || !Array.isArray(nextGame.moves)
    || !nextGame.moves.length) {
    return;
  }
  const latest = getLastMove(nextGame);
  const previousLatest = getLastMove(previousGame);
  const latestIndex = Number(latest && latest.index ? latest.index : nextGame.moves.length);
  const previousIndex = Number(previousLatest && previousLatest.index
    ? previousLatest.index
    : (previousGame.moves || []).length);
  if (!Number.isFinite(latestIndex) || latestIndex <= previousIndex) {
    return;
  }
  const viewerSide = nextGame.viewerSide || inferViewerSide(nextGame);
  const moveSide = String(latest && (latest.side || (latest.payload && latest.payload.side)) || '').trim();
  if (!viewerSide || !moveSide || moveSide === viewerSide) {
    return;
  }
  if (state.lastOpponentMoveNoticeGameId === nextGame.gameId
    && state.lastOpponentMoveNoticeIndex === latestIndex) {
    return;
  }
  state.lastOpponentMoveNoticeGameId = nextGame.gameId;
  state.lastOpponentMoveNoticeIndex = latestIndex;
  const isViewerTurn = nextGame.status === 'PLAYING' && nextGame.currentTurn === viewerSide;
  showToast(isViewerTurn ? '对手已落子，轮到你了' : '对手已落子', 'move', 3600);
  notifyOpponentMoveHaptic();
}

function notifyOpponentMoveHaptic() {
  if (notifyNative('haptic', { style: 'medium' })) {
    return;
  }
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      navigator.vibrate([70, 35, 90]);
    }
  } catch (_) {
    // Browsers may deny vibration; the visual notice remains available.
  }
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
  saveProfilePreferences({ soundEnabled: !!state.soundEnabled }).catch(() => null);
  showToast(state.soundEnabled ? '音效已开启' : '音效已关闭', 'info', 1400);
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
  return (game && game.resultText) || '执子对弈，落子无悔。';
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


/* ==================== Guofeng Water Ink Rendering Functions ==================== */

function renderWelcomePage() {
  return `
    <div class="welcomePane">
      <div class="welcomeHeader">
        <div class="stampLogo">棋</div>
        <h1 class="welcomeTitle">轻棋局<span class="onlineStamp">Online</span></h1>
      </div>
      <div class="illustCircle bg-welcome_illust"></div>
      <div class="welcomeText">
        <h2>落子之间，自有风雅</h2>
        <p>在线象棋与五子棋对局，轻松开局，随时对弈</p>
      </div>
      <div class="sliderDots">
        <span class="dot active"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </div>
      <div class="welcomeActions">
        <button class="btn welcomeBtn" data-nav="home">开始对局</button>
        <div class="loginHint">已有账号？<a href="#/me">登录</a></div>
      </div>
    </div>
  `;
}

function leaderboardRowsHtml(items, emptyText) {
  const list = (items || []).slice(0, 4);
  if (!list.length) {
    return `<div class="banner" style="padding:12px;font-size:13px">${escapeHtml(emptyText || '暂无榜单数据。')}</div>`;
  }
  return list.map((item, index) => `
    <div class="deskRankRow">
      <span class="rankNum num-${index + 1}">${index + 1}</span>
      <strong class="rankUser">${escapeHtml(item.username || '-')}</strong>
      <span class="rankScore">${item.wins != null ? item.wins : (item.score || 0)}</span>
    </div>
  `).join('');
}

function leaderboardBucket(board, gameType) {
  const root = board || {};
  const byType = root.byGameType && root.byGameType[gameType];
  if (byType && Array.isArray(byType.winBoard)) {
    return byType.winBoard;
  }
  return root.winBoard || [];
}

function renderHomePageGuofeng() {
  const b = state.bootstrap || { recentGames: [], activeRooms: 0, totalUsers: 0, totalGames: 0 };
  if (!state.communityLeaderboard) {
    loadCommunityLeaderboard();
  }
  const leaderboard = state.communityLeaderboard || { winBoard: [], activityBoard: [], byGameType: {} };
  const xqBoard = leaderboardBucket(leaderboard, 'XIANGQI');
  const gmBoard = leaderboardBucket(leaderboard, 'GOMOKU');
  return `
    <div class="deskHome">
      <section class="deskHero panel">
        <div class="deskHeroIllustLeft bg-welcome_illust"></div>
        <div class="deskHeroCopy">
          <h1>落子之间，自有风雅</h1>
          <p>在线象棋与五子棋对局、AI 练习与复盘分析，随时开局</p>
          <div class="deskHeroActions">
            <button class="deskModeCard deskModeCard--red" data-nav="play/xiangqi">
              <strong>象棋对局</strong>
              <span>楚河汉界，以局会友</span>
            </button>
            <button class="deskModeCard deskModeCard--green" data-nav="play/gomoku">
              <strong>五子棋对局</strong>
              <span>黑白落点，东风入手</span>
            </button>
          </div>
        </div>
        <div class="deskHeroIllustRight bg-detail_gomoku"></div>
      </section>
      
      <div class="deskQuickGrid">
        <button class="deskQuickItem" data-action="quick-start-public-match" data-game-type="XIANGQI" data-time-seconds="300">
          <span class="icon">⚡</span>
          <div class="text"><strong>快速匹配</strong><span>真人匹配 实时对局</span></div>
        </button>
        <button class="deskQuickItem" data-action="create-room-xiangqi">
          <span class="icon">👥</span>
          <div class="text"><strong>好友对弈</strong><span>邀请好友 随时切磋</span></div>
        </button>
        <button class="deskQuickItem" data-action="quick-start-ai-practice">
          <span class="icon">🤖</span>
          <div class="text"><strong>人机练习</strong><span>象棋 AI 对战</span></div>
        </button>
        <button class="deskQuickItem" data-nav="me">
          <span class="icon">👤</span>
          <div class="text"><strong>个人中心</strong><span>战绩总览 真实数据</span></div>
        </button>
      </div>
      
      <div class="deskHomeThreeCol">
        <section class="panel col-left">
          <div class="deskSectionHeader"><h3>快捷入口</h3></div>
          <div class="taskList">
            <div class="taskRow">
              <div class="taskInfo">
                <strong>进入对局大厅</strong>
                <span>创建房间 / 邀请码 / 匹配</span>
              </div>
              <button class="btn btn-red btn-small" data-nav="play">前往</button>
            </div>
            <div class="taskRow">
              <div class="taskInfo">
                <strong>残局与棋谱</strong>
                <span>题库与教程</span>
              </div>
              <button class="btn btn-red btn-small" data-nav="learn/puzzles/ALL">前往</button>
            </div>
            <div class="taskRow">
              <div class="taskInfo">
                <strong>公开观战</strong>
                <span>进行中可实时观战</span>
              </div>
              <button class="btn btn-red btn-small" data-nav="watch">前往</button>
            </div>
          </div>
          <div class="muted" style="margin-top:12px;font-size:12px">活动房间 ${b.activeRooms || 0} · 用户 ${b.totalUsers || 0} · 对局 ${b.totalGames || 0}</div>
        </section>
        
        <section class="panel col-mid">
          <div class="deskSectionHeader">
            <h3>象棋排行榜</h3>
            <button class="ghost btn-small" data-nav="community">全部</button>
          </div>
          <div class="deskRankList">
            ${leaderboardRowsHtml(xqBoard, '暂无象棋榜单数据。')}
          </div>
        </section>
        
        <section class="panel col-right">
          <div class="deskSectionHeader">
            <h3>五子棋排行榜</h3>
            <button class="ghost btn-small" data-nav="community">全部</button>
          </div>
          <div class="deskRankList">
            ${leaderboardRowsHtml(gmBoard, '暂无五子棋榜单数据。')}
          </div>
        </section>
      </div>
    </div>
  `;
}

async function shareCurrentRoom(event) {
  const roomCode = String(event.currentTarget?.getAttribute('data-room-code') || state.room?.roomCode || '').trim();
  const url = window.location.href;
  if (notifyNative('shareRoom', { roomCode, url })) {
    notifyNative('haptic', { style: 'light' });
    return;
  }
  const title = roomCode ? `轻棋局 · 房间 ${roomCode}` : '轻棋局好友对弈';
  try {
    if (navigator.share) {
      await navigator.share({ title, text: roomCode ? `房间码：${roomCode}` : title, url });
      return;
    }
    await navigator.clipboard.writeText(roomCode ? `${title}\n${url}` : url);
    showToast('房间邀请已复制', 'success');
  } catch (error) {
    if (error && error.name === 'AbortError') return;
    showToast('暂时无法分享，请手动复制房间码', 'error');
  }
}

function mobileIcon(name) {
  const paths = {
    home: '<path d="M4 11.5 12 5l8 6.5V20a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1Z"/>',
    play: '<path d="M7 4h10l3 6-3 10h-4l-1-3-1 3H7L4 10Zm1.5 6H6m10 0h2m-8-2v4m-2-2h4"/>',
    learn: '<path d="M4 5.5A3.5 3.5 0 0 1 7.5 2H11v17H7.5A3.5 3.5 0 0 0 4 22Zm16 0A3.5 3.5 0 0 0 16.5 2H13v17h3.5A3.5 3.5 0 0 1 20 22Z"/>',
    watch: '<path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z"/><circle cx="12" cy="12" r="2.5"/>',
    me: '<circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>',
    spark: '<path d="m13 2-8 11h6l-1 9 9-12h-6Z"/>',
    robot: '<rect x="4" y="7" width="16" height="12" rx="3"/><path d="M12 3v4M8 12h.01M16 12h.01M8 16h8"/>',
    friends: '<circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.5"/><path d="M3 20a6 6 0 0 1 12 0m0-5a5 5 0 0 1 6 5"/>',
    refresh: '<path d="M20 7v5h-5"/><path d="M4 17v-5h5"/><path d="M6.1 8.5A7 7 0 0 1 18.4 6L20 8m-16 8 1.6 2A7 7 0 0 0 17.9 15.5"/>',
    back: '<path d="m15 18-6-6 6-6"/>',
    chevron: '<path d="m9 18 6-6-6-6"/>'
  };
  return `<svg class="mobileIcon" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${paths[name] || paths.chevron}</svg>`;
}

function renderMobilePageHeader({ eyebrow, title, backPath = 'home', backLabel = '返回首页' }) {
  return `
    <header class="mobileContentHeader">
      <button data-nav="${backPath}" aria-label="${backLabel}">${mobileIcon('back')}</button>
      <div><span>${eyebrow}</span><h1>${title}</h1></div>
      <button data-nav="me" aria-label="打开个人中心">${mobileIcon('me')}</button>
    </header>
  `;
}

function renderMobileContentPage(route, content) {
  const titles = {
    learn: ['学习', '残局与教程'],
    watch: ['观战', '正在进行的对局'],
    community: ['棋友', '排行榜与动态'],
    me: ['我的', '战绩与偏好'],
    room: ['对局', '房间等候'],
    help: ['帮助', '使用说明']
  };
  const [eyebrow, title] = titles[route.page] || ['轻棋局', '棋局中心'];
  const backPath = route.page === 'room' ? 'play' : 'home';
  const backLabel = backPath === 'home' ? '返回首页' : '返回对局大厅';
  return `
    <section class="mobileContentPage mobileContentPage--${route.page}">
      ${renderMobilePageHeader({ eyebrow, title, backPath, backLabel })}
      <div class="mobileContentBody">${content}</div>
    </section>
  `;
}

function renderMobileModePage(route) {
  const isGomoku = route.id === 'gomoku';
  const title = isGomoku ? '五子棋模式' : '中国象棋模式';
  const content = isGomoku ? renderPlayGomoku() : renderPlayXiangqi();
  return `
    <section class="mobileModePage">
      ${renderMobilePageHeader({ eyebrow: '选择棋种', title, backPath: 'play', backLabel: '返回对局大厅' })}
      <div class="mobileContentBody">${content}</div>
    </section>
  `;
}

function renderMobileHomePage() {
  const b = state.bootstrap || { recentGames: [], activeRooms: 0, totalUsers: 0, totalGames: 0, activity: {} };
  const recent = (b.recentGames || []).slice(0, 2);
  const activity = b.activity || {};
  const board = leaderboardBucket(state.communityLeaderboard || {}, 'XIANGQI').slice(0, 1);
  const continuePath = activity.game && activity.game.gameId
    ? `game/${escapeHtml(activity.game.gameId)}`
    : (activity.room && activity.room.roomId ? `room/${escapeHtml(activity.room.roomId)}` : 'learn/puzzles/ALL');
  const continueLabel = activity.game ? '继续未完棋局' : (activity.room ? '回到等候房间' : '今日残局挑战');

  return `
    <div class="mobileHome">
      <header class="mobileAppHeader">
        <a class="mobileBrand" href="#/home" aria-label="轻棋局首页"><span class="mobileBrandSeal">棋</span><span><strong>轻棋局</strong><small>落子之间，自有风雅</small></span></a>
        <button class="mobileProfileButton" data-nav="me" aria-label="进入个人中心">${state.me ? escapeHtml((state.me.username || '棋').slice(0, 1)) : '客'}</button>
      </header>

      <section class="mobileHero" aria-labelledby="mobile-home-title">
        <div class="mobileHeroWash" aria-hidden="true"><span class="mobileBoardGlyph">楚河<br>汉界</span></div>
        <div class="mobileHeroCopy">
          <span class="mobileEyebrow">随时开局 · 从容落子</span>
          <h1 id="mobile-home-title">下一局，<br>从这一手开始</h1>
          <p>真人匹配、好友约棋与 AI 练习，一处完成。</p>
          <button class="mobilePrimaryAction" data-action="open-mobile-quick-start">
            ${mobileIcon('spark')}<span>快速开始一局</span><small>默认中国象棋</small>
          </button>
        </div>
      </section>

      <section class="mobileContinueStrip" data-nav="${continuePath}" aria-label="${continueLabel}">
        <span class="mobileContinueMark">${activity.game || activity.room ? '续' : '题'}</span>
        <span><strong>${continueLabel}</strong><small>${activity.game || activity.room ? '保留当前进度，继续落子' : '用一盘短题热热手'}</small></span>
        ${mobileIcon('chevron')}
      </section>

      <section class="mobileSection">
        <div class="mobileSectionHead"><div><span>常用入口</span><h2>你想怎么下？</h2></div><button data-nav="play">全部模式</button></div>
        <div class="mobileActionList">
          <button data-action="quick-start-ai-practice"><span class="mobileActionIcon is-red">${mobileIcon('robot')}</span><span><strong>人机练习</strong><small>无需等待，随时开局</small></span>${mobileIcon('chevron')}</button>
          <button data-action="create-room-xiangqi"><span class="mobileActionIcon is-green">${mobileIcon('friends')}</span><span><strong>好友约棋</strong><small>创建房间，分享房间码</small></span>${mobileIcon('chevron')}</button>
          <button data-nav="play/gomoku"><span class="mobileActionIcon is-ink"><b>五</b></span><span><strong>五子棋</strong><small>黑白落点，轻松一局</small></span>${mobileIcon('chevron')}</button>
        </div>
      </section>

      <section class="mobileSection mobileRecentSection">
        <div class="mobileSectionHead"><div><span>最近棋局</span><h2>留下的每一步</h2></div><button data-nav="me/records">全部战绩</button></div>
        <div class="mobileRecentList">
          ${recent.length ? recent.map(game => `
            <button data-nav="analysis/${escapeHtml(game.gameId || '')}">
              <span class="mobileGameSeal ${game.gameType === 'GOMOKU' ? 'is-green' : ''}">${game.gameType === 'GOMOKU' ? '五' : '象'}</span>
              <span><strong>${game.gameType === 'GOMOKU' ? '五子棋' : '中国象棋'} · ${escapeHtml(game.resultText || '已归档')}</strong><small>${escapeHtml(game.firstUsername || '-')} 对 ${escapeHtml(game.secondUsername || '-')}</small></span>
              ${mobileIcon('chevron')}
            </button>
          `).join('') : '<div class="mobileEmptyState"><strong>还没有棋局记录</strong><span>从上面的快速开始，落下第一子。</span></div>'}
        </div>
      </section>

      <section class="mobileRankNote">
        <span>象棋榜</span>
        ${board.length ? `<strong>${escapeHtml(board[0].username || '-')}</strong><small>${board[0].wins != null ? board[0].wins : (board[0].score || 0)} 胜</small>` : '<strong>榜单正在静候高手</strong><small>完成对局后将出现真实排名</small>'}
        <button data-nav="community">查看榜单</button>
      </section>
      ${renderMobileQuickStartSheet()}
    </div>
  `;
}

function renderMobileQuickStartSheet() {
  if (!state.mobileQuickStartOpen) return '';
  const gameType = state.mobileQuickStartGameType === 'GOMOKU' ? 'GOMOKU' : 'XIANGQI';
  return `
    <div class="mobileSheetOverlay" data-action="close-mobile-quick-start">
      <section class="mobileQuickStartSheet" role="dialog" aria-modal="true" aria-labelledby="quick-start-title" data-mobile-sheet>
        <span class="mobileSheetHandle" aria-hidden="true"></span>
        <div class="mobileSheetHeading"><span>快速开始</span><h2 id="quick-start-title">这一局，怎么下？</h2></div>
        <div class="mobileGameToggle" role="group" aria-label="选择棋种">
          <button class="${gameType === 'XIANGQI' ? 'is-active' : ''}" data-action="select-mobile-game" data-game-type="XIANGQI">中国象棋</button>
          <button class="${gameType === 'GOMOKU' ? 'is-active' : ''}" data-action="select-mobile-game" data-game-type="GOMOKU">五子棋</button>
        </div>
        <div class="mobileStartModes">
          <button data-action="quick-start-public-match" data-game-type="${gameType}" data-time-seconds="300">${mobileIcon('spark')}<span><strong>真人快速匹配</strong><small>5 分钟场，匹配在线棋友</small></span>${mobileIcon('chevron')}</button>
          <button data-action="${gameType === 'GOMOKU' ? 'quick-start-gomoku-practice' : 'quick-start-ai-practice'}">${mobileIcon('robot')}<span><strong>人机练习</strong><small>立即开局，磨练棋力</small></span>${mobileIcon('chevron')}</button>
          <button data-action="${gameType === 'GOMOKU' ? 'create-room-gomoku' : 'create-room-xiangqi'}">${mobileIcon('friends')}<span><strong>好友约棋</strong><small>创建房间并分享房间码</small></span>${mobileIcon('chevron')}</button>
        </div>
        <button class="mobileSheetCancel" data-action="close-mobile-quick-start">暂不开始</button>
      </section>
    </div>
  `;
}

function renderMobileLobby() {
  const rooms = ((state.lobby && state.lobby.rooms) || []).slice(0, 8);
  return `
    <div class="mobileLobby">
      ${renderMobilePageHeader({ eyebrow: '对局大厅', title: '找一位棋友' })}
      <section class="mobileLobbyLead">
        <span class="mobileEyebrow">中国象棋 · 默认 5 分钟</span>
        <h2>有人等你落下第一子</h2>
        <button class="mobilePrimaryAction" data-action="quick-start-public-match" data-game-type="XIANGQI" data-time-seconds="300">${mobileIcon('spark')}<span>立即快速匹配</span><small>真人实时对局</small></button>
      </section>
      <div class="mobileLobbyActions">
        <button data-action="create-room-xiangqi"><span>创建房间</span><small>邀请好友对弈</small></button>
        <label><span>加入房间</span><span class="mobileJoinRow"><input id="joinCode" type="text" placeholder="输入房间码" autocomplete="off"><button data-action="join-by-code">加入</button></span></label>
      </div>
      <section class="mobileSection">
        <div class="mobileSectionHead"><div><span>公开房间</span><h2>正在等候</h2></div><button data-action="refresh-lobby" aria-label="刷新大厅">${mobileIcon('refresh')}<span>${rooms.length} 间</span></button></div>
        <div class="mobileRoomList">
          ${rooms.length ? rooms.map(room => `
            <button data-nav="room/${escapeHtml(room.roomId || '')}">
              <span class="mobileGameSeal ${room.gameType === 'GOMOKU' ? 'is-green' : ''}">${room.gameType === 'GOMOKU' ? '五' : '象'}</span>
              <span><strong>${escapeHtml(room.hostUsername || '棋友')} 的${room.gameType === 'GOMOKU' ? '五子棋' : '象棋'}房</strong><small>${escapeHtml(room.roomCode || '')} · ${room.guestUsername ? '对局中' : '等待加入'}</small></span>
              ${mobileIcon('chevron')}
            </button>
          `).join('') : '<div class="mobileEmptyState"><strong>暂时没有公开房间</strong><span>创建一间棋室，邀请好友先来一局。</span></div>'}
        </div>
      </section>
    </div>
  `;
}

function renderPlayLobbyDesk() {
  const recentGames = ((state.bootstrap && state.bootstrap.recentGames) || []).slice(0, 5);
  if (!state.communityLeaderboard) {
    loadCommunityLeaderboard();
  }
  const leaderboard = state.communityLeaderboard || { winBoard: [], byGameType: {} };
  const xqBoard = leaderboardBucket(leaderboard, 'XIANGQI');
  return `
    <div class="deskLobby">
      <aside class="panel deskSidebar">
        <div class="sidebarUser">
          <img class="avatar" src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'%3E%3Crect width='40' height='40' fill='%238c2e21'/%3E%3Ctext x='20' y='25' text-anchor='middle' font-size='18' fill='white'%3E${escapeHtml((state.me && state.me.username || '棋').slice(0,1))}%3C/text%3E%3C/svg%3E" />
          <div class="userMeta">
            <strong>${escapeHtml(state.me && state.me.username || '未登录')}</strong>
            <span class="vipBadge">${state.me ? '棋友' : '游客'}</span>
          </div>
        </div>
        <button class="deskSidebarItem" data-nav="home">首页</button>
        <button class="deskSidebarItem is-active" data-action="quick-start-public-match" data-game-type="XIANGQI">快速匹配</button>
        <button class="deskSidebarItem" data-action="quick-start-ai-practice">人机对战</button>
        <button class="deskSidebarItem" data-action="create-room-xiangqi">好友对弈</button>
        <button class="deskSidebarItem" data-action="create-room-xiangqi">创建房间</button>
        <button class="deskSidebarItem" data-nav="watch">公开观战</button>
        <button class="deskSidebarItem" data-nav="me/settings">偏好设置</button>
        <button class="deskSidebarItem" data-nav="help">帮助</button>
      </aside>
      <section class="deskLobbyMain">
        <div class="panel deskLobbySearch">
          <label class="searchFieldLabel" for="lobbySearchInput">搜索大厅</label>
          <div class="searchBar searchBar--lobby">
            <span class="searchIcon" aria-hidden="true">🔍</span>
            <input type="search" id="lobbySearchInput" name="lobbySearch" placeholder="搜索房间、玩家、房间号..." value="${escapeHtml(state.lobbySearch.query)}" autocomplete="off" />
          </div>
          <div class="deskLobbyTabs">
            <button class="pill is-active">全部</button>
            <button class="pill" data-nav="play/xiangqi">象棋</button>
            <button class="pill" data-nav="play/gomoku">五子棋</button>
            <button class="pill" data-nav="learn/practice">人机</button>
            <button class="pill" data-nav="watch">观战</button>
          </div>
        </div>
        <div class="deskLobbyCards">
          <div class="panel deskModePanel deskModePanel--red" data-nav="play/xiangqi">
            <div class="deskModePanelCopy">
              <div class="meta">在线象棋</div>
              <h3>在线象棋</h3>
              <p>楚河汉界，智策对决</p>
              <button class="btn" data-action="quick-start-public-match" data-game-type="XIANGQI">快速匹配</button>
            </div>
            <div class="deskModePanelBg bg-detail_xiangqi"></div>
          </div>
          <div class="panel deskModePanel deskModePanel--green" data-nav="play/gomoku">
            <div class="deskModePanelCopy">
              <div class="meta">五子棋</div>
              <h3>五子棋</h3>
              <p>五子连珠，乐趣其中</p>
              <button class="btn" data-nav="play/gomoku">进入模式页</button>
            </div>
            <div class="deskModePanelBg bg-detail_gomoku"></div>
          </div>
        </div>
        <section class="panel deskLobbyRecent">
          <div class="deskSectionHeader"><h3>${state.lobbySearch.query ? '搜索结果' : '最近对局'}</h3></div>
          <div class="recentList">
            ${state.lobbySearch.query ? (
              state.lobbySearch.loading ? '<div class="banner">正在搜索中...</div>' : (
                state.lobbySearch.error ? `<div class="banner error">${escapeHtml(state.lobbySearch.error)}</div>` : (
                  (state.lobbySearch.rooms.length || state.lobbySearch.players.length) ? (
                    [
                      ...state.lobbySearch.rooms.map(room => `
                        <div class="recentRow" data-nav="room/${room.roomId}">
                          <span class="gameBadge ${room.gameType === 'XIANGQI' ? 'red' : 'green'}">${room.gameType === 'XIANGQI' ? '帅' : '五'}</span>
                          <div class="gameDetails">
                            <strong>${room.gameType === 'XIANGQI' ? '中国象棋' : '五子棋'} (${room.roomCode})</strong>
                            <span class="muted">${escapeHtml(room.hostUsername)}${room.guestUsername ? ` vs ${room.guestUsername}` : ' · 等待加入'}</span>
                          </div>
                          <span class="gameTime">${escapeHtml(room.status)}</span>
                        </div>
                      `),
                      ...state.lobbySearch.players.map(player => `
                        <div class="recentRow">
                          <span class="gameBadge red">人</span>
                          <div class="gameDetails">
                            <strong>${escapeHtml(player.username)} (玩家)</strong>
                            <span class="muted">当前在线</span>
                          </div>
                          <span class="gameTime">在线</span>
                        </div>
                      `)
                    ].join('')
                  ) : '<div class="banner">未找到匹配的公开房间或玩家。</div>'
                )
              )
            ) : (
              recentGames.map(game => `
                <div class="recentRow" data-nav="analysis/${game.gameId}">
                  <span class="gameBadge ${game.gameType === 'XIANGQI' ? 'red' : 'green'}">${game.gameType === 'XIANGQI' ? '帅' : '五'}</span>
                  <div class="gameDetails">
                    <strong>${game.gameType === 'XIANGQI' ? '中国象棋' : '五子棋'} · ${escapeHtml(game.resultText || '已归档')}</strong>
                    <span class="muted">${escapeHtml(game.firstUsername || '-')} vs ${escapeHtml(game.secondUsername || '-')}</span>
                  </div>
                  <span class="gameTime">已完赛</span>
                </div>
              `).join('') || '<div class="banner">暂无近期对局。</div>'
            )}
          </div>
        </section>
      </section>
      <aside class="panel deskLobbyAside">
        <div class="deskSectionHeader"><h3>创建房间 & 邀请好友</h3></div>
        <div class="lobbyActionGrid">
          <button class="actionBtn actionBtn--create" data-action="create-room-xiangqi">
            <span class="actionIcon">➕</span>
            <div class="actionText">
              <strong>创建房间</strong>
              <span>自定义规则 邀请好友</span>
            </div>
          </button>
          <div class="joinCodeBlock">
            <label class="searchFieldLabel" for="joinCode">房间码</label>
            <div class="searchBar searchBar--join">
              <span class="searchIcon" aria-hidden="true">#</span>
              <input id="joinCode" type="text" name="joinCode" placeholder="输入房间码，例如 AB12CD" autocomplete="off" spellcheck="false" />
            </div>
            <button class="actionBtn actionBtn--join" data-action="join-by-code" style="width:100%;margin-top:10px">
              <span class="actionIcon">🏠</span>
              <div class="actionText">
                <strong>加入房间</strong>
                <span>输入房间码 快速加入</span>
              </div>
            </button>
          </div>
        </div>
        <div class="deskSectionHeader">
          <h3>推荐高手</h3>
        </div>
        <div class="deskRankList">
          ${leaderboardRowsHtml(xqBoard, '暂无榜单数据。')}
        </div>
        <button class="ghost btn-block" data-nav="community">查看全部高手</button>
        ${state.status ? `<div class="status" style="margin-top:10px">${escapeHtml(state.status)}</div>` : ''}
      </aside>
    </div>
  `;
}

function renderHelpPage() {
  return `
    <section class="panel deskHelpPanel">
      <div class="meta">帮助</div>
      <h2 class="sectionTitle">使用说明</h2>
      <div class="deskHelpGrid">
        <div class="card">
          <h3>在线对局</h3>
          <p>从“对局”进入大厅，可创建私密房或公开房，双方准备后自动开局。</p>
        </div>
        <div class="card">
          <h3>AI 练习</h3>
          <p>从“练习”或首页快捷入口开始 AI 对战，支持象棋与五子棋，复盘入口保持不变。</p>
        </div>
        <div class="card">
          <h3>分析复盘</h3>
          <p>每局结束后都能进入分析页，按步数回看局面，不增加会员门槛。</p>
        </div>
        <div class="card">
          <h3>账号与音效</h3>
          <p>右上角保留登录、注册、个人页和音效开关。当前版本不提供会员功能。</p>
        </div>
      </div>
    </section>
  `;
}

function renderPlayXiangqi() {
  return `
    <div class="modeSelectPage xiangqiTheme">
      <div class="modeTopIllust bg-detail_xiangqi">
        <div class="modeHeaderNav">
          <div class="btn-back-custom" data-nav="home">ㄑ</div>
          <div class="btn-star-custom">☆</div>
        </div>
        <div class="stampTitleWrap">
          <h1 class="stampTitle">楚河 汉界</h1>
          <span class="stampTextDecor">象棋模式</span>
        </div>
      </div>
      
      <div class="modeDetailCard">
        <h2>在线象棋</h2>
        <p class="subtitle">真人匹配，好友对弈，残局练习，享受从容对弈之趣。</p>
        
        <div class="quickActions">
          <div class="actionBox" data-action="quick-start-public-match" data-game-type="XIANGQI" style="cursor:pointer;">
            ⚡
            <span>实时匹配</span>
          </div>
          <div class="actionBox" data-action="create-room-xiangqi" style="cursor:pointer;">
            👥
            <span>好友约战</span>
          </div>
          <div class="actionBox" data-action="quick-start-ai-practice" style="cursor:pointer;">
            🤖
            <span>人机练习</span>
          </div>
          <div class="actionBox" data-nav="learn/puzzles/ALL" style="cursor:pointer;">
            📖
            <span>残局练习</span>
          </div>
        </div>
      </div>
      
      <div class="modeContentSplit">
        <div class="benefitList">
          <h4>你将获得</h4>
          <ul>
            <li><span>🛡️</span> 公开匹配与私密房，覆盖不同场景</li>
            <li><span>⚖️</span> 同棋种公开候场匹配，先到先得</li>
            <li><span>🧩</span> 残局题库与 AI 练习，提升棋力</li>
            <li><span>📈</span> 战绩与排行榜记录成长（非段位系统）</li>
          </ul>
        </div>
        <div class="modeOptions">
          <h4>选择模式</h4>
          <div class="optionCard active" data-action="quick-start-public-match" data-game-type="XIANGQI" data-time-seconds="600">
            <h5>标准匹配</h5>
            <span>10 分钟场，从容对弈</span>
          </div>
          <div class="optionCard" data-action="quick-start-public-match" data-game-type="XIANGQI" data-time-seconds="300">
            <h5>快棋匹配</h5>
            <span>5 分钟场，节奏明快</span>
          </div>
          <div class="optionCard" data-action="create-room-xiangqi">
            <h5>友谊对局</h5>
            <span>邀请好友，切磋对决</span>
          </div>
        </div>
      </div>
      
      <div class="modeBottomActions">
        <button class="btn-block btn-red" data-action="quick-start-public-match" data-game-type="XIANGQI">立即匹配</button>
      </div>
    </div>
  `;
}

function renderPlayGomoku() {
  return `
    <div class="modeSelectPage gomokuTheme">
      <div class="modeTopIllust bg-detail_gomoku">
        <div class="modeHeaderNav">
          <div class="btn-back-custom" data-nav="home">ㄑ</div>
          <div class="btn-star-custom">☆</div>
        </div>
        <div class="stampTitleWrap">
          <h1 class="stampTitle" style="color:var(--brand-green);">黑白 乾坤</h1>
          <span class="stampTextDecor" style="background:var(--brand-green);">五子棋模式</span>
        </div>
      </div>
      
      <div class="modeDetailCard">
        <h2>五子棋</h2>
        <p class="subtitle">轻松上手，节奏明快，随时开启一局黑白之间的思考较量。</p>
        
        <div class="statsGrid">
          <div class="statBox">
            <strong>5 分</strong>
            <span>默认匹配时长</span>
          </div>
          <div class="statBox">
            <strong>多级</strong>
            <span>AI 难度</span>
          </div>
          <div class="statBox">
            <strong>联机</strong>
            <span>房间对战</span>
          </div>
          <div class="statBox">
            <strong>复盘</strong>
            <span>局后分析</span>
          </div>
        </div>
      </div>
      
      <div>
        <h4>核心功能</h4>
        <div class="featuresGrid">
          <div class="featureCard" data-action="quick-start-public-match" data-game-type="GOMOKU" style="cursor:pointer;">
            <div class="cardIcon green">⚡</div>
            <div class="cardInfo">
              <h5>快速匹配</h5>
              <p>真人公开候场匹配</p>
            </div>
          </div>
          <div class="featureCard" data-action="quick-start-gomoku-practice" style="cursor:pointer;">
            <div class="cardIcon green">🤖</div>
            <div class="cardInfo">
              <h5>人机对战</h5>
              <p>挑战智能 AI 练习</p>
            </div>
          </div>
          <div class="featureCard" data-action="create-room-gomoku" style="cursor:pointer;">
            <div class="cardIcon green">👥</div>
            <div class="cardInfo">
              <h5>双人联机</h5>
              <p>好友随时对局</p>
            </div>
          </div>
          <div class="featureCard" data-nav="me/records" style="cursor:pointer;">
            <div class="cardIcon green">📜</div>
            <div class="cardInfo">
              <h5>复盘记录</h5>
              <p>对局复盘回顾</p>
            </div>
          </div>
        </div>
      </div>
      
      <div class="modeBottomActions">
        <button class="btn-block btn-green" data-action="quick-start-public-match" data-game-type="GOMOKU">立即匹配</button>
      </div>
    </div>
  `;
}

function renderBottomNav(activePage) {
  if (isBoardRoutePage(activePage) || activePage === 'welcome') {
    return '';
  }
  return renderMobileBottomNav(activePage);
}

function renderMobileBottomNav(activePage) {
  return `
    <nav class="mobileBottomNav" aria-label="主要导航">
      <a href="#/home" data-mobile-nav="home" class="${activePage === 'home' ? 'is-active' : ''}">${mobileIcon('home')}<span>首页</span></a>
      <a href="#/play" data-mobile-nav="play" class="${activePage === 'play' ? 'is-active' : ''}">${mobileIcon('play')}<span>对局</span></a>
      <a href="#/learn/puzzles/ALL" data-mobile-nav="learn" class="${activePage === 'learn' ? 'is-active' : ''}">${mobileIcon('learn')}<span>学习</span></a>
      <a href="#/watch" data-mobile-nav="watch" class="${activePage === 'watch' ? 'is-active' : ''}">${mobileIcon('watch')}<span>观战</span></a>
      <a href="#/me" data-mobile-nav="me" class="${activePage === 'me' ? 'is-active' : ''}">${mobileIcon('me')}<span>我的</span></a>
    </nav>
  `;
}



