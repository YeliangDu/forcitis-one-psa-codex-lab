// Generates packages/api/src/generated/{schema,validators,operations}.ts from packages/api/openapi.json (written by the backend's OpenApiExportTest).
// schema.ts = TypeScript types (openapi-typescript); validators.ts = Zod schemas of every component and of every operation's 200 answer;
// operations.ts = one function per operation that validates the answer at runtime and carries its platform traits (If-Match, Idempotency-Key).
// The three files always move together (`--check` in `pnpm verify`).
// The document must keep the platform conventions (PHASE1A_PLATFORM_CONVENTIONS): ProblemDetail + ErrorCode components, an error answer on every
// operation, cursor paging ({items, nextCursor} with cursor/limit, never page/offset), If-Match / Idempotency-Key as the only header parameters.
//   node scripts/generate-api.mjs           regenerate
//   node scripts/generate-api.mjs --check   fail when the committed files differ from what openapi.json produces (runs in `pnpm verify`)
//   --spec <file> --out <dir>                read another document / write another directory (scripts/generate-api.test.mjs)
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';
import ts from 'typescript';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'packages', 'api');
const valueOf = (flag) => process.argv.find((argument, index, all) => all[index - 1] === flag);
const specPath = valueOf('--spec') ?? join(root, 'openapi.json');
const outDir = valueOf('--out') ?? join(root, 'src', 'generated');
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

const IF_MATCH = 'If-Match';
const IDEMPOTENCY_KEY = 'Idempotency-Key';
const CONTENT_DISPOSITION = 'Content-Disposition';
const PAGING_PARAMETERS_FORBIDDEN = new Set(['page', 'size', 'offset', 'pageSize', 'pageNo']);

function requireSchema(name) {
  if (spec.components?.schemas?.[name] === undefined) {
    throw new Error(`generate-api: components.schemas.${name} is missing. The backend document must declare it (ProblemDetail and the ErrorCode enum are part of the contract).`);
  }
}
requireSchema('ProblemDetail');
requireSchema('ErrorCode');
if (spec.components.schemas.ErrorCode.enum === undefined || spec.components.schemas.ErrorCode.enum.length === 0) {
  throw new Error('generate-api: components.schemas.ErrorCode must be a non-empty string enum.');
}
if (spec.components.schemas.ProblemDetail.properties?.errorCode?.$ref !== '#/components/schemas/ErrorCode') {
  throw new Error('generate-api: ProblemDetail.errorCode must reference the ErrorCode enum.');
}

function resolveSchema(node) {
  return node?.$ref === undefined ? node : spec.components.schemas[refName(node.$ref)];
}

function checkConventions(operation, where) {
  const failures = [];
  const parameters = operation.parameters ?? [];
  const errorAnswers = Object.entries(operation.responses ?? {}).filter(([status]) => status === 'default' || /^[45]\d\d$/.test(status));
  const declaresProblem = errorAnswers.some(([, answer]) => answer.content?.['application/problem+json']?.schema?.$ref === '#/components/schemas/ProblemDetail');
  if (!declaresProblem) failures.push('declares no application/problem+json ProblemDetail error answer');
  if (errorAnswers.some(([, answer]) => Object.values(answer.content ?? {}).some((media) => media.schema?.$ref !== '#/components/schemas/ProblemDetail'))) {
    failures.push('has an error answer that is not ProblemDetail');
  }
  for (const parameter of parameters) {
    if (parameter.in === 'query' && PAGING_PARAMETERS_FORBIDDEN.has(parameter.name)) failures.push(`uses the query parameter "${parameter.name}"; lists page by cursor + limit`);
    const carriesUpload = operation.requestBody?.content?.['application/octet-stream'] !== undefined;
    const managed = parameter.name === IF_MATCH || parameter.name === IDEMPOTENCY_KEY || (carriesUpload && parameter.name === CONTENT_DISPOSITION);
    if (parameter.in === 'header' && !managed) failures.push(`declares the header parameter "${parameter.name}"; only ${IF_MATCH}, ${IDEMPOTENCY_KEY} and, on a binary upload, ${CONTENT_DISPOSITION} are managed by the client`);
    if (parameter.in === 'header' && parameter.name === IF_MATCH && parameter.required !== true) failures.push('declares an optional If-Match; a versioned update must require it');
  }
  const hasCursor = parameters.some((parameter) => parameter.in === 'query' && parameter.name === 'cursor');
  const answer = resolveSchema(operation.responses?.['200']?.content?.['application/json']?.schema ?? operation.responses?.['200']?.content?.['*/*']?.schema);
  const paged = answer?.properties?.nextCursor !== undefined;
  if (hasCursor && !(paged && answer.properties.items?.type === 'array')) failures.push('takes a cursor but its 200 answer is not {items, nextCursor}');
  if (paged && !hasCursor) failures.push('answers with nextCursor but takes no cursor parameter');
  if (hasCursor && !parameters.some((parameter) => parameter.in === 'query' && parameter.name === 'limit')) failures.push('takes a cursor but no limit');
  if (failures.length > 0) throw new Error(`generate-api: ${where}: ${failures.join('; ')}`);
}

