#!/usr/bin/env node
/**
 * scripts/check-env-names.mjs
 *
 * Verifies that the backend env variable names in infra/render.yaml and
 * .env.example are consistent. Fails (exit 1) if any name appears in one
 * file but not the other, so drift is caught in CI before deployment.
 *
 * The comparison is for backend variables only. Frontend VITE_* variables
 * live in frontend/.env.example and are not expected to appear in render.yaml.
 *
 * Usage:
 *   node scripts/check-env-names.mjs
 */

import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// 1. Parse render.yaml — collect all `key:` values under the API service
// ---------------------------------------------------------------------------

const renderYaml = readFileSync(resolve(root, 'infra/render.yaml'), 'utf8');

// Extract all `- key: VARNAME` lines
const renderKeys = new Set(
  [...renderYaml.matchAll(/^\s*-\s*key:\s*(\S+)/gm)].map(m => m[1])
);

// ---------------------------------------------------------------------------
// 2. Parse .env.example — collect all VARNAME= lines (skip comments/blanks)
// ---------------------------------------------------------------------------

const envExample = readFileSync(resolve(root, '.env.example'), 'utf8');

const envKeys = new Set(
  envExample
    .split('\n')
    .filter(line => line.trim() && !line.trim().startsWith('#'))
    .map(line => line.split('=')[0].trim())
    .filter(Boolean)
);

// ---------------------------------------------------------------------------
// 3. Exclude frontend-only variables — VITE_* are only in frontend/.env.example
// ---------------------------------------------------------------------------

for (const k of [...renderKeys]) {
  if (k.startsWith('VITE_')) renderKeys.delete(k);
}
for (const k of [...envKeys]) {
  if (k.startsWith('VITE_')) envKeys.delete(k);
}

// ---------------------------------------------------------------------------
// 4. Variables expected only in one file — these are intentional exclusions
// ---------------------------------------------------------------------------

// render.yaml has PORT which is Render-internal; .env.example doesn't need it.
const onlyInRenderOk = new Set(['PORT', 'NODE_VERSION']);

// .env.example has SUPABASE_URL and SUPABASE_ANON_KEY for the keepalive workflow
// (GitHub Actions secrets, not render.yaml vars). SESSION_COOKIE_SECURE is a
// local override not needed in render.yaml (defaults to true in production via
// application.yml.example).
const onlyInEnvOk = new Set(['SUPABASE_URL', 'SUPABASE_ANON_KEY', 'SESSION_COOKIE_SECURE']);

// ---------------------------------------------------------------------------
// 5. Find unexpected drift
// ---------------------------------------------------------------------------

let failed = false;

for (const key of renderKeys) {
  if (!envKeys.has(key) && !onlyInRenderOk.has(key)) {
    console.error(`DRIFT: "${key}" is in render.yaml but missing from .env.example`);
    failed = true;
  }
}

for (const key of envKeys) {
  if (!renderKeys.has(key) && !onlyInEnvOk.has(key)) {
    console.error(`DRIFT: "${key}" is in .env.example but missing from render.yaml`);
    failed = true;
  }
}

if (failed) {
  console.error('\nFailed: env variable names are out of sync between render.yaml and .env.example.');
  console.error('Fix the drift and re-run: node scripts/check-env-names.mjs');
  process.exit(1);
} else {
  console.log('OK: render.yaml and .env.example env variable names are consistent.');
}

