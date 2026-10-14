import { z } from 'zod';

export const SYNC_RUN_RELEASE_PERMISSION = 'organization.workforce:sync-run:execute';

export const syncRunSchema = z.object({
  syncRunId: z.string(),
  trigger: z.string(),
  status: z.string(),
  startedAt: z.string().nullish(),
  finishedAt: z.string().nullish(),
  orgUnitChangeCount: z.number().nullish(),
  employeeChangeCount: z.number().nullish(),
  positionChangeCount: z.number().nullish(),
  issueCount: z.number().nullish(),
  isolatedRowCount: z.number().nullish(),
  heldReason: z.string().nullish(),
  failureReason: z.string().nullish(),
  releasedAt: z.string().nullish(),
});

export const syncDiffSchema = z.object({
  entityKind: z.string().nullish(),
  subjectKey: z.string().nullish(),
  changeKind: z.string().nullish(),
  issueKind: z.string().nullish(),
  detail: z.string().nullish(),
  changedFields: z.array(z.string()).nullish(),
});

export const syncRunDetailSchema = z.object({ syncRun: syncRunSchema, diffs: z.array(syncDiffSchema) });

export const releaseResultSchema = z.object({ outcome: z.string(), message: z.string().nullish() });

export type SyncRun = z.infer<typeof syncRunSchema>;
export type SyncDiff = z.infer<typeof syncDiffSchema>;

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
