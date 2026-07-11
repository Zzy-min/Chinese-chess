/**
 * Browser smoke for Online FE/BE wiring completeness (local PublicWebMain).
 * Usage: node smoke.js [outDir] [baseUrl]
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const outDir = process.argv[2] || __dirname;
const baseUrl = (process.argv[3] || 'http://127.0.0.1:18388').replace(/\/$/, '');
const online = `${baseUrl}/online`;
const PWD = 'SmokePass2026!';

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function parseSetCookie(setCookieHeader) {
  if (!setCookieHeader) return null;
  const first = Array.isArray(setCookieHeader) ? setCookieHeader[0] : String(setCookieHeader).split(/,(?=\s*[^;]+=)/)[0];
  const m = String(first).match(/^([^=]+)=([^;]+)/);
  if (!m) return null;
  return { name: m[1], value: m[2], url: baseUrl, path: '/' };
}

async function registerUser(request, username) {
  const res = await request.post(`${baseUrl}/online/api/auth/register`, {
    data: { username, password: PWD },
    failOnStatusCode: false
  });
  const body = await res.json().catch(() => null);
  const setCookie = res.headers()['set-cookie'];
  const cookie = parseSetCookie(setCookie);
  return { status: res.status(), body, cookie, setCookie };
}

async function withAuthedContext(browser, username) {
  const context = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    baseURL: baseUrl
  });
  const reg = await registerUser(context.request, username);
  // Playwright stores Set-Cookie from API into the context jar automatically.
  // Also force-add if header parsing works (some versions only expose via headers).
  if (reg.cookie) {
    await context.addCookies([reg.cookie]).catch(() => null);
  }
  if (reg.status !== 200) {
    await context.close();
    throw new Error(`register failed for ${username}: status=${reg.status} body=${JSON.stringify(reg.body)}`);
  }
  const cookies = await context.cookies(baseUrl);
  if (!cookies.some(c => c.name === 'XQ_AUTH')) {
    await context.close();
    throw new Error(`register ok but no XQ_AUTH cookie for ${username}; setCookie=${JSON.stringify(reg.setCookie)}`);
  }
  const page = await context.newPage();
  // Must open same-origin page before relative fetch() works in page.evaluate.
  await page.goto(`${online}#/home`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  return { context, page, reg, username };
}

(async () => {
  ensureDir(outDir);
  const report = {
    checkedAt: new Date().toISOString(),
    baseUrl,
    steps: {},
    ok: true,
    failures: [],
    screenshots: []
  };

  const mark = (name, data, pass = true) => {
    report.steps[name] = { pass, ...data };
    if (!pass) {
      report.ok = false;
      report.failures.push(name);
    }
  };

  const shot = async (page, name) => {
    const file = path.join(outDir, name);
    await page.screenshot({ path: file, fullPage: true });
    report.screenshots.push(name);
  };

  let browser;
  try {
    browser = await chromium.launch({
      headless: true,
      channel: process.env.PW_CHANNEL || 'chrome'
    });

    // ---------- 1) Guest: home + quick-match auth gate ----------
    {
      const context = await browser.newContext({ viewport: { width: 1366, height: 768 } });
      const page = await context.newPage();
      await page.goto(`${online}#/home`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await page.waitForSelector('[data-action="quick-start-public-match"]', { timeout: 20000 });
      await page.waitForTimeout(600);
      const homeText = await page.locator('body').innerText();
      const hasQuickMatch = await page.locator('[data-action="quick-start-public-match"]').count();
      const hasFakeLb = homeText.includes('棋圣无名') || homeText.includes('清风徐来');
      const hasFakeSignin = homeText.includes('已连续签到 3 天');
      await page.locator('[data-action="quick-start-public-match"]').first().click();
      await page.waitForTimeout(500);
      const authVisible = (await page.locator('.authOverlay').count()) > 0;
      await shot(page, '01-guest-home-auth.png');
      mark('guestQuickMatchAuthGate', {
        hasQuickMatch,
        authVisible,
        hasFakeLb,
        hasFakeSignin,
        navHasWatch: homeText.includes('观战')
      }, hasQuickMatch > 0 && authVisible && !hasFakeLb && !hasFakeSignin);
      await context.close();
    }

    // ---------- 2/3) User A quick match ----------
    const unameA = `smoke_a_${Date.now()}`;
    const a = await withAuthedContext(browser, unameA);
    {
      const me = await a.page.evaluate(async () => {
        const res = await fetch('/online/api/auth/me', { credentials: 'include' });
        return { status: res.status, body: await res.json().catch(() => null) };
      });
      mark('registerUserA', { username: unameA, me }, !!(me.status === 200 && me.body && me.body.id));

      await a.page.goto(`${online}#/home`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForSelector('[data-action="quick-start-public-match"]', { timeout: 20000 });
      await a.page.waitForTimeout(400);
      await a.page.locator('[data-action="quick-start-public-match"]').first().click();
      await a.page.waitForFunction(
        () => /#\/?(room|game)\//.test(location.hash),
        null,
        { timeout: 15000 }
      ).catch(() => null);
      await a.page.waitForTimeout(800);
      const hashA = await a.page.evaluate(() => location.hash);
      const bodyA = await a.page.locator('body').innerText();
      const roomCodeM = bodyA.match(/房间码\s*([A-Z0-9]+)/i);
      await shot(a.page, '02-userA-quick-match.png');
      mark('userAQuickMatch', {
        hash: hashA,
        isRoom: /#\/?room\//.test(hashA),
        isGame: /#\/?game\//.test(hashA),
        roomCode: roomCodeM ? roomCodeM[1] : '',
        statusText: bodyA.includes('候场') || bodyA.includes('等待') || bodyA.includes('准备') || bodyA.includes('匹配')
      }, /#\/?(room|game)\//.test(hashA));
    }

    // ---------- 4) User B quick match (should join A if still waiting) ----------
    const unameB = `smoke_b_${Date.now()}`;
    const b = await withAuthedContext(browser, unameB);
    {
      await b.page.goto(`${online}#/home`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await b.page.waitForSelector('[data-action="quick-start-public-match"]', { timeout: 20000 });
      await b.page.locator('[data-action="quick-start-public-match"]').first().click();
      await b.page.waitForFunction(
        () => /#\/?(room|game)\//.test(location.hash),
        null,
        { timeout: 15000 }
      ).catch(() => null);
      await b.page.waitForTimeout(1000);
      const hashB = await b.page.evaluate(() => location.hash);
      await shot(b.page, '03-userB-quick-match.png');
      mark('userBQuickMatch', {
        username: unameB,
        hash: hashB,
        isRoomOrGame: /#\/?(room|game)\//.test(hashB)
      }, /#\/?(room|game)\//.test(hashB));
    }

    // ---------- 5) Join by code ----------
    const unameC = `smoke_c_${Date.now()}`;
    const c = await withAuthedContext(browser, unameC);
    const unameD = `smoke_d_${Date.now()}`;
    const d = await withAuthedContext(browser, unameD);
    {
      const created = await c.page.evaluate(async () => {
        const res = await fetch('/online/api/rooms', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ gameType: 'XIANGQI', initialTimeSeconds: 600, isPublic: false })
        });
        return { status: res.status, body: await res.json().catch(() => null) };
      });
      const hostRoomCode = created.body && created.body.roomCode;

      await d.page.goto(`${online}#/play`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await d.page.waitForSelector('#joinCode', { timeout: 15000 });
      await d.page.fill('#joinCode', hostRoomCode || '');
      await d.page.locator('[data-action="join-by-code"]').first().click();
      await d.page.waitForFunction(
        () => /#\/?room\//.test(location.hash),
        null,
        { timeout: 12000 }
      ).catch(() => null);
      await d.page.waitForTimeout(600);
      const hashD = await d.page.evaluate(() => location.hash);
      await shot(d.page, '04-join-by-code.png');
      mark('joinByCode', {
        createdStatus: created.status,
        hostRoomCode,
        hash: hashD,
        joined: /#\/?room\//.test(hashD)
      }, created.status === 200 && !!hostRoomCode && /#\/?room\//.test(hashD));
    }

    // ---------- 6) Profile dashboard + preferences ----------
    {
      await a.page.goto(`${online}#/me`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(900);
      const meBody = await a.page.locator('body').innerText();
      const noFakeMedals = !meBody.includes('12枚') && !meBody.includes('ID: 10086');
      const hasSidebar = (await a.page.locator('.profileSidebarItem').count()) >= 5;
      await shot(a.page, '05-me-overview.png');

      await a.page.goto(`${online}#/me/records`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(500);
      const recordsText = await a.page.locator('body').innerText();
      await shot(a.page, '06-me-records.png');

      await a.page.goto(`${online}#/me/settings`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(500);
      const themeBtn = a.page.locator('[data-action="toggle-theme"]').first();
      let themeToggled = false;
      if (await themeBtn.count()) {
        const t1 = (await themeBtn.innerText()).trim();
        await themeBtn.click();
        await a.page.waitForTimeout(500);
        const t2 = (await themeBtn.innerText()).trim();
        themeToggled = t1 !== t2;
      }
      const prefs = await a.page.evaluate(async () => {
        const res = await fetch('/online/api/profile/preferences', { credentials: 'include' });
        return { status: res.status, body: await res.json().catch(() => null) };
      });
      await shot(a.page, '07-me-settings.png');
      mark('profileDashboard', {
        noFakeMedals,
        hasSidebar,
        recordsOk: recordsText.includes('对局记录') || recordsText.includes('暂无对局'),
        themeToggled,
        prefsStatus: prefs.status,
        boardTheme: prefs.body && prefs.body.boardTheme
      }, noFakeMedals && hasSidebar && themeToggled && prefs.status === 200);
    }

    // ---------- 7) Watch ----------
    {
      await a.page.goto(`${online}#/watch`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(800);
      const watchText = await a.page.locator('body').innerText();
      await shot(a.page, '08-watch.png');
      mark('watchPage', {
        loaded: watchText.includes('观战') || watchText.includes('公开'),
        labels: watchText.includes('实时观战') || watchText.includes('复盘分析') || watchText.includes('等待') || watchText.includes('暂无')
      }, watchText.includes('观战') || watchText.includes('公开'));
    }

    // ---------- 8) AI practice ----------
    {
      const practice = await a.page.evaluate(async () => {
        const res = await fetch('/online/api/learn/practice-games', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            gameType: 'XIANGQI',
            difficulty: 'MEDIUM',
            humanFirst: true,
            preferredEngine: 'BUILTIN'
          })
        });
        return { status: res.status, body: await res.json().catch(() => null) };
      });
      if (practice.status === 200 && practice.body && practice.body.gameId) {
        await a.page.goto(`${online}#/practice/${practice.body.gameId}`, {
          waitUntil: 'domcontentloaded',
          timeout: 60000
        });
        await a.page.waitForSelector('.xiangqiBoard, .gomokuBoard', { timeout: 15000 });
        const hasBoard = (await a.page.locator('.xiangqiBoard, .gomokuBoard').count()) > 0;
        await shot(a.page, '09-practice.png');
        mark('aiPractice', { gameId: practice.body.gameId, hasBoard }, hasBoard);
      } else {
        mark('aiPractice', { practice }, false);
      }
    }

    // ---------- 9) Tutorial detail ----------
    {
      await a.page.goto(`${online}#/learn/puzzles/ALL`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(800);
      const featured = a.page.locator('[data-learn-filter="featured"]').first();
      if (await featured.count()) {
        await featured.click();
        await a.page.waitForTimeout(400);
      }
      const detailBtn = a.page.locator('[data-action="view-tutorial-detail"]').first();
      let tutorialExpanded = false;
      const hasDetailBtn = (await detailBtn.count()) > 0;
      if (hasDetailBtn) {
        await detailBtn.click();
        await a.page.waitForTimeout(400);
        tutorialExpanded = (await a.page.locator('.learnTutorialDetail').count()) > 0
          || (await a.page.locator('body').innerText()).includes('目标');
      }
      await shot(a.page, '10-learn-tutorial.png');
      mark('tutorialDetail', { hasDetailBtn, tutorialExpanded }, hasDetailBtn ? tutorialExpanded : true);
    }

    // ---------- 10) Community ----------
    {
      await a.page.goto(`${online}#/community`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(600);
      const communityText = await a.page.locator('body').innerText();
      await shot(a.page, '11-community.png');
      mark('community', {
        hasTabs: communityText.includes('象棋') || communityText.includes('五子'),
        noFake: !communityText.includes('棋圣无名')
      }, !communityText.includes('棋圣无名'));
    }

    // ---------- 11) Asset cache bust ----------
    {
      const assetVersion = await a.page.evaluate(() =>
        Array.from(document.querySelectorAll('script[src*="app.js"]')).map(s => s.getAttribute('src'))
      );
      mark('assetCacheBust', { assetVersion }, assetVersion.some(s => s && s.includes('20260711')));
    }

    // ---------- 12) AI entry text honesty on home ----------
    {
      await a.page.goto(`${online}#/home`, { waitUntil: 'domcontentloaded', timeout: 60000 });
      await a.page.waitForTimeout(400);
      const text = await a.page.locator('body').innerText();
      const matchIsNotAi = text.includes('真人匹配') || text.includes('快速匹配');
      const hasAiEntry = text.includes('人机练习') || text.includes('AI');
      mark('copyHonesty', { matchIsNotAi, hasAiEntry }, matchIsNotAi && hasAiEntry);
    }

    await a.context.close();
    await b.context.close();
    await c.context.close();
    await d.context.close();
  } catch (err) {
    report.ok = false;
    report.error = String(err && err.stack || err);
  } finally {
    if (browser) await browser.close();
  }

  const summaryPath = path.join(outDir, 'verification-summary.json');
  fs.writeFileSync(summaryPath, JSON.stringify(report, null, 2), 'utf8');
  console.log(JSON.stringify({
    ok: report.ok,
    failures: report.failures,
    summaryPath,
    steps: Object.fromEntries(Object.entries(report.steps).map(([k, v]) => [k, v.pass])),
    error: report.error || null
  }, null, 2));
  process.exit(report.ok ? 0 : 1);
})();
