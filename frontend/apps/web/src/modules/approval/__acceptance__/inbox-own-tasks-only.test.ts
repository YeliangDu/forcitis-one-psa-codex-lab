import { describe, expect, it } from 'vitest';

// IMP-1021-PAR-01: the inbox shows this system's approval tasks only. Judged on the source text of the web inbox module, the shared
// approval model in packages/core and the generated api types; test files themselves are not judged.
const webSources = import.meta.glob<string>('../**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true });
const coreSources = import.meta.glob<string>('../../../../../../packages/core/src/approval/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true });
const generatedSchema = import.meta.glob<string>('../../../../../../packages/api/src/generated/schema.ts', { query: '?raw', import: 'default', eager: true });
const generatedValidators = import.meta.glob<string>('../../../../../../packages/api/src/generated/validators.ts', { query: '?raw', import: 'default', eager: true });

const isProduction = (path: string) => !/\.test\.tsx?$/.test(path) && !path.includes('__acceptance__') && !path.includes('/testing/');
const productionFiles = (sources: Record<string, string>) => Object.entries(sources).filter(([path]) => isProduction(path));

const OLD_TODO_SOURCE = /legacy|老系统|detailUrl/i;

function filesMentioningOldTodos(sources: Record<string, string>): string[] {
  return productionFiles(sources)
    .filter(([, text]) => OLD_TODO_SOURCE.test(text))
    .map(([path]) => path)
    .sort();
}

/** The type literal `Name: { ... };` of schema.ts (openapi-typescript style, closing brace at 8 spaces). */
function typeBlock(text: string, name: string): string {
  const from = text.indexOf(`        ${name}: {`);
  expect(from, `${name} is declared in schema.ts`).toBeGreaterThanOrEqual(0);
  return text.slice(from, text.indexOf('\n        };', from));
}

/** The `export const NameSchema = z.object({ ... });` of validators.ts. */
function zodBlock(text: string, name: string): string {
  const from = text.indexOf(`export const ${name}Schema = z.object({`);
  expect(from, `${name}Schema is declared in validators.ts`).toBeGreaterThanOrEqual(0);
  return text.slice(from, text.indexOf('\n});', from));
}

describe('F-IMP1021 收件箱只含本系统审批待办（前端 web 与共享层）', () => {
  it('F-IMP1021-20 web 收件箱模块源码不引用老待办来源（legacy、老系统、detailUrl）', () => {
    expect(productionFiles(webSources).length, 'web approval sources were found').toBeGreaterThan(3);
    expect(filesMentioningOldTodos(webSources)).toEqual([]);
  });

  it('F-IMP1021-21 packages/core 的审批模型与 hooks 不引用老待办来源，也没有 legacy 筛选页签', () => {
    expect(productionFiles(coreSources).length, 'core approval sources were found').toBeGreaterThan(3);
    expect(filesMentioningOldTodos(coreSources)).toEqual([]);
  });

  it('F-IMP1021-22 生成的 api 类型里 InboxResponse 没有 legacyCount、legacyAvailable，InboxItemResponse 没有 source、detailUrl', () => {
    const schema = Object.values(generatedSchema)[0];
    expect(typeof schema, 'generated schema.ts was found').toBe('string');
    const response = typeBlock(schema ?? '', 'InboxResponse');
    const item = typeBlock(schema ?? '', 'InboxItemResponse');
    expect(response).toContain('newCount');
    expect(response).not.toMatch(/legacyCount|legacyAvailable/);
    expect(item).toContain('taskId');
    expect(item).not.toMatch(/\bsource\b|detailUrl/);
  });

  it('F-IMP1021-23 生成的 zod 校验里 InboxResponseSchema、InboxItemResponseSchema 没有老待办字段', () => {
    const validators = Object.values(generatedValidators)[0];
    expect(typeof validators, 'generated validators.ts was found').toBe('string');
    const response = zodBlock(validators ?? '', 'InboxResponse');
    const item = zodBlock(validators ?? '', 'InboxItemResponse');
    expect(response).toContain('newCount');
    expect(response).not.toMatch(/legacyCount|legacyAvailable/);
    expect(item).toContain('taskId');
    expect(item).not.toMatch(/\bsource\b|detailUrl/);
  });
});
