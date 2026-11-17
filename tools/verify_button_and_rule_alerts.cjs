const { chromium } = require('C:/Users/Lenovo/node_modules/playwright-chromium');

const BASE_URL = process.env.TEST_BASE_URL || 'http://127.0.0.1:18499';
const CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe';

function assert(cond, msg) {
  if (!cond) {
    throw new Error(`ASSERTION FAILED: ${msg}`);
  }
}

async function waitForAiDone(page) {
  await page.waitForFunction(() => {
    const cells = document.querySelectorAll('.xiangqiCell, .gomokuCell');
    return cells.length > 0 && Array.from(cells).some(c => !c.disabled);
  }, { timeout: 15000 });
}

async function main() {
  const browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true,
  });

  const context = await browser.newContext({ viewport: { width: 1366, height: 860 } });
  const page = await context.newPage();

  const consoleErrors = [];
  const pageErrors = [];
  const serverErrors = [];

  page.on('console', msg => {
    if (msg.type() === 'error') {
      const txt = msg.text();
      if (!txt.includes('status of 400 (Bad Request)')) {
        consoleErrors.push(txt);
      }
    }
  });
  page.on('pageerror', err => pageErrors.push(String(err)));
  page.on('response', res => {
    if (res.status() >= 500) {
      serverErrors.push(`${res.status()} ${res.url()}`);
    }
  });

  // 1. Open home & register a fresh test user
  await page.goto(`${BASE_URL}/online/#/home`, { waitUntil: 'networkidle' });

  // Verify button tactile styles on landing/home
  const btnStyles = await page.evaluate(() => {
    const primary = document.querySelector('.btn');
    const ghost = document.querySelector('.ghost');
    return {
      primaryBg: primary ? getComputedStyle(primary).backgroundImage : '',
      primaryShadow: primary ? getComputedStyle(primary).boxShadow : '',
      ghostBg: ghost ? getComputedStyle(ghost).backgroundImage : '',
      ghostShadow: ghost ? getComputedStyle(ghost).boxShadow : '',
    };
  });
  console.log('Button styles:', btnStyles);
  assert(btnStyles.primaryBg.includes('linear-gradient'), `Primary .btn should have gradient, got: ${btnStyles.primaryBg}`);
  assert(btnStyles.primaryShadow !== 'none', 'Primary .btn should have box-shadow');

  // Register via API in page context so auth cookie is set cleanly
  const username = `e2e_${Date.now().toString().slice(-6)}`;
  const regRes = await page.evaluate(async (u) => {
    const r = await fetch('/online/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: u, password: 'Password123!' }),
    });
    const text = await r.text();
    return { status: r.status, text };
  }, username);
  assert(regRes.status === 200 || regRes.status === 201, `Register failed: ${JSON.stringify(regRes)}`);
  await page.reload({ waitUntil: 'networkidle' });

  // 2. Test Xiangqi 蹩马腿 & 将军
  console.log('Register:', regRes);
  const xqCreate = await page.evaluate(async () => {
    const r = await fetch('/online/api/learn/practice-games', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        gameType: 'XIANGQI',
        difficulty: 'EASY',
        humanFirst: true,
        initialFen: '4k4/9/9/9/9/9/9/9/R8/1NB1K4 w - - 0 1'
      }),
    });
    const text = await r.text();
    return { status: r.status, text, body: text ? JSON.parse(text) : null };
  });
  assert(xqCreate.status === 200 || xqCreate.status === 201, `Create Xiangqi practice failed: ${JSON.stringify(xqCreate)}`);
  const xqGameId = xqCreate.body.gameId;

  await page.goto(`${BASE_URL}/online/#/practice/${xqGameId}`, { waitUntil: 'networkidle' });
  await waitForAiDone(page);

  // Trigger 蹩马腿: Red Horse at (9,1) -> (8,3), blocked by Red Elephant at (9,2)
  console.log('Cell (9,1) before click:', await page.evaluate(() => ({
    text: document.querySelector('.xiangqiCell[data-row="9"][data-col="1"]')?.textContent?.trim(),
    cls: document.querySelector('.xiangqiCell[data-row="9"][data-col="1"]')?.className,
    status: document.querySelector('[data-live-status]')?.textContent?.trim(),
  })));
  await page.click('.xiangqiCell[data-row="9"][data-col="1"]');
  console.log('Cell (9,1) after 1st click:', await page.evaluate(() => ({
    cls: document.querySelector('.xiangqiCell[data-row="9"][data-col="1"]')?.className,
    status: document.querySelector('[data-live-status]')?.textContent?.trim(),
  })));
  await page.click('.xiangqiCell[data-row="8"][data-col="3"]');
  await page.waitForTimeout(500);
  console.log('After 2nd click:', await page.evaluate(() => ({
    status: document.querySelector('[data-live-status]')?.textContent?.trim(),
    ruleAlert: document.querySelector('[data-live-rule-alert]')?.innerHTML,
  })));

  await page.waitForSelector('.ruleAlertBanner .ruleAlertBadge', { timeout: 5000 });
  const horseAlert = await page.evaluate(() => ({
    badge: document.querySelector('.ruleAlertBanner .ruleAlertBadge')?.textContent?.trim(),
    text: document.querySelector('.ruleAlertBanner .ruleAlertText')?.textContent?.trim(),
    hasBlocker: !!document.querySelector('.xiangqiCell.is-rule-blocker[data-row="9"][data-col="2"]'),
    hasBlocked: !!document.querySelector('.xiangqiCell.is-rule-blocked[data-row="8"][data-col="3"]'),
  }));
  console.log('Xiangqi 蹩马腿 alert:', horseAlert);
  assert(horseAlert.badge === '蹩马腿', `Expected 蹩马腿 badge, got: ${horseAlert.badge}`);
  assert(horseAlert.hasBlocker, 'Expected blocker cell (9,2) to have .is-rule-blocker');
  assert(horseAlert.hasBlocked, 'Expected target cell (8,3) to have .is-rule-blocked');

  // Dismiss rule alert
  await page.click('[data-action="dismiss-rule-alert"]');
  await page.waitForFunction(() => !document.querySelector('.ruleAlertBanner'), { timeout: 3000 });

  // Trigger 将军 (Check) by moving Red Rook (8,0) -> (8,4) and verify /move response has inCheckSide & checkTactic
  const checkMovePromise = page.waitForResponse(res => res.url().includes(`/practice-games/${xqGameId}/move`) && res.status() === 200);
  await page.click('.xiangqiCell[data-row="8"][data-col="0"]');
  await page.click('.xiangqiCell[data-row="8"][data-col="4"]');
  const checkMoveRes = await (await checkMovePromise).json();
  console.log('Xiangqi 将军 /move response:', { inCheckSide: checkMoveRes.inCheckSide, checkTactic: checkMoveRes.checkTactic });
  assert(checkMoveRes.inCheckSide === 'BLACK', `Expected inCheckSide BLACK, got ${checkMoveRes.inCheckSide}`);
  assert(!!checkMoveRes.checkTactic, `Expected non-empty checkTactic, got ${checkMoveRes.checkTactic}`);

  // Also test live persistent 将军 banner & 必须应将 when human is in check
  const xqInCheckCreate = await page.evaluate(async () => {
    const r = await fetch('/online/api/learn/practice-games', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        gameType: 'XIANGQI',
        difficulty: 'EASY',
        humanFirst: true,
        initialFen: '4k4/9/9/9/9/9/P8/9/4r4/4K4 w - - 0 1'
      }),
    });
    const text = await r.text();
    return { status: r.status, body: text ? JSON.parse(text) : null };
  });
  assert(xqInCheckCreate.status === 200 || xqInCheckCreate.status === 201, 'Create in-check game failed');
  await page.goto(`${BASE_URL}/online/#/practice/${xqInCheckCreate.body.gameId}`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.ruleAlertBanner.is-check .ruleAlertBadge', { timeout: 5000 });
  const checkAlert = await page.evaluate(() => ({
    badge: document.querySelector('.ruleAlertBanner.is-check .ruleAlertBadge')?.textContent?.trim(),
    text: document.querySelector('.ruleAlertBanner.is-check .ruleAlertText')?.textContent?.trim(),
    kingInCheck: !!document.querySelector('.xiangqiCell.is-in-check'),
  }));
  console.log('Xiangqi 贴身车/将军 persistent alert:', checkAlert);
  assert(!!checkAlert.badge, 'Expected non-empty check tactic badge');
  assert(checkAlert.kingInCheck, 'Expected Red King to have .is-in-check');

  // Try moving Red Pawn (6,0) -> (5,0) without resolving check -> triggers 必须应将
  await page.click('.xiangqiCell[data-row="6"][data-col="0"]');
  await page.click('.xiangqiCell[data-row="5"][data-col="0"]');
  await page.waitForSelector('.ruleAlertBanner .ruleAlertBadge', { timeout: 5000 });
  const mustRespondBadge = await page.textContent('.ruleAlertBanner .ruleAlertBadge');
  console.log('Xiangqi 必须应将 badge:', mustRespondBadge.trim());
  assert(mustRespondBadge.trim() === '必须应将', `Expected 必须应将, got ${mustRespondBadge}`);

  // 3. Test Gomoku 三三禁手 deterministically in a 2-player online room
  const guestContext = await browser.newContext({ viewport: { width: 1366, height: 860 } });
  const guestPage = await guestContext.newPage();
  await guestPage.goto(`${BASE_URL}/online/#/home`, { waitUntil: 'networkidle' });
  const guestUser = `e2e_w_${Date.now().toString().slice(-5)}`;
  await guestPage.evaluate(async (u) => {
    await fetch('/online/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: u, password: 'Password123!' }),
    });
  }, guestUser);

  // Host creates a Gomoku room & readies
  const roomCreate = await page.evaluate(async () => {
    const r = await fetch('/online/api/rooms', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameType: 'GOMOKU', initialTimeSeconds: 600, publicRoom: false }),
    });
    return r.json();
  });
  const roomId = roomCreate.roomId;
  await page.evaluate(async (id) => {
    await fetch(`/online/api/rooms/${id}/ready`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ready: true }) });
  }, roomId);

  // Guest joins & readies -> starts game
  const startedRoom = await guestPage.evaluate(async (id) => {
    await fetch(`/online/api/rooms/${id}/join`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    const r = await fetch(`/online/api/rooms/${id}/ready`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ready: true }) });
    return r.json();
  }, roomId);
  const gmGameId = startedRoom.gameId;
  assert(!!gmGameId, `Expected started Gomoku gameId, got ${JSON.stringify(startedRoom)}`);

  await page.goto(`${BASE_URL}/online/#/game/${gmGameId}`, { waitUntil: 'networkidle' });
  await guestPage.goto(`${BASE_URL}/online/#/game/${gmGameId}`, { waitUntil: 'networkidle' });

  // Black plays (7,7), (7,8), (8,6), (9,6); White plays (0,0), (0,1), (0,2), (0,3)
  const blackSetup = [[7, 7], [7, 8], [8, 6], [9, 6]];
  const whiteSetup = [[0, 0], [0, 1], [0, 2], [0, 3]];
  for (let i = 0; i < blackSetup.length; i++) {
    const [br, bc] = blackSetup[i];
    const [wr, wc] = whiteSetup[i];
    await waitForAiDone(page);
    await page.click(`.gomokuCell[data-row="${br}"][data-col="${bc}"]`);
    await page.waitForFunction(([r, c]) => document.querySelector(`.gomokuCell[data-row="${r}"][data-col="${c}"]`)?.classList.contains('is-black'), [br, bc]);
    await guestPage.evaluate(async ([gid, r, c]) => {
      await fetch(`/online/api/games/${gid}/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ row: r, col: c }),
      });
    }, [gmGameId, wr, wc]);
    await page.waitForFunction(([r, c]) => document.querySelector(`.gomokuCell[data-row="${r}"][data-col="${c}"]`)?.classList.contains('is-white'), [wr, wc]);
  }
  await waitForAiDone(page);

  // Now Black clicks (7, 6) -> triggers 三三禁手
  await page.click('.gomokuCell[data-row="7"][data-col="6"]');
  await page.waitForSelector('.ruleAlertBanner.is-forbidden .ruleAlertBadge', { timeout: 5000 });
  const gomokuAlert = await page.evaluate(() => ({
    badge: document.querySelector('.ruleAlertBanner .ruleAlertBadge')?.textContent?.trim(),
    text: document.querySelector('.ruleAlertBanner .ruleAlertText')?.textContent?.trim(),
    hasBlocked: !!document.querySelector('.gomokuCell.is-rule-blocked[data-row="7"][data-col="6"]'),
  }));
  console.log('Gomoku 三三禁手 alert:', gomokuAlert);
  assert(gomokuAlert.badge === '三三禁手', `Expected 三三禁手 badge, got: ${gomokuAlert.badge}`);
  assert(gomokuAlert.hasBlocked, 'Expected cell (7,6) to have .is-rule-blocked');
  await guestContext.close();

  assert(consoleErrors.length === 0, `Console errors: ${consoleErrors.join('; ')}`);
  assert(pageErrors.length === 0, `Page errors: ${pageErrors.join('; ')}`);
  assert(serverErrors.length === 0, `5xx errors: ${serverErrors.join('; ')}`);

  console.log('ALL PLAYWRIGHT E2E ASSERTIONS PASSED!');
  await browser.close();
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
