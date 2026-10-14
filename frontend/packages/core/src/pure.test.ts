import { describe, expect, it } from 'vitest';
import { formatDateTime } from './format-date-time';
import { safeExternalUrl, safeInternalPath } from './safe-url';

describe('safeExternalUrl', () => {
  it('只放行绝对的 http(s) 地址', () => {
    expect(safeExternalUrl('https://old.example.com/a?b=1')).toBe('https://old.example.com/a?b=1');
    expect(safeExternalUrl('http://old.example.com/')).toBe('http://old.example.com/');
    for (const bad of ['javascript:alert(1)', 'data:text/html,x', '/relative/path', '//evil.example.com', '', '  ', null, undefined]) {
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

describe('formatDateTime', () => {
  it('没有时间或时间无效时显示破折号', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('not a date')).toBe('—');
    expect(formatDateTime('2026-10-14T01:00:00Z')).toMatch(/2026/);
  });
});
