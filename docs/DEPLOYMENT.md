# Deploying PayResolve AI

Step-by-step guide for a fresh deployment on Render using the files in this repository.

---

## A. Accounts and keys

Collect these before you start. You need them all before you can redeploy with live values.

| What | Where to get it |
|---|---|
| **PayPal Sandbox app** | [developer.paypal.com](https://developer.paypal.com) → My Apps & Credentials → Create App. App type: **Merchant**. This gives you a Client ID and a Secret. Also create a personal sandbox buyer account to approve test payments. |
| **Supabase project** | [supabase.com](https://supabase.com) → New project. After creation, go to Project Settings → Database → Connection String → Session Pooler (port 5432). Copy the JDBC string and project password. Also copy the project URL and anon public key from the API section. |
| **Groq API key** | [console.groq.com](https://console.groq.com) → API Keys → Create API Key. No credit card needed. |
| **Gemini API key** | [aistudio.google.com](https://aistudio.google.com) → Get API key. Free tier. |
| **AG Studio trial license** | [ag-grid.com/ag-studio](https://www.ag-grid.com/ag-studio/) → free trial. This is a client-side key that ends up in the browser bundle. Without it the Operations page shows a license notice; all other pages work. |
| **Render account** | [render.com](https://render.com). Connect your GitHub account. Apply hackathon credits in the dashboard. |

---

## B. GitHub repository secrets

Go to your repository on GitHub → Settings → Secrets and variables → Actions → New repository secret.

Add exactly these three secrets. No other GitHub secrets are needed. CI uses mock PayPal values declared in the workflow file itself, and Render picks up its secrets from the Render dashboard.

| Secret name | Value | Required by |
|---|---|---|
| `SUPABASE_URL` | `https://<project-ref>.supabase.co` | `supabase-keepalive.yml` — without this the keep-alive workflow exits with an error |
| `SUPABASE_ANON_KEY` | Your Supabase project's anon public key | `supabase-keepalive.yml` — same |
| `RENDER_API_URL` | Full https URL of the deployed API, e.g. `https://payresolve-api.onrender.com` (no trailing slash) | `render-health-ping.yml` — without this the health-ping workflow exits with an error |

After adding `SUPABASE_URL` and `SUPABASE_ANON_KEY`, trigger the keep-alive workflow once by hand: Actions → Supabase Keep-Alive → Run workflow. Confirm it exits 0.

---

## C. Render — backend service

1. In Render, create a new **Web Service**. Connect the GitHub repository.
2. Render reads `infra/render.yaml` from the repository. If using the blueprint, the service is configured automatically. If creating manually, set:
   - **Runtime:** Docker
   - **Dockerfile path:** `./backend/Dockerfile`
   - **Docker context:** `./backend`
   - **Health check path:** `/actuator/health`
   - **Plan:** Free (switch to Starter only after hackathon credits are applied)

3. Set these environment variables in the Render dashboard. Variables marked **secret** must be set there; they are not in the YAML file.

| Variable | Value | Secret? |
|---|---|---|
| `SERVER_PORT` | `8080` | No — already in render.yaml |
| `PORT` | `8080` | No — already in render.yaml |
| `DEMO_MODE` | `true` | No — already in render.yaml |
| `SESSION_TIMEOUT` | `30m` | No — already in render.yaml |
| `FLYWAY_ENABLED` | `true` | No — already in render.yaml |
| `PAYPAL_BASE_URL` | `https://api-m.sandbox.paypal.com` | No — already in render.yaml |
| `AI_PROVIDER` | `groq` | No — already in render.yaml |
| `GROQ_MODEL` | `openai/gpt-oss-120b` | No — already in render.yaml |
| `GEMINI_MODEL` | `gemini-3.5-flash` | No — already in render.yaml |
| `OPERATOR_USERNAME` | `operator` | No — already in render.yaml |
| `VIEWER_USERNAME` | `viewer` | No — already in render.yaml |
| `PAYPAL_USE_SYNTHETIC_DISPUTES` | `false` (or `true` if you lack dispute scopes) | No — already in render.yaml |
| `DEMO_RESET_LINKS` | `true` | No — already in render.yaml |
| `DEMO_BUYER_EMAIL` | Optional sandbox buyer email for Demo Shop card | No — optional |
| `DEMO_BUYER_PASSWORD` | Optional sandbox buyer password for Demo Shop card | No — optional |
| `FRONTEND_ORIGIN` | Full https URL of the UI, e.g. `https://payresolve-ui.onrender.com` | **Set in dashboard** |
| `APP_PUBLIC_URL` | Full https URL of the API, e.g. `https://payresolve-api.onrender.com` | **Set in dashboard** |
| `DATABASE_URL` | Supabase Session pooler JDBC string, e.g. `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` | **Secret** |
| `DB_USER` | `postgres.<project-ref>` | **Secret** |
| `DB_PASSWORD` | Your Supabase database password | **Secret** |
| `PAYPAL_CLIENT_ID` | From the PayPal developer dashboard | **Secret** |
| `PAYPAL_CLIENT_SECRET` | From the PayPal developer dashboard | **Secret** |
| `PAYPAL_WEBHOOK_ID` | From the PayPal webhook you create in step E | **Secret** (set after step E) |
| `GEMINI_API_KEY` | Your Gemini key | **Secret** |
| `GROQ_API_KEY` | Your Groq key | **Secret** |
| `OPERATOR_PASSWORD` | A password of your choice | **Secret** |
| `VIEWER_PASSWORD` | A password of your choice | **Secret** |

> **Why FRONTEND_ORIGIN and APP_PUBLIC_URL are not in the YAML:** Render's `fromService ... property: host` returns the hostname without the `https://` scheme, which would break CORS validation and PayPal redirect URLs. Set both to full https URLs in the dashboard after both services are live.

---

## D. Render — frontend service

1. Create a new **Static Site** in Render, connected to the same repository.
2. Set:
   - **Build command:** `cd frontend && npm ci && npm run build`
   - **Publish directory:** `./frontend/dist`
   *(Static sites deploy globally on Render's CDN edge and do not take a `region` property in `infra/render.yaml`.)*

3. Add these rewrite rules (in the Render dashboard under Redirects/Rewrites, in this order):

   | Source | Destination | Type |
   |---|---|---|
   | `/api/*` | `https://payresolve-api.onrender.com/api/:splat` | Rewrite |
   | `/actuator/*` | `https://payresolve-api.onrender.com/actuator/:splat` | Rewrite |
   | `/*` | `/index.html` | Rewrite |

   **Why these rewrites exist:** The UI and API are on different Render subdomains. `onrender.com` is on the Public Suffix List, so browsers treat them as cross-site. The session cookie set by the API would not be sent on cross-site requests. By rewriting `/api/*` to point at the API, the browser only ever talks to `payresolve-ui.onrender.com` (one origin). The session cookie works without `SameSite=None` or CORS pre-flight for auth calls.

   > **If Render gives your API a different name:** update the destination URLs in the rewrite rules to match the actual https URL Render assigns.

4. Set these environment variables:

   | Variable | Value | Secret? |
   |---|---|---|
   | `NODE_VERSION` | `22` | No — already in render.yaml |
   | `VITE_AG_LICENSE` | Your AG Studio license key | **Set in dashboard** (ends up in the browser bundle; not a backend secret) |

   `VITE_API_BASE_URL` is **not set**. The frontend's `api.ts` falls back to an empty string (relative path) when the variable is absent, so all `/api/*` calls go to the same origin and are rewritten by Render.

5. **Security Headers & CSRF Protection:**
   The API and Render edge CDN enforce strict security headers:
   - `Content-Security-Policy`: Scoped to Google Fonts, AG Charts/Studio stylesheets, and verified backend/AI APIs.
   - `X-Content-Type-Options: nosniff` and `frame-ancestors 'none'`.
   - CSRF protection: Mutating endpoints require `X-XSRF-TOKEN` (fetched from `/api/auth/csrf` and handled automatically by `frontend/src/lib/api.ts`).
   - Attachment controls: Uploads are restricted to `OPERATOR` accounts, verified by magic bytes (PNG, JPEG, PDF), and capped at 5 files per case.

---

## E. After both services are live

1. Copy the UI URL from the Render dashboard. Set `FRONTEND_ORIGIN` on the **API service** to that full https URL.
2. Copy the API URL. Set `APP_PUBLIC_URL` on the **API service** to that full https URL.
3. Also set `RENDER_API_URL` as a **GitHub Secret** to that same API URL.
4. In the PayPal developer dashboard, go to your Sandbox app → Webhooks → Add Webhook.
   - URL: `https://<api-url>.onrender.com/api/webhooks/paypal` (direct to the API — the webhook comes from PayPal, not through the UI)
   - Subscribe to these events:
     - `CHECKOUT.ORDER.APPROVED`
     - `PAYMENT.CAPTURE.COMPLETED`
     - `PAYMENT.CAPTURE.DENIED`
     - `PAYMENT.CAPTURE.REFUNDED`
     - `PAYMENT.CAPTURE.REVERSED`
     - `CUSTOMER.DISPUTE.CREATED`
     - `CUSTOMER.DISPUTE.UPDATED`
     - `CUSTOMER.DISPUTE.RESOLVED`
5. Copy the Webhook ID from the PayPal dashboard. Set it as `PAYPAL_WEBHOOK_ID` on the **API service** in Render.
6. Trigger a redeployment of the API service so the new values take effect.

---

## F. Smoke test checklist (End-to-End Personal Sandbox Account Sequence)

Run these after a successful deployment, in order. Look up your personal Sandbox buyer email inside the PayPal Developer Dashboard (Testing Tools -> Sandbox Accounts); never commit or share any sandbox password.

1. - [ ] **Health** — `GET https://<api>.onrender.com/actuator/health` returns `{"status":"UP"}`.
2. - [ ] **Sign in** — Open the UI, sign in as operator. The queue page loads. No CORS error in the browser console.
3. - [ ] **PayPal Diagnostics** — Click **Sandbox mode LIVE** in the bottom sidebar (or visit `/status`). Confirm all configuration checks (`PayPal Sandbox Base URL`, `PayPal Client Credentials & OAuth Token`, `PAYPAL_WEBHOOK_ID Configured`, and `Registered Webhook URL & Events`) show **PASS**. Fix any reported hints (such as mismatched webhook URL or missing event subscriptions) before testing checkout.
4. - [ ] **Clean Happy-Path Payment (Personal Sandbox Account)**
   - Open **Demo shop** (`/shop`). Verify Fault switches are reset (**Reset all to false**).
   - Add an item to the cart and click **Pay with PayPal Sandbox**.
   - PayPal Sandbox opens: log in using your **personal Sandbox buyer account** from your PayPal Developer Dashboard and complete approval.
   - PayPal redirects to `/shop/return` on the UI domain (not the API URL).
5. - [ ] **Live Return Polling & Payment Trace** — Do **not** refresh `/shop/return`. Verify the animated PayResolve loader shows **Payment captured**, then **Waiting for PayPal's confirmation (webhook)** while polling `GET /api/shop/orders/{id}` every 2 seconds, and transitions automatically to **Payment confirmed (`PAID`)** once `PAYMENT.CAPTURE.COMPLETED` is processed. Scroll down on `/shop/return` (or expand the order row in **Demo shop** Recent orders) to inspect the 8-step **Payment trace** stepper and hover an info icon (`info`) to view its explanation.
6. - [ ] **Auto-Refresh Confirmation** — Open **Events** (`/events`) and **Queue** (`/queue`). Verify recent orders, events, and cases show the live `"updated just now"` indicator refreshing every 5 seconds while the browser tab is visible. Verify `PAYMENT.CAPTURE.COMPLETED` appears with `verified: true` and `status: Processed`.
7. - [ ] **Fault Injection & Case Flow (`Paid, not marked`)**
   - Return to **Demo shop**, toggle **Fail webhook handler** ON, and complete another checkout with your personal Sandbox buyer account.
   - On `/shop/return`, observe **Payment captured**, **Webhook received (verified)**, and **Webhook processed (FAILED)** in the vertical trace stepper (or after 60s if `Drop webhook` is active, verify the delayed confirmation notice with a direct link to the Queue).
   - Open **Queue** (auto-refreshes every 5s): a **`Paid, not marked`** case opens automatically.
8. - [ ] **Investigation & Approval** — Open the case (`/case/{id}`), press **Investigate** (verifying `groq` or `gemini` provider badge), then approve or reject the proposed action. Confirm `audit_log` records the decision under **Actions**.
9. - [ ] **Viewer gets 403** — Sign out, sign in as viewer, verify `/status` reports that live PayPal diagnostics require Operator role, and confirm `GET /api/paypal/diagnostics` and `POST /api/actions/{id}/approve` return HTTP **403**.
10. - [ ] **Theme toggle** — Toggle dark mode. The theme switches smoothly across all pages, grids, and steppers.
11. - [ ] **Keep-alive** — Go to GitHub Actions → Supabase Keep-Alive → Run workflow. It should exit 0.

---

## G. Free-tier notes

- **Render free web services** sleep after about 15 minutes of idle traffic. The first request after sleep takes about 60 seconds to get a response while the instance wakes up. The `render-health-ping.yml` workflow pings `/actuator/health` every 10 minutes during the judging window (8–16 Nov 2026 UTC) to keep the instance warm. It retries three times with 20-second waits before failing, so a single cold-start ping does not break the workflow. Outside the window the workflow exits early.
- **Supabase free tier** pauses a project if no data operations occur for about 7 days. The `supabase-keepalive.yml` workflow inserts one row into `public.keepalive` every 2 days. If the project does pause, it auto-resumes on the next request (but the first request will be slow). You can also un-pause it manually from the Supabase dashboard.
- **Groq and Gemini free limits** can throttle under load. The app falls back to the other provider if the primary fails, and falls back to the rule-based findings if both are unavailable. The report is marked `degraded` in that case.
- Render free services share CPU with other users. Response times may vary.

---

## H. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Login succeeds but every subsequent API call redirects to `/sign-in` | Session cookie not sent — the UI is on a different origin than the API and there is no rewrite proxy | Confirm the Render rewrite rules are in place and in the right order. Confirm `FRONTEND_ORIGIN` on the API matches the UI's exact https URL. |
| `CORS error: Access-Control-Allow-Origin missing` | `FRONTEND_ORIGIN` on the API service is not set, or is set without `https://` | Set it to the full UI URL including scheme, e.g. `https://payresolve-ui.onrender.com` |
| Webhook returns 401 or signature verification fails | `PAYPAL_WEBHOOK_ID` is wrong or missing, or the webhook URL points at the wrong endpoint | The webhook URL must end with `/api/webhooks/paypal` on the API domain. `PAYPAL_WEBHOOK_ID` must match the ID shown in the PayPal dashboard for that webhook. |
| `No open port detected` in Render logs | The Docker container is not listening on the expected port | Confirm `PORT=8080` and `SERVER_PORT=8080` are both set in the Render dashboard. The Dockerfile `EXPOSE`s 8080. |
| Flyway error `Found non-empty schema(s) ... without schema history table` | A table exists in the database that was not created by Flyway, and `baseline-on-migrate` is false | Delete the unexpected tables, or restore from a clean state. Do not set `baseline-on-migrate=true` unless you know exactly what it will skip. |
| Flyway checksum error | A migration file was edited after it was applied | Never edit applied migrations. Revert the file to its committed state. |
| AG Studio license banner on Operations page | `VITE_AG_LICENSE` is not set, or was set after the frontend was already built | Set `VITE_AG_LICENSE` in the Render dashboard for the UI service, then trigger a rebuild. |
| PayPal return URL sends buyer to the API URL instead of the shop page | Old version of `ShopOrderService.java` used `APP_PUBLIC_URL` for return/cancel | Pull the latest commit and redeploy. The fixed version uses `FRONTEND_ORIGIN` + `/shop/return` and `/shop/cancel`. |

---

## I. API tests with Postman

PayResolve provides a Postman collection at `postman/PayResolve.postman_collection.json`, a Portman contract test config at `postman/portman-config.json`, and an OpenAPI-derived skeleton at `postman/generated.postman_collection.json`:

1. **`offline`** — Automated suite for CI and contract testing (`21 requests, 132 assertions`) organized into `auth`, `queue`, `case`, `events`, `actions`, and `benchmark` sub-folders. Requires no PayPal credentials or external services:
   - `auth`: Actuator health (`/actuator/health`), CSRF bootstrap (`/api/auth/csrf`), operator login (`/api/auth/login`), and profile check (`/api/auth/me`).
   - `queue`: Exception queue pagination (`/api/cases`) and operational telemetry overview (`/api/stats/overview`).
   - `case`: Case details (`/api/cases/{id}`), evidence graph (`/graph`), chronological timeline (`/timeline`), and latest persisted investigation (`/investigations/latest`).
   - `events`: Durable webhook events inbox (`/api/events`) and stats (`/api/events/stats`).
   - `actions`: Remediation actions (`/api/actions`), immutable audit log (`/api/audit`), CSRF write security (`403 Forbidden`), Viewer login + CSRF refresh + Viewer RBAC (`403 Forbidden` on approvals), and session logout (`/api/auth/logout`).
   - `benchmark`: Benchmark precision, recall, and grounding telemetry (`/api/benchmark/latest`).
   - All offline requests assert HTTP status, response time, JSON Content-Type, OpenAPI JSON-Schema contracts, and zero leaked secrets.

2. **`sandbox`** — Manual suite (`11 requests`) for PayPal Sandbox testing with real credentials (OAuth2 Bearer token, Orders v2, capture, refunds, disputes, webhook signature verification, and webhook replay into `/api/webhooks/paypal`).

### Running Newman locally

To execute the offline suite against a local or deployed backend:

```bash
# Against local backend
npx newman run postman/PayResolve.postman_collection.json --folder offline

# Against deployed Render backend
npx newman run postman/PayResolve.postman_collection.json --folder offline \
  --env-var baseUrl=https://payresolve-api.onrender.com \
  --env-var operatorPassword=your_deployed_operator_password \
  --env-var viewerPassword=your_deployed_viewer_password
```

### Keeping Postman in sync with OpenAPI

When backend endpoints are modified, regenerate request skeletons directly from the live OpenAPI `3.0.1` specification (`/v3/api-docs` -> `postman/generated.postman_collection.json`):

```bash
node scripts/postman-from-openapi.mjs
```

Optionally regenerate contract test skeletons with `@apideck/portman`:

```bash
npx @apideck/portman -l apimatic/payresolve-openapi.json -c postman/portman-config.json -o postman/portman-contract.postman_collection.json
```

### Continuous Integration (CI)

The GitHub Actions workflow `.github/workflows/ci.yml` includes an `api-tests` job that:
1. Spawns an ephemeral PostgreSQL 16 container.
2. Starts the Spring Boot backend with `DEMO_MODE=true` and mock parameters.
3. Polls `/actuator/health` until the service is healthy.
4. Executes the offline Newman suite and exports the JSON test report (`postman-report.json`).
5. Uploads the report as a workflow artifact named `postman-api-test-report`.

---

## J. Render Blueprint Validation & Optional Local Render MCP

### Validating `infra/render.yaml`

`infra/render.yaml` includes the YAML schema directive `# yaml-language-server: $schema=https://render.com/schema/render.yaml.json` for IDE autocomplete/validation.

- **Automatic CI check:** Whenever `infra/render.yaml` changes, `.github/workflows/render-blueprint-validate.yml` validates the parsed YAML against Render's published JSON Schema (`https://render.com/schema/render.yaml.json`).
- **Workspace validation with Render CLI (`v2.36.0`+):**
  If you have authenticated the Render CLI locally (`render login` and `render workspace set <workspace-id>`), run:
  ```bash
  render blueprints validate infra/render.yaml
  ```
- **Offline JSON-Schema validation (zero credentials):**
  ```bash
  curl -fsSL https://render.com/schema/render.yaml.json -o render-schema.json
  npx --yes js-yaml infra/render.yaml > render-doc.json
  npx --yes -p ajv-cli -p ajv-formats ajv validate -s render-schema.json -d render-doc.json --spec=draft2020 --strict=false -c ajv-formats
  ```

### Optional Local Render MCP Server (Developer Tooling Only)

You can optionally connect a local AI coding assistant (Cursor, Claude Desktop, Claude Code) to Render's hosted MCP endpoint for inspecting deploys and logs locally. **Never put Render API keys into the app runtime, `.env`, or GitHub Actions:**

```json
{
  "mcpServers": {
    "render": {
      "url": "https://mcp.render.com/mcp",
      "headers": {
        "Authorization": "Bearer <YOUR_PERSONAL_RENDER_API_KEY>"
      }
    }
  }
}
```



