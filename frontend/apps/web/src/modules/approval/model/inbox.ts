import type { Schema } from '@forcitis/api';

export type InboxItem = Schema<'InboxItem'>;
export type MergedInbox = Schema<'MergedInbox'>;

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
  return `/approval/tasks/${encodeURIComponent(taskId)}`;
}
