import { describe, expect, it } from 'vitest';
import { checkedPage, nextCursorOf } from './paging';

describe('cursor paging', () => {
  it('treats an empty cursor like none', () => {
    expect(nextCursorOf({ items: [], nextCursor: '' })).toBeUndefined();
    expect(nextCursorOf({ items: [] })).toBeUndefined();
    expect(nextCursorOf({ items: [], nextCursor: 'b' })).toBe('b');
  });

  it('fails when the server repeats the cursor it was given, instead of looping', () => {
    expect(() => checkedPage({ items: [], nextCursor: 'a' }, 'a')).toThrow(/never end/);
    expect(checkedPage({ items: [], nextCursor: 'b' }, 'a').nextCursor).toBe('b');
  });
});
