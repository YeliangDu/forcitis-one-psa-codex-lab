import { describe, expect, it } from 'vitest';
import { checkedPage, collectPages, nextCursorOf } from './paging';

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

  it('reads pages until the server stops giving a cursor', async () => {
    const asked: (string | undefined)[] = [];
    const pages = [{ items: [1, 2], nextCursor: 'b' }, { items: [3], nextCursor: 'c' }, { items: [4] }];
    const items = await collectPages(async (cursor) => {
      asked.push(cursor);
      return pages[asked.length - 1] ?? { items: [] };
    });
    expect(items).toEqual([1, 2, 3, 4]);
    expect(asked).toEqual([undefined, 'b', 'c']);
  });

  it('fails when the list is longer than the page budget, instead of cutting it silently', async () => {
    let counter = 0;
    await expect(collectPages(async () => ({ items: [1], nextCursor: String((counter += 1)) }), 3)).rejects.toThrow(/more than 3 pages/);
  });
});
