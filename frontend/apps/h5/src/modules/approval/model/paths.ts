import type { Schema } from '@forcitis/api';

export type InboxItem = Schema<'InboxItemResponse'>;
export type MergedInbox = Schema<'InboxResponse'>;

export const INBOX_PAGE = '/app/pages/inbox';
export const TASK_PAGE = '/app/pages/task';

export type InboxFilterKey = 'all' | 'new' | 'legacy';

export function filterInbox(items: readonly InboxItem[], filter: InboxFilterKey): InboxItem[] {
  if (filter === 'all') return [...items];
  const source = filter === 'new' ? 'NEW' : 'LEGACY';
  return items.filter((item) => item.source === source);
}

export function inboxRowId(item: InboxItem): string {
  return `${item.source ?? ''}:${item.taskId ?? ''}`;
}

export function taskPath(taskId: string): string {
  return `${TASK_PAGE}?taskId=${encodeURIComponent(taskId)}`;
}
