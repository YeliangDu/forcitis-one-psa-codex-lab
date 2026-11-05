import type { Schema } from '@forcitis/api';

export type AccountListItem = Schema<'AccountListItemResponse'>;
export type Account = Schema<'UserAccountResponse'>;
export type AccountGrant = Schema<'AccountGrantResponse'>;
export type Role = Schema<'RoleResponse'>;

/** The permission the change-roles action needs; the server checks it again, this only keeps the button away from people who would be refused. */
export const ACCOUNT_ROLE_UPDATE = 'platform.identity:account-role:update';

export const ACCOUNT_LIST_PATH = '/admin/accounts';

export function accountPath(userId: string): string {
  return `${ACCOUNT_LIST_PATH}/${userId}`;
}

export const ACCOUNT_STATUS_LABELS: Readonly<Record<string, string>> = { ENABLED: '已启用', DISABLED: '已停用' };

export function accountStatusTone(status: string): 'posted' | 'neutral' {
  return status === 'ENABLED' ? 'posted' : 'neutral';
}

/** The person's name when the account list knows one, else the account id. */
export function accountLabel(item: Pick<AccountListItem, 'displayName' | 'userId'>): string {
  return item.displayName ?? item.userId;
}

/** Matches the typed text against name, employee code and account id; the list has no server-side text search, so it narrows the pages already loaded. */
export function matchesSearch(item: AccountListItem, text: string): boolean {
  const needle = text.trim().toLowerCase();
  if (needle === '') return true;
  return [item.displayName, item.employeeCode, item.userId].some((value) => value?.toLowerCase().includes(needle) === true);
}

export function roleName(roles: readonly Role[], code: string): string {
  return roles.find((role) => role.code === code)?.name ?? code;
}

/** What the server answered to a role replacement: nothing (done) or the request that another holder has to confirm. */
export type ReplaceOutcome = { kind: 'done' } | { kind: 'awaiting'; requestId: string };

export const AUTHORIZATION_REQUESTS_PATH = '/admin/authorization-requests';
