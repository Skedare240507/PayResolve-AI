# PayResolve — Product Requirements Document

Status: v1.1 | Owner: Rohan | Target: PayPal AI Hackathon, deadline 12:00 pm PST, 12 Nov 2026 (internal submit: 12 Nov 6:00 pm IST; feature freeze: end of 10 Nov)

## 1. One-liner

When a payment, an order, a refund and a delivery disagree, PayResolve shows exactly what happened, with proof, and proposes a safe next step a human can approve.

## 2. Problem

Money events live in several places: PayPal, the merchant's order system, refund records, courier tracking and customer email. When they disagree, someone rebuilds the story by hand across tabs, and a wrong guess costs money (double charge, double refund, missed dispute deadline).

### Scenarios
1. **Paid but marked unpaid.** A customer pays $84.50 and PayPal shows the capture completed. The merchant server crashed during the webhook, so order #10482 stays PENDING. The customer emails "I paid!". The tempting fix, "please pay again", double-charges them. PayResolve links the capture to the order and shows the mismatch with evidence.
2. **Duplicate webhook.** The same event arrives twice; without idempotency, the order ships twice. PayResolve shows both deliveries and proves one business action ran.
3. **"Where's my refund?"** The customer says refunded; the order system says nothing. PayResolve separates requested, pending and completed, and warns against a second refund.
4. **"Item not received" dispute.** Payment data is in PayPal, tracking with the courier, messages in email. PayResolve builds the timeline, lists missing evidence, and a human submits through PayPal's process.
5. **Amount mismatch.** Order total $84.50 but captured $79.50 after a shipping or tax mapping bug. PayResolve flags it for manual review and never edits money silently.
6. **Agent-initiated order mismatch (optional).** An AI agent places an order and the cart or amount changed before capture. Used as a seeded source to connect to the agentic-commerce theme.

## 3. Users

| Persona | Needs |
|---|---|
| Support agent (Meera) | Answer "did this customer pay?" in under a minute without asking engineering |
| Merchant owner (Priya) | Stop double charges and double refunds; know what needs her attention |
| Finance/ops lead (Arjun) | Evidence for disputes and a clean audit trail |
| Hackathon judge | See a working end-to-end flow quickly (read-only viewer login) |

## 4. Goals and non-goals

**Goals**
- G1. Detect payment exceptions from real PayPal Sandbox events.
- G2. Explain each case with a source-linked evidence graph.
- G3. Use AI as a live, tool-using investigator that cites records and admits uncertainty.
- G4. Keep a human in control of every consequential action.
- G5. Report measured results from a seeded benchmark, including misses.

**Non-goals**
- Real money movement, production credentials, multi-tenancy, other PSPs, automated dispute submission, fraud scoring, accounting close.

## 5. Functional requirements

Priority: P0 must ship, P1 should, P2 nice, P3 only if time.

### 5.1 PayPal integration
| ID | Requirement | Pri |
|---|---|---|
| F-01 | Create and capture a Sandbox order from the demo shop; `custom_id` carries the shop order id | P0 |
| F-02 | Receive PayPal webhooks and verify signatures through PayPal's verify endpoint | P0 |
| F-03 | Store every event raw before processing; unverified events never processed | P0 |
| F-04 | Fetch live order, capture and refund details from PayPal | P0 |
| F-05 | Read disputes from the Sandbox Disputes API where available; otherwise use labelled synthetic fixtures | P2 |

### 5.2 Ingestion and idempotency
| ID | Requirement | Pri |
|---|---|---|
| F-10 | Duplicate `paypal_event_id` is accepted (200) and ignored | P0 |
| F-11 | Processing is retryable and has exactly-once business effect | P0 |
| F-12 | Event inbox view with status (received, verified, processed, failed) | P1 |

### 5.3 Demo shop and fault switches
| ID | Requirement | Pri |
|---|---|---|
| F-20 | Minimal shop: products, checkout, order status, refunds, shipment status | P0 |
| F-21 | Demo-only fault switches: fail webhook handler, drop webhook, alter order total, duplicate delivery | P0 |
| F-22 | Fault switches are clearly labelled "demo only" and disabled by config in non-demo mode | P1 |

### 5.4 Detection and cases
| ID | Requirement | Pri |
|---|---|---|
| F-30 | Detectors: `PAID_NOT_MARKED`, `AMOUNT_MISMATCH`, `DUPLICATE_EVENT` | P0 |
| F-31 | Detectors: `REFUND_MISMATCH`, `ORPHAN_CAPTURE` | P1 |
| F-32 | Detector: `DISPUTE_EVIDENCE_GAP` | P2 |
| F-33 | One case per `(type, paypalOrderId)`; reruns update, not duplicate | P0 |
| F-34 | Case lifecycle: OPEN, INVESTIGATING, ACTION_PROPOSED, RESOLVED, DISMISSED | P1 |

### 5.5 Exception queue (AG Grid)
| ID | Requirement | Pri |
|---|---|---|
| F-40 | Grid with type, severity, amount, age, status; sort and filter | P0 |
| F-41 | Row click opens case page; keyboard accessible | P1 |
| F-42 | SLA / age highlighting | P2 |

### 5.6 Evidence graph and timeline (hero)
| ID | Requirement | Pri |
|---|---|---|
| F-50 | Graph of PayPal order, capture, refund, webhook events, shop order, shipment, dispute evidence | P0 |
| F-51 | Missing links drawn as dashed "missing" nodes with explanation | P0 |
| F-52 | Clicking a node shows its raw record and source (PayPal or Shop) | P1 |
| F-53 | Chronological timeline beside the graph | P1 |

