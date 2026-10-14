import { operations } from '@forcitis/api';
import { z } from 'zod';
import { orgUnitSchema } from '../model/org-unit';
import { releaseResultSchema, syncRunDetailSchema, syncRunSchema } from '../model/sync-run';

export async function fetchOrgUnits(includeRevoked: boolean) {
  return z.array(orgUnitSchema).parse(await operations.getOrganizationOrgUnits({ query: { includeRevoked } }));
}

export async function fetchSyncRuns() {
  return z.array(syncRunSchema).parse(await operations.getOrganizationSyncRuns());
}

export async function fetchSyncRun(syncRunId: string) {
  return syncRunDetailSchema.parse(await operations.getOrganizationSyncRunsBySyncRunId({ path: { syncRunId: Number(syncRunId) } }));
}

export async function releaseSyncRun(syncRunId: string) {
  return releaseResultSchema.parse(await operations.postOrganizationSyncRunsBySyncRunIdRelease({ path: { syncRunId: Number(syncRunId) } }));
}
