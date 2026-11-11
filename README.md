# PayResolve AI

PayResolve helps a small online shop work out what really happened to a PayPal payment. It lines up PayPal's records with the shop's own order, refund and shipment records, shows the gaps on a graph, and lets an AI assistant read through the evidence and explain it. The assistant can only read. A person decides what happens next.

We built it for the PayPal AI Hackathon. It runs against PayPal's Sandbox, so no real money moves.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Live demo: [add the deployed URL here]
Demo video: [add the YouTube link here]

## The problem

A payment touches several systems. PayPal knows the money was captured. The shop's database has an order with a status. A webhook is supposed to connect the two, and sometimes it doesn't arrive, arrives twice, or fails halfway.

Then a customer writes in to say they paid, and the order still says pending. Telling them to pay again could charge them twice. Refunding without checking could pay out twice. Whoever is on support has to open PayPal, the order system and the shipping records and piece the story together by hand.

PayResolve does that piece-together step for you and shows its sources.

We did not run interviews or surveys for this project. The problem comes from PayPal's public documentation and from the industry articles listed at the end of this file.

## What it does

1. It receives PayPal webhooks, checks their signatures, and stores every event before doing anything with it. A repeated event is counted, not processed twice.
2. Five rule-based detectors look for problems: paid but not marked paid, amount mismatch, duplicate event, refund mismatch, and orphan capture (a capture with no matching order). No AI is involved in this step.
3. Each problem becomes a case with an evidence graph. Records that should exist but don't appear as dashed "missing" nodes.
4. An AI investigator reads the case using read-only tools. It writes a report listing what is confirmed, what conflicts and what is missing. A separate check compares every record id and amount in the report with the database before you see it.
5. The investigator can suggest an action, such as syncing an order's status. Only a signed-in operator can approve it, and every decision goes into an append-only audit log.
6. A small demo shop with fault switches lets you break things on purpose (fail the webhook handler, drop it, change the order total, deliver twice) so you can watch each case type appear.
7. A benchmark page runs the detectors and investigator against seeded cases and records the results.
8. An operations dashboard built with AG Studio summarises cases, events and benchmark data.
9. Every chart, KPI, percentage, count, duration, and derived value has an interactive info tooltip (`<GoogleIcon name="info" />`) showing its exact formula, source table, time window, and underlying real numbers.

## What is real and what is not

- PayPal calls are real, but only against the Sandbox.
- Creating test disputes in the Sandbox can need extra PayPal permissions. If you don't have them, set `PAYPAL_USE_SYNTHETIC_DISPUTES=true` and dispute data comes from clearly labelled fixtures.
- Password reset is a demo. No email service is connected, so the reset link is shown on screen.
- The AI providers have free-tier rate limits. If the first provider fails, the app falls back to the next one, and if all fail it shows the rule-based findings and marks the report as degraded.
- Benchmark cases are seeded and synthetic. They measure how this project's own detectors behave, not how it would do on a real merchant's data.

## Run it yourself

### What you need

- Java 21
- Node.js 22 and npm
- Docker (for local Postgres and for the backend tests)
- A free PayPal developer account
- A free Gemini API key from Google AI Studio and a free Groq API key from console.groq.com. Both are optional for a first look, but without them the AI panel falls back to the rule-based findings.
- A Supabase project, if you prefer it over local Postgres

### 1. Get the code

```bash
git clone https://github.com/Rohan45create/PayResolve-AI.git
cd PayResolve-AI
```

### 2. Create the config files

Two files are not committed because they are local to you: `.env` and `backend/src/main/resources/application.yml`. The second one only contains `${PLACEHOLDER}` references, so you copy the example as it is.

macOS or Linux:

```bash
cp .env.example .env
cp backend/src/main/resources/application.yml.example backend/src/main/resources/application.yml
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
Copy-Item backend\src\main\resources\application.yml.example backend\src\main\resources\application.yml
```

Open `.env` and fill it in. The table below lists the variables that matter.

