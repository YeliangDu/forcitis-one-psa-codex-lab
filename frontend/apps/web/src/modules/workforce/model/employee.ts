import type { Schema } from '@forcitis/api';

export type EmployeeRoster = Schema<'EmployeeRosterResponse'>;
export type EmployeeDetail = Schema<'EmployeeDetailResponse'>;
export type EmploymentFilter = Schema<'EmploymentFilter'>;

export const EMPLOYMENT_FILTERS: readonly { value: EmploymentFilter; label: string }[] = [
  { value: 'EMPLOYED', label: '在职' },
  { value: 'ON_UNPAID_LEAVE', label: '停薪留职' },
  { value: 'SEPARATED', label: '已离职' },
  { value: 'ALL', label: '全部' },
];

export const EMPLOYMENT_STATUS_LABELS: Readonly<Record<string, string>> = {
  EMPLOYED: '在职',
  SEPARATED: '已离职',
};

/** A person on unpaid leave is still EMPLOYED; the badge says 停薪留职 instead of 在职. */
export function employmentStatusLabel(employmentStatus: string, onUnpaidLeave: boolean): string {
  if (employmentStatus === 'EMPLOYED' && onUnpaidLeave) return '停薪留职';
  return EMPLOYMENT_STATUS_LABELS[employmentStatus] ?? employmentStatus;
}

export const ASSIGNMENT_KIND_LABELS: Readonly<Record<string, string>> = {
  PRIMARY: '主岗',
  CONCURRENT: '兼岗',
};

export function employmentFilterOf(value: string): EmploymentFilter {
  return EMPLOYMENT_FILTERS.find((filter) => filter.value === value)?.value ?? 'EMPLOYED';
}

export function employeePath(employeeId: string): string {
  return `/admin/employees/${encodeURIComponent(employeeId)}`;
}
