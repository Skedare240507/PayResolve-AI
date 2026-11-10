#!/usr/bin/env node
/**
 * scripts/postman-from-openapi.mjs
 *
 * Regenerates Postman request skeletons from the running backend's OpenAPI 3.x
 * specification (/v3/api-docs) using openapi-to-postmanv2.
 *
 * Usage:
 *   node scripts/postman-from-openapi.mjs
 *   node scripts/postman-from-openapi.mjs --url http://127.0.0.1:8080/v3/api-docs
 *   node scripts/postman-from-openapi.mjs --out postman/generated.postman_collection.json
 *   node scripts/postman-from-openapi.mjs --spec apimatic/payresolve-openapi.json
 */

import { execFileSync } from 'node:child_process';
import { writeFileSync, unlinkSync, readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const projectRoot = resolve(__dirname, '..');

// Parse CLI arguments
const args = process.argv.slice(2);
function getArg(flag, defaultValue) {
  const idx = args.indexOf(flag);
  if (idx !== -1 && idx + 1 < args.length) {
    return args[idx + 1];
  }
  return defaultValue;
}

const specFileArg = getArg('--spec', null);
const openApiUrl = getArg('--url', process.env.OPENAPI_URL || 'http://127.0.0.1:8080/v3/api-docs');
const outputPath = resolve(projectRoot, getArg('--out', 'postman/generated.postman_collection.json'));

async function fetchWithFallback(url) {
  try {
    const res = await fetch(url);
    if (res.ok) return res;
  } catch {
    // If localhost failed, try 127.0.0.1 fallback
    if (url.includes('localhost')) {
      const fallbackUrl = url.replace('localhost', '127.0.0.1');
      return await fetch(fallbackUrl);
    }
    throw new Error(`Failed to connect to ${url}`);
  }
  throw new Error(`Failed to fetch OpenAPI docs from ${url}`);
}

async function main() {
  console.log('🔄 PayResolve OpenAPI -> Postman Skeleton Generator');
  let tempSpecPath = null;
  let sourceDescription = '';

  try {
    let specContent = '';
    if (specFileArg) {
      const fullSpecPath = resolve(process.cwd(), specFileArg);
      if (!existsSync(fullSpecPath)) {
        throw new Error(`Specified spec file not found: ${fullSpecPath}`);
      }
      specContent = readFileSync(fullSpecPath, 'utf8');
      sourceDescription = `local file: ${fullSpecPath}`;
    } else {
      console.log(`🌐 Fetching OpenAPI definition from: ${openApiUrl}`);
      const res = await fetchWithFallback(openApiUrl);
      if (!res.ok) {
        throw new Error(`Failed to fetch OpenAPI docs: HTTP ${res.status} ${res.statusText}`);
      }
      specContent = await res.text();
      sourceDescription = openApiUrl;
    }

    // Verify it parses as JSON
    const parsed = JSON.parse(specContent);
    const pathCount = Object.keys(parsed.paths || {}).length;
    console.log(`✔ Loaded OpenAPI ${parsed.openapi || '3.x'} specification (${pathCount} paths) from ${sourceDescription}`);

    // Write to a temporary file for openapi-to-postmanv2 CLI
    tempSpecPath = resolve(projectRoot, '.openapi-spec-temp.json');
    writeFileSync(tempSpecPath, specContent, 'utf8');

    console.log(`⚡ Running openapi-to-postmanv2 conversion -> ${outputPath}...`);

    const isWindows = process.platform === 'win32';
    const npxCmd = isWindows ? 'npx.cmd' : 'npx';

    execFileSync(npxCmd, [
      '--yes',
      'openapi-to-postmanv2',
      '-s', tempSpecPath,
      '-o', outputPath,
      '-p'
    ], {
      stdio: 'inherit',
      cwd: projectRoot,
      shell: isWindows
    });

    if (existsSync(outputPath)) {
      const generatedJson = JSON.parse(readFileSync(outputPath, 'utf8'));
      const topLevelFolders = (generatedJson.item || []).map(i => i.name).join(', ');
      console.log(`\n✅ Generated Postman collection successfully!`);
      console.log(`📁 Output file: ${outputPath}`);
      console.log(`📦 Collection Name: ${generatedJson.info?.name}`);
      console.log(`📑 Top-level Folders: ${topLevelFolders}`);
    } else {
      throw new Error(`Expected output file was not created: ${outputPath}`);
    }
  } finally {
    if (tempSpecPath && existsSync(tempSpecPath)) {
      try {
        unlinkSync(tempSpecPath);
      } catch {
        // ignore cleanup error
      }
    }
  }
}

main().catch((err) => {
  console.error('❌ Generation failed:', err.message);
  process.exit(1);
});
