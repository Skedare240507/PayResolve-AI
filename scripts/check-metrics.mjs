import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const frontendSrc = path.join(rootDir, 'frontend', 'src');

console.log('🔍 Checking metric registry usage and consistency...');

// 1. Read metricDefinitions.ts
const registryFile = path.join(frontendSrc, 'lib', 'metricDefinitions.ts');
if (!fs.existsSync(registryFile)) {
  console.error('❌ Missing frontend/src/lib/metricDefinitions.ts');
  process.exit(1);
}

const registryContent = fs.readFileSync(registryFile, 'utf8');
const idMatches = [...registryContent.matchAll(/'([a-zA-Z0-9_.]+)':\s*\{/g)];
const registeredIds = new Set(idMatches.map((m) => m[1]));

console.log(`📋 Found ${registeredIds.size} metrics registered in METRIC_REGISTRY.`);

// 2. Scan frontend/src for metricId usages (including metricId: '...' in object literals)
const usedIds = new Set();
const invalidUsages = [];

function scanDir(dir) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== 'node_modules' && entry.name !== 'dist') {
        scanDir(fullPath);
      }
    } else if (entry.isFile() && (entry.name.endsWith('.tsx') || entry.name.endsWith('.ts'))) {
      if (entry.name === 'metricDefinitions.ts') continue;
      const content = fs.readFileSync(fullPath, 'utf8');
      const matches = [
        ...content.matchAll(/metricId=["']([a-zA-Z0-9_.]+)["']/g),
        ...content.matchAll(/metricId:\s*['"]([a-zA-Z0-9_.]+)['"]/g),
      ];
      for (const m of matches) {
        const id = m[1];
        usedIds.add(id);
        if (!registeredIds.has(id)) {
          invalidUsages.push({ file: path.relative(rootDir, fullPath), id });
        }
      }
    }
  }
}

scanDir(frontendSrc);

console.log(`🎯 Found ${usedIds.size} metricId usages across frontend code.`);

let hasErrors = false;

if (invalidUsages.length > 0) {
  hasErrors = true;
  console.error('\n❌ Unregistered metricId usages found:');
  for (const inv of invalidUsages) {
    console.error(`  - ${inv.file}: "${inv.id}"`);
  }
}

const unusedIds = [...registeredIds].filter((id) => !usedIds.has(id));
if (unusedIds.length > 0) {
  console.log(`ℹ️  ${unusedIds.length} registry entries reserved for docs & multi-source telemetry: ${unusedIds.join(', ')}`);
}

if (hasErrors) {
  process.exit(1);
} else {
  console.log('\n✅ All metricId references valid and verified against registry.');
}
