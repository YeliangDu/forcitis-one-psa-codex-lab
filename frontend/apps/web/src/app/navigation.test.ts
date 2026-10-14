import { describe, expect, it } from 'vitest';
import { toNavEntries } from './navigation';

describe('toNavEntries', () => {
  it('把用户接口的导航树转成菜单：目录没有路径，页面有路径', () => {
    const entries = toNavEntries([
      { name: '待办', routePath: '/inbox', children: [] },
      { name: '系统管理', routePath: null, children: [{ name: '编号规则', routePath: '/admin/numbering-rules' }] },
    ]);
    expect(entries).toEqual([
      { key: '/待办', label: '待办', path: '/inbox' },
      { key: '/系统管理', label: '系统管理', children: [{ key: '/系统管理/编号规则', label: '编号规则', path: '/admin/numbering-rules' }] },
    ]);
  });
});
