import type { Schema } from '@forcitis/api';

export type PersonnelCommand = Schema<'PersonnelReadResponse'>;
export type PersonnelCommandKind = Schema<'PersonnelCommandKind'>;
export type EnterPersonnelCommandBody = Schema<'EnterPersonnelCommandRequest'>;

/** END_CONCURRENT is not offered: it names the concurrent post it ends by assignment id, and no read of the contract returns that id. */
export const SUBMITTABLE_KINDS = ['HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION', 'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END', 'CHANGE_LEGAL_ENTITY', 'START_CONCURRENT', 'ACTING_START', 'ACTING_END', 'ACTING_EXTEND'] as const satisfies readonly PersonnelCommandKind[];
export type SubmittableKind = (typeof SUBMITTABLE_KINDS)[number];

export const COMMAND_KIND_LABELS: Readonly<Record<string, string>> = {
  HIRE: '入职',
  REGULARIZE: '转正',
  TRANSFER: '调岗',
  SEPARATE: '离职',
  REINSTATE: '复职',
  CORRECTION: '更正',
  UNPAID_LEAVE_START: '停薪留职开始',
  UNPAID_LEAVE_END: '停薪留职结束',
  CHANGE_LEGAL_ENTITY: '变更法人归属',
  START_CONCURRENT: '开始兼岗',
  END_CONCURRENT: '结束兼岗',
  ACTING_START: '开始代理',
  ACTING_END: '提前结束',
  ACTING_EXTEND: '代理延长',
};

export const COMMAND_STATUS_LABELS: Readonly<Record<string, string>> = {
  PENDING: '待生效',
  HELD: '待放行',
  EFFECTIVE: '已生效',
  WITHDRAWN: '已撤回',
  AWAITING_ATTRIBUTION_COUNTERSIGN: '待法人归属会签',
};

export const COMMAND_STATUS_TONES: Readonly<Record<string, 'posted'>> = { EFFECTIVE: 'posted' };

export const HOLD_STATUS_LABELS: Readonly<Record<string, string>> = {
  HELD: '待放行',
  RELEASED: '已放行',
  WITHDRAWN: '已撤回',
};

/** The fields a kind takes, as the server accepts them (PersonnelCommand.requireShapeOfKind); the server still decides every rule. */
export interface KindFields {
  readonly acting?: 'start' | 'end' | 'extend';
  readonly newPerson?: true;
  readonly date: 'effectiveDate' | 'lastWorkingDay';
  readonly orgUnit?: 'required' | 'optional';
  readonly position?: 'required' | 'optional';
  readonly reportsTo?: 'required' | 'optional';
  readonly feeEarner?: 'required' | 'optional';
  readonly phase?: 'required' | 'optional';
  readonly legalEntity?: 'required' | 'optional';
  /** The employer of a hire (targetEmployerLegalEntityId, required since #1401), distinct from the cost legal entity. */
  readonly employer?: 'required';
}

export const KIND_FIELDS: Readonly<Record<SubmittableKind, KindFields>> = {
  HIRE: { newPerson: true, date: 'effectiveDate', orgUnit: 'required', position: 'required', reportsTo: 'optional', feeEarner: 'required', phase: 'required', employer: 'required' },
  REINSTATE: { date: 'effectiveDate', orgUnit: 'required', position: 'required', reportsTo: 'optional', feeEarner: 'required', phase: 'required' },
  TRANSFER: { date: 'effectiveDate', orgUnit: 'optional', position: 'optional', reportsTo: 'optional', feeEarner: 'optional', legalEntity: 'optional' },
  CORRECTION: { date: 'effectiveDate', orgUnit: 'optional', position: 'optional', reportsTo: 'optional', feeEarner: 'optional', phase: 'optional', legalEntity: 'optional' },
  CHANGE_LEGAL_ENTITY: { date: 'effectiveDate', legalEntity: 'required' },
  START_CONCURRENT: { date: 'effectiveDate', orgUnit: 'required', position: 'required' },
  SEPARATE: { date: 'lastWorkingDay' },
  REGULARIZE: { date: 'effectiveDate' },
  UNPAID_LEAVE_START: { date: 'effectiveDate' },
  UNPAID_LEAVE_END: { date: 'effectiveDate' },
  ACTING_START: { date: 'effectiveDate', acting: 'start' },
  ACTING_END: { date: 'effectiveDate', acting: 'end' },
  ACTING_EXTEND: { date: 'effectiveDate', acting: 'extend' },
};

export function isSubmittableKind(value: string): value is SubmittableKind {
  return SUBMITTABLE_KINDS.some((kind) => kind === value);
}

