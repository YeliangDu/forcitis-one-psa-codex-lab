import { operations } from '@forcitis/api';

export function fetchOrgUnits(includeRevoked: boolean) {
  return operations.getOrganizationOrgUnits({ query: { includeRevoked } });
}

export function fetchSyncRuns(cursor: string | undefined) {
  return operations.getOrganizationSyncRuns({ query: cursor === undefined ? {} : { cursor } });
}

export function fetchSyncRun(syncRunId: string) {
  return operations.getOrganizationSyncRunsBySyncRunId({ path: { syncRunId: Number(syncRunId) } });
}

export function releaseSyncRun(syncRunId: string, reason: string) {
  return operations.postOrganizationSyncRunsBySyncRunIdRelease({ path: { syncRunId: Number(syncRunId) }, body: { reason: reason.trim() } });
}
