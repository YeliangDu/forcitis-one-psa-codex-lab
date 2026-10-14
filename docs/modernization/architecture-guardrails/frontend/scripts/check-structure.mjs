// F13 narrow-interface budget: each module's index.ts exists and exports at most MAX names.
// F6 (file half): a file name must not end in a vague suffix (the identifier half is @typescript-eslint/naming-convention).
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const MAX = 8;
const VAGUE = /(^|[-/\\])(util|utils|helper|manager|info|data|impl)\.tsx?$/;
let failed = false;

for (const module of readdirSync('src/modules')) {
  const index = join('src/modules', module, 'index.ts');
  if (!existsSync(index)) {
    console.error(`modules/${module} has no index.ts. Every module exposes its public surface through src/modules/<m>/index.ts.`);
    failed = true;
    continue;
  }
  const source = readFileSync(index, 'utf8');
  const names = [...source.matchAll(/export\s+(?:type\s+)?\{([^}]*)\}/g)].flatMap((m) => m[1].split(',').map((s) => s.trim()).filter(Boolean));
  if (names.length > MAX) {
    console.error(`modules/${module}/index.ts exports ${names.length} names (max ${MAX}). Deepen the module instead of widening its interface.`);
    failed = true;
  }
}

for (const entry of readdirSync('src', { recursive: true })) {
  if (!entry.startsWith(join('api', 'generated')) && VAGUE.test(entry)) {
    console.error(`src/${entry}: file name ends in a vague suffix (util/helper/manager/info/data/impl). Name it after what it does, e.g. time-entry-row.tsx.`);
    failed = true;
  }
}
process.exit(failed ? 1 : 0);
