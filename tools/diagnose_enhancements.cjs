const { chromium } = require('C:/Users/Lenovo/node_modules/playwright-chromium');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://127.0.0.1:18388';
const CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const SHOTS_DIR = path.join(__dirname, '..', 'artifacts', 'enhancement-shots');

if (!fs.existsSync(SHOTS_DIR)) {
  fs.mkdirSync(SHOTS_DIR, { recursive: true });
}

async function runEnhancementDiagnosis() {
  const browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true
  });

  const results = {
    consoleErrors: [],
    failedRequests: [],
    buttonMetrics: {},
    imagesInspected: {}
  };

  const viewports = [
    { name: 'desktop-1440', width: 1440, height: 900 },
    { name: 'tablet-768', width: 768, height: 1024 },
    { name: 'mobile-375', width: 375, height: 812 }
  ];

  const routes = [
    '#/learn/puzzles/ALL',
    '#/learn/puzzles/ENDGAME_FEN',
    '#/learn',
    '#/play/gomoku',
    '#/home'
  ];

  for (const vp of viewports) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 2 // test high-DPI Retina clarity
    });
    const page = await context.newPage();

    page.on('console', msg => {
      if (msg.type() === 'error') {
        results.consoleErrors.push(`[${vp.name}][${page.url()}] ${msg.text()}`);
      }
    });

    page.on('requestfailed', req => {
      results.failedRequests.push(`[${vp.name}] ${req.method()} ${req.url()}: ${req.failure()?.errorText}`);
    });

    for (const route of routes) {
      const url = `${BASE_URL}/online${route}`;
      await page.goto(url, { waitUntil: 'networkidle' });
      await page.waitForTimeout(500);

      // Measure CTA button if on learn page
      if (route.includes('learn')) {
        const ctaMetrics = await page.evaluate(() => {
          const btn = document.querySelector('.learnEndgameCta');
          if (!btn) return null;
          const rect = btn.getBoundingClientRect();
          const svg = btn.querySelector('.mobileIcon');
          const svgRect = svg ? svg.getBoundingClientRect() : null;
          const cs = window.getComputedStyle(btn);
          return {
            text: btn.innerText.trim(),
            width: rect.width,
            height: rect.height,
            padding: cs.padding,
            borderRadius: cs.borderRadius,
            background: cs.backgroundImage || cs.background,
            svg: svgRect ? { width: svgRect.width, height: svgRect.height } : null
          };
        });
        results.buttonMetrics[`${vp.name}_${route}`] = ctaMetrics;
      }

      // Check Gomoku image rendering if on home or play/gomoku
      if (route === '#/home' || route === '#/play/gomoku') {
        const imgMetrics = await page.evaluate((r) => {
          const el = r === '#/home' 
            ? document.querySelector('.deskHeroIllustRight') || document.querySelector('.deskModePanelBg')
            : document.querySelector('.modeTopIllust');
          if (!el) return null;
          const cs = window.getComputedStyle(el);
          return {
            bgImage: cs.backgroundImage ? cs.backgroundImage.substring(0, 60) + '...' : null,
            bgSize: cs.backgroundSize,
            imageRendering: cs.imageRendering,
            rect: el.getBoundingClientRect()
          };
        }, route);
        results.imagesInspected[`${vp.name}_${route}`] = imgMetrics;
      }

      const shotName = `${vp.name}_${route.replace(/[\/#]/g, '_')}.png`;
      await page.screenshot({ path: path.join(SHOTS_DIR, shotName) });
    }

    await context.close();
  }

  await browser.close();
  console.log(JSON.stringify(results, null, 2));
}

runEnhancementDiagnosis().catch(err => {
  console.error(err);
  process.exit(1);
});
