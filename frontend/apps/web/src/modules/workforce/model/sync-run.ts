import type { Schema } from '@forcitis/api';

export const SYNC_RUN_RELEASE_PERMISSION = 'organization.workforce:sync-run:execute';

export type SyncRun = Schema<'SyncRunResponse'>;
export type SyncDiff = Schema<'SyncDiffResponse'>;

export const SYNC_STATUS_LABELS: Readonly<Record<string, string>> = {
  RUNNING: '进行中',
  SUCCEEDED: '成功',
  HELD: '被中止',
  RELEASED: '已放行',
  FAILED: '失败',
};

export const SYNC_TRIGGER_LABELS: Readonly<Record<string, string>> = {
  SCHEDULED: '定时',
  MANUAL: '手动',
};

export type SyncTone = 'posted' | 'pending' | 'rejected' | 'info' | 'neutral';

export function syncTone(status: string): SyncTone {
  if (status === 'SUCCEEDED' || status === 'RELEASED') return 'posted';
  if (status === 'HELD') return 'pending';
  if (status === 'FAILED') return 'rejected';
  if (status === 'RUNNING') return 'info';
  return 'neutral';
}

export function canRelease(run: SyncRun, mayRelease: boolean): boolean {
  return mayRelease && run.status === 'HELD';
}
