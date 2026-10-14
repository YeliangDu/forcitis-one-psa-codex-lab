import type { Schema } from '@forcitis/api';

export type ProcessKey = Schema<'ProcessKey'>;
export type DocumentType = Schema<'DocumentType'>;
export type TaskRecordAction = Schema<'TaskRecordAction'>;

/** A value the table does not know (a newer server) shows as a dash; the client reports it once (G-1). */
export function labelOf(labels: Readonly<Record<string, string>>, value: string): string {
  return labels[value] ?? '—';
}

/** The tone of a value the table does not know is neutral. */
export function toneOf<Tone extends string>(tones: Readonly<Record<string, Tone>>, value: string): Tone | 'neutral' {
  return tones[value] ?? 'neutral';
}

export const PROCESS_LABELS: Readonly<Record<ProcessKey, string>> = {
  COST_TIMESHEET_APPROVAL: '工时单审批',
};

export const DOCUMENT_TYPE_LABELS: Readonly<Record<DocumentType, string>> = {
  JOURNAL_VOUCHER: '会计凭证',
  QUOTE: '报价单',
  TIMESHEET: '工时单',
};

export type OutcomeTone = 'posted' | 'pending' | 'rejected' | 'info';

export const TASK_RECORD_ACTION_TONES: Readonly<Record<TaskRecordAction, OutcomeTone>> = {
  APPROVED: 'posted',
  AUTO_APPROVED: 'posted',
  REJECTED: 'rejected',
  RETURNED: 'pending',
  TRANSFERRED: 'info',
};

export const TASK_RECORD_ACTION_LABELS: Readonly<Record<TaskRecordAction, string>> = {
  APPROVED: '通过',
  AUTO_APPROVED: '自动通过',
  REJECTED: '驳回',
  RETURNED: '退回',
  TRANSFERRED: '转交',
};