| Variable | What it is |
|---|---|
| `DATABASE_URL`, `DB_USER`, `DB_PASSWORD` | Postgres connection. Leave the defaults to use the local Docker database. For Supabase, use the Session pooler string (see below). |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` | From your Sandbox REST app in the PayPal developer dashboard. |
| `PAYPAL_WEBHOOK_ID` | The id PayPal gives you after you add a webhook (step 6). |
| `PAYPAL_BASE_URL` | `https://api-m.sandbox.paypal.com` |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Used for questions that include an image or PDF. |
| `GROQ_API_KEY`, `GROQ_MODEL` | Used for text-only questions. |
| `OPERATOR_USERNAME`, `OPERATOR_PASSWORD` | The built-in account that can approve actions. Choose your own password. |
| `VIEWER_USERNAME`, `VIEWER_PASSWORD` | The built-in read-only account. |
| `APP_PUBLIC_URL` | Where PayPal can reach the backend. Use `http://localhost:8080` until you set up a tunnel. |
| `FRONTEND_ORIGIN` | `http://localhost:5173` for local work. |
| `DEMO_MODE` | `true` turns on the fault switches. |
| `PAYPAL_USE_SYNTHETIC_DISPUTES` | Optional. `true` uses fixture disputes. |

The frontend reads its own variables from `frontend/.env`, not from the root file. The only one you need is `VITE_AG_LICENSE`, for the AG Studio dashboard. AG Studio has a free trial license. Without it, the Operations page shows a notice and the rest of the app still works. Anything starting with `VITE_` ends up in the browser bundle, so never put a secret there.

### 3. Start a database

Local Postgres:

```bash
docker compose -f infra/docker-compose.yml up -d
```

This matches the default values for `DATABASE_URL`, `DB_USER` and `DB_PASSWORD`.

Supabase instead: in your project, open Connect and copy the Session pooler string (port 5432). The direct connection only works over IPv6, and the transaction pooler needs prepared statements turned off, so the Session pooler is the easy one. Put it in `.env` like this:

```
DATABASE_URL=jdbc:postgresql://aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require
DB_USER=postgres.PROJECT_REF
DB_PASSWORD=the password you set when creating the project
```

Tables are created by Flyway when the backend starts. You don't run any SQL yourself.

### 4. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`. When it is up, `http://localhost:8080/actuator/health` returns `UP`.

### 5. Start the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` and sign in with the operator or viewer account from your `.env`.

### 6. Receive real PayPal webhooks (optional but worth doing)

PayPal can't reach `localhost`, so open a tunnel:

```bash
cloudflared tunnel --url http://localhost:8080
```

It prints an `https://...trycloudflare.com` address. That address changes every time you restart the tunnel.

1. Put the address in `.env` as `APP_PUBLIC_URL` and restart the backend.
2. In the PayPal developer dashboard, open your Sandbox app (type: Merchant), go to Webhooks and add one. The URL is your tunnel address plus `/api/webhooks/paypal`.
3. Subscribe to `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.DENIED`, `PAYMENT.CAPTURE.REFUNDED`, `PAYMENT.CAPTURE.REVERSED` and the `CUSTOMER.DISPUTE.*` events.
4. Copy the Webhook ID into `.env` as `PAYPAL_WEBHOOK_ID` and restart the backend.

If you create a new webhook later, its id changes too, so update `.env` again. A mismatched id makes signature checks fail.

### 7. Try it (Sandbox buyer test sequence)

1. Sign in as the **Operator**.
2. Open **Status** (`/status`, or click **Sandbox mode LIVE** at the bottom of the sidebar) to run PayPal Diagnostics (`GET /api/paypal/diagnostics`). Confirm that `PAYPAL_BASE_URL`, OAuth credentials, `PAYPAL_WEBHOOK_ID`, and your registered webhook URL (`APP_PUBLIC_URL + /api/webhooks/paypal`) all show `PASS`.
3. Open **Demo shop** (`/shop`). For a clean happy path first, click **Reset all to false** on the Fault switches card; or enable **Fail webhook handler** to observe a live discrepancy.
4. Add items to the cart and click **Pay with PayPal Sandbox**.
5. Sign in with your **personal PayPal Sandbox buyer account** from the PayPal Developer Dashboard (never reuse real passwords or share credentials) and click **Complete Purchase / Pay Now**.
6. PayPal redirects to `/shop/return`. Do not refresh: watch the animated loader poll `GET /api/shop/orders/{id}` every 2 seconds (`Payment captured` -> `Waiting for PayPal's confirmation (webhook)` -> `PAID` on happy path, or a 60-second delayed-confirmation alert if no webhook arrives).
7. Inspect the vertical **Payment trace** stepper on `/shop/return` (or expand the order row in **Demo shop** Recent orders) to see timestamps and info tooltips for every step from Order created to Case opened.
8. **Events**, **Exception queue**, and **Demo shop** recent orders auto-refresh every 5 seconds while visible (`updated just now`). When a fault switch causes a failure, open the opened case in the Queue, click **Investigate**, and approve or reject the proposed action.