const operations = [];
const seen = new Set();
for (const path of Object.keys(spec.paths).sort()) {
  for (const method of METHODS) {
    if (!spec.paths[path][method]) continue;
    const name = operationName(method, path);
    if (seen.has(name)) throw new Error(`generate-api: two operations would be named ${name}`);
    seen.add(name);
    checkConventions(spec.paths[path][method], `${method} ${path}`);
    const headerNames = (spec.paths[path][method].parameters ?? []).filter((parameter) => parameter.in === 'header').map((parameter) => parameter.name);
    operations.push({ name, method, path, versioned: headerNames.includes(IF_MATCH), idempotent: headerNames.includes(IDEMPOTENCY_KEY) });
  }
}

const ZERO_PAD = '  ';

/** Keywords the Zod emitter understands. Any other keyword fails the generation instead of being silently ignored (G-1). */
const KNOWN_KEYWORDS = new Set(['type', 'format', 'enum', 'items', 'properties', 'required', 'additionalProperties', 'oneOf', 'pattern', 'uniqueItems', 'description', 'example', 'examples', 'default', 'title', '$ref', 'minimum', 'maximum', 'minLength', 'maxLength', 'minItems', 'maxItems']);

function refName(ref) {
  const match = /^#\/components\/schemas\/(\w+)$/.exec(ref);
  if (!match) throw new Error(`generate-api: unsupported $ref ${ref}`);
  return match[1];
}

function containsRef(node) {
  if (node === null || typeof node !== 'object') return false;
  if (node.$ref !== undefined) return true;
  return Object.values(node).some((value) => containsRef(value));
}

function literal(value) {
  return JSON.stringify(value);
}

/** One OpenAPI schema node as a Zod expression. Properties holding a $ref become getters, so recursive schemas need no declaration order. */
function zodOf(node, where) {
  for (const key of Object.keys(node)) {
    if (!KNOWN_KEYWORDS.has(key)) throw new Error(`generate-api: ${where}: unsupported schema keyword "${key}"`);
  }
  if (node.$ref !== undefined) return `${refName(node.$ref)}Schema`;
  if (node.oneOf !== undefined) return `z.union([${node.oneOf.map((option, index) => zodOf(option, `${where}.oneOf[${index}]`)).join(', ')}])`;
  const nullable = Array.isArray(node.type) && node.type.includes('null');
  const type = Array.isArray(node.type) ? node.type.find((candidate) => candidate !== 'null') : node.type;
  let expression;
  switch (type) {
    case 'string': {
      expression = node.enum === undefined ? 'z.string()' : `lenientEnum(${literal(where)}, [${node.enum.map(literal).join(', ')}])`;
      if (node.pattern !== undefined) expression += `.regex(new RegExp(${literal(node.pattern)}))`;
      break;
    }
    case 'integer':
      expression = 'z.number().int()';
      break;
    case 'number':
      expression = 'z.number()';
      break;
    case 'boolean':
      expression = 'z.boolean()';
      break;
    case 'array':
      if (node.items === undefined) throw new Error(`generate-api: ${where}: array without items`);
      expression = `z.array(${zodOf(node.items, `${where}[]`)})`;
      break;
    case 'object': {
      if (node.properties === undefined) {
        expression = typeof node.additionalProperties === 'object' ? `z.record(z.string(), ${zodOf(node.additionalProperties, `${where}{}`)})` : 'z.unknown()';
        break;
      }
      const required = new Set(node.required ?? []);
      const members = Object.entries(node.properties).map(([name, child]) => {
        const value = zodOf(child, `${where}.${name}`) + (required.has(name) ? '' : '.optional()');
        const key = /^[A-Za-z_$][\w$]*$/.test(name) ? name : literal(name);
        return containsRef(child) ? `get ${key}() { return ${value}; }` : `${key}: ${value}`;
      });
      expression = `z.object({\n${members.map((member) => `${ZERO_PAD}${member},`).join('\n')}\n})`;
      break;
    }
    default:
      throw new Error(`generate-api: ${where}: unsupported schema type ${literal(node.type)}`);
  }
  return nullable ? `${expression}.nullable()` : expression;
}

