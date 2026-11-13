const { chromium } = require('C:/Users/Lenovo/node_modules/playwright-chromium');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://127.0.0.1:18388';
const CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const SHOTS_DIR = path.join(__dirname, '..', 'artifacts', 'me-diagnosis-shots');

if (!fs.existsSync(SHOTS_DIR)) {
  fs.mkdirSync(SHOTS_DIR, { recursive: true });
}

async function runMeDiagnosis() {
  const browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true
  });

  const report = {
    consoleErrors: [],
    failedRequests: [],
    checks: []
  };

  const viewports = [
    { name: 'desktop-1440', width: 1440, height: 900 },
    { name: 'tablet-768', width: 768, height: 1024 },
    { name: 'mobile-375', width: 375, height: 812 },
    { name: 'mobile-320', width: 320, height: 568 }
  ];

  // --- PART 1: Guest State (Not Logged In) ---
  console.log('=== Checking Guest State ===');
  for (const vp of viewports) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 2
    });
    const page = await context.newPage();

    page.on('console', msg => {
      if (msg.type() === 'error') {
        report.consoleErrors.push(`[Guest][${vp.name}] ${msg.text()}`);
      }
    });

    await page.goto(`${BASE_URL}/online#/me`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);

    const shotPath = path.join(SHOTS_DIR, `guest-${vp.name}.png`);
    await page.screenshot({ path: shotPath, fullPage: true });

    const metrics = await page.evaluate(() => {
      const el = document.querySelector('.placeholder') || document.querySelector('.profilePage') || document.querySelector('#app');
      const bodyWidth = document.body.scrollWidth;
      const windowWidth = window.innerWidth;
      const hasHorizontalOverflow = bodyWidth > windowWidth;
      return {
        html: el ? el.outerHTML.slice(0, 300) : 'null',
        hasHorizontalOverflow,
        bodyWidth,
        windowWidth
      };
    });

    report.checks.push({
      mode: 'guest',
      viewport: vp.name,
      screenshot: shotPath,
      metrics
    });
    await context.close();
  }

  // --- PART 2: Logged-in State ---
  console.log('=== Checking Logged-in State ===');
  const userContext = await browser.newContext();
  const regPage = await userContext.newPage();
  const username = `me_user_${Date.now()}`;
  await regPage.goto(`${BASE_URL}/online#/home`, { waitUntil: 'networkidle' });
  const authRes = await regPage.evaluate(async (u) => {
    const res = await fetch('/online/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: u, password: 'Password123!' })
    });
    return { status: res.status, data: await res.json() };
  }, username);
  console.log('User registered:', username, authRes.status);
  const cookies = await userContext.cookies();
  await userContext.close();

  const tabsToCheck = ['overview', 'records', 'study', 'achievements', 'inbox', 'settings', 'help'];

  for (const vp of viewports) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 2
    });
    await context.addCookies(cookies);
    const page = await context.newPage();

    page.on('console', msg => {
      if (msg.type() === 'error') {
        report.consoleErrors.push(`[Auth][${vp.name}] ${msg.text()}`);
      }
    });

    for (const tab of tabsToCheck) {
      const route = tab === 'overview' ? '#/me' : `#/me/${tab}`;
      await page.goto(`${BASE_URL}/online${route}`, { waitUntil: 'networkidle' });
      await page.waitForTimeout(600);

      const shotPath = path.join(SHOTS_DIR, `auth-${tab}-${vp.name}.png`);
      await page.screenshot({ path: shotPath, fullPage: true });

      const metrics = await page.evaluate((tabName) => {
        const bodyWidth = document.body.scrollWidth;
        const windowWidth = window.innerWidth;
        const hasHorizontalOverflow = bodyWidth > windowWidth;
        const profilePage = document.querySelector('.profilePage');
        const sidebar = document.querySelector('.profileSidebar');
        const main = document.querySelector('.profileMain');
        const headerCard = document.querySelector('.profileHeaderCard');
        const statsGrid = document.querySelector('.profileStatsGrid');

        return {
          tab: tabName,
          hasHorizontalOverflow,
          bodyWidth,
          windowWidth,
          profilePageRect: profilePage ? profilePage.getBoundingClientRect() : null,
          sidebarRect: sidebar ? sidebar.getBoundingClientRect() : null,
          mainRect: main ? main.getBoundingClientRect() : null,
          headerCardRect: headerCard ? headerCard.getBoundingClientRect() : null,
          statsGridRect: statsGrid ? statsGrid.getBoundingClientRect() : null
        };
      }, tab);

      report.checks.push({
        mode: 'authenticated',
        tab,
        viewport: vp.name,
        screenshot: shotPath,
        metrics
      });
    }
    await context.close();
  }

  await browser.close();

  fs.writeFileSync(
    path.join(SHOTS_DIR, 'report.json'),
    JSON.stringify(report, null, 2),
    'utf-8'
  );
  console.log('Diagnosis complete! Report saved to', path.join(SHOTS_DIR, 'report.json'));
}

runMeDiagnosis().catch(err => {
  console.error('Diagnosis failed:', err);
  process.exit(1);
});
