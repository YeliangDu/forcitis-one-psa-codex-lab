import type { Schema } from '@forcitis/api';

export const SYNC_RUN_RELEASE_PERMISSION = 'organization.workforce:sync-run:execute';

export type SyncRun = Schema<'SyncRunResponse'>;
export type SyncDiff = Schema<'SyncDiffResponse'>;

export const SYNC_STATUS_LABELS: Readonly<Record<string, string>> = {
  RUNNING: '运行中',
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

export const RELEASE_OUTCOME_MESSAGES: Readonly<Record<string, string>> = {
  NOT_FOUND: '同步记录不存在',
  NOT_HELD: '该同步记录当前不是被中止状态，无法放行',
  NOT_APPLIED: '放行时应用组织数据失败，请查看同步记录的说明后重试',
};
