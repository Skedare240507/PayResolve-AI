# Workflow Audit — PayResolve AI

Generated: 2026-11-08. Source files: `.github/workflows/ci.yml`, `supabase-keepalive.yml`, `render-health-ping.yml`.

## Secret dependency table

| Workflow | Secret name | Required / Optional | What breaks without it |
|---|---|---|---|
| `supabase-keepalive.yml` | `SUPABASE_URL` | Required | Workflow exits 1 immediately with a clear error message |
| `supabase-keepalive.yml` | `SUPABASE_ANON_KEY` | Required | Same as above |
| `render-health-ping.yml` | `RENDER_API_URL` | Required | Workflow exits 1 with instructions to add it; scheduled run does not silently succeed |

> **No GitHub secrets are needed for CI.** The `ci.yml` backend job uses mock values declared directly in the workflow file: `mock_ci_client_id`, `mock_ci_secret`, `mock_ci_webhook_id`. The Postgres service is an ephemeral container, not Supabase.

## Findings and fixes applied

### ci.yml

| Finding | Fix |
|---|---|
| `npm run lint` was missing from the frontend job | Added a `Lint frontend` step before `Build frontend` |
| No concurrency control — repeated pushes queued redundant runs | Added `concurrency: group/cancel-in-progress: true` at the workflow level |
| No env-name drift check | Added a third job `check-env-names` that runs `scripts/check-env-names.mjs` |

**actionlint notes:** `actionlint` is not installed on this machine (not in PATH, not via pip). Manual review found no YAML syntax errors, no invalid expression syntax, no unquoted bare strings in `run:` blocks, and no misused `${{ }}` expressions. All `uses:` versions are pinned to major tags (`@v4`). No shell injection vectors in variable expansions.

**CI uses no real secrets.** Confirmed by reading the job: `DATABASE_URL` points to the ephemeral Postgres container, and PayPal credentials are placeholder strings. Tests use Testcontainers and WireMock — no call reaches a real API.

### supabase-keepalive.yml

| Finding | Fix |
|---|---|
| Missing-secret failure message was terse | Expanded to three lines explaining exactly which values to use and where to find them |
| No comment explaining the RLS grant | Added an inline comment referencing V2__keepalive.sql |

**RLS grant check (without calling production):** V2__keepalive.sql (read from the repo) creates the `keepalive` table with `ENABLE ROW LEVEL SECURITY` and a policy `FOR ALL TO anon USING (true) WITH CHECK (true)`. V5__rls_and_hardening.sql does not modify the keepalive table's policies (it hardens other tables). The anon role can still INSERT into `public.keepalive`. The workflow will work after both secrets are added.

> **Action required:** After adding `SUPABASE_URL` and `SUPABASE_ANON_KEY` to GitHub Secrets (Settings → Secrets and variables → Actions), trigger the workflow once by hand: Actions → Supabase Keep-Alive → Run workflow. Confirm it exits 0.

### render-health-ping.yml

| Finding | Fix |
|---|---|
| Defaulted to hardcoded `https://payresolve-api.onrender.com` guess if secret was empty | Now exits 1 with clear instructions if `RENDER_API_URL` is empty |
| No validation that the value is a full https URL | Added `https://*` prefix check |
| Hard-failed on any non-200 (including cold-start wakeup) | Added 3-attempt retry loop with 20-second waits; exits non-zero only after all three fail |
| Window start/end epochs were comments in the script body | Moved to named top-level `env:` vars `DEMO_START_EPOCH` and `DEMO_END_EPOCH` for easy editing |
| `workflow_dispatch` input defaulted to empty string when no secret and no input | Fixed: empty check now triggers exit 1 with instructions |

**Window epochs confirmed:**
- `1794096000` = 2026-11-08 00:00:00 UTC ✓
- `1794873599` = 2026-11-16 23:59:59 UTC ✓

## Latest workflow runs

`gh` CLI is not authenticated in this session. Cannot list latest runs. Check the Actions tab on GitHub directly.

