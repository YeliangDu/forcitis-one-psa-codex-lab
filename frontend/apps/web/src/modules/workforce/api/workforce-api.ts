import { DEFAULT_PAGE_LIMIT, checkedPage, operations } from '@forcitis/api';

export function fetchOrgUnits(includeRevoked: boolean) {
  return operations.getOrganizationOrgUnits({ query: { includeRevoked } });
}

export async function fetchSyncRunPage(cursor: string | undefined) {
  return checkedPage(await operations.getOrganizationSyncRuns({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }), cursor);
}

export function fetchSyncRun(syncRunId: string) {
  return operations.getOrganizationSyncRunsBySyncRunId({ path: { syncRunId: Number(syncRunId) } });
}

export function releaseSyncRun(syncRunId: string, reason: string, idempotencyKey: string) {
  return operations.postOrganizationSyncRunsBySyncRunIdRelease({ path: { syncRunId: Number(syncRunId) }, body: { reason } }, { idempotencyKey });
}
