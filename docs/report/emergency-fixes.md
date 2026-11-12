# PayResolve AI — Emergency Security & Bug Audit Fixes (2026-11-12)

This report documents the 9-step emergency security, reliability, and AI audit fixes implemented ahead of the 2026-11-12 hackathon deadline without breaking the core demo flow (sign in, queue, case inspection, AI investigation, action approval, Demo Shop PayPal Sandbox checkout, webhook ingestion, and Events inbox).

**No secrets were committed or printed.** `.env` and `backend/src/main/resources/application.yml` remain untracked and excluded from git history.

---

## Summary of Implemented Steps

### Step 1 — Lock Down RBAC & Re-enable CSRF (`bff90ad`)
- Replaced permissive authorization rules in `SecurityConfig.java` with an explicit role matrix:
  - **Public**: `POST /api/webhooks/paypal`, `POST /api/auth/login|signup|forgot-password|reset-password`, `GET /api/auth/csrf`, `GET /api/auth/me`, `GET /actuator/health` (details hidden), `GET /api/shop/products`, and token-gated buyer return endpoints (`POST /api/shop/orders/{id}/paypal/capture`, `GET /api/shop/orders/{id}/status`).
  - **Signed-in (`VIEWER` or `OPERATOR`)**: all other `GET` endpoints plus `POST /api/cases/{id}/investigate` and `POST /api/agent/chat`.
  - **OPERATOR only**: all other mutating `POST`/`PUT`/`PATCH`/`DELETE` endpoints, PayPal diagnostics (`GET /api/paypal/diagnostics`), Swagger UI (`/swagger-ui.html`), and `/v3/api-docs`.
- Re-enabled `CookieCsrfTokenRepository` CSRF protection for all endpoints except `/api/webhooks/**`.
- Configured exact CORS origins from `FRONTEND_ORIGIN` via `WebMvcConfig.java` and `SecurityConfig.java`.
- Updated `frontend/src/lib/api.ts` and `authStore.ts` to fetch/cache `XSRF-TOKEN`, attach `X-XSRF-TOKEN` on mutating requests, retry once on `403`, and clear cached tokens on login/logout.
- Added `SecurityAuthorizationIntegrationTest.java` and updated existing MockMvc integration tests with explicit roles and CSRF tokens.

### Step 2 — Rate Limits, Login Lockout & Payload Limits (`8e84514`)
- Added `RateLimitService.java` and `RateAndPayloadFilter.java`:
  - Per-IP/per-user sliding limits across login, password reset, AI investigate/chat, webhook ingest, and evidence uploads (`429 Too Many Requests` with `Retry-After`).
  - Strict `256 KB` payload ceiling on `POST /api/webhooks/paypal` (`413 Payload Too Large` on oversize bodies).
- Added `LoginAttemptService.java` and updated `AuthService.java`:
  - 5 failed login attempts lock the account for 15 minutes while maintaining constant-time dummy BCrypt verification and uniform `"Invalid username or password"` responses.
- Enforced `4MB` file / `5MB` multipart request limits across YAML configurations.
- Added `RateLimitAndLockoutIntegrationTest.java`.

### Step 3 — Close Password Reset Account/Token Exposure (`e639268`)
- Added `PasswordResetTokenRepository.invalidateActiveTokensForUser` and `ActiveSessionRegistry.java`.
- Updated `AuthProperties.java` and `AuthService.java`:
  - Stored only SHA-256 `token_hash` in `password_reset_tokens` (never plain reset tokens and never logged).
  - Enforced 15-minute expiry, invalidated prior active reset tokens on new requests, and invalidated all active HTTP sessions upon password reset.
  - Added `DEMO_RESET_LINKS` toggle (`false` by default) — when off, never reveals whether an account exists or returns a reset URL in responses; never exposes `OPERATOR` reset links even in demo mode.
- Updated `ForgotPasswordPage.tsx`, `CheckEmailPage.tsx`, `ResetPasswordPage.tsx`, and auth tests.

