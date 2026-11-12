# APIMatic & OpenAPI Validation Report

Generated: 2026-11-10

## 1. APIMatic CLI Environment & Blocker Status

| Check | Version / Runtime | Command | Exact Result |
|---|---|---|---|
| APIMatic Auth Status | `@apimatic/cli/1.5.0` (`node-v22.12.0`) | `npx --yes @apimatic/cli auth status` | **Blocked (`exit 1`)**: `Authorization has been denied for this request. Please run apimatic auth login to log in via browser, or provide a valid auth key using the apimatic auth login --auth-key` |
| APIMatic CLI v2 Compatibility | `@apimatic/cli@2.2.0` | `apimatic --version` (`node-v22.12.0`) | **Blocked (`exit 1`)**: `Error: The APIMatic CLI needs Node >=22.14.0; this is Node 22.12.0.` |
| APIMatic `api validate` (Before & After) | `@apimatic/cli/1.5.0` | `npx --yes @apimatic/cli api validate --file apimatic\payresolve-openapi.json` | **Blocked (`exit 1`)**: `Authorization has been denied for this request. Please run apimatic auth login...` (requires authenticated APIMatic account/key to call `https://api.apimatic.io`). |
| APIMatic `portal generate` | `@apimatic/cli/1.5.0` | `npx --yes @apimatic/cli portal generate --input=apimatic-portal --destination=apimatic-portal/portal --force` | **Failed (`v1.5.0` incompatible + auth required)**: `apimatic-portal/src/apimatic.json` targets `@apimatic/cli@2` schema (`Node >=22.14.0` + `apimatic auth login` required). |

Because APIMatic cloud CLI actions (`api validate`, `sdk generate`, `portal generate`, `plugin generate`) require `Node >=22.14.0` and interactive `apimatic auth login`, TypeScript/Python SDK generation (`scripts/sdk-smoke.mjs`), portal generation/hosting (`payresolve-docs`), and Context Plugin generation were **stopped** rather than faked.

---

## 2. Offline OpenAPI Specification Validation (`@redocly/cli lint`)

To eliminate genuine specification bugs in Spring Boot `/v3/api-docs` and sync `apimatic/payresolve-openapi.json` and `apimatic-portal/src/spec/payresolve-openapi.json`, we ran `@redocly/cli@2.77.0` before and after SpringDoc remediation.

### Before (`OpenApiConfig` missing; stale `apimatic/payresolve-openapi.json`)

- **`frontend/src/types/openapi.json` (`/v3/api-docs` raw export, OpenAPI `3.1.0`):**
  - Command: `npx --yes @redocly/cli lint frontend\src\types\openapi.json --format=stylish`
  - Result: **`❌ Validation failed with 51 errors and 53 warnings.`**
  - Root causes:
    1. `security-defined` (51 errors): No security schemes or security requirements defined.
    2. `operation-4xx-response` (51 warnings): Missing 4xx error response definitions across operations.
    3. `info-license` (1 warning) & `no-server-example.com` (1 warning): Missing API metadata, license, and production server URL.
    4. `GET /api/auth/csrf` exported `CsrfToken` as a required `query` parameter (`"in": "query", "required": true`) due to missing `@Parameter(hidden = true)`.
    5. `CaseAttachmentController` `ResponseEntity<?>` exported empty object schemas without `201 Created` / `413` responses.
- **`apimatic/payresolve-openapi.json` (stale/hand-edited OpenAPI `3.0.3`):**
  - Command: `npx --yes @redocly/cli lint apimatic\payresolve-openapi.json --format=stylish`
  - Result: **Valid schema, `271 warnings`** — stale schema examples containing non-existent fields (`customerEmail`, `shippingAddress`, `actionType`, `stepNumber`, wrong `id` types) and missing newer endpoints (`/api/stats/overview`, `/api/cases/{id}/investigations/latest`).

### Fixes Implemented in Backend Source

1. Added `backend/src/main/java/com/payresolve/common/OpenApiConfig.java`:
   - `@OpenAPIDefinition` with `PayResolve AI API` `1.0.0`, MIT license, GitHub contact, Production (`https://payresolve-ai.onrender.com`) and Local (`http://localhost:8080`) servers.
   - `@SecurityScheme` definitions for `cookieAuth` (`JSESSIONID`) and `csrfToken` (`X-XSRF-TOKEN`) applied uniformly by `OpenApiCustomizer` (with empty security `{}` for public login/signup/CSRF/webhook endpoints).
   - Shared `ProblemDetail` RFC 9457 schema and default `400`, `401`, `403`, `404`, `500` responses; normalized `*/*` media types to `application/json`; cleared body content on `204`; normalized `Map<String, Object>` schemas (`additionalProperties: true`).
2. Updated controller annotations in `AuthController.java` (`@Parameter(hidden = true) CsrfToken`, login `401`), `CaseAttachmentController.java` (`201 Created` `AttachmentDto`, `413`), `InvestigationController.java` (`204 No Content`), and `WebhookController.java`.
3. Configured `springdoc.api-docs.version: openapi_3_0` in `backend/src/test/resources/application.yml` and `backend/src/main/resources/application.yml.example`.
4. Re-exported spec via `OpenApiExportTest`, regenerated `frontend/src/types/api.ts` via `npm --prefix frontend run codegen`, synced both `apimatic/payresolve-openapi.json` and `apimatic-portal/src/spec/payresolve-openapi.json` (`OpenAPI 3.0.1`, `46` paths), and verified `npm --prefix frontend run lint` + `npm --prefix frontend run build` (`0` errors).

### After

- Command: `npx --yes @redocly/cli lint apimatic\payresolve-openapi.json --format=stylish`
- Result: **`Woohoo! Your API description is valid. You have 1 warning.`** (**0 errors**, 1 intentional warning `no-server-example.com` on local dev server `http://localhost:8080`).

## 3. Manual Steps Required to Unblock APIMatic Cloud CLI

1. Upgrade Node.js to `>=22.14.0` (e.g., `winget upgrade OpenJS.NodeJS.22` -> `22.23.4`).
2. Authenticate APIMatic CLI: `npx @apimatic/cli@2 auth login`.
3. Run APIMatic validation, TypeScript SDK generation, and Docs-as-Code portal generation:
   ```bash
   npx @apimatic/cli@2 api validate --file=apimatic/payresolve-openapi.json
   npx @apimatic/cli@2 sdk generate --language=typescript --input=apimatic-portal
   npx @apimatic/cli@2 portal generate --input=apimatic-portal --destination=apimatic-portal/portal
   npx @apimatic/cli@2 portal serve --input=apimatic-portal
   ```

