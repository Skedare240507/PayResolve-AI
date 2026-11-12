import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const frontendDir = path.join(projectRoot, 'frontend');

async function isServerUp(url) {
  try {
    const res = await fetch(url);
    return res.ok;
  } catch {
    return false;
  }
}

async function waitForUrl(url, timeoutMs = 30000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await isServerUp(url)) return true;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Timed out waiting for ${url}`);
}

function buildTracePayload(orderId, paid) {
  const t0 = '2026-11-11T12:00:00Z';
  const t1 = '2026-11-11T12:00:02Z';
  const t2 = '2026-11-11T12:00:10Z';
  const t3 = '2026-11-11T12:00:12Z';
  const t4 = '2026-11-11T12:00:16Z';

  return {
    orderId,
    paypalOrderId: `PP-${orderId}`,
    orderStatus: paid ? 'PAID' : 'AWAITING_CONFIRMATION',
    totalMinor: 2400,
    currency: 'USD',
    captureId: `CAP-${orderId}`,
    linkedCaseId: null,
    generatedAt: t4,
    steps: [
      { id: 'ORDER_CREATED', label: 'Order created', status: 'COMPLETED', completed: true, timestamp: t0, timestampLabel: t0, detail: 'Order created', explanation: 'Order saved' },
      { id: 'PAYPAL_ORDER_CREATED', label: 'PayPal order created', status: 'COMPLETED', completed: true, timestamp: t1, timestampLabel: t1, detail: 'Linked PayPal order', explanation: 'Created with PayPal' },
      { id: 'BUYER_APPROVED', label: 'Buyer approved', status: 'COMPLETED', completed: true, timestamp: t2, timestampLabel: t2, detail: 'Buyer approved', explanation: 'Buyer approved' },
      { id: 'CAPTURED_API', label: 'Captured (API)', status: 'COMPLETED', completed: true, timestamp: t3, timestampLabel: t3, detail: 'Captured', externalRef: `CAP-${orderId}`, explanation: 'Server capture' },
      paid
        ? { id: 'WEBHOOK_RECEIVED', label: 'Webhook received', status: 'VERIFIED', completed: true, timestamp: t4, timestampLabel: t4, detail: 'Verified webhook WH-1 received', externalRef: 'WH-1', explanation: 'Webhook received' }
        : { id: 'WEBHOOK_RECEIVED', label: 'Webhook received', status: 'PENDING', completed: false, timestamp: null, timestampLabel: 'not yet', detail: 'No PAYMENT.CAPTURE.COMPLETED webhook received yet.', explanation: 'Webhook received' },
      paid
        ? { id: 'WEBHOOK_PROCESSED', label: 'Webhook processed', status: 'COMPLETED', completed: true, timestamp: t4, timestampLabel: t4, detail: 'Worker processed webhook', externalRef: 'WH-1', explanation: 'Worker processed' }
        : { id: 'WEBHOOK_PROCESSED', label: 'Webhook processed', status: 'PENDING', completed: false, timestamp: null, timestampLabel: 'not yet', detail: 'Waiting for webhook', explanation: 'Worker processed' },
      paid
        ? { id: 'ORDER_MARKED_PAID', label: 'Shop order marked PAID', status: 'COMPLETED', completed: true, timestamp: t4, timestampLabel: t4, detail: 'Order marked PAID', explanation: 'Order paid' }
        : { id: 'ORDER_MARKED_PAID', label: 'Shop order marked PAID', status: 'PENDING', completed: false, timestamp: null, timestampLabel: 'not yet', detail: 'Remains AWAITING_CONFIRMATION', explanation: 'Order paid' },
      { id: 'CASE_OPENED', label: 'Case opened (if any)', status: 'NONE', completed: false, timestamp: null, timestampLabel: 'not yet', detail: 'No discrepancy case opened.', explanation: 'Case check' },
    ],
  };
}

async function setupCommonRoutes(page) {
  await page.route('**/api/auth/csrf', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ token: 'playwright-csrf-token' }),
    })
  );
  await page.route('**/api/auth/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ id: 1, username: 'operator', email: 'operator@payresolve.local', role: 'OPERATOR', authenticated: true }),
    })
  );
  await page.route('**/api/cases**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }),
    })
  );
}

async function run() {
  let baseUrl = 'http://localhost:5173';
  let previewProc = null;

  if (!(await isServerUp(baseUrl))) {
    baseUrl = 'http://127.0.0.1:4173';
    console.log('Starting vite preview on port 4173 for return-page polling Playwright test...');
    previewProc = spawn('npx', ['vite', 'preview', '--host', '127.0.0.1', '--port', '4173', '--strictPort'], {
      cwd: frontendDir,
      shell: true,
      stdio: 'ignore',
    });
    await waitForUrl(baseUrl, 30000);
  } else {
    console.log('Using active frontend dev server at http://localhost:5173');
  }

  const browser = await chromium.launch({ headless: true });

  try {
    // -------------------------------------------------------------------------
    // Scenario 1: Poll every 2s until order becomes PAID, then stop polling
    // -------------------------------------------------------------------------
    {
      const context = await browser.newContext({ viewport: { width: 1280, height: 850 } });
      await context.addInitScript(() => {
        localStorage.setItem(
          'payresolve_current_user',
          JSON.stringify({ id: 1, username: 'operator', email: 'operator@payresolve.local', role: 'operator', authenticated: true })
        );
      });
      const page = await context.newPage();
      await setupCommonRoutes(page);

      let orderPollCount = 0;
      await page.route('**/api/shop/orders/ORD-POLL-1/capture**', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'AWAITING_CONFIRMATION',
            amountMinor: 2400,
            currency: 'USD',
            amount: '24.00 USD',
            steps: buildTracePayload('ORD-POLL-1', false).steps,
          }),
        })
      );

      await page.route('**/api/shop/orders/ORD-POLL-1/status**', (route) => {
        orderPollCount += 1;
        const paid = orderPollCount >= 3;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: paid ? 'PAID' : 'AWAITING_CONFIRMATION',
            amountMinor: 2400,
            currency: 'USD',
            amount: '24.00 USD',
            steps: buildTracePayload('ORD-POLL-1', paid).steps,
          }),
        });
      });

      await page.goto(`${baseUrl}/shop/return?orderId=ORD-POLL-1&token=PP-ORD-POLL-1`);

      await page.getByText('Payment captured', { exact: true }).waitFor({ state: 'visible', timeout: 5000 });
      await page.getByText("Waiting for PayPal's confirmation (webhook)").waitFor({ state: 'visible', timeout: 5000 });
      await page.locator('[data-testid="payment-trace-stepper"]').waitFor({ state: 'visible', timeout: 5000 });

      // Wait until polling observes poll #3 (2s * 2 intervals = ~4.5s)
      await page.getByText('Payment confirmed').waitFor({ state: 'visible', timeout: 10000 });
      assert.ok(orderPollCount >= 3, `Expected at least 3 order polls, got ${orderPollCount}`);

      const countAtPaid = orderPollCount;
      await page.waitForTimeout(2600);
      assert.equal(orderPollCount, countAtPaid, 'Polling must stop after order becomes PAID');

      console.log(`Scenario 1 PASSED: transitioned to PAID after ${countAtPaid} polls and stopped.`);
      await context.close();
    }

    // -------------------------------------------------------------------------
    // Scenario 2: After 60 seconds without webhook, show timeout message + Queue link and stop polling
    // -------------------------------------------------------------------------
    {
      const context = await browser.newContext({ viewport: { width: 1280, height: 850 } });
      await context.addInitScript(() => {
        localStorage.setItem(
          'payresolve_current_user',
          JSON.stringify({ id: 1, username: 'operator', email: 'operator@payresolve.local', role: 'operator', authenticated: true })
        );
      });
      const page = await context.newPage();
      await page.clock.install();
      await setupCommonRoutes(page);

      let timeoutPollCount = 0;
      await page.route('**/api/shop/orders/ORD-TIMEOUT-1/capture**', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'AWAITING_CONFIRMATION',
            amountMinor: 2400,
            currency: 'USD',
            amount: '24.00 USD',
            steps: buildTracePayload('ORD-TIMEOUT-1', false).steps,
          }),
        })
      );

      await page.route('**/api/shop/orders/ORD-TIMEOUT-1/status**', (route) => {
        timeoutPollCount++;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'AWAITING_CONFIRMATION',
            amountMinor: 2400,
            currency: 'USD',
            amount: '24.00 USD',
            steps: buildTracePayload('ORD-TIMEOUT-1', false).steps,
          }),
        });
      });

      await page.goto(`${baseUrl}/shop/return?orderId=ORD-TIMEOUT-1&token=PP-ORD-TIMEOUT-1`);
      await page.getByText('Payment captured', { exact: true }).waitFor({ state: 'visible', timeout: 5000 });

      // Advance fake clock past 60s in chunks so interval/timeout callbacks fire naturally
      for (let i = 0; i < 6; i++) {
        await page.clock.fastForward(10_500);
      }

      const expectedMsg =
        "Captured, but PayPal's confirmation has not arrived yet. This creates a Paid, not marked case if it never comes.";
      await page.getByText(expectedMsg).waitFor({ state: 'visible', timeout: 5000 });
      const queueLink = page.getByRole('link', { name: 'View Exception Queue' });
      await queueLink.waitFor({ state: 'visible' });
      assert.equal(await queueLink.getAttribute('href'), '/queue');

      const countAtTimeout = timeoutPollCount;
      await page.clock.fastForward(6000);
      assert.equal(timeoutPollCount, countAtTimeout, 'Polling must stop after 60s timeout');

      console.log(`Scenario 2 PASSED: 60s timeout displayed queue link and stopped polling (${countAtTimeout} polls).`);
      await context.close();
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Navigating away from /shop/return stops polling
    // -------------------------------------------------------------------------
    {
      const context = await browser.newContext({ viewport: { width: 1280, height: 850 } });
      await context.addInitScript(() => {
        localStorage.setItem(
          'payresolve_current_user',
          JSON.stringify({ id: 1, username: 'operator', email: 'operator@payresolve.local', role: 'operator', authenticated: true })
        );
      });
      const page = await context.newPage();
      await setupCommonRoutes(page);

      let leavePollCount = 0;
      await page.route('**/api/shop/orders/ORD-LEAVE-1/capture**', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'AWAITING_CONFIRMATION',
            amountMinor: 2400,
            currency: 'USD',
            amount: '24.00 USD',
            steps: buildTracePayload('ORD-LEAVE-1', false).steps,
          }),
        })
      );

      await page.route('**/api/shop/orders/ORD-LEAVE-1/status**', (route) => {
        leavePollCount++;
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'AWAITING_CONFIRMATION',
            amountMinor: 2400,
            currency: 'USD',
            amount: '24.00 USD',
            steps: buildTracePayload('ORD-LEAVE-1', false).steps,
          }),
        });
      });

      await page.goto(`${baseUrl}/shop/return?orderId=ORD-LEAVE-1&token=PP-ORD-LEAVE-1`);
      await page.getByText('Payment captured', { exact: true }).waitFor({ state: 'visible', timeout: 5000 });
      await page.getByRole('link', { name: 'View Exception Queue' }).click();
      await page.waitForURL('**/queue');

      const countOnLeave = leavePollCount;
      await page.waitForTimeout(2600);
      assert.equal(leavePollCount, countOnLeave, 'Polling must stop immediately when leaving /shop/return');

      console.log(`Scenario 3 PASSED: navigation to /queue halted further polling cleanly.`);
      await context.close();
    }
  } finally {
    await browser.close();
    if (previewProc) {
      previewProc.kill();
    }
  }
}

run().catch((err) => {
  console.error('❌ Return page Playwright polling test failed:', err);
  process.exit(1);
});
