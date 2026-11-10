---
title: PayResolve AI Developer Documentation
description: "Production guide, SDK reference, and API architecture for PayResolve AI"
---

# PayResolve AI — Developer Portal & SDK Reference

Welcome to the **PayResolve AI** Developer Portal, generated via **APIMatic Docs-as-Code**. 

PayResolve AI is an autonomous, merchant-side discrepancy resolution copilot built for the **PayPal AI Hackathon 2026**. It continuously reconciles storefront order records, PayPal captures, dispute events, and shipping carrier confirmations into a real-time **Evidence Graph**, uses Gemini 2.0 Flash with deterministic invariants to pinpoint root causes, and generates multi-language SDKs for enterprise merchant integrations.

---

## 🚀 Key Architectural Capabilities

### 1. Evidence Graph Topology
Unlike conventional linear logs, PayResolve AI models payment states as a directed acyclic graph:
- **Concrete Nodes:** Orders, PayPal Captures, Disputes, and Carrier Shipments.
- **Missing Link Invariants (Dashed Nodes):** Synthetic nodes highlighting missing webhook callbacks or unpaid capture deltas.
- **AG Grid Community Matrix:** Tabular exploration of topological nodes and causal transitions with instant CSV export.

### 2. Autonomous AI Investigator
- Combines Gemini 2.0 Flash reasoning with deterministic mathematical and state invariants.
- Eliminates AI hallucinations: every recommended corrective action (e.g., `SYNC_ORDER_STATUS`, `HOLD_REFUND`) must pass programmatic verification before human-in-the-loop approval.

### 3. APIMatic Multi-Language SDKs
APIMatic compiles PayResolve AI's OpenAPI 3.0.3 contract into native, type-safe SDK packages:
- **TypeScript / Node.js:** `@payresolve/sdk`
- **Python:** `payresolve-sdk`
- **C# / .NET:** `PayResolve.Sdk`

---

## 📦 Quick Start with the TypeScript SDK

Install the SDK in your merchant microservice:

```bash
npm install @payresolve/sdk
```

Initialize the client and inspect the exception queue:

```typescript
import { PayResolveClient, Environment } from '@payresolve/sdk';

const client = new PayResolveClient({
  environment: Environment.Production, // or Environment.Sandbox for local dev
  timeout: 10000,
});

// 1. Fetch case details and evidence topology
const caseDetails = await client.cases.getCaseDetails('PR-0142');
const graph = await client.evidence.getEvidenceGraph('PR-0142');

console.log(`Case Status: ${caseDetails.status}`);
console.log(`Discrepancy Invariant: ${graph.summary}`);
```

---

## 🐍 Quick Start with the Python SDK

```bash
pip install payresolve-sdk
```

```python
from payresolve import PayResolveClient, Environment

client = PayResolveClient(
    environment=Environment.PRODUCTION
)

# Fetch unhandled exception queue
cases = client.cases.list_cases(status="OPEN", size=10)
for case in cases.content:
    print(f"[{case.id}] {case.type} - Discrepancy Amount: ${case.amount}")
```

---

## 🔐 Environments & Authentication

| Environment | Base URL | Description |
|---|---|---|
| **Production** | `https://payresolve-ai.onrender.com` | Live cloud deployment on Render |
| **Development** | `http://localhost:8080` | Local Spring Boot backend |

Authentication is governed via secure HTTP-only session cookies (`JSESSIONID`) or CSRF-protected operator bearer headers. State-mutating endpoints (`POST`, `PUT`, `DELETE`, `PATCH`) require the `X-CSRF-TOKEN` header obtained from `/api/auth/csrf`.