### Step 4 — PayPal Return Flow, Buyer Security & SPA 404 Fallback (`d5dd7f1`)
- Added `FrontendOriginValidator.java` failing fast on startup if `FRONTEND_ORIGIN` is blank, invalid, or `localhost`/`127.0.0.1` while `APP_PUBLIC_URL` uses `https://`.
- Updated `ShopOrderService.java` and `ShopController.java` to ignore client-supplied `returnUrl`/`cancelUrl` (building redirects strictly from validated `FRONTEND_ORIGIN`), require matching `?token=` against `paypalOrderId`, verify PayPal order `APPROVED` status before server-side capture, support idempotent return capture, and expose minimal `BuyerOrderStatusDto`.
- Updated `frontend/public/404.html` and `frontend/index.html` (`history.replaceState` SPA redirect recovery for static hosting), made `/shop/return` and `/shop/cancel` public routes in `App.tsx`, and added standalone public header + `"Sign in to see the full trace"` prompt in `ShopReturnPage.tsx`.
- Updated `NotFoundPage.tsx` and verified all 3 scenarios in `scripts/test-return-polling.mjs`.

### Step 5 — Eliminate Duplicate-Event False Positives, Arrival Races & Webhook Poisoning (`6846d84`)
- Created Flyway migration `V8__rejected_webhooks_and_duplicate_cleanup.sql`:
  - Added `rejected_webhooks` table (RLS enabled with no policies) for quarantining invalid/unverified webhook payloads (`401`).
  - Added `next_attempt_at` and `payload_mismatch` columns to `webhook_events`.
  - Resolved noisy false-positive open `DUPLICATE_EVENT` cases where only a single completed payment existed.
- Updated `DuplicateEventDetector.java` and `WebhookIngestService.java`:
  - Removed the bogus `attempts > 1` check.
  - Stopped opening `HIGH`/`OPEN` cases on benign PayPal retries; emitted `LOW`/`RESOLVED` informational duplicate records instead, reserving `HIGH`/`OPEN` strictly for conflicting capture/refund totals or multiple completed payments.
  - Restricted duplicate-event checks to `PAYMENT.CAPTURE.COMPLETED` and `PAYMENT.CAPTURE.REFUNDED`.
  - Quarantined unverified webhooks in `rejected_webhooks` with rate limiting and hardened `WebhookEventRepository.upsert` so unverified payloads can never poison or mutate verified `webhook_events` rows.
- Replaced all `rawBody.contains(...)` / `raw_body LIKE` substring lookups with indexed column queries in `WebhookEventRepository.findByOrderRefs` and `CaseDetectionService.java`.
- Added arrival-race `next_attempt_at` deferred retry (`3s` backoff up to `90s`) in `WebhookWorkerService.java`.
- Added `GET /api/cases/stats` (`CaseQueueStatsDto.java`) and updated `DuplicateEventDetectorTest` & `WebhookIngestIntegrationTest`.

### Step 6 — Signed-in Sandbox Test Buyer Card (`ead144f`)
- Added `GET /api/shop/demo-buyer` (`DemoBuyerDto.java`, signed-in users only) backed by `DEMO_BUYER_EMAIL` / `DEMO_BUYER_PASSWORD` in `.env.example`, `application.yml.example`, and `infra/render.yaml` (`sync: false`).
- Added a copyable `"Sandbox test buyer"` card on `DemoShopPage.tsx` with `<InfoTip metricId="shop.sandboxTestBuyer" />`.
- Added `DemoBuyerConfiguredTest.java` and `ShopIntegrationTest` assertions.

### Step 7 — Protect Live Demo Fault Switches (`06d6509`)
- Updated `DemoFaultSwitches.java`, `DemoFaultsDto.java`, and `DemoFaultController.java`:
  - Added a 10-minute auto-reset safety window (`resetsAt` reconstructed from persisted `updated_at`, enforced via `@Scheduled(fixedDelay = 15_000)` and lazily on reads/getters via injected `Clock`).
  - Exposed `resetsAt` and `activeFaultNames` on `/api/demo/faults`.
- Added `DemoFaultBanner.tsx` mounted in `AppLayout.tsx` displaying `"Demo fault active: <names>, resets in mm:ss"` with an operator-only `"Reset all"` button, and restricted fault toggles to operators in `DemoShopPage.tsx`.
- Added `DemoFaultSwitchesTest.java`.

