/// <reference types="vite/client" />
import { describe, expect, it } from 'vitest';

// IMP-1021-PAR-01: the h5 inbox shows this system's approval tasks only. Judged on the source text of the h5 approval module and
// the h5 README; test files themselves are not judged.
const moduleSources = import.meta.glob<string>('../**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true });
const readme = import.meta.glob<string>('../../../../README.md', { query: '?raw', import: 'default', eager: true });

const OLD_TODO_SOURCE = /legacy|老系统|detailUrl/i;
const isProduction = (path: string) => !/\.test\.tsx?$/.test(path) && !path.includes('__acceptance__') && !path.includes('/testing/');

describe('F-IMP1021 收件箱只含本系统审批待办（前端 h5）', () => {
  it('F-IMP1021-30 h5 审批模块源码不引用老待办来源（legacy、老系统、detailUrl）', () => {
    const files = Object.entries(moduleSources).filter(([path]) => isProduction(path));
    expect(files.length, 'h5 approval sources were found').toBeGreaterThan(3);
    expect(files.filter(([, text]) => OLD_TODO_SOURCE.test(text)).map(([path]) => path).sort()).toEqual([]);
  });

  it('F-IMP1021-31 apps/h5/README.md 的页面说明不再写合并老系统待办', () => {
    const files = Object.entries(readme);
    expect(files.length, 'h5 README was found').toBe(1);
    const text = files[0]?.[1];
    expect(typeof text, 'h5 README text was read').toBe('string');
    expect(text ?? '').not.toMatch(/老系统|legacy/i);
  });
});
