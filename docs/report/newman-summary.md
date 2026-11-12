# Postman, Newman & Portman Summary Report

Generated: 2026-11-10

## 1. OpenAPI Skeleton & Portman Contract Generation

- `node scripts/postman-from-openapi.mjs --spec apimatic/payresolve-openapi.json`
  - Input: `apimatic/payresolve-openapi.json` (`OpenAPI 3.0.1`, `46` paths)
  - Output: `postman/generated.postman_collection.json` (`PayResolve AI API`)
- `npx --yes @apideck/portman -l apimatic/payresolve-openapi.json -c postman/portman-config.json -o <scratch>/portman-configured.json`
  - Verified `@apideck/portman@1.35.2` conversion and `postman/portman-config.json` contract rules cleanly (`exit 0`).

## 2. Structured Collection (`postman/PayResolve.postman_collection.json`)

- **`offline`** (`21` requests across 6 domain sub-folders):
  - `auth` (4): Health Check, Fetch CSRF Token, Login as Operator, Get Authenticated Profile
  - `queue` (3): List Exception Queue, Filter Queue Page Size, Get Operational Telemetry Overview (`/api/stats/overview`)
  - `case` (4): Get Case Details, Get Evidence Graph, Get Evidence Timeline, Get Latest Persisted Investigation (`/api/cases/{id}/investigations/latest`)
  - `events` (2): List Events Inbox, Get Event Inbox Stats
  - `actions` (7): List Actions / Proposals, List Audit Trail, CSRF Required on Action Approval (`403`), Login as Viewer, Fetch Viewer CSRF Token, RBAC Security - Viewer Forbidden on Action Approval (`403`), Logout
  - `benchmark` (1): Latest Benchmark Telemetry
- **`sandbox`** (`11` manual PayPal Sandbox requests preserved intact).

## 3. Local Newman Run Output

Command:
```bash
npx --yes newman run postman/PayResolve.postman_collection.json --folder offline --env-var baseUrl=http://127.0.0.1:8080 --reporters 'cli,json'
```

| Metric | Executed | Failed |
|---|---|---|
| Iterations | 1 | 0 |
| Requests | 21 | 0 |
| Test Scripts | 21 | 0 |
| Assertions | 132 | 0 |
| Total Run Duration | 18.3s | — |
| Avg / Min / Max Response | 761ms / 6ms / 9449ms | — |
| Data Received | 26.26kB (approx) | — |

Standalone sub-folder verification (`--folder benchmark`): `1` request, `7` assertions, `0` failures (`515ms`).

