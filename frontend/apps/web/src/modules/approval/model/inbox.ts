import { z } from 'zod';

export const inboxItemSchema = z.object({
  source: z.enum(['NEW', 'LEGACY']),
  taskId: z.string(),
  title: z.string(),
  nodeName: z.string().nullish(),
  createdAt: z.string().nullish(),
  claimed: z.boolean(),
  detailUrl: z.string().nullish(),
});

export const mergedInboxSchema = z.object({
  items: z.array(inboxItemSchema),
  newCount: z.number(),
  legacyCount: z.number(),
  totalCount: z.number(),
  legacyAvailable: z.boolean(),
});

export type InboxItem = z.infer<typeof inboxItemSchema>;
export type MergedInbox = z.infer<typeof mergedInboxSchema>;

export type InboxFilterKey = 'all' | 'new' | 'legacy';

export function filterInbox(items: readonly InboxItem[], filter: InboxFilterKey): InboxItem[] {
  if (filter === 'all') return [...items];
  const source = filter === 'new' ? 'NEW' : 'LEGACY';
  return items.filter((item) => item.source === source);
}

export function inboxRowId(item: InboxItem): string {
  return `${item.source}:${item.taskId}`;
}

export function taskPath(taskId: string): string {
  return `/approval/tasks/${encodeURIComponent(taskId)}`;
}
