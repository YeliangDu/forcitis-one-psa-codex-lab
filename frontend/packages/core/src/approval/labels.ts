import type { Schema } from '@forcitis/api';

export type ProcessKey = Schema<'ProcessKey'>;
export type DocumentType = Schema<'DocumentType'>;
export type ApprovalStatus = Schema<'ApprovalStatus'>;
export type TaskRecordAction = Schema<'TaskRecordAction'>;

export const PROCESS_LABELS: Readonly<Record<ProcessKey, string>> = {
  COST_TIMESHEET_APPROVAL: '工时单审批',
};

export const DOCUMENT_TYPE_LABELS: Readonly<Record<DocumentType, string>> = {
  JOURNAL_VOUCHER: '会计凭证',
  QUOTE: '报价单',
  TIMESHEET: '工时单',
};

export const APPROVAL_STATUS_LABELS: Readonly<Record<ApprovalStatus, string>> = {
  APPROVED: '已通过',
  CANCELLED: '已撤销',
  REJECTED: '已驳回',
  RETURNED: '已退回',
  RUNNING: '审批中',
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