### Step 8 — Fix AI Provider Models, Timeouts/Retry, Evidence Snapshot & UI Telemetry (`a5171e0`)
- **Root causes found via Supabase `llm_calls` inspection & live API probes:**
  - `infra/render.yaml` and `.env.example` defaulted `GROQ_MODEL` to removed `llama-3.3-70b-versatile` and `GEMINI_MODEL` to retired `gemini-2.0-flash` (`404 Model Not Found`). Historical `llm_calls` rows (`#41`–`#46`) on Render also showed Groq `401 Unauthorized: Invalid API Key`.
  - `InvestigatorService.gatherToolEvidenceContext` previously omitted full `case_evidence`, payment, refund, and shipment payloads from the LLM prompt, leaving real models unable to confirm root causes cleanly.
  - `LlmCallRepository.findByCaseId` lacked an `id DESC` tiebreaker when consecutive attempts shared the same timestamp, causing the frontend `llmCalls[0]` badge to display the first-inserted `FAILED` Groq attempt (`"GROQ ... fallback 0 ms · 0 in / 0 out"`) even when `rule_fallback` actually ran.
  - `CaseDetailsPage.tsx` checked `report?.status === 'DEGRADED'` instead of `activeInvestigation.status === 'DEGRADED'` / `report?.degraded`, displaying `"Citations verified"` even on unverified fallback runs.
- **Fixes applied:**
  - Updated defaults across `infra/render.yaml`, `.env.example`, `InvestigatorService.java`, and `backend/src/test/resources/application.yml` to `GROQ_MODEL=openai/gpt-oss-120b` and `GEMINI_MODEL=gemini-3.5-flash`.
  - Added `LlmHttpSupport.java` with `10s` connect / `35s` read `JdkClientHttpRequestFactory` timeouts, transient retry-once (`429`/`5xx`/timeouts), Groq `json_validate_failed` fallback without `response_format`, `x-goog-api-key` header auth on Gemini (no API key in URLs), and secret-safe error classification.
  - Fixed PostgreSQL FK ordering so `investigation_steps` are inserted strictly after `investigations`, and supplied normalized `case_evidence` + order/payment/refund/shipment/webhook snapshots directly in the investigator prompt.
  - Added `ORDER BY created_at DESC, id DESC` in `LlmCallRepository.java`, omitted fake `1 ms` / `0 tokens` telemetry on `rule_fallback`, added `verifiedCitations` / `totalCitations` in `InvestigationVerifier.java` + `InvestigationResponseDto.java`, and updated `CaseDetailsPage.tsx` + `metricDefinitions.ts` to show exact primary/fallback/rule-based provider chips and `"n of m citations verified"` or `"Not verified"`.
  - Added `LlmRouterWireMockTest.java` and verified a live `PR-0142` investigation against Supabase + Groq (`openai/gpt-oss-120b`): `status=CAUSE_FOUND`, `provider=groq`, `providerMode=PRIMARY`, `latencyMs=4023`, `promptTokens=1278`, `completionTokens=1175`, `verifiedCitations=9`, `totalCitations=9`, `recommendedAction=SYNC_ORDER_STATUS`.

---

## Important Deployment & Local Environment Notes

1. **Render Dashboard Env Vars**: Update `GROQ_API_KEY` on `payresolve-api` in the Render dashboard (historical Render `llm_calls` rows failed with `401 Invalid API Key` before local `.env` verification confirmed the local Groq key works cleanly), and ensure `GROQ_MODEL=openai/gpt-oss-120b` and `GEMINI_MODEL=gemini-3.5-flash`.
2. **Local Startup with Render `.env`**: Step 4's `FrontendOriginValidator` intentionally fails fast if `APP_PUBLIC_URL` starts with `https://` while `FRONTEND_ORIGIN` points to `http://localhost:5173`. When running locally against a `.env` configured with Render's `APP_PUBLIC_URL`, override `APP_PUBLIC_URL=http://localhost:8080` and `SESSION_COOKIE_SECURE=false` in the local shell environment.
3. **Docker Build Check**: Skipped locally because the Docker Desktop Linux engine pipe (`//./pipe/dockerDesktopLinuxEngine`) was not running.

---

## Verification Ledger (Step 9 Final Checks)

- Backend (`cd backend; .\mvnw.cmd verify`): `BUILD SUCCESS` (`Tests run: 185, Failures: 0, Errors: 0, Skipped: 0`).
- Frontend lint (`npm --prefix frontend run lint`): `Found 1 warning and 0 errors` (pre-existing floating-ui `InfoTip.tsx` ref warning).
- Frontend build (`npm --prefix frontend run build`): `tsc -b && vite build` passed (`✓ built in 8.07s`).
- Metrics registry check (`npm --prefix frontend run check:metrics`): passed (`61` registered metrics, `56` valid usages).
- Playwright return-page polling (`node scripts/test-return-polling.mjs`): passed all 3 scenarios (`PAID` after 3 polls, 60s timeout queue link, navigation abort).
- Docker build: skipped (Docker Desktop daemon not running locally).

