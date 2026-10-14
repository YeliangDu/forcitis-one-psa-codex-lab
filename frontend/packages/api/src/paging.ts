export interface CursorPage<Item> {
  items: readonly Item[];
  nextCursor?: string | undefined;
}

export const DEFAULT_PAGE_LIMIT = 50;
export const MAX_PAGE_LIMIT = 200;

/** A server that answers a page with the cursor it was asked for would be read forever; refuse the page instead. */
export function checkedPage<Page extends CursorPage<unknown>>(page: Page, requested: string | undefined): Page {
  if (page.nextCursor !== undefined && page.nextCursor !== '' && page.nextCursor === requested) {
    throw new Error('The server answered a page with the cursor it was asked for; it would never end.');
  }
  return page;
}

/** The cursor of the next page, or undefined when this was the last one. */
export function nextCursorOf(page: CursorPage<unknown>): string | undefined {
  return page.nextCursor === undefined || page.nextCursor === '' ? undefined : page.nextCursor;
}

export async function collectPages<Item>(fetchPage: (cursor: string | undefined) => Promise<CursorPage<Item>>, maxPages = 100): Promise<Item[]> {
  const items: Item[] = [];
  let cursor: string | undefined;
  for (let fetched = 0; fetched < maxPages; fetched += 1) {
    const page = checkedPage(await fetchPage(cursor), cursor);
    items.push(...page.items);
    cursor = nextCursorOf(page);
    if (cursor === undefined) return items;
  }
  throw new Error(`The list has more than ${maxPages} pages; narrow the query instead of reading it all.`);
}
