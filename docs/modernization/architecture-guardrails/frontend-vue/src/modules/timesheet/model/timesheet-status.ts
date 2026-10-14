import type { StatusTone } from '@/shared/design-system';

export type TimesheetStatus = 'POSTED' | 'PENDING' | 'REJECTED' | 'REVERSAL';

export const STATUS_LABEL: Record<TimesheetStatus, string> = {
  POSTED: '已过账',
  PENDING: '待审批',
  REJECTED: '已驳回',
  REVERSAL: '冲销',
};

export function statusTone(status: TimesheetStatus): StatusTone {
  return status.toLowerCase() as Lowercase<TimesheetStatus>;
}

export function isEditable(status: TimesheetStatus): boolean {
  return status === 'PENDING' || status === 'REJECTED';
}
