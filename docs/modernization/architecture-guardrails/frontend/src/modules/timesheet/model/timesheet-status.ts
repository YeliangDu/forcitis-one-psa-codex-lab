export type TimesheetStatus = 'DRAFT' | 'IN_APPROVAL' | 'REJECTED' | 'APPROVED' | 'CANCELLED';

export function isEditable(status: TimesheetStatus): boolean {
  return status === 'DRAFT' || status === 'REJECTED' || status === 'CANCELLED';
}
