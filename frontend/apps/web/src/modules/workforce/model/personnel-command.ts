import type { Schema } from '@forcitis/api';

export type PersonnelCommand = Schema<'PersonnelReadResponse'>;
export type PersonnelCommandKind = Schema<'PersonnelCommandKind'>;
export type EnterPersonnelCommandBody = Schema<'EnterPersonnelCommandRequest'>;

/** END_CONCURRENT is not offered: it names the concurrent post it ends by assignment id, and no read of the contract returns that id. */
export const SUBMITTABLE_KINDS = ['HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION', 'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END', 'CHANGE_LEGAL_ENTITY', 'START_CONCURRENT'] as const satisfies readonly PersonnelCommandKind[];
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
};

export const COMMAND_STATUS_LABELS: Readonly<Record<string, string>> = {
  PENDING: '待生效',
  HELD: '待放行',
  EFFECTIVE: '已生效',
  WITHDRAWN: '已撤回',
  AWAITING_ATTRIBUTION_COUNTERSIGN: '待法人归属会签',
};

export const HOLD_STATUS_LABELS: Readonly<Record<string, string>> = {
  HELD: '待放行',
  RELEASED: '已放行',
  WITHDRAWN: '已撤回',
};

/** The fields a kind takes, as the server accepts them (PersonnelCommand.requireShapeOfKind); the server still decides every rule. */
export interface KindFields {
  readonly newPerson?: true;
  readonly date: 'effectiveDate' | 'lastWorkingDay';
  readonly orgUnit?: 'required' | 'optional';
  readonly position?: 'required' | 'optional';
  readonly reportsTo?: 'required' | 'optional';
  readonly feeEarner?: 'required' | 'optional';
  readonly phase?: 'required' | 'optional';
  readonly legalEntity?: 'required' | 'optional';
}

export const KIND_FIELDS: Readonly<Record<SubmittableKind, KindFields>> = {
  HIRE: { newPerson: true, date: 'effectiveDate', orgUnit: 'required', position: 'required', reportsTo: 'optional', feeEarner: 'required', phase: 'required' },
  REINSTATE: { date: 'effectiveDate', orgUnit: 'required', position: 'required', reportsTo: 'optional', feeEarner: 'required', phase: 'required' },
  TRANSFER: { date: 'effectiveDate', orgUnit: 'optional', position: 'optional', reportsTo: 'optional', feeEarner: 'optional', legalEntity: 'optional' },
  CORRECTION: { date: 'effectiveDate', orgUnit: 'optional', position: 'optional', reportsTo: 'optional', feeEarner: 'optional', phase: 'optional', legalEntity: 'optional' },
  CHANGE_LEGAL_ENTITY: { date: 'effectiveDate', legalEntity: 'required' },
  START_CONCURRENT: { date: 'effectiveDate', orgUnit: 'required', position: 'required' },
  SEPARATE: { date: 'lastWorkingDay' },
  REGULARIZE: { date: 'effectiveDate' },
  UNPAID_LEAVE_START: { date: 'effectiveDate' },
  UNPAID_LEAVE_END: { date: 'effectiveDate' },
};

export function submittableKindOf(value: string): SubmittableKind {
  return SUBMITTABLE_KINDS.find((kind) => kind === value) ?? 'TRANSFER';
}

/** What the form holds; empty text is "not stated". */
export interface CommandDraft {
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
}

export const EMPTY_DRAFT: CommandDraft = {
  employeeId: '', date: '', displayName: '', gender: '', email: '', wecomUserId: '',
  orgUnitId: '', positionId: '', reportsToEmployeeId: '', feeEarner: '', phase: '', legalEntityId: '',
};

export const GENDER_OPTIONS = [{ value: 'MALE', label: '男' }, { value: 'FEMALE', label: '女' }] as const;
export const PHASE_OPTIONS = [{ value: 'PROBATION', label: '试用' }, { value: 'REGULAR', label: '正式' }] as const;
export const FEE_EARNER_OPTIONS = [{ value: 'true', label: '是' }, { value: 'false', label: '否' }] as const;

function text(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
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
