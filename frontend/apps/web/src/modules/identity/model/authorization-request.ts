import type { Schema } from '@forcitis/api';

export type AuthorizationRequest = Schema<'AuthorizationRequestResponse'>;

export type Decision = 'confirm' | 'reject';

export interface Deciding {
  request: AuthorizationRequest;
  decision: Decision;
}

const KIND_LABELS: Record<string, string> = { CREATE_ROLE: '新建角色', UPDATE_ROLE: '修改角色', REPLACE_ROLES: '替换角色' };

/** The label of the request kind; a kind this page does not know yet is shown as the server sends it. */
export function kindLabel(request: AuthorizationRequest): string {
  return KIND_LABELS[request.kind] ?? request.kind;
}

/** The name when the server found one, else the id it stands for. */
export function personLabel(name: string | undefined, id: string | undefined): string {
  return name ?? id ?? '—';
}

export function rolesLabel(request: AuthorizationRequest): string {
  if (request.roleName !== undefined) return request.roleName;
  return request.roleLabels.map((role) => role.name ?? role.code).join('、') || '—';
}
