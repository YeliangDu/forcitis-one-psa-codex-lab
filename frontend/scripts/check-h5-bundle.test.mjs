// Proves the H5 bundle check refuses a second React and assets outside the sub-path. Run by `pnpm test:scripts`.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');

function check(script, { index } = {}) {
  const dist = mkdtempSync(join(tmpdir(), 'h5-dist-'));
  mkdirSync(join(dist, 'js'));
  writeFileSync(join(dist, 'js', 'app.js'), script);
  writeFileSync(join(dist, 'index.html'), index ?? '<html><head><script src="/h5/js/app.js"></script></head></html>');
  const env = { ...process.env };
  delete env.H5_PUBLIC_PATH;
  return spawnSync(process.execPath, [join(frontend, 'apps', 'h5', 'scripts', 'check-bundle.mjs'), '--dist', dist], { encoding: 'utf8', env });
}

const copy = (name, version, domVersion) => `var ${name}a={};${name}a.version="${version}";var ${name}b={};${name}b.version="${domVersion}";`;
const REACT_18 = copy('one', '18.3.1', '18.3.1-next-f1338f8080-20240426');
const REACT_18_AGAIN = copy('two', '18.3.1', '18.3.1-next-f1338f8080-20240426');
const REACT_19 = copy('three', '19.3.0', '19.3.0');

test('one React 18 under /h5/ passes', () => {
  const run = check(REACT_18);
  assert.equal(run.status, 0, run.stderr);
});

test('a second React fails the check, whatever its version', () => {
  assert.match(check(REACT_18 + REACT_19).stderr, /exactly one React 18/);
  assert.match(check(REACT_18 + REACT_18_AGAIN).stderr, /exactly one React 18/);
  assert.match(check(REACT_19).stderr, /exactly one React 18/);
  assert.match(check('var none = 1;').stderr, /exactly one React 18/);
});

test('assets outside the sub-path fail the check', () => {
  const run = check(REACT_18, { index: '<html><head><script src="/js/app.js"></script></head></html>' });
  assert.equal(run.status, 1);
  assert.match(run.stderr, /outside the sub-path \/h5\//);
});
