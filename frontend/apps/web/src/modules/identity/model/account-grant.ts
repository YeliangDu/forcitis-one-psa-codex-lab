import type { Schema } from '@forcitis/api';

export type AccountGrant = Schema<'AccountGrantResponse'>;
export type Account = Schema<'AccountListItemResponse'>;

/** How a grant was opened (the stored grant channel); the relationship a default role came from is not stored. */
const SOURCE_LABELS: Record<string, string> = { MANUAL: '人工授权', DEFAULT_ROLE: '建档默认角色', OPERATIONS: '运维命令', MIGRATION: '老角色迁入' };

/** A channel this page does not know yet is shown as the server sends it. */
export function sourceLabel(grant: AccountGrant): string {
  return SOURCE_LABELS[grant.channel] ?? grant.channel;
}

export function accountLabel(account: Account): string {
  const name = account.displayName ?? account.userId;
  return account.employeeCode === undefined || account.employeeCode === null ? name : `${name}（${account.employeeCode}）`;
}