### 5.7 AI investigator (hero)
| ID | Requirement | Pri |
|---|---|---|
| F-60 | Agent with read-only tools queries PayPal and merchant data live | P0 |
| F-61 | Structured report: confirmed, conflicts, missing, recommended action, each with evidence ids | P0 |
| F-62 | Verifier rejects statements citing non-existent ids or wrong amounts | P0 |
| F-63 | Explicit `CAUSE_NOT_DETERMINED` when evidence is insufficient | P0 |
| F-64 | Step log shows each tool call, arguments and latency | P1 |
| F-65 | Graceful degradation when the LLM is unavailable | P1 |

### 5.8 Actions and approval
| ID | Requirement | Pri |
|---|---|---|
| F-70 | AI can only propose actions; operator approves or rejects | P0 |
| F-71 | Actions: `SYNC_ORDER_STATUS`, `HOLD_REFUND` (flag), `REQUEST_REVIEW` | P1 |
| F-72 | Executions are idempotent and written to the audit log | P1 |
| F-73 | Dispute evidence checklist generator (human submits) | P2 |

### 5.9 Benchmark and reporting
| ID | Requirement | Pri |
|---|---|---|
| F-80 | 15 to 20 seeded cases with expected outcomes | P1 |
| F-81 | Run produces precision, recall, time-to-cause, grounding rate | P1 |
| F-82 | Results page and README table with real numbers, including failures | P1 |

### 5.10 Access
| ID | Requirement | Pri |
|---|---|---|
| F-90 | Operator login; read-only viewer login for judges, credentials in the submission | P1 |

## 6. Hero feature: Payment Evidence Graph + AI Investigator

Open a broken transaction and see one connected map of everything that touched it. Click Investigate and watch the agent query PayPal and the merchant system, then return: what is confirmed, what conflicts, what is missing, and one safe next step, each statement linked to a record.

**Success moment (demo):** from "customer says paid, system says unpaid" to a sourced explanation and an approved fix in under a minute.

## 7. AI requirements

- Read-only tools only; no tool can move money or change state.
- Output must validate against the report schema; invalid output triggers one retry, then a partial report.
- Every claim cites record ids; the verifier enforces it.
- The model is told to answer `CAUSE_NOT_DETERMINED` rather than guess.
- Low temperature; step cap and timeout; all tool calls logged.
- Provider and model are configuration, not code (default: Google Gemini free tier via Spring AI).

## 8. Non-functional requirements

| Area | Requirement |
|---|---|
| Correctness | Money in integer minor units plus currency; no floats |
| Latency | Webhook returns 200 in under 1 s; case page under 2 s; investigation target under 20 s |
| Reliability | Replaying one event 10 times yields one business effect |
| Security | No secrets in repo; signature verification; CSRF on; synthetic data only |
| Accessibility | Keyboard navigable queue and case page; color is never the only signal |
| Usability | Core flow works on a laptop screen with no instructions |
| Demo safety | Works from a fresh deploy with a seed script; fault switches reset in one click |

## 9. Hackathon alignment

| Criterion (equal weight) | How PayResolve answers |
|---|---|
| Technological Implementation | Real Sandbox flow, verified webhooks, idempotency, tool-calling AI with a verifier |
| Design | Queue to case to graph to approve, one coherent operator workflow |
| Potential Impact | Specific merchant pain; operator interviews for evidence |
| Innovation | Provenance and safe actions instead of a score or a chatbot |
| Presentation | One story, under 3 minutes, shown working |

Partner & prize targets: PayPal, APIMatic Context Plugin, AG Studio (AG Grid prize), Render (paid via hackathon credits, free-plan fallback), Postman. (Skipping: Bryntum, Elastic, Zapier, Astropods, KERNEL, Channel3). Optional theme tie-in: agent-initiated order scenario (F-scenario 6).

Submission requirements: public repo with a visible open-source license; README with setup and run steps or a hosted demo; text description; tools used; YouTube demo under 3 minutes, no copyrighted music or third-party trademarks.

## 10. Demo script (3 minutes)

| Time | Beat |
|---|---|
| 0:00 | Problem: "Customer says paid. System says unpaid. What actually happened?" |
| 0:20 | Real Sandbox checkout; fault switch makes the webhook handler fail |
| 0:50 | Queue shows a new case |
| 1:05 | Open case: evidence graph with the capture linked and the order link missing |
| 1:25 | Investigate: agent steps visible; report with cited evidence |
| 2:00 | Propose and approve `SYNC_ORDER_STATUS`; order turns paid; audit entry |
| 2:20 | Replay the same webhook 5 times; one effect (idempotency) |
| 2:35 | Benchmark numbers on screen, including a miss |
| 2:50 | Close: what it prevents, what is next |

## 11. Success metrics (measured, not guessed)

- Time to identify cause, manual versus PayResolve, on the same scripted cases.
- Detection precision and recall on the seeded cases, with the sample size.
- Duplicate actions prevented in the replay test.
- Grounding rate: share of AI statements backed by valid cited records.
- Dispute evidence completeness for a given reason (if F-32 ships).
- Report real numbers even when small; do not invent market statistics.

## 12. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Sandbox Disputes API needs extra scopes | Test on day 2; fall back to labelled fixtures |
| Webhooks cannot reach localhost | Tunnel in dev; deploy to Render early |
| Idea looks like an admin tool | Lead with the hero demo; live AI investigator; sharp video |
| AI hallucination | Read-only tools, schema, verifier, `CAUSE_NOT_DETERMINED` |
| Scope creep | Cut list in `phases.md`; P0 first |
| LLM outage or rate limit during demo | Cached investigation for seeded demo cases; degrade gracefully |
| Late submission | Feature freeze two days before deadline; submit with a buffer |

## 13. Open questions

- Can the Sandbox Disputes API be exercised with default scopes? (Fall back to synthetic labelled fixtures if not)
