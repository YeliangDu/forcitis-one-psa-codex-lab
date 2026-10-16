import { nextCursorOf } from '@forcitis/core';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchOrgUnits, fetchSyncRun, fetchSyncRunPage, releaseSyncRun } from '../api/workforce-api';

const SYNC_RUNS_KEY = ['workforce', 'sync-runs'] as const;

export function useOrgUnits(includeRevoked: boolean) {
  return useQuery({ queryKey: ['workforce', 'org-units', includeRevoked], queryFn: () => fetchOrgUnits(includeRevoked) });
}

export function useSyncRuns() {
  const query = useInfiniteQuery({
    queryKey: SYNC_RUNS_KEY,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchSyncRunPage(pageParam),
    getNextPageParam: nextCursorOf,
  });
  return { ...query, runs: query.data?.pages.flatMap((page) => page.items) };
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
  return useMutation({
    mutationFn: ({ syncRunId, reason }: { syncRunId: string; reason: string }) => releaseSyncRun(syncRunId, reason),
    onSettled: () => queryClient.invalidateQueries({ queryKey: SYNC_RUNS_KEY }),
  });
}
