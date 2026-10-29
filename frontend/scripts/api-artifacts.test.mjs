import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

test('GOV-1023-QUA-02: missing or empty artifacts fail with the generation command; all four permit consumers', (context) => {
  const root = mkdtempSync(join(tmpdir(), 'api-artifacts-'));
  context.after(() => rmSync(root, { recursive: true, force: true }));
  const script = join(root, 'scripts', 'api-artifacts.mjs');
  mkdirSync(dirname(script), { recursive: true });
  copyFileSync(join(dirname(fileURLToPath(import.meta.url)), 'api-artifacts.mjs'), script);
  const check = () => spawnSync(process.execPath, [script, '--check'], { encoding: 'utf8' });
  for (const file of ['openapi.json', 'src/generated/schema.ts', 'src/generated/validators.ts', 'src/generated/operations.ts']) {
    const missing = check();
    assert.notEqual(missing.status, 0);
    assert.ok(missing.stderr.includes(file), missing.stderr);
    assert.match(missing.stderr, /bash scripts\/codegen\/generate_api.sh/);
    const path = join(root, 'packages', 'api', file);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, '');
    assert.notEqual(check().status, 0);
    writeFileSync(path, file === 'openapi.json' ? '{"paths":{"/api/v1/cost/timesheets":{"get":{}}}}' : '// generated\n');
  }
  assert.equal(check().status, 0);
});
