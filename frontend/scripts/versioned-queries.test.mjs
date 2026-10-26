// A GET whose answer is later sent back with If-Match must be read with useVersionedQuery (no structural sharing, no select):
// otherwise TanStack Query keeps the old object, and with it the old ETag, when a new answer has the same content. Run by `pnpm test:scripts`.
import assert from 'node:assert/strict';
import { requireGeneratedApi, requireOpenApi } from './api-artifacts.mjs';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const PLAIN_QUERY = /\b(useQuery|useInfiniteQuery|useSuspenseQuery|useQueries)\s*[(<]/g;

/** Names of the generated GET operations whose resource also has a versioned (If-Match) operation. */
export function versionedGets(spec, operationsSource) {
  const resourceOf = (path) => path.replace(/:[a-z-]+$/, '');
  const resources = new Set();
  for (const [path, methods] of Object.entries(spec.paths)) {
    for (const operation of Object.values(methods)) {
      if ((operation.parameters ?? []).some((parameter) => parameter.in === 'header' && parameter.name === 'If-Match')) resources.add(resourceOf(path));
    }
  }
  const names = [];
  for (const [, name, path] of operationsSource.matchAll(/export const (\w+) = [\s\S]*?\('get', '([^']+)'/g)) {
    if (resources.has(path)) names.push(name);
  }
  return names;
}

/** Exported functions of a file that call one of the operations. */
export function adaptersOf(source, operationNames) {
  const starts = [...source.matchAll(/export (?:async )?function (\w+)\s*\(/g)].map((match) => ({ name: match[1], at: match.index }));
  const found = [];
  starts.forEach((start, index) => {
    const body = source.slice(start.at, starts[index + 1]?.at ?? source.length);
    if (operationNames.some((operation) => new RegExp(`\\b${operation}\\b`).test(body))) found.push(start.name);
  });
  return found;
}

/** The text of every plain query call in a source file, up to its closing parenthesis. */
export function plainQueryCalls(source) {
  const calls = [];
  for (const match of source.matchAll(PLAIN_QUERY)) {
    const open = source.indexOf('(', match.index);
    let depth = 0;
    let end = open;
    for (; end < source.length; end += 1) {
      if (source[end] === '(') depth += 1;
      if (source[end] === ')') depth -= 1;
      if (depth === 0) break;
    }
    calls.push(source.slice(open, end + 1));
  }
  return calls;
}

export function violations(files, spec, operationsSource) {
  const operations = versionedGets(spec, operationsSource);
  const adapters = Object.values(files).flatMap((source) => adaptersOf(source, operations));
  const problems = [];
  for (const [file, source] of Object.entries(files)) {
    const calls = plainQueryCalls(source);
    const used = adapters.filter((adapter) => calls.some((call) => new RegExp(`\\b${adapter}\\b`).test(call)));
    if (used.length > 0) problems.push(`${file} reads ${used.join(', ')} (a GET sent back with If-Match) with a plain query; use useVersionedQuery`);
  }
  return { operations, adapters, problems };
}

function sourceFiles(root) {
  const files = {};
  for (const entry of readdirSync(root, { recursive: true, withFileTypes: true })) {
    const path = join(entry.parentPath, entry.name);
    if (entry.isFile() && /\.tsx?$/.test(entry.name) && !/\.test\./.test(entry.name) && !path.includes('/generated/')) files[path.slice(frontend.length + 1)] = readFileSync(path, 'utf8');
  }
  return files;
}

const SPEC = {
  paths: {
    '/things/{id}': { get: {}, put: { parameters: [{ in: 'header', name: 'If-Match', required: true }] } },
    '/things/{id}:act': { post: { parameters: [{ in: 'header', name: 'If-Match', required: true }] } },
    '/others': { get: {} },
  },
};
const GENERATED = `export const getThings = (a) => request('get', '/things/{id}', a);\nexport const getOthers = (a) => request('get', '/others', a);\n`;

test('the GET of a resource with a versioned update is found, and a GET of another resource is not', () => {
  assert.deepEqual(versionedGets(SPEC, GENERATED), ['getThings']);
});

test('a plain query over such a GET is a violation, whichever file it is in; useVersionedQuery is not', () => {
  const adapter = { 'a/api.ts': 'export function fetchThing(id) {\n  return operations.getThings({ id });\n}\nexport function fetchOther() {\n  return operations.getOthers();\n}\n' };
  const plain = violations({ ...adapter, 'a/hook.ts': "import { fetchThing } from './api';\nexport const useThing = (id) => useQuery({ queryKey: ['t'], queryFn: () => fetchThing(id) });\n" }, SPEC, GENERATED);
  assert.deepEqual(plain.adapters, ['fetchThing']);
  assert.equal(plain.problems.length, 1);
  assert.match(plain.problems[0], /a\/hook\.ts reads fetchThing/);
  const versioned = violations({ ...adapter, 'a/hook.ts': "export const useThing = (id) => useVersionedQuery({ queryKey: ['t'], queryFn: () => fetchThing(id) });\n" }, SPEC, GENERATED);
  assert.deepEqual(versioned.problems, []);
  const mixed = violations({ ...adapter, 'a/hook.ts': "export const useOther = () => useQuery({ queryKey: ['o'], queryFn: fetchOther });\nexport const useThing = (id) => useVersionedQuery({ queryKey: ['t'], queryFn: () => fetchThing(id) });\n" }, SPEC, GENERATED);
  assert.deepEqual(mixed.problems, []);
  const unrelated = violations({ ...adapter, 'a/hook.ts': "export const useOther = () => useQuery({ queryKey: ['o'], queryFn: fetchOther });\n" }, SPEC, GENERATED);
  assert.deepEqual(unrelated.problems, []);
});

test('in this repository every versioned GET is read with useVersionedQuery', () => {
  requireGeneratedApi();
  const spec = requireOpenApi();
  const generated = readFileSync(join(frontend, 'packages', 'api', 'src', 'generated', 'operations.ts'), 'utf8');
  const files = { ...sourceFiles(join(frontend, 'apps', 'web', 'src')), ...sourceFiles(join(frontend, 'apps', 'h5', 'src')), ...sourceFiles(join(frontend, 'packages', 'core', 'src')) };
  const result = violations(files, spec, generated);
  assert.ok(result.operations.length >= 3, `expected the task, the numbering rule and the work calendar year, got ${result.operations.join(', ')}`);
  assert.ok(result.adapters.length >= 3, `expected an adapter for each, got ${result.adapters.join(', ')}`);
  assert.deepEqual(result.problems, []);
});
