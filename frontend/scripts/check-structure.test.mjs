// Proves the structure check refuses a file copied between the two apps (M-15). Run by `pnpm test:scripts`.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { duplicatesAcrossRoots } from './check-structure.mjs';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');

function workspace(files) {
  const base = mkdtempSync(join(tmpdir(), 'check-structure-'));
  for (const [path, content] of Object.entries(files)) {
    mkdirSync(dirname(join(base, path)), { recursive: true });
    writeFileSync(join(base, path), content);
  }
  return base;
}

const ROOTS = ['apps/web/src', 'apps/h5/src', 'packages/core/src'];

test('the same content in both apps is reported, whatever the file is called', () => {
  const base = workspace({
    'apps/web/src/shared/lib/failure.ts': 'export const a = 1;\n',
    'apps/h5/src/shared/helpers/errors.ts': 'export const a = 1;\n',
    'apps/web/src/only-web.ts': 'export const web = 1;\n',
    'apps/h5/src/only-h5.ts': 'export const h5 = 1;\n',
  });
  assert.deepEqual(duplicatesAcrossRoots(ROOTS, base), [['apps/h5/src/shared/helpers/errors.ts', 'apps/web/src/shared/lib/failure.ts']]);
});

test('reformatting or re-commenting a copy does not hide it', () => {
  const base = workspace({
    'apps/web/src/a.ts': 'export function total(a: number, b: number) {\n  return a + b;\n}\n',
    'apps/h5/src/b.ts': '// the sum\nexport function total(a: number,\n  b: number) { /* add */ return a + b; } // done\n',
  });
  assert.deepEqual(duplicatesAcrossRoots(ROOTS, base), [['apps/h5/src/b.ts', 'apps/web/src/a.ts']]);
});

test('a copy of a file of packages/core inside an app is reported too', () => {
  const base = workspace({
    'packages/core/src/safe-url.ts': 'export const safe = (v: string) => v.startsWith("https:");\n',
    'apps/h5/src/shared/safe-url.ts': 'export const safe = (v: string) => v.startsWith("https:");\n',
  });
  assert.deepEqual(duplicatesAcrossRoots(ROOTS, base), [['apps/h5/src/shared/safe-url.ts', 'packages/core/src/safe-url.ts']]);
});

test('files that merely look alike, a copy inside one app and empty placeholders are not duplicates', () => {
  const base = workspace({
    'apps/web/src/a.ts': 'export const a = 1;\n',
    'apps/h5/src/a.ts': 'export const a = 2;\n',
    'apps/web/src/b.ts': 'same inside web\n',
    'apps/web/src/c.ts': 'same inside web\n',
    'apps/web/src/.gitkeep': '',
    'apps/h5/src/.gitkeep': '',
    'apps/web/src/only-comment.ts': '// nothing here\n',
    'apps/h5/src/only-comment.ts': '/* nothing, differently said */\n',
  });
  assert.deepEqual(duplicatesAcrossRoots(ROOTS, base), []);
});

test('the repository itself has no code that exists twice', () => {
  assert.deepEqual(duplicatesAcrossRoots(ROOTS, frontend), []);
});

test('the command line fails on a copy and names both files, and passes once the copy is gone', () => {
  const skeleton = {
    'apps/web/src/modules/m/index.ts': 'export const web = 1;\n',
    'apps/h5/src/modules/m/index.ts': 'export const h5 = 1;\n',
    'packages/api/src/a.ts': 'export const api = 1;\n',
    'packages/core/src/a.ts': 'export const core = 1;\n',
    'packages/ui/src/a.ts': 'export const ui = 1;\n',
    'packages/design-system/src/a.ts': 'export const ds = 1;\n',
  };
  const run = (files) => spawnSync(process.execPath, [join(frontend, 'scripts', 'check-structure.mjs')], { cwd: workspace({ ...skeleton, ...files }), encoding: 'utf8' });

  const copied = run({ 'apps/web/src/shared/failure.ts': 'export const same = 1;\n', 'apps/h5/src/shared/failure.ts': 'export const same = 1;\n' });
  assert.equal(copied.status, 1);
  assert.match(copied.stderr, /apps\/h5\/src\/shared\/failure\.ts = apps\/web\/src\/shared\/failure\.ts/);
  assert.match(copied.stderr, /packages\/core/);

  const clean = run({ 'apps/web/src/shared/failure.ts': 'export const web = 1;\n', 'apps/h5/src/shared/failure.ts': 'export const h5 = 1;\n' });
  assert.equal(clean.status, 0, clean.stderr);
});