### Tests and checks

```bash
cd backend && ./mvnw verify        # needs Docker for Testcontainers (H2 & WireMock suites run even without Docker)
cd frontend && npm run lint && npm run build
node scripts/test-return-polling.mjs  # Playwright smoke test for /shop/return polling & 60s timeout
```

### API tests with Postman

The repository includes an API test suite in `postman/PayResolve.postman_collection.json` plus a Portman contract configuration (`postman/portman-config.json`) and generated skeleton (`postman/generated.postman_collection.json`):

1. **`offline`** (`21 requests, 132 assertions` across six sub-folders: `auth`, `queue`, `case`, `events`, `actions`, `benchmark`) — Automated regression, OpenAPI JSON-Schema contract, and security suite requiring zero external credentials. Validates actuator health, CSRF bootstrap/refresh, operator authentication, exception queue & telemetry overview (`/api/stats/overview`), case evidence graphs/timelines/investigations, durable event inbox/stats, actions & immutable audit pagination, CSRF & VIEWER RBAC 403 enforcement, and benchmark telemetry, while checking zero leaked secrets.
2. **`sandbox`** (`11 requests`) — Manual PayPal Sandbox end-to-end suite. Covers OAuth token acquisition, Orders v2 creation, buyer approval redirect, server-side capture, dispute retrieval, webhook cryptographic verification, and webhook replay into `/api/webhooks/paypal`. Requires personal sandbox credentials.

Run the offline suite against a running backend using Newman:

```bash
npx newman run postman/PayResolve.postman_collection.json --folder offline
```

Regenerate Postman request skeletons directly from the backend's live OpenAPI `3.0.1` definition (`/v3/api-docs` -> `postman/generated.postman_collection.json`):

```bash
node scripts/postman-from-openapi.mjs
```

In GitHub Actions CI (`.github/workflows/ci.yml`), the `api-tests` job starts a PostgreSQL service container, launches the backend with `DEMO_MODE=true` and mock values, executes the offline Newman suite, and uploads `postman-report.json` as a build artifact.

### OpenAPI Contract & APIMatic Integration

PayResolve AI exports an OpenAPI `3.0.1` specification directly from Spring Boot `/v3/api-docs` ([OpenApiConfig.java](backend/src/main/java/com/payresolve/common/OpenApiConfig.java)) and synchronizes it across:
- `frontend/src/types/openapi.json` (feeding `npm run codegen` -> `frontend/src/types/api.ts`)
- `apimatic/payresolve-openapi.json` (validated offline via `@redocly/cli lint` with 0 errors)
- `apimatic-portal/src/spec/payresolve-openapi.json` (with APIMatic Docs-as-Code config in `apimatic-portal/src/apimatic.json` and target SDK metadata in `apimatic/apimatic-meta.json`)

> Running APIMatic's cloud CLI (`@apimatic/cli@2` `api validate`, `sdk generate`, `portal generate`, `plugin generate`) requires **Node.js `>=22.14.0`** and interactive **`apimatic auth login`**, so generated SDKs (`@payresolve/sdk`, `payresolve-sdk`) and static portal artifacts are not yet built or published in this repository. See [apimatic/README.md](apimatic/README.md) for setup commands.

---

## How the AI is wired

The investigator calls tools that only read: the PayPal order and capture, webhook events for an order, the shop order, shipment details and the case's evidence. None of them can change data or move money.

Text questions go to Groq. Questions that include an image or PDF go to Gemini. If a provider fails, the request falls back to the other one, and if both fail the app returns the rule-based findings marked as degraded. We only send synthetic Sandbox data to these services. Which provider answered is shown in the AI panel.

## Repository layout

```
backend/    Spring Boot 4.1 (Java 21), Spring AI, Flyway, PostgreSQL
frontend/   React 19, TypeScript, Vite, Tailwind CSS v4
docs/       Requirements, architecture, build plan, rules, working notes
infra/      docker-compose.yml for local Postgres, render.yaml for deployment
postman/    Postman collection and environment
supabase/   keepalive.sql
scripts/    Playwright helpers used while checking pages
.github/workflows/   CI, Supabase keep-alive, Render health ping
```

