import { DEFAULT_PAGE_LIMIT, collectPages, operations } from '@forcitis/api';

export function fetchAccounts() {
  return collectPages((cursor) => operations.getPlatformUserAccounts({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

export function fetchAccountGrants(userId: string) {
  return collectPages((cursor) => operations.getPlatformAccountRoleGrants({ query: { userId, limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}
