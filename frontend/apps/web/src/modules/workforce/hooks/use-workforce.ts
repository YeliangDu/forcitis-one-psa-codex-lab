import { useIntentKeys } from '@forcitis/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchOrgUnits, fetchSyncRun, fetchSyncRuns, releaseSyncRun } from '../api/workforce-api';

const SYNC_RUNS_KEY = ['workforce', 'sync-runs'] as const;

export function useOrgUnits(includeRevoked: boolean) {
  return useQuery({ queryKey: ['workforce', 'org-units', includeRevoked], queryFn: () => fetchOrgUnits(includeRevoked) });
}

export function useSyncRuns() {
  return useQuery({ queryKey: SYNC_RUNS_KEY, queryFn: fetchSyncRuns });
}

export function useSyncRunDetail(syncRunId: string | undefined) {
  return useQuery({
    queryKey: ['workforce', 'sync-run', syncRunId],
    queryFn: () => fetchSyncRun(syncRunId ?? ''),
    enabled: syncRunId !== undefined,
  });
}

export function useReleaseSyncRun() {
  const queryClient = useQueryClient();
  const keys = useIntentKeys();
  return useMutation({
    mutationFn: (syncRunId: string) => releaseSyncRun(syncRunId, keys.keyFor(`release:${syncRunId}`)),
    onSuccess: () => keys.done(),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SYNC_RUNS_KEY }),
  });
}
