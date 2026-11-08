import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const motionDir = path.resolve(projectRoot, 'docs/report/motion');

if (!fs.existsSync(motionDir)) {
  fs.mkdirSync(motionDir, { recursive: true });
}

console.log(`Recording motion captures to: ${motionDir}`);

const BASE_URL = 'http://localhost:5173';

async function recordScenario(scenarioName, targetFilename, actionFn) {
  console.log(`\nStarting recording for: ${scenarioName}...`);
  const browser = await chromium.launch({ headless: true });
  const tempDir = path.resolve(motionDir, `temp-${Date.now()}`);
  fs.mkdirSync(tempDir, { recursive: true });

  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
    recordVideo: {
      dir: tempDir,
      size: { width: 1280, height: 720 },
    },
  });

  await context.addInitScript(() => {
    const user = {
      username: 'operator',
      role: 'operator',
      email: 'operator@payresolve.dev',
      createdAt: new Date().toISOString()
    };
    localStorage.setItem('payresolve_current_user', JSON.stringify(user));
  });

  const page = await context.newPage();

  try {
    await actionFn(page);
    await page.waitForTimeout(1000);
  } finally {
    await page.close();
    await context.close();
    await browser.close();
  }

  // Find generated video
  const files = fs.readdirSync(tempDir).filter((f) => f.endsWith('.webm'));
  if (files.length > 0) {
    const src = path.resolve(tempDir, files[0]);
    const dest = path.resolve(motionDir, targetFilename);
    fs.copyFileSync(src, dest);
    console.log(`  ✓ Saved: ${targetFilename}`);
  }

  // Cleanup temp dir
  fs.rmSync(tempDir, { recursive: true, force: true });
}

async function main() {
  // 1. Theme Toggle: circular view transition reveal
  await recordScenario('View Transition Theme Toggle', 'theme-toggle.webm', async (page) => {
    await page.goto(`${BASE_URL}/queue`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    // Find visible theme toggle button in desktop sidebar
    const toggleBtn = page.locator('aside button[aria-label="Toggle theme"]');
    if (await toggleBtn.count() > 0) {
      await toggleBtn.click();
      await page.waitForTimeout(800);
      await toggleBtn.click();
      await page.waitForTimeout(800);
    }
  });

  // 2. Animated PayResolve Logo Loader
  await recordScenario('Animated Logo Loader', 'logo-loader.webm', async (page) => {
    await page.goto(`${BASE_URL}/redirecting`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
  });

  // 3. Route Transitions: 120ms fade-and-slide
  await recordScenario('Route Fade and Slide Transition', 'route-transition.webm', async (page) => {
    await page.goto(`${BASE_URL}/queue`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);
    await page.click('aside a[href="/operations"]');
    await page.waitForTimeout(1000);
    await page.click('aside a[href="/events"]');
    await page.waitForTimeout(1000);
    await page.click('aside a[href="/queue"]');
    await page.waitForTimeout(800);
  });

  // 4. Staggered Item Reveal on Queue & Timeline
  await recordScenario('Staggered Item Reveal', 'queue-stagger.webm', async (page) => {
    await page.goto(`${BASE_URL}/queue`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    await page.goto(`${BASE_URL}/events`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
  });

  // 5. KPI Count-Up Animation
  await recordScenario('KPI Numbers Count-Up Animation', 'kpi-countup.webm', async (page) => {
    await page.goto(`${BASE_URL}/queue`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    await page.goto(`${BASE_URL}/benchmark`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
  });

  console.log(`\nAll 5 motion videos recorded successfully in: ${motionDir}`);
}

main().catch((err) => {
  console.error('Recording error:', err);
  process.exit(1);
});
