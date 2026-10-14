export function taskPath(taskId: string): string {
  return `/approval/tasks/${encodeURIComponent(taskId)}`;
}
