const { chromium } = require('C:/Users/Lenovo/node_modules/playwright-chromium');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://127.0.0.1:18388';
const CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const SHOTS_DIR = path.join(__dirname, '..', 'artifacts', 'game-flow-shots');

if (!fs.existsSync(SHOTS_DIR)) {
  fs.mkdirSync(SHOTS_DIR, { recursive: true });
}

async function testGameFlows() {
  const browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true
  });

  const testResults = {
    auth: false,
    xiangqi: {},
    gomoku: {},
    errors: []
  };

  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });
  const page = await context.newPage();

  page.on('console', msg => {
    if (msg.type() === 'error') {
      const loc = msg.location();
      testResults.errors.push(`Console error: ${msg.text()} at ${loc.url}:${loc.lineNumber}`);
    }
  });
  page.on('requestfailed', req => {
    testResults.errors.push(`Failed request: ${req.url().slice(0, 100)} - ${req.failure() ? req.failure().errorText : ''}`);
  });

  try {
    // Step 1: Open site
    await page.goto(`${BASE_URL}/online#/home`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);

    // Register / Login
    const username = `u_test_${Date.now()}`;
    const password = 'Password123!';

    // Call API directly from page to ensure cookie is set
    const authRes = await page.evaluate(async ({ u, p }) => {
      const res = await fetch('/online/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: u, password: p })
      });
      return { status: res.status, data: await res.json() };
    }, { u: username, p: password });

    console.log('Auth register result:', authRes.status, authRes.data);
    testResults.auth = authRes.status === 200;

    // Reload page to let state.me be loaded
    await page.reload({ waitUntil: 'networkidle' });
    await page.waitForTimeout(600);

    // Step 2: Xiangqi Practice
    console.log('Navigating to Xiangqi practice...');
    // Create practice game via evaluate
    const xqGame = await page.evaluate(async () => {
      const res = await fetch('/online/api/learn/practice-games', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          gameType: 'XIANGQI',
          difficulty: 'EASY',
          humanFirst: true,
          preferredEngine: 'BUILTIN'
        })
      });
      return await res.json();
    });

    console.log('Created Xiangqi game:', xqGame.gameId);
    testResults.xiangqi.created = !!xqGame.gameId;

    // Navigate to practice page
    await page.goto(`${BASE_URL}/online#/practice/${xqGame.gameId}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SHOTS_DIR, 'xiangqi_01_loaded.png') });

    // Inspect board
    const xqCells = await page.$$eval('[data-board="xiangqi"]', els => els.length);
    const xqPieces = await page.$$eval('.piece', els => els.length);
    console.log(`Xiangqi cells: ${xqCells}, pieces: ${xqPieces}`);
    testResults.xiangqi.cells = xqCells;
    testResults.xiangqi.pieces = xqPieces;

    // Try making a move: Red cannon (row 7, col 1) to (row 7, col 4)
    // Click from cell (7, 1)
    const fromCell = await page.$('[data-board="xiangqi"][data-row="7"][data-col="1"]');
    if (fromCell) {
      await fromCell.click();
      await page.waitForTimeout(300);
      await page.screenshot({ path: path.join(SHOTS_DIR, 'xiangqi_02_selected.png') });

      // Click to cell (7, 4)
      const toCell = await page.$('[data-board="xiangqi"][data-row="7"][data-col="4"]');
      if (toCell) {
        await toCell.click();
        console.log('Moved Red Cannon 7,1 -> 7,4');
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(SHOTS_DIR, 'xiangqi_03_after_move.png') });

        // Wait for AI move (poll)
        await page.waitForTimeout(2000);
        await page.screenshot({ path: path.join(SHOTS_DIR, 'xiangqi_04_after_ai.png') });

        const movesCount = await page.$$eval('.moveRecordRow, .liveMoveItem, [data-live-moves] tr', els => els.length);
        console.log('Moves count in UI:', movesCount);
        testResults.xiangqi.movesCount = movesCount;
      }
    }

    // Step 3: Gomoku Practice
    console.log('Navigating to Gomoku practice...');
    const gomokuGame = await page.evaluate(async () => {
      const res = await fetch('/online/api/learn/practice-games', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          gameType: 'GOMOKU',
          difficulty: 'EASY',
          humanFirst: true,
          preferredEngine: 'BUILTIN'
        })
      });
      return await res.json();
    });

    console.log('Created Gomoku game:', gomokuGame.gameId);
    testResults.gomoku.created = !!gomokuGame.gameId;

    await page.goto(`${BASE_URL}/online#/practice/${gomokuGame.gameId}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SHOTS_DIR, 'gomoku_01_loaded.png') });

    const gomokuCells = await page.$$eval('[data-board="gomoku"]', els => els.length);
    console.log('Gomoku cells:', gomokuCells);
    testResults.gomoku.cells = gomokuCells;

    // Click center cell: row 7, col 7
    const centerCell = await page.$('[data-board="gomoku"][data-row="7"][data-col="7"]');
    if (centerCell) {
      await centerCell.click();
      console.log('Placed stone at 7,7');
      await page.waitForTimeout(500);
      await page.screenshot({ path: path.join(SHOTS_DIR, 'gomoku_02_after_move.png') });

      // Wait for AI move
      await page.waitForTimeout(2500);
      await page.screenshot({ path: path.join(SHOTS_DIR, 'gomoku_03_after_ai.png') });

      const stonesCount = await page.$$eval('.gomokuCell.is-black, .gomokuCell.is-white', els => els.length);
      console.log('Gomoku stones count in UI:', stonesCount);
      testResults.gomoku.stonesCount = stonesCount;
    }

  } catch (err) {
    console.error('Test error:', err);
    testResults.errors.push(err.message);
  }

  await browser.close();
  console.log('Test Game Flows finished:', JSON.stringify(testResults, null, 2));
}

testGameFlows().catch(console.error);