/** What the form holds; empty text is "not stated". */
export interface CommandDraft {
  actingCoveredOrgUnitId: string;
  actingCoveredPositionId: string;
  actingCoveredEmployeeId: string;
  targetActingDutyId: string;
  actingLastDay: string;
  reason: string;
  employeeId: string;
  date: string;
  displayName: string;
  gender: string;
  email: string;
  wecomUserId: string;
  orgUnitId: string;
  positionId: string;
  reportsToEmployeeId: string;
  feeEarner: string;
  phase: string;
  legalEntityId: string;
  employerLegalEntityId: string;
}

export const EMPTY_DRAFT: CommandDraft = {
  actingCoveredOrgUnitId: '', actingCoveredPositionId: '', actingCoveredEmployeeId: '', targetActingDutyId: '', actingLastDay: '', reason: '',
  employeeId: '', date: '', displayName: '', gender: '', email: '', wecomUserId: '',
  orgUnitId: '', positionId: '', reportsToEmployeeId: '', feeEarner: '', phase: '', legalEntityId: '', employerLegalEntityId: '',
};

export const GENDER_OPTIONS = [{ value: 'MALE', label: '男' }, { value: 'FEMALE', label: '女' }] as const;
export const PHASE_OPTIONS = [{ value: 'PROBATION', label: '试用' }, { value: 'REGULAR', label: '正式' }] as const;
export const FEE_EARNER_OPTIONS = [{ value: 'true', label: '是' }, { value: 'false', label: '否' }] as const;

function text(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

/** Date-only arithmetic in UTC avoids daylight-saving shifts in the browser's zone. */
function dayAfter(day: string): string | undefined {
  if (day === '') return undefined;
  const date = new Date(`${day}T00:00:00Z`);
  if (Number.isNaN(date.getTime())) return undefined;
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

/** Only what the kind takes and the user filled in; the server owns every rule about it. */
export function bodyOf(kind: SubmittableKind, draft: CommandDraft): EnterPersonnelCommandBody {
  const fields = KIND_FIELDS[kind];
  const body: EnterPersonnelCommandBody = { commandKind: kind };
  const date = text(draft.date);
  if (date !== undefined) body[fields.date] = date;
  if (fields.newPerson === true) {
    const displayName = text(draft.displayName);
    const email = text(draft.email);
    const wecomUserId = text(draft.wecomUserId);
    if (displayName !== undefined) body.displayName = displayName;
    if (draft.gender === 'MALE' || draft.gender === 'FEMALE') body.gender = draft.gender;
    if (email !== undefined) body.email = email;
    if (wecomUserId !== undefined) body.wecomUserId = wecomUserId;
  } else if (draft.employeeId !== '') {
    body.employeeId = draft.employeeId;
  }
  if (fields.orgUnit !== undefined && draft.orgUnitId !== '') body.targetOrgUnitId = draft.orgUnitId;
  if (fields.position !== undefined && draft.positionId !== '') body.targetPositionId = draft.positionId;
  if (fields.reportsTo !== undefined && draft.reportsToEmployeeId !== '') body.targetReportsToEmployeeId = draft.reportsToEmployeeId;
  if (fields.feeEarner !== undefined && draft.feeEarner !== '') body.isTargetFeeEarner = draft.feeEarner === 'true';
  if (fields.phase !== undefined && (draft.phase === 'PROBATION' || draft.phase === 'REGULAR')) body.targetEmploymentPhaseKind = draft.phase;
  if (fields.legalEntity !== undefined && draft.legalEntityId !== '') body.targetLegalEntityId = draft.legalEntityId;
  if (fields.employer !== undefined && draft.employerLegalEntityId !== '') body.targetEmployerLegalEntityId = draft.employerLegalEntityId;
  if (fields.acting !== undefined) {
    const reason = text(draft.reason);
    if (reason !== undefined) body.reason = reason;
    if (fields.acting === 'start') {
      if (draft.actingCoveredOrgUnitId !== '') body.actingCoveredOrgUnitId = draft.actingCoveredOrgUnitId;
      if (draft.actingCoveredPositionId !== '') body.actingCoveredPositionId = draft.actingCoveredPositionId;
      if (draft.actingCoveredEmployeeId !== '') body.actingCoveredEmployeeId = draft.actingCoveredEmployeeId;
    } else if (draft.targetActingDutyId !== '') {
      body.targetActingDutyId = draft.targetActingDutyId;
    }
    if (fields.acting !== 'end') {
      const endsBefore = dayAfter(draft.actingLastDay);
      if (endsBefore !== undefined) body.actingEndsBeforeDate = endsBefore;
    }
  }
  return body;
}

export function personnelCommandPath(personnelCommandId: string): string {
  return `/admin/personnel-commands/${encodeURIComponent(personnelCommandId)}`;
}

export function newPersonnelCommandPath(employeeId?: string): string {
  return employeeId === undefined ? '/admin/personnel-commands/new' : `/admin/personnel-commands/new?employeeId=${encodeURIComponent(employeeId)}`;
}

export function employeeCommandsPath(employeeId: string): string {
  return `/admin/personnel-commands?employeeId=${encodeURIComponent(employeeId)}`;
}
