// Runs after `taro build --type h5`: every emitted script must parse. Taro's minifier once wrote class private fields of a dependency as
// invalid syntax (`#"e"`), which unit tests cannot see and which leaves the page blank. The bundle must also stay at ES2020 (Chrome 80 /
// iOS 13.4), what WeCom's embedded browsers handle; Taro's own dependencies are shipped untranspiled at that level.
// Exactly one React must be bundled, and it must be 18: a second copy (React 19 from the shared package) would break every hook at runtime.
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'acorn';
import { publicPathOf } from '../config/public-path.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const dist = join(root, 'js');
const ECMA_VERSION = 2020;
let failed = false;
const files = readdirSync(dist).filter((name) => name.endsWith('.js'));
for (const name of files) {
  const source = readFileSync(join(dist, name), 'utf8');
  try {
    parse(source, { ecmaVersion: ECMA_VERSION });
  } catch (error) {
    console.error(`dist/js/${name} is not valid ES${ECMA_VERSION}: ${error.message}`);
    failed = true;
  }
  if (source.includes('react.transitional.element')) {
    console.error(`dist/js/${name} contains React 19: @forcitis/core is shared with apps/web (React 19), so its React imports must resolve to this app's React 18 (config/index.ts alias).`);
    failed = true;
  }
}
const publicPath = publicPathOf(process.env);
const page = readFileSync(join(root, 'index.html'), 'utf8');
const references = [...page.matchAll(/\b(?:src|href)=["']?([^"'\s>]+)/g)].map((match) => match[1]).filter((target) => !/^(?:[a-z][a-z0-9+.-]*:|#)/i.test(target));
if (references.length === 0) {
  console.error('dist/index.html references no scripts or styles: the H5 build produced nothing usable.');
  failed = true;
}
for (const target of references) {
  if (!target.startsWith(publicPath)) {
    console.error(`dist/index.html references "${target}", outside the sub-path ${publicPath}; the page would not load when served there.`);
    failed = true;
  }
}
if (files.length === 0) {
  console.error('dist/js has no scripts: the H5 build produced nothing.');
  failed = true;
}
if (!failed) console.log(`h5 bundle: ${files.length} scripts parse as ES${ECMA_VERSION}`);
process.exit(failed ? 1 : 0);
