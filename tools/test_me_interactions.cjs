const { chromium } = require('C:/Users/Lenovo/node_modules/playwright-chromium');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://127.0.0.1:18388';
const CHROME_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe';

async function testInteractions() {
  const browser = await chromium.launch({
    executablePath: CHROME_PATH,
    headless: true
  });

  console.log('--- TEST 1: Guest Page Actions ---');
  {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    await page.goto(`${BASE_URL}/online#/me`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);

    // Verify Guest elements exist
    const title = await page.textContent('.profileGuestTitles h2');
    console.log('Guest page title:', title);
    if (!title.includes('登堂入室')) throw new Error('Guest title mismatch');

    // Click "立即登录"
    await page.click('button[data-auth-mode="login"]');
    await page.waitForSelector('.authOverlay', { state: 'visible' });
    console.log('Auth modal opened successfully on login click');

    // Close auth modal
    await page.keyboard.press('Escape');
    await page.waitForTimeout(300);

    // Click "客卿试局"
    await page.click('button.profileGuestBtn--browse');
    await page.waitForTimeout(500);
    console.log('Navigated URL after browse click:', page.url());
    if (!page.url().includes('#play')) throw new Error('Navigation failed');

    await context.close();
  }

  console.log('--- TEST 2: Logged-in Desktop Interactions ---');
  {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();
    const u = `test_me_${Date.now()}`;
    await page.goto(`${BASE_URL}/online#/home`, { waitUntil: 'networkidle' });
    await page.evaluate(async (username) => {
      await fetch('/online/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password: 'Password123!' })
      });
    }, u);
    await page.reload({ waitUntil: 'networkidle' });

    // Navigate to /me
    await page.goto(`${BASE_URL}/online#/me`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);

    // Verify user card
    const user = await page.textContent('.profileUserMeta strong');
    console.log('Logged-in user card username:', user);
    if (user !== u) throw new Error('Username mismatch');

    // Test "编辑资料" button
    await page.click('.profileEditBtn');
    await page.waitForTimeout(500);
    console.log('URL after edit btn click:', page.url());
    if (!page.url().includes('me/settings')) throw new Error('Edit button nav failed');

    // Test settings toggles
    const soundBtn = page.locator('.settingRow button[data-action="toggle-sound"]');
    const initSound = await soundBtn.textContent();
    await soundBtn.click();
    await page.waitForTimeout(300);
    const toggledSound = await soundBtn.textContent();
    console.log('Sound toggle:', initSound, '->', toggledSound);
    if (initSound === toggledSound) throw new Error('Sound toggle failed');

    // Test mobile version tap
    await page.click('button[data-action="mobile-version-tap"]');
    await page.waitForTimeout(300);
    const toast = await page.locator('.toastHost').textContent().catch(() => '');
    console.log('Toast on version tap:', toast);

    // Test help tab and copy diagnostics
    await page.click('button[data-nav="me/help"]');
    await page.waitForTimeout(500);
    if (!page.url().includes('me/help')) throw new Error('Help nav failed');
    await page.click('button[data-action="copy-diagnostics"]');
    await page.waitForTimeout(300);
    console.log('Copy diagnostics clicked successfully');

    await context.close();
  }

  console.log('--- TEST 3: Mobile Sub-page Back Navigation & Pill Tabs ---');
  {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 } });
    const page = await context.newPage();
    const u = `m_user_${Date.now()}`;
    await page.goto(`${BASE_URL}/online#/home`, { waitUntil: 'networkidle' });
    await page.evaluate(async (username) => {
      await fetch('/online/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password: 'Password123!' })
      });
    }, u);
    await page.reload({ waitUntil: 'networkidle' });

    // Navigate to /me/records
    await page.goto(`${BASE_URL}/online#/me/records`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);

    // Check pill tabs exist
    const activePill = await page.textContent('.profileMobileTabs .pill.is-active');
    console.log('Active mobile pill tab:', activePill.trim());
    if (activePill.trim() !== '对局记录') throw new Error('Active pill mismatch');

    // Click "学习档案" pill
    await page.click('.profileMobileTabs button[data-nav="me/study"]');
    await page.waitForTimeout(500);
    console.log('URL after pill click:', page.url());
    if (!page.url().includes('me/study')) throw new Error('Pill nav failed');

    // Click back button in header
    await page.click('.mobileContentHeader button:first-child');
    await page.waitForTimeout(500);
    console.log('URL after back click:', page.url());
    if (!page.url().includes('#me')) throw new Error('Back button should navigate to #me');

    await context.close();
  }

  await browser.close();
  console.log('ALL INTERACTION TESTS PASSED!');
}

testInteractions().catch(err => {
  console.error('Interactions test failed:', err);
  process.exit(1);
});
