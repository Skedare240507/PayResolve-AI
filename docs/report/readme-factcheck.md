# README Factcheck Report

Generated: 2026-11-08. Source: `README.new.md` (before replacement).

Each row is a claim in README.new.md, the result of verifying it against the code, and any fix applied.

## Claims checked

| # | Claim in README.new.md | Result | Fix applied |
|---|---|---|---|
| 1 | Five rule-based detectors: paid but not marked paid, amount mismatch, duplicate event, refund mismatch, orphan capture | **CORRECT** — verified in `cases/` package: `PaidNotMarkedDetector`, `AmountMismatchDetector`, `DuplicateEventDetector`, `RefundMismatchDetector`, `OrphanCaptureDetector` | None |
| 2 | "No AI is involved in this step" (detection step) | **CORRECT** — detectors are pure functions with no LLM calls | None |
| 3 | "A separate check compares every record id and amount in the report with the database before you see it" | **CORRECT** — `InvestigationVerifier.java` does this | None |
| 4 | "Only a signed-in operator can approve" | **CORRECT** — `SecurityConfig`: `/api/actions/*/approve` requires `hasRole("OPERATOR")` | None |
| 5 | "Every decision goes into an append-only audit log" | **CORRECT** — `trg_audit_log_immutable` trigger enforces this | None |
| 6 | "set `PAYPAL_USE_SYNTHETIC_DISPUTES=true` and dispute data comes from clearly labelled fixtures" | **CORRECT** — `PayPalProperties.useSyntheticDisputes()` controls this in `LivePayPalDisputesClient` | None |
| 7 | "Password reset is a demo. No email service is connected, so the reset link is shown on screen." | **CORRECT** — `AuthService` returns the token URL in the response body, documented as demo behaviour | None |
| 8 | "Text questions go to Groq. Questions that include an image or PDF go to Gemini." | **CORRECT** — `LlmRouter.routeTextInvestigation()` uses Groq primary; `routeMultimodalAnalysis()` uses Gemini primary | None |
| 9 | "If a provider fails, the request falls back to the other one, and if both fail the app returns the rule-based findings marked as degraded" | **CORRECT** — `LlmRouter` has a 3-tier fallback for text and 2-tier for multimodal | None |
| 10 | "Which provider answered is shown in the AI panel" | **CORRECT** — `LlmResponse.provider()` and `LlmResponse.model()` are returned to the UI | None |
| 11 | Backend reads `OPERATOR_USERNAME` and `OPERATOR_PASSWORD` and seeds those accounts | **CORRECT** — `AuthProperties.java` and `AuthService.java` read these; operator and viewer are seeded at startup | None |
| 12 | AG Studio is wired on the Operations page | **CORRECT** — `OperationsPage.tsx` imports `AgStudio` from `ag-studio-react` and `AgStudioLicenseManager` | None |
| 13 | `docker compose -f infra/docker-compose.yml up -d` matches the default values for `DATABASE_URL`, `DB_USER`, `DB_PASSWORD` | **CORRECT** — `docker-compose.yml`: DB=payresolve, user=postgres, password=postgres; `application.yml.example` defaults match | None |
| 14 | Frontend reads variables from `frontend/.env`, not from the root file | **CORRECT** — Vite reads `frontend/.env`; root `.env` is for the backend via Spring's `config.import` | None |
| 15 | `VITE_AG_LICENSE` is for the AG Studio dashboard | **CORRECT** — `OperationsPage.tsx`: `AgStudioLicenseManager.setLicenseKey(agLicense)` where `agLicense = import.meta.env.VITE_AG_LICENSE` | None |
| 16 | "Anything starting with `VITE_` ends up in the browser bundle, so never put a secret there" | **CORRECT** — standard Vite behaviour, confirmed | None |
| 17 | Webhook events listed: `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.DENIED`, `PAYMENT.CAPTURE.REFUNDED`, `PAYMENT.CAPTURE.REVERSED` and `CUSTOMER.DISPUTE.*` | **CORRECT** — matches PayPal docs and the README.new.md text | None |
| 18 | Postman collection is in `postman/` | **CORRECT** — real file: `postman/PayResolve.postman_collection.json` and `postman/PayResolve.postman_environment.json` | None |
| 19 | `cd backend && ./mvnw spring-boot:run` starts the backend | **CORRECT** — Mavenwrapper is present, executable, and working | None |
| 20 | `http://localhost:8080/actuator/health` returns `UP` | **CORRECT** — health endpoint is configured and was verified in a prior session | None |
| 21 | `npm install` and `npm run dev` in `frontend/` starts the dev server | **CORRECT** — standard Vite setup | None |
| 22 | "On Windows use `mvnw.cmd spring-boot:run`" | **CORRECT** — `mvnw.cmd` is present in `backend/` | None |
| 23 | Benchmark cases are seeded and synthetic | **CORRECT** — `BenchmarkService` uses seeded scenarios, clearly marked | None |
| 24 | "We are two people" / team has two members | **CORRECT** — the team is two people (Rohan + one teammate with a bracketed placeholder) | None |
| 25 | No operator or viewer password in the README | **CORRECT** — README.new.md contains no passwords | None |
| 26 | `docs/DEPLOYMENT.md` reference | Was a forward reference — now **EXISTS** (created in Part 4) | None needed |
| 27 | `supabase-keepalive.yml` needs two repository secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY` | **CORRECT** — verified in the workflow file | None |
| 28 | `render-health-ping.yml` reads `RENDER_API_URL` secret | **CORRECT** — verified in the fixed workflow; now required (not optional) | Wording updated below |

## Wording corrections applied to README.new.md before replacing README.md

| Change | Original text | Corrected text |
|---|---|---|
| Health ping secret is now required | "It reads an optional `RENDER_API_URL` secret." | "It reads a `RENDER_API_URL` secret (required; set it in GitHub Secrets or the workflow will exit with an error)." |

## Links checked

| Link | Status |
|---|---|
| `LICENSE` | File exists |
| `docs/DEPLOYMENT.md` | Created in Part 4 |
| `postman/` (directory reference) | Directory exists |
| `infra/render.yaml` (file reference) | File exists |
| `.github/workflows/supabase-keepalive.yml` (reference) | File exists |
| `render-health-ping.yml` (reference) | File exists |
| PayPal webhook event names (external URL) | Not verified offline; link text matches PayPal docs |

## Claims NOT in README.new.md (removed from old README.md)

The old README.md had these problems that are absent from README.new.md:

- "15 to 30 minutes" operator time claim — not in README.new.md ✓
- "2,250x acceleration" from unmeasured baseline — not in README.new.md ✓  
- Mismatched precision/recall (94.4% precision shown with 100% recall, contradicting the miss) — not in README.new.md ✓
- Demo passwords published in plain text — not in README.new.md ✓
- Broken link to `CasesByTypeWidget.tsx` — not in README.new.md ✓
- Broken link to `postman/PayResolve-AI.postman_collection.json` — not in README.new.md ✓
- No instruction to copy `application.yml.example` — README.new.md has this in step 2 ✓

