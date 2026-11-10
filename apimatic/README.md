# PayResolve AI — APIMatic Integration Guide & SDK Specification

This directory contains the **APIMatic** integration artifacts for PayResolve AI. It enables automated, type-safe multi-language SDK generation (TypeScript, Python, Go, C#) and interactive API portal documentation directly from PayResolve's OpenAPI 3.1 specification.

---

## 1. Why APIMatic in PayResolve AI?

Merchants operating on PayPal process transactions across various web storefronts (Shopify, WooCommerce, custom Node/Python microservices). When payment exceptions occur (e.g. `PAID_NOT_MARKED`, `AMOUNT_MISMATCH`, or `ORPHAN_CAPTURE`), merchants need programmatic access to:
1. Ingest raw payment exceptions into the detection queue.
2. Query the derived **Evidence Graph** (nodes and dashed missing links).
3. Trigger AI investigations with deterministic verification.
4. Execute operator-approved corrective actions (`SYNC_ORDER_STATUS`, `HOLD_REFUND`).

**APIMatic bridges this gap by turning PayResolve's Spring Boot REST APIs into production-ready SDKs and interactive API docs.**

---

## 2. Artifacts in this Directory

| File | Purpose |
|---|---|
| [`payresolve-openapi.json`](file:///c:/my%20projects/PayResolve%20AI/apimatic/payresolve-openapi.json) | Full OpenAPI 3.1 contract exported from SpringDoc (`/v3/api-docs`). |
| [`apimatic-meta.json`](file:///c:/my%20projects/PayResolve%20AI/apimatic/apimatic-meta.json) | APIMatic CodeGen configuration for TypeScript (`@payresolve/sdk`) and Python (`payresolve-sdk`). |
| [`README.md`](file:///c:/my%20projects/PayResolve%20AI/apimatic/README.md) | Verification guide and SDK usage examples. |

---

## 3. SDK Code Usage Examples

### A. TypeScript / Node.js SDK
```typescript
import { PayResolveClient, Environment } from '@payresolve/sdk';

const client = new PayResolveClient({
  environment: Environment.Production, // or Environment.Sandbox for localhost
  timeout: 10000,
});

// 1. Fetch Exception Case and Evidence Graph
const caseData = await client.cases.getCaseDetails('PR-0142');
const graph = await client.evidence.getEvidenceGraph('PR-0142');

console.log(`Case ${caseData.id} status: ${caseData.status}`);
console.log(`Graph contains ${graph.nodes?.length} nodes and ${graph.missingNodesCount} missing links.`);

// 2. Trigger AI Investigation
const investigation = await client.investigator.triggerInvestigation('PR-0142');
console.log(`Root cause: ${investigation.report?.rootCause}`);
```

### B. Python SDK
```python
from payresolve import PayResolveClient, Environment

client = PayResolveClient(
    environment=Environment.PRODUCTION
)

# 1. Inspect Exception Queue
queue_response = client.cases.list_cases(size=10, status="OPEN")
for case in queue_response.content:
    print(f"[{case.id}] {case.type_label} - Amount: {case.amount}")

# 2. Derive Graph Topology
graph = client.evidence.get_graph(case_id="PR-0142")
missing_nodes = [node for node in graph.nodes if node.is_missing]
print(f"Detected {len(missing_nodes)} missing invariants.")
```

---

## 4. Verification Instructions for Hackathon Judges

1. Open [APIMatic Portal](https://www.apimatic.io/).
2. Import `apimatic/payresolve-openapi.json` or connect the URL:
   `https://payresolve-ai.onrender.com/v3/api-docs`
3. Click **Generate SDK** for **TypeScript** or **Python**.
4. APIMatic automatically compiles complete client classes matching all 13 controller endpoints, data models, and error responses with zero manual coding.
