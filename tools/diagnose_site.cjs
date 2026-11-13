const { chromium } = require('C:/Users/Lenovo/node_modules/playwright-chromium');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://127.0.0.1:18388';
const CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const SHOTS_DIR = path.join(__dirname, '..', 'artifacts', 'diagnosis-shots');

if (!fs.existsSync(SHOTS_DIR)) {
  fs.mkdirSync(SHOTS_DIR, { recursive: true });
}

async function runDiagnosis() {
  const browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true
  });

  const report = {
    consoleErrors: [],
    consoleWarnings: [],
    failedRequests: [],
    overflows: [],
    pageMetrics: {},
    gameFlows: {}
  };

  const viewports = [
    { name: 'desktop-1440', width: 1440, height: 900 },
    { name: 'tablet-768', width: 768, height: 1024 },
    { name: 'mobile-375', width: 375, height: 812 },
    { name: 'mobile-320', width: 320, height: 568 }
  ];

  for (const vp of viewports) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 1
    });
    const page = await context.newPage();

    page.on('console', msg => {
      const type = msg.type();
      const text = msg.text();
      if (type === 'error') {
        report.consoleErrors.push(`[${vp.name}][${page.url()}] ${text}`);
      } else if (type === 'warning') {
        report.consoleWarnings.push(`[${vp.name}][${page.url()}] ${text}`);
      }
    });

    page.on('requestfailed', req => {
      report.failedRequests.push(`[${vp.name}] ${req.method()} ${req.url()}: ${req.failure()?.errorText}`);
    });

    // Test routes: home, play, learn, watch, me, community
    const routes = ['#/home', '#/play', '#/learn/puzzles/ALL', '#/watch', '#/me', '#/community'];
    for (const route of routes) {
      const url = `${BASE_URL}/online${route}`;
      await page.goto(url, { waitUntil: 'networkidle' });
      await page.waitForTimeout(400);

      // Check overflow
      const overflow = await page.evaluate(() => {
        const docEl = document.documentElement;
        const body = document.body;
        return {
          scrollWidth: Math.max(docEl.scrollWidth, body.scrollWidth),
          clientWidth: docEl.clientWidth,
          hasHorizontalOverflow: Math.max(docEl.scrollWidth, body.scrollWidth) > docEl.clientWidth + 1
        };
      });

      if (overflow.hasHorizontalOverflow) {
        report.overflows.push({
          viewport: vp.name,
          route,
          scrollWidth: overflow.scrollWidth,
          clientWidth: overflow.clientWidth
        });
      }

      const shotName = `${vp.name}_${route.replace(/[\/#]/g, '_')}.png`;
      await page.screenshot({ path: path.join(SHOTS_DIR, shotName) });
    }

    await context.close();
  }

  // Interactive Game Flow test on desktop and mobile
  const testContext = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });
  const page = await testContext.newPage();
  page.on('console', msg => {
    if (msg.type() === 'error') report.consoleErrors.push(`[interactive][${page.url()}] ${msg.text()}`);
  });

  try {
    // 1. Authenticate user for practice flow
    await page.goto(`${BASE_URL}/online#/home`, { waitUntil: 'networkidle' });
    const diagUser = `diag_${Date.now()}`;
    await page.evaluate(async (u) => {
      await fetch('/online/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: u, password: 'Password123!' })
      });
    }, diagUser);
    await page.reload({ waitUntil: 'networkidle' });

    // 2. Visit Xiangqi Practice
    console.log('Testing Xiangqi Practice Flow...');
    const xqGame = await page.evaluate(async () => {
      const res = await fetch('/online/api/learn/practice-games', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ gameType: 'XIANGQI', difficulty: 'EASY', humanFirst: true, preferredEngine: 'BUILTIN' })
      });
      return await res.json();
    });

    await page.goto(`${BASE_URL}/online#/practice/${xqGame.gameId}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(SHOTS_DIR, 'flow_xiangqi_init.png') });

    // Check if board rendered
    const hasXiangqiBoard = await page.$('.xiangqiBoard, [data-board="xiangqi"]');
    report.gameFlows.xiangqiBoardRendered = !!hasXiangqiBoard;

    // Check piece elements
    const piecesCount = await page.$$eval('.piece', els => els.length);
    report.gameFlows.xiangqiPiecesCount = piecesCount;
    console.log('Found xiangqi pieces:', piecesCount);

    // 3. Visit Gomoku Practice
    console.log('Testing Gomoku Practice Flow...');
    const gmGame = await page.evaluate(async () => {
      const res = await fetch('/online/api/learn/practice-games', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ gameType: 'GOMOKU', difficulty: 'EASY', humanFirst: true, preferredEngine: 'BUILTIN' })
      });
      return await res.json();
    });

    await page.goto(`${BASE_URL}/online#/practice/${gmGame.gameId}`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(SHOTS_DIR, 'flow_gomoku_init.png') });

    const hasGomokuBoard = await page.$('.gomokuBoard, [data-board="gomoku"]');
    report.gameFlows.gomokuBoardRendered = !!hasGomokuBoard;

    // 3. Check Performance Metrics on desktop
    const perfTiming = await page.evaluate(() => {
      const nav = performance.getEntriesByType('navigation')[0];
      const paint = performance.getEntriesByType('paint');
      return {
        domInteractive: nav ? nav.domInteractive : null,
        domComplete: nav ? nav.domComplete : null,
        loadEventEnd: nav ? nav.loadEventEnd : null,
        fcp: paint.find(p => p.name === 'first-contentful-paint')?.startTime || null
      };
    });
    report.pageMetrics = perfTiming;

  } catch (err) {
    report.gameFlows.error = err.message;
  }

  await browser.close();

  fs.writeFileSync(path.join(SHOTS_DIR, 'diagnosis_report.json'), JSON.stringify(report, null, 2), 'utf-8');
  console.log('Diagnosis completed! Report:', JSON.stringify(report, null, 2));
}

runDiagnosis().catch(err => {
  console.error('Diagnosis failed:', err);
  process.exit(1);
});
