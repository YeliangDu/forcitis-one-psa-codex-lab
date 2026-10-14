import { describe, expect, it } from 'vitest';
import { safeExternalUrl, safeInternalPath } from './safe-url';

describe('safeExternalUrl', () => {
  it('只放行绝对的 https 地址', () => {
    expect(safeExternalUrl('https://old.example.com/a?b=1')).toBe('https://old.example.com/a?b=1');
    for (const bad of ['http://old.example.com/', 'javascript:alert(1)', 'data:text/html,x', '/relative/path', '//evil.example.com', '', '  ', null, undefined]) {
      expect(safeExternalUrl(bad)).toBeUndefined();
    }
  });
});

describe('safeInternalPath', () => {
  it('只接受站内路径', () => {
    expect(safeInternalPath('/inbox?x=1')).toBe('/inbox?x=1');
    for (const bad of ['//evil.example.com', 'https://evil.example.com', 'inbox', '/\\evil', null, undefined]) {
      expect(safeInternalPath(bad)).toBeUndefined();
    }
  });
});
