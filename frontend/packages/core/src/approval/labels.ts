import type { Schema } from '@forcitis/api';

export type DocumentType = string;
export type TaskRecordAction = Schema<'TaskRecordAction'>;

const UNKNOWN_TEXT = '—';

/** A value the table does not know (a newer server) shows as a dash; the client reports it once (G-1). */
export function labelOf(labels: Readonly<Record<string, string>>, value: string): string {
  return labels[value] ?? UNKNOWN_TEXT;
}

/** The name of a process comes with the response (it is data of the process definition, REG-4); a response without one shows as a dash. */
export function processNameText(processName: string | undefined): string {
  return processName ?? UNKNOWN_TEXT;
}

/** The tone of a value the table does not know is neutral. */
export function toneOf<Tone extends string>(tones: Readonly<Record<string, Tone>>, value: string): Tone | 'neutral' {
  return tones[value] ?? 'neutral';
}

/**
 * The text and tone of a status badge from a label table and a tone table. Unlike labelOf, a value the label table does not know shows as
 * itself: these pages show the server's own status code, as they did before this helper. The tone of an unknown value is neutral.
 */
export function badgeOf<Tone extends string>(labels: Readonly<Record<string, string>>, tones: Readonly<Record<string, Tone>>, value: string): { label: string; tone: Tone | 'neutral' } {
  return { label: labels[value] ?? value, tone: toneOf(tones, value) };
}

export const DOCUMENT_TYPE_LABELS: Readonly<Record<string, string>> = {
  JOURNAL_VOUCHER: '会计凭证',
  QUOTE: '报价单',
  TIMESHEET: '工时单',
};

export type OutcomeTone = 'posted' | 'pending' | 'rejected' | 'info' | 'neutral';

export const TASK_RECORD_ACTION_TONES: Readonly<Record<TaskRecordAction, OutcomeTone>> = {
  ADVISED: 'info',
  APPROVED: 'posted',
  CLAIMED: 'info',
  PASSED_AUTOMATICALLY: 'posted',
  REASSIGNED: 'info',
  RELEASED: 'neutral',
  REJECTED: 'rejected',
  RETURNED: 'pending',
  TRANSFERRED: 'info',
};

export const TASK_RECORD_ACTION_LABELS: Readonly<Record<TaskRecordAction, string>> = {
  ADVISED: '加签建议',
  APPROVED: '通过',
  CLAIMED: '认领',
  PASSED_AUTOMATICALLY: '自动通过',
  REASSIGNED: '改派',
  RELEASED: '释放',
  REJECTED: '驳回',
  RETURNED: '退回',
  TRANSFERRED: '转交',
};
