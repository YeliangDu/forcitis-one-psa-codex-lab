import type { Schema } from '@forcitis/api';

export const TIMESHEET_SUBMIT_PERMISSION = 'cost.timesheet:timesheet:submit';

const BUSINESS_ZONE = 'Asia/Shanghai';

export type Timesheet = Schema<'TimesheetResponse'>;
export type TimesheetStatus = Schema<'TimesheetStatus'>;
export type SubmitBody = Required<Schema<'SubmitTimesheetRequest'>>;

export const TIMESHEET_STATUS_LABELS: Readonly<Record<TimesheetStatus, string>> = {
  APPROVED: '已通过',
  DRAFT: '草稿',
  REJECTED: '已驳回',
  RETURNED: '已退回',
  SUBMITTED: '审批中',
};

export type StatusTone = 'posted' | 'pending' | 'rejected' | 'info' | 'neutral';

export const TIMESHEET_STATUS_TONES: Readonly<Record<TimesheetStatus, StatusTone>> = {
  APPROVED: 'posted',
  DRAFT: 'neutral',
  REJECTED: 'rejected',
  RETURNED: 'pending',
  SUBMITTED: 'info',
};

/** The content may be changed and the timesheet submitted only in these two states. */
export function isEditable(status: TimesheetStatus): boolean {
  return status === 'DRAFT' || status === 'RETURNED';
}

/** Hours and dates of the business follow Shanghai time, whatever zone the browser is in. */
export function businessToday(now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: BUSINESS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: string) => parts.find((candidate) => candidate.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

export interface TimesheetDraft {
  workDate: string;
  workPackageId: string;
  hours: string;
  projectManagerId: string;
}

export const EMPTY_DRAFT: TimesheetDraft = { workDate: '', workPackageId: '', hours: '', projectManagerId: '' };

export type DraftProblems = Partial<Record<keyof TimesheetDraft, string>>;

const POSITIVE_INTEGER = /^[1-9]\d*$/;

/** The work date is filled in by the user: required and not later than today (ISO dates compare as text). */
export function draftProblems(draft: TimesheetDraft, today: string): DraftProblems {
  const problems: DraftProblems = {};
  if (draft.workDate === '') problems.workDate = '请填写工作日期';
  else if (draft.workDate > today) problems.workDate = '工作日期不能晚于今天';
  if (!POSITIVE_INTEGER.test(draft.workPackageId)) problems.workPackageId = '请填写工作包';
  if (!(Number(draft.hours) > 0)) problems.hours = '请填写工时';
  if (!POSITIVE_INTEGER.test(draft.projectManagerId)) problems.projectManagerId = '请填写项目经理';
  return problems;
}

export function submitBodyOf(draft: TimesheetDraft): SubmitBody {
  return {
    workDate: draft.workDate,
    workPackageId: Number(draft.workPackageId),
    hours: Number(draft.hours),
    projectManagerId: Number(draft.projectManagerId),
  };
}
