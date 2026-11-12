# Final Fixes Verification Report (Parts 1–3)

**Generated:** 2026-11-12  
**Commits:**
- Part 1: `fefe75d` (`fix: no case for normal payments, correct order matching, queue defaults`)
- Part 2: `e6c3f81` (`feat(brand): svg logo system, sizes, favicon, theme-correct variants`)

---

## Part 1: No Case for Normal Payments, Correct Order Matching & Queue Defaults

### 1. Root-Cause Diagnosis (`ORD-18178` & `PR-0005`)

- **`ORD-18178` (`0R949943MC649625L`, \$62.50 `PAID`, capture `95B81224BN717594K`):**
  - `WH-9HB69394KD866520J-6GG98750FC319370J` (`PAYMENT.CAPTURE.COMPLETED`, `delivery_count=1`, `attempts=1`, `verified=true`, `custom_id='ORD-18178'`, `paypal_order_id='0R949943MC649625L'`)
  - `WH-03K40647FU632902E-1PV03892PA485833J` (`CHECKOUT.ORDER.APPROVED`, `delivery_count=2`, `attempts=2`, `verified=true`, `custom_id='ORD-18178'`, `paypal_order_id='0R949943MC649625L'`)
- **`PR-0005` (`DUPLICATE_EVENT`, `Resolved`, `Medium`, `shop_order_id='ORD-11032'`, `paypal_order_id='7F4046481C0009705'`):**
  - Created by `DuplicateEventDetector` Check 3 (`event.verified() && event.deliveryCount() > 1`) when PayPal re-delivered `WH-1HD6137109590383D-3RG561117V758644L` (`CHECKOUT.ORDER.APPROVED` for `ORD-11032`, which also happened to be \$62.50) at `08:42:05Z` (14:12:05 IST) while `ORD-18178` was being paid.
- **PayPal Webhooks Diagnostics (`GET /api/paypal/diagnostics`):**
  - Only **one** webhook URL is registered on PayPal (`https://payresolve-api-5ve7.onrender.com/api/webhooks/paypal`); no duplicate webhook registration exists.

### 2. Implemented Fixes

- **Strict Order Matching (`CaseDetectionService`, `EvidenceService`, `InvestigatorTools`):**
  - Snapshots are built exclusively from rows matching `custom_id`, `paypal_order_id`, or `resource_id == paypal_capture_id` in `payments`.
  - Removed all `rawBody.contains(...)` and `raw_body LIKE ...` event-to-order matching.
  - Unlinked events only trigger `ORPHAN_CAPTURE` for `PAYMENT.CAPTURE.*` events (never `CHECKOUT.*`).
- **Absorbed Duplicates (`DuplicateEventDetector`, `EventInboxPage.tsx`, `CaseService`):**
  - Removed `DuplicateEventDetector` Check 3: verified re-deliveries (`delivery_count > 1`) absorbed idempotently create **zero** cases.
  - `EventInboxPage.tsx` displays `"delivered N times, absorbed"` badges when `deliveryCount > 1`.
  - `Duplicates blocked` is computed strictly as `SUM(delivery_count - 1)` over verified `webhook_events`.
  - `OPEN` `HIGH` `DUPLICATE_EVENT` cases are only opened for multiple payment rows per capture ID or payload mismatch.
- **Queue Defaults & KPI Polish (`CaseController`, `CaseRepository`, `CaseService`, `ExceptionQueuePage.tsx`, `ShopReturnPage.tsx`, `V9` migration):**
  - `GET /api/cases` defaults `includeResolved=false` (showing `OPEN`, `INVESTIGATING`, `ACTION_PROPOSED` only); `ExceptionQueuePage.tsx` adds a `Show resolved` toggle synced with `?showResolved=true`.
  - `Open cases` counts only active cases (`OPEN`, `INVESTIGATING`, `ACTION_PROPOSED`).
  - `Oldest open case` formats as a single-line readable duration (`4 d 1 h`, `38 min`) with non-wrapping `SLA: 60m`.
  - Seeded demo cases carry `synthetic = true` (`V9__synthetic_cases_and_dismiss_duplicates.sql`), display a `Seed` badge, are excluded from `Oldest open case`, and control visibility of the `Sample data` header button.
  - `ShopReturnPage.tsx` drops `"Waiting for "` once the webhook confirms `PAID`.

### 3. Supabase SQL Cleanup One-Liner

For any existing production/Supabase database where `V9` has not yet run, or to manually dismiss legacy `RESOLVED` `DUPLICATE_EVENT` rows:

```sql
UPDATE cases SET status = 'DISMISSED', summary = CONCAT(summary, ' (auto-dismissed: absorbed duplicate or retry)'), updated_at = NOW() WHERE type = 'DUPLICATE_EVENT' AND status = 'RESOLVED';
```

### 4. Part 1 Test Coverage

- `DuplicateEventDetectorTest`: verifies `attempts = 2, delivery_count = 1` and `delivery_count = 2` with 1 payment create `0` cases; verifies duplicate payment rows for same capture create `OPEN` `HIGH` `DUPLICATE_EVENT`.
- `WebhookIngestIntegrationTest`:
  - `twoVerifiedDeliveriesCreateZeroCasesAndIncrementDuplicatesBlocked`
  - `normalPaymentWithHandlerRetryCreatesZeroCases`
  - `twoOrdersWithSameAmountNeverCrossMatchEvents`
  - `twoPaymentRowsForOneCaptureCreateOneOpenHighCase`
- `CaseIntegrationTest`: verifies `GET /api/cases` excludes `RESOLVED` by default and `includeResolved=true` includes them.

---

## Part 2: Brand SVG System, Sizes, Favicons & Theme Consistency

- Added `svgo --multipass` optimized brand SVGs under `frontend/public/brand/`:
  - `logo-horizontal.svg`, `logo-reversed.svg`, `logo-symbol.svg`, `logo-symbol-light-check.svg`
- Added `<Logo />` (`frontend/src/components/Logo.tsx`) and `logoSizes` (`frontend/src/lib/logoSizes.ts`), and delegated `PayResolveLogo` / `PayResolveMark` to `<Logo />`.
- Generated `favicon.svg`, `favicon-16x16.png`, `favicon-32x32.png`, `apple-touch-icon.png`, `manifest.webmanifest`, `/brand/icon-192.png`, `/brand/icon-512.png`, and `/brand/og-image.png` (1200x630), wired into `frontend/index.html` with OpenGraph & Twitter Card metadata.
- Documented in `docs/brand.md`.

---

## Part 3: Playwright & Full Build Verification

- **Playwright Sweep (35 route/viewport/theme combinations):**
  - Public auth routes (`/login`, `/signup`, `/forgot-password`) at `1440x900` render `/brand/logo-reversed.svg` (`naturalWidth: 469`, height `32px`) inside the translucent pill on the dark-blue gradient.
  - All 8 app routes (`/queue`, `/case/PR-0142`, `/graph-explorer`, `/operations`, `/events`, `/shop`, `/actions`, `/benchmark`) tested across `1440x900` & `390x844` in both `light` and `dark` themes: `0` failures, `naturalWidth > 0` on all visible logos, `0` horizontal overflow, and theme-consistent desktop sidebar (`rgb(255, 255, 255)` light / `#0a0f1d` dark).
- **Live API Check (`localhost:8080`):** `activeCount=6`, `allCount=11`, `openCount=6`, `awaitingApprovalCount=1`, `duplicatesBlockedToday=9`, `resolved=1`, `dismissed=4`.

