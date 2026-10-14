// F13 narrow-interface budget: each apps/web and apps/h5 module's index.ts exists and exports at most MAX names.
// F6 (file half): a file name must not end in a vague suffix (the identifier half is @typescript-eslint/naming-convention).
// M-15: no file of apps/web/src may have the same content as a file of apps/h5/src (shared code lives in packages/core, never copied).
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX = 8;
const VAGUE = /(^|[-/\\])(util|utils|helper|manager|info|data|impl)\.tsx?$/;
const MODULE_ROOTS = ['apps/web/src/modules', 'apps/h5/src/modules'];
const SOURCE_ROOTS = ['apps/web/src', 'apps/h5/src', 'packages/api/src', 'packages/core/src', 'packages/ui/src', 'packages/design-system/src'];
const APP_ROOTS = ['apps/web/src', 'apps/h5/src'];

function filesUnder(root) {
  return readdirSync(root, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name));
}

/** Groups of files from different apps that have identical bytes. Empty files (placeholders such as .gitkeep) do not count. */
export function duplicatesAcrossApps(appRoots, base = '.') {
  const byHash = new Map();
  for (const appRoot of appRoots) {
    const root = join(base, appRoot);
    if (!existsSync(root)) continue;
    for (const file of filesUnder(root)) {
      if (statSync(file).size === 0) continue;
      const hash = createHash('sha256').update(readFileSync(file)).digest('hex');
      const group = byHash.get(hash) ?? [];
      group.push({ app: appRoot, file: relative(base, file) });
      byHash.set(hash, group);
    }
  }
  return [...byHash.values()].filter((group) => new Set(group.map((member) => member.app)).size > 1).map((group) => group.map((member) => member.file).sort());
}

function main() {
  let failed = false;

  for (const modules of MODULE_ROOTS) {
    for (const module of readdirSync(modules)) {
      const index = join(modules, module, 'index.ts');
      if (!existsSync(index)) {
        console.error(`${modules}/${module} has no index.ts. Every module exposes its public surface through ${modules}/<m>/index.ts.`);
        failed = true;
        continue;
      }
      const source = readFileSync(index, 'utf8');
      const names = [...source.matchAll(/export\s+(?:type\s+)?\{([^}]*)\}/g)].flatMap((m) => m[1].split(',').map((s) => s.trim()).filter(Boolean));
      if (names.length > MAX) {
        console.error(`${modules}/${module}/index.ts exports ${names.length} names (max ${MAX}). Deepen the module instead of widening its interface.`);
        failed = true;
      }
    }
  }

  for (const sourceRoot of SOURCE_ROOTS) {
    for (const entry of readdirSync(sourceRoot, { recursive: true })) {
      if (!entry.startsWith('generated') && VAGUE.test(entry)) {
        console.error(`${sourceRoot}/${entry}: file name ends in a vague suffix (util/helper/manager/info/data/impl). Name it after what it does, e.g. time-entry-row.tsx.`);
        failed = true;
      }
    }
  }

  for (const group of duplicatesAcrossApps(APP_ROOTS)) {
    console.error(`${group.join(' = ')}: the same file in two apps. Move it to packages/core (code both apps need is shared, never copied).`);
    failed = true;
  }

  process.exit(failed ? 1 : 0);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main();
