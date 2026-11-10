---
title: PayResolve AI Developer Documentation
description: "API architecture, OpenAPI 3.0.1 contract, and APIMatic Docs-as-Code configuration for PayResolve AI"
---

# PayResolve AI — Developer Portal & API Reference

Welcome to the **PayResolve AI** Developer Portal configuration (`apimatic-portal/`), backed by our OpenAPI `3.0.1` contract exported directly from SpringDoc (`/v3/api-docs`).

PayResolve AI is a PayPal Sandbox payment exception investigator built for the **PayPal AI Hackathon 2026**. It reconciles shop order records, PayPal captures, refunds, webhooks, and shipments into a read-time **Evidence Graph**, runs a read-only AI Investigator (Groq / Gemini with rule-based fallback) verified against database records, and requires human operator approval with an append-only audit log for every corrective action.

---

## Core Architectural Capabilities

### 1. Evidence Graph Topology
- **Concrete Nodes:** Shop Orders, PayPal Captures, Refunds, Webhook Events, and Carrier Shipments.
- **Missing Link Invariants (Dashed Nodes):** Derived nodes highlighting missing webhooks or capture mismatches.
- **AG Grid Community & AG Charts Community Views:** Filterable grids with CSV export across Exception Queue, Event Inbox, Actions/Audit, and Graph Explorer, plus telemetry charts on Operations, Benchmark, and Case Details.

### 2. Read-Only AI Investigator & Deterministic Verification
- Reads only from Sandbox orders/captures, webhook events, shop orders, shipments, and case attachments.
- Compares every cited ID and minor-unit amount in LLM output against database records before surfacing reports.
- Action execution is restricted to signed-in `OPERATOR` accounts with session + CSRF protection.

### 3. APIMatic SDK & Portal Targets
Configured in `apimatic/apimatic-meta.json` and `apimatic-portal/src/apimatic.json` (generated on demand via `@apimatic/cli@2` after `apimatic auth login`, not published to public registries):
- **TypeScript / Node.js:** target package `@payresolve/sdk`
- **Python:** target package `payresolve-sdk`
- **C# / .NET:** configured portal language `csharp`

---

## Quick Verification with REST / Postman

List open exception cases and aggregated operational telemetry using session cookies (`JSESSIONID`):

```bash
# 1. Authenticate and store session cookie + CSRF token
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"viewer","password":"<VIEWER_PASSWORD>"}'

# 2. Fetch exception queue and telemetry stats
curl -b cookies.txt http://localhost:8080/api/cases?status=OPEN
curl -b cookies.txt http://localhost:8080/api/stats/overview
```

---

## Environments & Authentication

| Environment | Base URL | Description |
|---|---|---|
| **Production Gateway** | `https://payresolve-ai.onrender.com` | Render static site + `/api/*` and `/actuator/*` rewrite proxy to `payresolve-api` |
| **Development** | `http://localhost:8080` | Local Spring Boot backend |

Authentication uses HTTP-only session cookies (`JSESSIONID`), with `X-XSRF-TOKEN` required for CSRF-protected state-mutating endpoints (obtained from `/api/auth/csrf`).
