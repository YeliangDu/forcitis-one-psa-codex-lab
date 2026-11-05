import type { Schema } from '@forcitis/api';

export type PermissionAdjustment = Schema<'PermissionAdjustmentResponse'>;

export const KIND_LABELS: Readonly<Record<string, string>> = { SENSITIVE_ROLE_REVIEW: '敏感角色复核', SCOPE_REVIEW: '范围复核' };

export const STATUS_LABELS: Readonly<Record<string, string>> = {
  OPEN: '待处理',
  UPHELD: '已维持',
  EXPIRED: '已到期',
  ADJUSTED: '已调整',
  CLOSED_BY_SEPARATION: '因离职关闭',
};

/** Why the item was opened. */
export const ORIGIN_LABELS: Readonly<Record<string, string>> = {
  ASSIGNMENT_CHANGE: '调岗',
  UPHOLD_EXPIRY: '维持到期',
  ORG_UNIT_INACTIVE: '组织单元失效',
  UNPAID_LEAVE_START: '停薪留职开始',
};

/** The field of the assignment that changed. */
export const TRIGGER_FIELD_LABELS: Readonly<Record<string, string>> = { ORG_UNIT: '部门', POSITION: '岗位' };

export function permissionAdjustmentPath(id: string): string {
  return `/admin/permission-adjustments/${id}`;
}

export const LIST_PATH = '/admin/permission-adjustments';

export function statusTone(status: string): 'pending' | 'posted' | 'neutral' {
  if (status === 'OPEN') return 'pending';
  if (status === 'UPHELD' || status === 'ADJUSTED') return 'posted';
  return 'neutral';
}

/** The name when the server found one, else the id it stands for. People never come with a name (contract 13 of #730 S3), only roles and org units do. */
export function nameOrId(name: string | undefined, id: string | undefined): string {
  return name ?? id ?? '—';
}

/** What the item is about: the person's id, or the role and the org unit when it names no person. */
export function aboutLabel(item: PermissionAdjustment): string {
  if (item.userId !== undefined) return item.userId;
  return [nameOrId(item.roleName, item.roleId), nameOrId(item.orgUnitName, item.orgUnitId)].join(' · ');
}

export type ReferenceKind = 'authorizationRequestId' | 'auditEntryId';

export const REFERENCE_LABELS: Readonly<Record<ReferenceKind, string>> = { authorizationRequestId: '授权申请编号', auditEntryId: '审计日志编号' };
