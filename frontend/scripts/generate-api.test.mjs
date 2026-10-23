// Proves the API generator refuses a document that breaks the platform conventions (M-14). Run by `pnpm test:scripts`.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const real = JSON.parse(readFileSync(join(frontend, 'packages', 'api', 'openapi.json'), 'utf8'));

function generate(change) {
  const spec = structuredClone(real);
  change(spec);
  const directory = mkdtempSync(join(tmpdir(), 'generate-api-'));
  writeFileSync(join(directory, 'openapi.json'), JSON.stringify(spec));
  const run = spawnSync(process.execPath, [join(frontend, 'scripts', 'generate-api.mjs'), '--spec', join(directory, 'openapi.json'), '--out', join(directory, 'generated')], { encoding: 'utf8' });
  return { ...run, validators: run.status === 0 ? readFileSync(join(directory, 'generated', 'validators.ts'), 'utf8') : '', operations: run.status === 0 ? readFileSync(join(directory, 'generated', 'operations.ts'), 'utf8') : '', types: run.status === 0 ? readFileSync(join(directory, 'generated', 'schema.ts'), 'utf8') : '' };
}

const decide = (spec) => spec.paths['/api/v1/platform/approval-tasks/{taskId}:decide'].post;

test('the committed document passes and carries the traits', () => {
  const run = generate(() => undefined);
  assert.equal(run.status, 0, run.stderr);
  assert.match(run.operations, /'\/api\/v1\/platform\/approval-tasks\/\{taskId\}:decide', args, options, responseSchemas\.postPlatformApprovalTasksByTaskIdDecide, \{ versioned: true, idempotent: true \}/);
  assert.match(run.validators, /export const ErrorCodeSchema = lenientEnum\("ErrorCode", \[/);
  assert.match(run.validators, /export const ErrorCodeValues = \[/);
  assert.match(run.validators, /export const ProblemDetailSchema = z\.object/);
});

test('enum-valued properties are open in the types (a newer server may send more), the enum components stay closed for exhaustive tables', () => {
  const run = generate(() => undefined);
  assert.match(run.types, /outcome\?: components\["schemas"\]\["AuditOutcome"\] \| \(string & \{\}\);/);
  assert.match(run.types, /AuditOutcome: "SUCCEEDED" \| "FAILED";/);
  assert.match(run.types, /ErrorCode: "COST_TIMESHEET_CUTOFF_PASSED"/);
});

test('a document without ProblemDetail or the ErrorCode enum is refused', () => {
  assert.match(generate((spec) => delete spec.components.schemas.ProblemDetail).stderr, /ProblemDetail is missing/);
  assert.match(generate((spec) => delete spec.components.schemas.ErrorCode).stderr, /ErrorCode is missing/);
  assert.match(generate((spec) => { spec.components.schemas.ErrorCode.enum = []; }).stderr, /non-empty string enum/);
  assert.match(generate((spec) => { spec.components.schemas.ProblemDetail.properties.errorCode = { type: 'string' }; }).stderr, /must reference the ErrorCode enum/);
});

test('an operation without a ProblemDetail error answer is refused, and so is a foreign error answer', () => {
  const missing = generate((spec) => {
    for (const status of Object.keys(decide(spec).responses)) if (status === 'default' || /^[45]/.test(status)) delete decide(spec).responses[status];
  });
  assert.notEqual(missing.status, 0);
  assert.match(missing.stderr, /approval-tasks\/\{taskId\}:decide: declares no application\/problem\+json ProblemDetail/);
  const foreign = generate((spec) => { decide(spec).responses['409'] = { description: 'x', content: { 'application/json': { schema: { type: 'object' } } } }; });
  assert.match(foreign.stderr, /error answer that is not ProblemDetail/);
});

test('page-number paging is refused: lists page by cursor and limit', () => {
  const run = generate((spec) => { spec.paths['/api/v1/platform/approval-tasks'].get.parameters.push({ in: 'query', name: 'page', required: false, schema: { type: 'integer' } }); });
  assert.match(run.stderr, /uses the query parameter "page"/);
});

test('a cursor needs the {items, nextCursor} answer and a limit, and nextCursor needs a cursor', () => {
  const noPage = generate((spec) => { delete spec.components.schemas.InboxResponse.properties.nextCursor; });
  assert.match(noPage.stderr, /takes a cursor but its 200 answer is not \{items, nextCursor\}/);
  const noLimit = generate((spec) => { spec.paths['/api/v1/platform/approval-tasks'].get.parameters = spec.paths['/api/v1/platform/approval-tasks'].get.parameters.filter((parameter) => parameter.name !== 'limit'); });
  assert.match(noLimit.stderr, /takes a cursor but no limit/);
  const orphan = generate((spec) => { spec.paths['/api/v1/platform/approval-tasks'].get.parameters = []; });
  assert.match(orphan.stderr, /answers with nextCursor but takes no cursor parameter/);
});

test('only If-Match and Idempotency-Key may be header parameters, and If-Match must be required', () => {
  const foreign = generate((spec) => decide(spec).parameters.push({ in: 'header', name: 'X-Foo', required: false, schema: { type: 'string' } }));
  assert.match(foreign.stderr, /header parameter "X-Foo"/);
  const optional = generate((spec) => { decide(spec).parameters.find((parameter) => parameter.name === 'If-Match').required = false; });
  assert.match(optional.stderr, /optional If-Match/);
});

test('an unknown schema keyword still fails the generation', () => {
  const run = generate((spec) => { spec.components.schemas.ProblemDetail.properties.title.nullableX = true; });
  assert.match(run.stderr, /unsupported schema keyword "nullableX"/);
});