function successSchema(operation, where) {
  const response = operation.responses?.['200'];
  const content = response?.content;
  if (content === undefined) return undefined;
  const media = content['application/json'] ?? content['*/*'];
  if (media?.schema === undefined) throw new Error(`generate-api: ${where}: the 200 answer has content but no schema`);
  return media.schema;
}

const componentEntries = Object.entries(spec.components?.schemas ?? {}).sort(([a], [b]) => a.localeCompare(b));
const componentSource = componentEntries.map(([name, node]) => {
  const expression = zodOf(node, name);
  const isObject = node.type === 'object' && node.properties !== undefined;
  return `export const ${name}Schema = ${isObject || !containsRef(node) ? expression : `z.lazy(() => ${expression})`};`;
}).join('\n\n');

const responseEntries = operations.map(({ name, method, path }) => {
  const schemaNode = successSchema(spec.paths[path][method], `${method} ${path}`);
  return schemaNode === undefined ? undefined : `  ${name}: ${zodOf(schemaNode, `${method} ${path}`)},`;
}).filter((entry) => entry !== undefined);

const HEADER = '// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.\n';
const isEnum = (node) => node.enum !== undefined || (node.$ref !== undefined && spec.components?.schemas?.[refName(node.$ref)]?.enum !== undefined);

/**
 * The Zod validators let a value a newer server added pass through (lenientEnum), so the type of an enum-valued property is
 * `Union | (string & {})`: a table lookup by it does not compile without a fallback (labelOf). The enum components themselves stay closed unions,
 * so `Record<Union, ...>` tables remain exhaustive.
 */
function withoutNull(type) {
  if (!ts.isUnionTypeNode(type)) return type;
  const kept = type.types.filter((member) => !(ts.isLiteralTypeNode(member) && member.literal.kind === ts.SyntaxKind.NullKeyword));
  if (kept.length === type.types.length) return type;
  return kept.length === 1 ? kept[0] : ts.factory.createUnionTypeNode(kept);
}

/**
 * The client removes every null from an answer before it is validated (the server writes an absent optional property as null), so no property is typed `| null`.
 * Enum-valued properties get the open union described above.
 */
function adjustProperty(property, schemaObject) {
  if (property.type === undefined) return undefined;
  const type = withoutNull(property.type);
  const open = ts.factory.createIntersectionTypeNode([ts.factory.createKeywordTypeNode(ts.SyntaxKind.StringKeyword), ts.factory.createTypeLiteralNode([])]);
  const finalType = isEnum(schemaObject) ? ts.factory.createUnionTypeNode([type, open]) : type;
  if (finalType === property.type) return undefined;
  return ts.factory.updatePropertySignature(property, property.modifiers, property.name, property.questionToken, finalType);
}

/** An enum component the backend marked nullable (type ["string", "null"]) is a plain enum for the types: the client removes nulls before anything reads them. */
const typeSpec = structuredClone(spec);
for (const component of Object.values(typeSpec.components?.schemas ?? {})) {
  if (component.enum !== undefined && Array.isArray(component.type)) component.type = component.type.find((candidate) => candidate !== 'null');
}

const schema = HEADER + astToString(await openapiTS(typeSpec, { transformProperty: adjustProperty }));
const errorCodeValues = `export const ErrorCodeValues = [${spec.components.schemas.ErrorCode.enum.map(literal).join(', ')}] as const;`;

const validatorsSource = `${HEADER}import { z } from 'zod';
import { lenientEnum } from '../contract';

${errorCodeValues}

${componentSource}

export const responseSchemas = {
${responseEntries.join('\n')}
} as const;
`;

const withResponseSchema = new Set(responseEntries.map((entry) => entry.trim().split(':')[0]));
function traitsOf({ versioned, idempotent }) {
  const traits = [versioned ? 'versioned: true' : '', idempotent ? 'idempotent: true' : ''].filter(Boolean);
  return traits.length === 0 ? '' : `, { ${traits.join(', ')} }`;
}

const operationsSource = `${HEADER}import { type Call, type Result, request } from '../runtime';
import type { paths } from './schema';
import { responseSchemas } from './validators';

${operations.map((operation) => {
  const { name, method, path } = operation;
  const traits = traitsOf(operation);
  const schemaArgument = withResponseSchema.has(name) ? `responseSchemas.${name}` : 'undefined';
  return `export const ${name} = (...[args, options]: Call<paths['${path}']['${method}']>) =>
  request<Result<paths['${path}']['${method}']>>('${method}', '${path}', args, options, ${schemaArgument}${traits});`;
}).join('\n\n')}
`;

const files = { 'schema.ts': schema, 'validators.ts': validatorsSource, 'operations.ts': operationsSource };
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
