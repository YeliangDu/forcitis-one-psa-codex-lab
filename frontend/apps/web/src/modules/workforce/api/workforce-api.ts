import { operations } from '@forcitis/api';

export function fetchOrgUnits(includeRevoked: boolean) {
  return operations.getOrganizationOrgUnits({ query: { includeRevoked } });
}

export function fetchSyncRuns() {
  return operations.getOrganizationSyncRuns();
}

export function fetchSyncRun(syncRunId: string) {
  return operations.getOrganizationSyncRunsBySyncRunId({ path: { syncRunId: Number(syncRunId) } });
}

export function releaseSyncRun(syncRunId: string) {
  return operations.postOrganizationSyncRunsBySyncRunIdRelease({ path: { syncRunId: Number(syncRunId) } });
}
