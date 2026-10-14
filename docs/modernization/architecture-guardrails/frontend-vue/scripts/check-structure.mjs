// F13 narrow-interface budget: each module's index.ts may export at most MAX names.
// F6 (SFC half): a .vue file name IS the component name, so it must not end in a vague suffix.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

const MAX = 8;
const VAGUE = /(Util|Utils|Helper|Manager|Info|Data|Impl)\.vue$/;
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
  if (VAGUE.test(entry)) {
    console.error(`src/${entry}: component name ends in a vague suffix (Util/Helper/Manager/Info/Data/Impl). Name it after what it shows, e.g. TimeEntryRow.vue.`);
    failed = true;
  }
}
process.exit(failed ? 1 : 0);
