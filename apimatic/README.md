# PayResolve AI — OpenAPI Contract & APIMatic Integration

This directory holds the synced OpenAPI `3.0.1` contract (`payresolve-openapi.json`) exported directly from SpringDoc (`/v3/api-docs`) and the APIMatic CodeGen configuration (`apimatic-meta.json`).

## Verified Files & Layout

| File | Purpose |
|---|---|
| [payresolve-openapi.json](payresolve-openapi.json) | OpenAPI 3.0.1 specification exported from SpringDoc `/v3/api-docs` via [OpenApiExportTest.java](../backend/src/test/java/com/payresolve/OpenApiExportTest.java) and [OpenApiConfig.java](../backend/src/main/java/com/payresolve/common/OpenApiConfig.java). |
| [apimatic-meta.json](apimatic-meta.json) | APIMatic CodeGen configuration targeting TypeScript (`@payresolve/sdk`) and Python (`payresolve-sdk`) against `https://payresolve-ai.onrender.com` and `http://localhost:8080`. |
| [apimatic-portal/src/apimatic.json](../apimatic-portal/src/apimatic.json) | APIMatic Docs-as-Code portal configuration (with mirrored spec at [apimatic-portal/src/spec/payresolve-openapi.json](../apimatic-portal/src/spec/payresolve-openapi.json)). |
| [README.md](README.md) | Synchronization, validation, and prerequisites guide. |

## Keeping the Spec Synchronized

After changing Spring Boot controllers or DTOs, re-export `/v3/api-docs`, regenerate TypeScript types, and sync the APIMatic specs:

```bash
cd backend && ./mvnw test -Dtest=OpenApiExportTest
cd ../frontend && npm run codegen && npm run build
```

Then copy `frontend/src/types/openapi.json` to `apimatic/payresolve-openapi.json` and `apimatic-portal/src/spec/payresolve-openapi.json`, and run an offline OpenAPI lint check:

```bash
npx --yes @redocly/cli lint apimatic/payresolve-openapi.json
```

## APIMatic Cloud CLI Status & Prerequisites

APIMatic's CLI commands (`apimatic api validate`, `apimatic sdk generate`, `apimatic portal generate`, `apimatic portal serve`, `apimatic plugin generate`) require:

1. **Node.js `>=22.14.0`** for `@apimatic/cli@2.x` (matching `apimatic-portal/src/apimatic.json`).
2. **APIMatic account authentication** via:
   ```bash
   npx @apimatic/cli@2 auth login
   ```

> **Note:** Target SDK package names configured in [apimatic-meta.json](apimatic-meta.json) (`@payresolve/sdk`, `payresolve-sdk`) are not published to public registries (npm/PyPI) and have not yet been generated locally in this environment because APIMatic CLI cloud generation requires `apimatic auth login`.

Once authenticated on Node `>=22.14.0`, run:

```bash
npx @apimatic/cli@2 api validate --file=apimatic/payresolve-openapi.json
npx @apimatic/cli@2 sdk generate --language=typescript --input=apimatic-portal
npx @apimatic/cli@2 portal generate --input=apimatic-portal --destination=apimatic-portal/portal
```
