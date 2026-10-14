// Generates packages/api/src/generated/{schema,operations}.ts from packages/api/openapi.json (written by the backend's OpenApiExportTest).
//   node scripts/generate-api.mjs           regenerate
//   node scripts/generate-api.mjs --check   fail when the committed files differ from what openapi.json produces (runs in `pnpm verify`)
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'packages', 'api');
const specPath = join(root, 'openapi.json');
const outDir = join(root, 'src', 'generated');
const check = process.argv.includes('--check');

const spec = JSON.parse(readFileSync(specPath, 'utf8'));
const METHODS = ['get', 'post', 'put', 'patch', 'delete'];

function pascal(word) {
  return word.split(/[-_]/).filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join('');
}

/** getPlatformApprovalTasksByTaskId, postPlatformApprovalTasksByTaskIdClaim: method, path segments, path parameters as By<Name>, custom action last. */
function operationName(method, path) {
  const parts = [];
  for (const segment of path.split('/').slice(3)) {
    const [resource, action] = segment.split(':');
    const parameter = resource.match(/^\{(\w+)\}$/);
    parts.push(parameter ? `By${pascal(parameter[1])}` : pascal(resource));
    if (action) parts.push(pascal(action));
  }
  return method + parts.join('');
}

const operations = [];
const seen = new Set();
for (const path of Object.keys(spec.paths).sort()) {
  for (const method of METHODS) {
    if (!spec.paths[path][method]) continue;
    const name = operationName(method, path);
    if (seen.has(name)) throw new Error(`generate-api: two operations would be named ${name}`);
    seen.add(name);
    operations.push({ name, method, path });
  }
}

const HEADER = '// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.\n';
const schema = HEADER + astToString(await openapiTS(spec));
const operationsSource = `${HEADER}import { type Call, type Result, request } from '../runtime';
import type { paths } from './schema';

${operations.map(({ name, method, path }) => `export const ${name} = (...[args, options]: Call<paths['${path}']['${method}']>) =>
  request<Result<paths['${path}']['${method}']>>('${method}', '${path}', args, options);`).join('\n\n')}
`;

const files = { 'schema.ts': schema, 'operations.ts': operationsSource };
let stale = false;
for (const [file, content] of Object.entries(files)) {
  const target = join(outDir, file);
  if (check) {
    if (!existsSync(target) || readFileSync(target, 'utf8') !== content) {
      console.error(`packages/api/src/generated/${file} does not match packages/api/openapi.json. Run: pnpm generate:api`);
      stale = true;
    }
  } else {
    mkdirSync(outDir, { recursive: true });
    writeFileSync(target, content);
  }
}
if (check && !stale) console.log(`generated API code matches openapi.json (${operations.length} operations)`);
process.exit(stale ? 1 : 0);