## Tech stack

- Backend: Java 21, Spring Boot 4.1.2, Spring Security, Spring AI 2.0.2, Flyway, PostgreSQL
- Frontend: React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, React Router, AG Grid, AG Studio, React Flow with dagre for layout
- Database: PostgreSQL, hosted on Supabase's free plan or run locally in Docker
- AI: Groq and Google Gemini
- Hosting: Render
- Payments: PayPal Sandbox (Orders, captures, refunds, webhooks)

## Free-tier upkeep

Supabase pauses free projects that go unused for about a week. The workflow `.github/workflows/supabase-keepalive.yml` inserts one row into `public.keepalive` every two days to prevent that. It needs two repository secrets, `SUPABASE_URL` and `SUPABASE_ANON_KEY`. A second workflow, `render-health-ping.yml`, calls the backend's health endpoint every ten minutes during the judging window, because Render's free web services sleep when idle. It reads a `RENDER_API_URL` secret (required; set it in GitHub Secrets or the workflow will exit with an error).

## Deployment

The backend runs as a Docker web service on Render and the frontend as a Render static site. `infra/render.yaml` describes both. The step-by-step guide and the exact list of environment variables are in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Partner tools used

| Tool | How we use it (verified in this repo) |
|---|---|
| PayPal Sandbox | Order creation, server-side capture, webhook signature verification, refund and capture lookups |
| AG Grid Community (`36.3.0`) | Theming API (`themeQuartz`), sorting, column/quick filtering, pagination, left-pinned IDs, `localStorage` column persistence, and CSV export across Exception Queue, Event Inbox, Actions & Audit Ledger, and Evidence Graph Explorer (`/graph-explorer`) |
| AG Charts Community (`14.3.0`) | Accessible bar, donut, and line/area telemetry charts with `sr-only` data tables and light/dark CSS-token theming across `/operations`, `/benchmark`, and `/cases/:id` |
| AG Studio (`3.1.0`) | Customizable `/operations` dashboard widgets (`createWidgets`) and Operations Assistant panel (trial license via `VITE_AG_LICENSE` optional) |
| APIMatic | Synced SpringDoc OpenAPI `3.0.1` contract (`apimatic/payresolve-openapi.json`), target SDK config (`apimatic/apimatic-meta.json`), and Docs-as-Code portal config (`apimatic-portal/`); cloud SDK/portal CLI generation awaits Node `>=22.14.0` and `apimatic auth login` |
| Render | Docker web service + static site blueprint (`infra/render.yaml`) with schema validation CI workflow (`.github/workflows/render-blueprint-validate.yml`) and health ping (`.github/workflows/render-health-ping.yml`) |
| Postman & Newman | Structured `offline` collection (`auth`, `queue`, `case`, `events`, `actions`, `benchmark` — 21 requests, 132 OpenAPI JSON-schema/RBAC/CSRF assertions), manual `sandbox` folder (11 requests), OpenAPI skeleton generator, and Portman config (`postman/`) |
| Google Gemini & Groq | Dual-engine AI layer: Gemini for multimodal attachment extraction and Groq for rule-grounded root-cause investigations, with deterministic database grounding |
| Supabase | PostgreSQL hosting via Session Pooler with `.github/workflows/supabase-keepalive.yml` |

## Team

We are two people.

| Name | What they worked on |
|---|---|
| Rohan Gangawane ([LinkedIn](https://www.linkedin.com/in/rohan-gangawane/)) | Backend, database, AI integration, deployment |
| Sahil Kedare ([LinkedIn](https://www.linkedin.com/in/sahil-kedare24/)) | Frontend, UI design & design systems, AG Grid & APIMatic integration |

We used AI coding assistants while building this project.

## Reading behind the idea

- [PayPal webhook event names](https://developer.paypal.com/api/rest/webhooks/event-names)
- [Chargebacks911, 2026 Chargeback Field Report](https://chargebacks911.com/2026-chargeback-field-report/)
- [Forkast, the agentic commerce liability gap](https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/)
- [The Paypers, disputes and friendly fraud in agentic commerce](https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce)

## License and notice

MIT, see [LICENSE](LICENSE). PayResolve AI is an independent project. It is not affiliated with, endorsed by or sponsored by PayPal, Inc. PayPal is a trademark of PayPal, Inc.
