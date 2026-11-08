# PayResolve — Phases and Plan

Window: Mon 2 Nov to Thu 12 Nov 2026 (11 days). Hard deadline: 12:00 pm PST, 12 Nov = about 1:30 am IST, 13 Nov.
**Target submit time: Thu 12 Nov, 6:00 pm IST (4:30 am PST) at the latest. Do not plan to use the last night.**
Feature freeze: end of 10 Nov. Only bug fixes, docs and video after that. Current date: 7 Nov. Hard deadline: 12 Nov 12:00 pm PST (submit by 12 Nov 6:00 pm IST).

## Phase overview

| Phase | Dates | Goal | Exit criteria |
|---|---|---|---|
| 0. Setup and de-risk | 2 to 3 Nov | Everything external works | Sandbox order created, approved and captured; webhook received on a public URL; Disputes API checked; repo, CI and DB running |
| 1. Payment and ingestion | 3 to 5 Nov | Real events flow in safely | Demo shop checkout works; verified webhooks stored; replay yields one effect |
| 2. Cases and detection | 5 to 7 Nov | The system notices problems | Three P0 detectors pass table tests; fault switches create each case |
| 3. Queue and evidence graph | 7 to 8 Nov | The hero screen exists | AG Grid queue; case page with graph and timeline |
| 4. AI investigator | 8 to 9 Nov | Live investigation with proof | Tool-calling agent, schema, verifier, step log in UI |
| 5. Actions and approval | 9 to 10 Nov | Human-controlled fixes | Propose, approve, execute idempotently, audit |
| 6. Harden, benchmark, ship | 10 to 12 Nov | Judges can run and love it | Deployed; benchmark numbers; README; license; video; submitted |

## Day-by-day

| Day | Date | Plan | Done when |
|---|---|---|---|
| 1 | Mon 2 Nov | Scaffold backend (target Boot 4.1.x, currently 3.4.2; fix in next step) and frontend (built by teammate; mock data + localStorage auth; to wire to backend); GitHub repo with LICENSE; CI builds; sandbox app, credentials, Postman | `./mvnw verify` and `npm run build` green in CI |
| 2 | Tue 3 Nov | PayPal client: token, create order, capture; tunnel up; webhook endpoint stub receives a real event; **check Disputes API access** | A real `PAYMENT.CAPTURE.COMPLETED` is logged |
| 3 | Wed 4 Nov | Signature verification; `webhook_events` inbox with unique event id; worker with SKIP LOCKED; deploy skeleton to Render (API, UI; Supabase free Postgres via Session pooler + keepalive Action) | Duplicate event is ignored; app reachable on a public URL |
| 4 | Thu 5 Nov | Demo shop (products, checkout UI, order status); `AWAITING_CONFIRMATION` to `PAID` on processed event; fault switches | Full checkout works; a fault leaves the order unpaid |
| 5 | Fri 6 Nov | Case model; detectors `PAID_NOT_MARKED`, `AMOUNT_MISMATCH`, `DUPLICATE_EVENT`; replay test | Table-driven detector tests green |
| 6 | Sat 7 Nov | Current checkpoint (7 Nov): align docs & decisions; prepare Boot 4.1.x upgrade; wire Supabase session pooler; begin wiring frontend to backend; AG Grid queue | Queue shows seeded and live cases |
| 7 | Sun 8 Nov | Evidence builder; graph endpoint; React Flow graph with missing-link nodes; timeline | Case page tells the story without AI |
| 8 | Mon 9 Nov | Investigator: Spring AI tools (Google Gemini free tier; configurable by env vars), structured report, verifier, step log; UI panel; cached demo investigations | Investigate works on all P0 cases |
| 9 | Tue 10 Nov | Actions: propose, approve, execute, audit; login and viewer role; **FEATURE FREEZE at end of day** | End-to-end demo path runs twice in a row |
| 10 | Wed 11 Nov | Benchmark run and numbers; README; screenshots; Devpost text; record video (take 1); fix bugs | README run steps verified from a clean clone |
| 11 | Thu 12 Nov | Final video, final deploy check, submit by 6:00 pm IST; keep buffer for upload and form issues | Submission confirmed on Devpost |

## Phase details

### Phase 0: Setup and de-risk
- Repo with `backend/`, `frontend/`, `docs/`, `infra/`, MIT or Apache-2.0 LICENSE, `.env.example`.
- Spring Initializr / pom: Web, Validation, Security, Data JPA, Flyway, PostgreSQL (Supabase session pooler), Actuator, Spring AI starter (Google Gemini free tier default, configurable via env vars).
- Vite React TS template; Tailwind v4 via `@tailwindcss/vite`; TanStack Query; React Router.
- PayPal Sandbox: app, buyer and merchant accounts, webhook registration, Postman collection.
- **De-risk checklist:** token works, order and capture work, webhook reaches the public URL, Disputes API reachable or fixtures decided.

### Phase 1: Payment and ingestion
- `paypal` client with timeouts and token cache. `ingest` inbox and worker.
- Idempotency proven by a test that replays one event 10 times.

### Phase 2: Cases and detection
- `Detector` interface; one class per case type; shared `Snapshot` input.
- Fault switches map one-to-one to scenarios, so each demo scenario is reproducible in two clicks.

### Phase 3: Queue and evidence graph
- Evidence items normalised into one shape; graph derived at read time.
- Design pass for the case page: graph left, timeline and AI panel right.

### Phase 4: AI investigator
- Tools return trimmed, id-bearing JSON. Verifier checks ids and amounts.
- Cache one successful investigation per seeded case to protect the live demo.

### Phase 5: Actions and approval
- No money-moving action in v1. Idempotency key on every proposal.

### Phase 6: Harden, benchmark, ship
- Benchmark: 15 to 20 cases; record real precision, recall, grounding rate, time-to-cause.
- Deployment: fresh deploy plus seed script works; fault reset button works.
- Submission pack: repo link, deployed URL, viewer credentials, video URL, text description, tools used.

## Cut list (cut from the top when behind)

1. Bryntum, Elastic, Zapier, Astropods, KERNEL, Channel3 (all skipped). APIMatic Context Plugin retained.
2. Agent-initiated order scenario
3. `DISPUTE_EVIDENCE_GAP` and the checklist
4. `REFUND_MISMATCH` and `ORPHAN_CAPTURE` detectors
5. Dark mode, extra filters, SLA highlighting
6. Live LLM during the video (use cached investigations; still real code paths)

Never cut: real Sandbox capture, verified webhooks, idempotency, the three P0 detectors, the evidence graph, the investigator with verifier, human approval, the README and the video.

## Daily rhythm

- Start: read `memory.md`, pick the day's two outcomes.
- During: small commits, tests with each feature.
- End: update `memory.md` (status, decisions, gotchas), push, deploy.
- If a day's exit criterion slips by more than half a day, trigger the next cut.

## Definition of done (project)

- Public repo with license; README run steps work from a clean clone.
- Deployed demo with viewer login; seed and reset work.
- Benchmark numbers published in README.
- Video under 3 minutes uploaded to YouTube and linked.
- Devpost submission complete and confirmed before the buffer time.
