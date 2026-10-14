export const INBOX_PAGE = '/app/pages/inbox';
export const TASK_PAGE = '/app/pages/task';

export function taskPath(taskId: string): string {
  return `${TASK_PAGE}?taskId=${encodeURIComponent(taskId)}`;
}
