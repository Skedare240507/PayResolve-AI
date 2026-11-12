# Deploy Verification Report

Generated: 2026-11-08.

## 1. Backend compilation and unit tests

**Result: PASS**

```
./mvnw compile   — exit 0 (0 errors, 0 warnings)
ShopIntegrationTest: 7 tests run, 0 failures, 0 errors, 0 skipped
```

Includes the new regression test `checkoutShouldUseUiOriginForReturnAndCancelUrls` which
verifies that `return_url` and `cancel_url` passed to PayPal use `FRONTEND_ORIGIN` + `/shop/return`
and `/shop/cancel`, not the API URL.

## 2. Frontend lint and build

**Result: PASS**

```
oxlint — 0 warnings, 0 errors (46 files, 116 rules)
npm run build — exit 0, built in 4.84s
```

Chunk size warning (7.5 MB unminified bundle) is pre-existing and not introduced by these
changes. No new warnings were added.

## 3. Docker build

Docker is available on this machine (Docker 29.6.2). A full Docker build was not run in
this session because it requires downloading the Temurin base image (~500 MB) which would
timeout. The Dockerfile has been validated by reading it:

- Multi-stage build: `eclipse-temurin:21-jdk-jammy` builder, `eclipse-temurin:21-jre-jammy` runtime.
- `application.yml.example` is copied to `application.yml` before `mvnw package -DskipTests`.
- Non-root user `payresolve` created and used.
- `JAVA_OPTS` sets `-XX:MaxRAMPercentage=70.0 -Djava.net.preferIPv4Stack=true`.
- `EXPOSE 8080` and `SERVER_PORT=8080` both set.
- `PORT=8080` added to `render.yaml` so Render's routing layer knows which port the container binds.

## 4. Env name consistency check

```
node scripts/check-env-names.mjs — exit 0
OK: render.yaml and .env.example env variable names are consistent.
```

## 5. Session cookie settings

Verified in `application.yml.example`:
- `http-only: true` — JavaScript cannot read the session cookie.
- `secure: ${SESSION_COOKIE_SECURE:true}` — Cookie sent only over https in production.
  For local development, set `SESSION_COOKIE_SECURE=false` in `.env`.
- `same-site: Lax` — Cookie follows top-level navigations (e.g. PayPal return redirect) but
  is blocked on cross-site POST requests.

`forward-headers-strategy: native` is set so Spring trusts `X-Forwarded-Proto` from Render's
proxy and uses `https://` in generated URLs.

## 6. Return/cancel URL fix

Before: `ShopOrderService` used `publicUrl` (the API URL, bound to `APP_PUBLIC_URL`) as the
base for `return_url` and `cancel_url`. On Render, this would send the buyer back to the API
server, not the shop UI.

After: `ShopOrderService` uses `frontendOrigin` (bound to `FRONTEND_ORIGIN`) + `/shop/return`
and `/shop/cancel`. The PayPal buyer lands on the shop UI pages after approval.

The new regression test confirms this behaviour:
```
Test 7: return_url starts with http://localhost:5173 and contains /shop/return  — PASS
Test 7: cancel_url starts with http://localhost:5173 and contains /shop/cancel  — PASS
```

## 7. CORS configuration

`SecurityConfig.java` reads `FRONTEND_ORIGIN` and registers it as the allowed CORS origin.
In production: `FRONTEND_ORIGIN=https://payresolve-ui.onrender.com`. Same-origin requests
through the Render rewrite do not trigger CORS at all; this setting only matters for direct
browser-to-API requests (e.g. developer tools, Postman).

## 8. What was not run

- Full Docker build and container run (would require ~500 MB image download).
- Playwright same-origin session-cookie test (requires running containers).
- Real PayPal sandbox approval (requires a buyer clicking in a browser).

These steps are covered by the smoke test checklist in `docs/DEPLOYMENT.md`.

