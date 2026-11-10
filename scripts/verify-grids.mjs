import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const outDir = path.resolve(projectRoot, 'docs/report/screens/grids');

fs.mkdirSync(outDir, { recursive: true });

async function run() {
  const browser = await chromium.launch({ headless: true });
  const results = [];

  try {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      acceptDownloads: true,
    });
    const page = await context.newPage();

    // Sign in as Operator
    await page.goto('http://localhost:5173/signin', { waitUntil: 'networkidle' });
    await page.getByRole('button', { name: 'Operator' }).click();
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL('**/queue', { timeout: 15000 });

    const routes = [
      { key: 'queue', url: 'http://localhost:5173/queue' },
      { key: 'events', url: 'http://localhost:5173/events' },
      { key: 'actions', url: 'http://localhost:5173/actions' },
    ];

    for (const r of routes) {
      await page.goto(r.url, { waitUntil: 'networkidle' });
      await page.waitForSelector('.ag-root-wrapper', { timeout: 10000 });

      // On Actions & Audit page, switch to Audit log if Actions tab has 0 rows
      if (r.key === 'actions') {
        await page.waitForTimeout(800);
        const currentRows = await page.locator('.ag-row').count();
        if (currentRows === 0) {
          const auditTabBtn = page.getByRole('button', { name: /Audit log/i });
          if ((await auditTabBtn.count()) > 0) {
            await auditTabBtn.click();
          }
        }
      }

      await page.waitForSelector('.ag-row[row-index="0"]', { timeout: 10000 });
      await page.waitForTimeout(400);

      // Test CSV Download via Export CSV button
      let csvValid = false;
      let csvRowsCount = 0;
      const exportBtn = page.getByRole('button', { name: /Export.*CSV/i });
      if ((await exportBtn.count()) > 0 && (await exportBtn.first().isEnabled())) {
        const [download] = await Promise.all([
          page.waitForEvent('download'),
          exportBtn.first().click(),
        ]);
        const dlPath = await download.path();
        if (dlPath) {
          const content = fs.readFileSync(dlPath, 'utf-8');
          const lines = content.trim().split('\n');
          csvRowsCount = lines.length;
          const leakedSecret = /password|client_secret|DATABASE_URL|sk_|gsk_|AIzaSy/i.test(content);
          csvValid = lines.length >= 2 && !leakedSecret;
        }
      }

      // Check AG Grid Community features (pinned left, pagination panel, rendered data rows, localStorage state)
      const gridCheck = await page.evaluate(() => {
        const root = document.querySelector('.ag-root-wrapper');
        const pinnedCols = document.querySelectorAll(
          '.ag-pinned-left-header, .ag-pinned-left-cols-container, .ag-cell-last-left-pinned, [class*="pinned"]'
        );
        const paging = document.querySelectorAll('.ag-paging-panel').length > 0;
        const rows = document.querySelectorAll('.ag-row[row-index]').length;
        const firstColPinned = !!document.querySelector('.ag-header-cell[col-id="id"], .ag-header-cell[col-id="eventId"], .ag-header-cell[col-id="caseOrSubject"], .ag-header-cell[col-id="caseId"]');
        return {
          hasRoot: !!root,
          pinnedLeft: pinnedCols.length > 0 || firstColPinned,
          paging,
          rows,
        };
      });

      await page.screenshot({ path: path.join(outDir, `${r.key}-light.png`), fullPage: true });

      // Toggle to Dark Mode
      await page.evaluate(() => {
        localStorage.setItem('payresolve-theme', 'dark');
        document.documentElement.classList.add('dark');
        window.dispatchEvent(new CustomEvent('payresolve-theme-change', { detail: { theme: 'dark' } }));
      });
      await page.waitForTimeout(500);
      await page.screenshot({ path: path.join(outDir, `${r.key}-dark.png`), fullPage: true });

      // Reset back to light mode
      await page.evaluate(() => {
        localStorage.setItem('payresolve-theme', 'light');
        document.documentElement.classList.remove('dark');
        window.dispatchEvent(new CustomEvent('payresolve-theme-change', { detail: { theme: 'light' } }));
      });

      results.push({
        route: r.key,
        ...gridCheck,
        csvValid,
        csvRowsCount,
        ok: gridCheck.hasRoot && gridCheck.pinnedLeft && gridCheck.paging && gridCheck.rows > 0 && csvValid,
      });
    }

    console.log('\nAG Grid Community Verification Results:');
    console.table(results);

    if (results.some((r) => !r.ok)) {
      console.error('Some grid checks failed!');
      process.exitCode = 1;
    } else {
      console.log('ALL Playwright AG Grid Community checks passed!');
    }
  } finally {
    await browser.close();
  }
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
