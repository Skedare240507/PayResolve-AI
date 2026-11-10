import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const outDir = path.resolve(projectRoot, 'docs/report/screens/charts');

fs.mkdirSync(outDir, { recursive: true });

async function waitForUrl(url, timeoutMs = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok) return true;
    } catch {
      // retry
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

const FRONTEND_URL = 'http://127.0.0.1:5173';

async function seedIfEmpty() {
  try {
    await fetch('http://127.0.0.1:8080/api/cases/seed', { method: 'POST' });
    const benchRes = await fetch('http://127.0.0.1:8080/api/benchmark/latest');
    if (benchRes.ok) {
      const txt = await benchRes.text();
      if (!txt || txt.trim() === '' || txt.trim() === 'null') {
        await fetch('http://127.0.0.1:8080/api/benchmark/run', { method: 'POST' });
      }
    }
    const invRes = await fetch('http://127.0.0.1:8080/api/cases/PR-0142/investigations/latest');
    let needsInv = invRes.status === 204 || !invRes.ok;
    if (invRes.ok && invRes.status !== 204) {
      const invJson = await invRes.json();
      if (!invJson.steps || invJson.steps.length === 0) needsInv = true;
    }
    if (needsInv) {
      await fetch('http://127.0.0.1:8080/api/cases/PR-0142/investigate', { method: 'POST' });
    }
  } catch (err) {
    console.warn('Seed helper warning:', err.message);
  }
}

async function run() {
  console.log('Waiting for backend and frontend...');
  await waitForUrl('http://127.0.0.1:8080/actuator/health', 90000);
  await waitForUrl('http://localhost:5173/', 30000);
  await seedIfEmpty();

  const browser = await chromium.launch({ headless: true });
  const results = [];

  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await context.newPage();

    // Sign in as Operator
    await page.goto('http://localhost:5173/signin', { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: 'Operator' }).click();
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL('**/queue', { timeout: 15000 });

    const targets = [
      { key: 'operations', url: 'http://localhost:5173/operations', minCanvases: 5 },
      { key: 'benchmark', url: 'http://localhost:5173/benchmark', minCanvases: 2 },
      { key: 'case', url: 'http://localhost:5173/case/PR-0142', minCanvases: 2 },
    ];

    // 1. Desktop 1440px: Light -> Toggle to Dark WITHOUT reload!
    for (const t of targets) {
      // Ensure light mode first
      await page.evaluate(() => {
        localStorage.setItem('payresolve-theme', 'light');
        document.documentElement.classList.remove('dark');
        window.dispatchEvent(new CustomEvent('payresolve-theme-change', { detail: { theme: 'light' } }));
      });
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto(t.url, { waitUntil: 'networkidle' });
      await page.waitForFunction(
        (min) => Array.from(document.querySelectorAll('canvas')).filter((c) => c.width > 0 && c.height > 0).length >= min,
        t.minCanvases,
        { timeout: 10000 }
      ).catch(() => {});
      await page.waitForTimeout(400);

      const lightCheck = await page.evaluate((minCanvases) => {
        const canvases = Array.from(document.querySelectorAll('canvas'));
        const nonZeroCanvases = canvases.filter((c) => c.width > 0 && c.height > 0).length;
        const overflow = document.documentElement.scrollWidth > window.innerWidth + 2;
        return { canvases: canvases.length, nonZeroCanvases, overflow, ok: nonZeroCanvases >= minCanvases && !overflow };
      }, t.minCanvases);

      // Hover first canvas to confirm tooltip renders on hover
      let tooltipRendered = false;
      const firstCanvas = page.locator('canvas').first();
      if ((await firstCanvas.count()) > 0) {
        const box = await firstCanvas.boundingBox();
        if (box) {
          await page.mouse.move(box.x + box.width * 0.45, box.y + box.height * 0.45);
          await page.waitForTimeout(300);
          tooltipRendered = await page.evaluate(() => {
            return document.querySelectorAll('.ag-charts-tooltip').length > 0;
          });
        }
      }

      const lightPath = path.join(outDir, `${t.key}-charts-light-1440.png`);
      await page.screenshot({ path: lightPath, fullPage: true });
      results.push({ route: t.key, viewport: '1440px', theme: 'light', ...lightCheck, tooltipRendered });

      // Toggle theme WITHOUT reload via clicking the theme toggle button or dispatching theme toggle
      const toggled = await page.evaluate(() => {
        const btn = document.querySelector('button[aria-label*="mode" i], button[title*="mode" i]');
        if (btn) {
          btn.click();
          return true;
        }
        document.documentElement.classList.add('dark');
        localStorage.setItem('payresolve-theme', 'dark');
        window.dispatchEvent(new CustomEvent('payresolve-theme-change', { detail: { theme: 'dark' } }));
        return false;
      });
      await page.waitForTimeout(800);

      const darkNoReloadCheck = await page.evaluate((minCanvases) => {
        const isDark = document.documentElement.classList.contains('dark');
        const canvases = Array.from(document.querySelectorAll('canvas'));
        const nonZeroCanvases = canvases.filter((c) => c.width > 0 && c.height > 0).length;
        const overflow = document.documentElement.scrollWidth > window.innerWidth + 2;
        return { isDark, canvases: canvases.length, nonZeroCanvases, overflow, ok: isDark && nonZeroCanvases >= minCanvases && !overflow };
      }, t.minCanvases);

      const darkPath = path.join(outDir, `${t.key}-charts-dark-1440.png`);
      await page.screenshot({ path: darkPath, fullPage: true });
      results.push({ route: t.key, viewport: '1440px', theme: 'dark (no reload)', buttonClicked: toggled, ...darkNoReloadCheck });
    }

    // 2. Mobile 390px: Light & Dark
    await page.setViewportSize({ width: 390, height: 844 });
    for (const mode of ['light', 'dark']) {
      await page.evaluate((m) => {
        localStorage.setItem('payresolve-theme', m);
        if (m === 'dark') document.documentElement.classList.add('dark');
        else document.documentElement.classList.remove('dark');
        window.dispatchEvent(new CustomEvent('payresolve-theme-change', { detail: { theme: m } }));
      }, mode);

      for (const t of targets) {
        if (mode === 'light' && t.key !== 'operations') continue; // capture operations both light & dark at 390px + others in dark 390px
        await page.goto(t.url, { waitUntil: 'networkidle' });
        await page.waitForFunction(
          (min) => Array.from(document.querySelectorAll('canvas')).filter((c) => c.width > 0 && c.height > 0).length >= min,
          t.minCanvases,
          { timeout: 10000 }
        ).catch(() => {});
        await page.waitForTimeout(400);
        const mobileCheck = await page.evaluate((minCanvases) => {
          const canvases = Array.from(document.querySelectorAll('canvas'));
          const nonZeroCanvases = canvases.filter((c) => c.width > 0 && c.height > 0).length;
          const overflow = document.documentElement.scrollWidth > window.innerWidth + 2;
          return { canvases: canvases.length, nonZeroCanvases, overflow, ok: nonZeroCanvases >= minCanvases && !overflow };
        }, t.minCanvases);

        const mobilePath = path.join(outDir, `${t.key}-charts-${mode}-390.png`);
        await page.screenshot({ path: mobilePath, fullPage: true });
        results.push({ route: t.key, viewport: '390px', theme: mode, ...mobileCheck });
      }
    }

    console.log('\nChart Verification Results:');
    console.table(results);

    const anyFailed = results.some((r) => !r.ok);
    if (anyFailed) {
      console.error('Some chart checks failed!');
      process.exitCode = 1;
    } else {
      console.log('ALL Playwright chart checks passed!');
    }
  } finally {
    await browser.close();
  }
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
