# Phase 0 Baseline Report: PayResolve AI

**Date:** 2026-11-08  
**Environment:** Windows (PowerShell), Java 24 / Spring Boot 4.1.2, Node 22 / React 19, Supabase PostgreSQL (Session Pooler)

---

## 1. Git State Baseline
- **Branch:** `main`
- **Recent Commits:**
  1. `50ba5b6` - feat(benchmark): evaluation suite and metrics
  2. `30af46d` - chore(deploy): render
  3. `ca45aa1` - feat(db): V5 hardening and code updates
  4. `ac35492` - feat(operations): AG Studio dashboard with custom PayResolve widgets and AI agent
  5. `0b579e5` - feat(frontend): wire real API with TanStack Query, AG Grid and React Flow
- **Working Tree:** Clean (verified zero uncommitted changes). Secrets and `.env` are properly ignored and not tracked.

---

## 2. Automated Build & Verification Baselines
- **Backend Verification:**
  - Command: `./mvnw test -B`
  - Result: **PASS** (Spring Boot 4.1.2, Spring Security 7, Spring Data JDBC, WireMock 3.13.2, Testcontainers, Flyway, BenchmarkServiceTest).
- **Frontend Code Quality & Compilation:**
  - Command: `npm --prefix frontend run lint` (oxlint)
  - Result: **PASS** (0 errors, 0 warnings across 43 source files).
  - Command: `npm --prefix frontend run build` (tsc -b && vite build)
  - Result: **PASS** (Production bundle generated in 4.89s, 0 TypeScript errors).

---

## 3. Database & Runtime Health Baseline
- **Actuator Health Probe (`GET /actuator/health`):**
  - Status: **HTTP 200 UP** (`{"status":"UP","groups":["liveness","readiness"]}`).
- **Flyway Migrations Status:**
  - All 5 schema migrations validated against Supabase PostgreSQL:
    - `V1__init.sql`: Core 14 tables (cases, webhook_events, shop_orders, audit_log, etc.)
    - `V2__keepalive.sql`: Automation keepalive table with RLS
    - `V3__demo_fault_switches.sql`: Persistence for demo switches
    - `V4__case_status_updates.sql`: Case update tracking
    - `V5__rls_and_hardening.sql`: RLS enforcement on all app tables and check constraints
  - Migration rank: Schema `public` is up-to-date at version 5.

---

## 4. Visual Baseline Screenshot Inventory (`docs/report/screens/before/`)
All 21 routes captured at 1440px wide in both Light and Dark modes (42 screenshots):
1. `landing-light.png` / `landing-dark.png` (`/`)
2. `signin-light.png` / `signin-dark.png` (`/signin`)
3. `forgot-password-light.png` / `forgot-password-dark.png` (`/forgot-password`)
4. `check-email-light.png` / `check-email-dark.png` (`/check-email`)
5. `reset-password-light.png` / `reset-password-dark.png` (`/reset-password`)
6. `password-updated-light.png` / `password-updated-dark.png` (`/password-updated`)
7. `signed-out-light.png` / `signed-out-dark.png` (`/signed-out`)
8. `queue-light.png` / `queue-dark.png` (`/queue`)
9. `case-workspace-light.png` / `case-workspace-dark.png` (`/case/PR-0142`)
10. `events-light.png` / `events-dark.png` (`/events`)
11. `demo-shop-light.png` / `demo-shop-dark.png` (`/shop`)
12. `actions-audit-light.png` / `actions-audit-dark.png` (`/actions`)
13. `benchmark-light.png` / `benchmark-dark.png` (`/benchmark`)
14. `operations-light.png` / `operations-dark.png` (`/operations`)
15. `sandbox-unreachable-light.png` / `sandbox-unreachable-dark.png` (`/status`)
16. `empty-error-feedback-states-light.png` / `empty-error-feedback-states-dark.png` (`/ui-states`)
17. `loading-states-light.png` / `loading-states-dark.png` (`/ui-states?tab=loading`)
18. `403-viewer-access-light.png` / `403-viewer-access-dark.png` (`/403`)
19. `404-not-found-light.png` / `404-not-found-dark.png` (`/404`)
20. `500-server-error-light.png` / `500-server-error-dark.png` (`/500`)
21. `redirecting-light.png` / `redirecting-dark.png` (`/redirecting`)

---

## 5. Known Defects Identified for Phase 1 Remediation

| Page / Component | Identified Defect | Remediation Target |
|---|---|---|
| **Exception Queue (`/queue`)** | KPI cards show static numbers (12, 3, 7, 38 min); table has fallback mock rows (`PR-0142` etc.) mixed in; Low severity lacks pill styling; ages show static values; pagination text missing; orphan captures truncated without full copy tooltip. | Compute KPIs dynamically from API; purge mock row fallback; style Low severity as border pill; real relative age; add pagination text `Showing x of y`; full copyable tooltip on orphan captures. |
| **Case Workspace (`/case/:id`)** | Header shows hardcoded 'Resolved' & 'Low' for PR-0142; Investigate button active on resolved cases; evidence graph overlapping rightward edge labels; AI investigator panel shows static text without live steps; timeline/dispute empty. | Dynamic header from case record; disable investigate on resolved; Dagre left-to-right layered layout with >=80px node spacing & edge label pills; live step log from API; real timeline and dispute checklist. |
| **Operations (`/operations`)** | AG Studio dashboard widgets need full rendering; 2,250x multiplier based on unmeasured 45min assumption; AI agent lacks backend endpoint. | Render 3 PayResolve themed custom widgets; replace invented speedup multiplier with measured median latency ($n=18$); wire agent chat to backend `/api/agent/chat`. |
| **Event Inbox (`/events`)** | Event ID wraps (`WH- SMOKE- 001`); raw JSON clipped/mis-indented without copy button; delivery count display missing. | `whitespace-nowrap font-mono` with ellipsis & copy button; `JSON.stringify(..., null, 2)` pretty-print; display `delivery_count` ("delivered n times"). |
| **Demo Shop (`/shop`)** | Fault switches are unstyled native checkboxes; cart stepper and totals static; PayPal return flow needs complete lifecycle and animated logo loader. | Accessible `role="switch"` toggles with backend persistence; active cart state & order calculation; PayPal sandbox approval return & cancel pages with brand loader. |
| **Actions & Audit (`/actions`)** | "Seed sample actions" shows in production; audit log username and timestamps truncated. | Guard dev button with `demo-mode` labelled "Demo helper"; full username + ISO formatted audit timestamp. |
| **Benchmark (`/benchmark`)** | Precision and recall metrics need explicit confusion matrix counts; median time must separate rule-only vs AI investigator. | Detailed confusion matrix (TP, FP, FN); separate rule-only (1.2 ms) vs AI investigator (1.4 s); show deliberate Case 8 miss. |

