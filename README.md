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

### 7. Try it

1. Sign in as the operator.
2. Open Demo shop and switch on "Fail webhook handler".
3. Add something to the cart and pay with the PayPal Sandbox button. Approve the payment with a Sandbox buyer account from your PayPal developer dashboard.
4. Open the Queue. A "Paid, not marked" case should appear.
5. Open it, press Investigate, then approve or reject the suggested action.
6. Open Events to see the stored webhook, and Actions to see the audit log.

### Tests and checks

```bash
cd backend && ./mvnw verify        # needs Docker for Testcontainers
cd frontend && npm run lint && npm run build
```

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

| Tool | How we use it |
|---|---|
| PayPal Sandbox | Order creation, server-side capture, webhook signature verification, refund and capture lookups |
| AG Grid and AG Studio | AG Grid for the exception queue. AG Studio for the Operations dashboard and its assistant |
| Render | Hosting, defined in `infra/render.yaml` |
| Postman | Collection in `postman/` for the PayPal flow and the API |
| Groq and Gemini | The text and multimodal AI providers described above |
| Supabase | Hosted PostgreSQL |

## Team

We are two people.

| Name | What they worked on |
|---|---|
| Rohan Gangawane ([LinkedIn](https://www.linkedin.com/in/rohan-gangawane/)) | Backend, database, AI integration, deployment |
| [Teammate name] ([LinkedIn](add link)) | Frontend |

We used AI coding assistants while building this project.

## Reading behind the idea

- [PayPal webhook event names](https://developer.paypal.com/api/rest/webhooks/event-names)
- [Chargebacks911, 2026 Chargeback Field Report](https://chargebacks911.com/2026-chargeback-field-report/)
- [Forkast, the agentic commerce liability gap](https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/)
- [The Paypers, disputes and friendly fraud in agentic commerce](https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce)

## License and notice

MIT, see [LICENSE](LICENSE). PayResolve AI is an independent project. It is not affiliated with, endorsed by or sponsored by PayPal, Inc. PayPal is a trademark of PayPal, Inc.
