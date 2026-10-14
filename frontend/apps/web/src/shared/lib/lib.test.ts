import { describe, expect, it } from 'vitest';
import { describeFailure, failureOf } from './failure';
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

describe('describeFailure', () => {
  it('优先使用模块给出的业务错误码文案，其次按状态码给通用文案', () => {
    const conflict = Object.assign(new Error('x'), { status: 409, code: 'A' });
    expect(describeFailure(conflict, { A: '专用文案' })).toBe('专用文案');
    expect(describeFailure(conflict)).toBe('当前状态不允许此操作，请刷新后重试');
    expect(describeFailure(Object.assign(new Error('x'), { status: 503 }))).toBe('服务暂时不可用，请稍后重试');
    expect(describeFailure(new TypeError('Failed to fetch'))).toBe('无法连接服务器，请检查网络后重试');
  });

  it('读不出状态时不抛错', () => {
    expect(failureOf('boom')).toEqual({ status: undefined, code: undefined, body: undefined });
  });
});

describe('formatDateTime', () => {
  it('没有时间或时间无效时显示破折号', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('not a date')).toBe('—');
    expect(formatDateTime('2026-10-14T01:00:00Z')).toMatch(/2026/);
  });
});
