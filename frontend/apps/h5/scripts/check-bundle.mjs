// Runs after `taro build --type h5`: every emitted script must parse. Taro's minifier once wrote class private fields of a dependency as
// invalid syntax (`#"e"`), which unit tests cannot see and which leaves the page blank. The bundle must also stay at ES2020 (Chrome 80 /
// iOS 13.4), what WeCom's embedded browsers handle; Taro's own dependencies are shipped untranspiled at that level.
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'acorn';

const dist = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist', 'js');
const ECMA_VERSION = 2020;
let failed = false;
const files = readdirSync(dist).filter((name) => name.endsWith('.js'));
for (const name of files) {
  try {
    parse(readFileSync(join(dist, name), 'utf8'), { ecmaVersion: ECMA_VERSION });
  } catch (error) {
    console.error(`dist/js/${name} is not valid ES${ECMA_VERSION}: ${error.message}`);
    failed = true;
  }
}
if (files.length === 0) {
  console.error('dist/js has no scripts: the H5 build produced nothing.');
  failed = true;
}
if (!failed) console.log(`h5 bundle: ${files.length} scripts parse as ES${ECMA_VERSION}`);
process.exit(failed ? 1 : 0);
