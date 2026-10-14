export interface CursorPage<Item> {
  items: readonly Item[];
  nextCursor?: string | undefined;
}

export const DEFAULT_PAGE_LIMIT = 50;
export const MAX_PAGE_LIMIT = 200;

/** The cursor of the next page, or undefined when this was the last one. A server that answers with the cursor it was given would loop forever. */
export function nextCursorOf(page: CursorPage<unknown>, requested: string | undefined): string | undefined {
  if (page.nextCursor === undefined || page.nextCursor === '') return undefined;
  if (page.nextCursor === requested) throw new Error('The server answered a page with the cursor it was asked for; it would never end.');
  return page.nextCursor;
}

export async function collectPages<Item>(fetchPage: (cursor: string | undefined) => Promise<CursorPage<Item>>, maxPages = 100): Promise<Item[]> {
  const items: Item[] = [];
  let cursor: string | undefined;
  for (let fetched = 0; fetched < maxPages; fetched += 1) {
    const page = await fetchPage(cursor);
    items.push(...page.items);
    cursor = nextCursorOf(page, cursor);
    if (cursor === undefined) return items;
  }
  throw new Error(`The list has more than ${maxPages} pages; narrow the query instead of reading it all.`);
}
