import { nextCursorOf, useVersionedQuery } from '@forcitis/core';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type AdjustmentFilter, fetchAdjustment, fetchAdjustmentPage, fetchOwnerChoices, markAdjusted, upholdAdjustment } from '../api/permission-adjustments-api';
import type { ReferenceKind } from '../model/permission-adjustment';

const KEY = ['identity', 'permission-adjustments'] as const;

export function usePermissionAdjustments(filter: AdjustmentFilter) {
  const query = useInfiniteQuery({
    queryKey: [...KEY, 'list', filter.status, filter.kind],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchAdjustmentPage(filter, pageParam),
    getNextPageParam: nextCursorOf,
  });
  return { ...query, items: query.data?.pages.flatMap((page) => page.items) };
}

export function usePermissionAdjustment(permissionAdjustmentId: string) {
  return useVersionedQuery({ queryKey: [...KEY, 'item', permissionAdjustmentId], queryFn: () => fetchAdjustment(permissionAdjustmentId), enabled: permissionAdjustmentId !== '' });
}

export function useOwnerChoices(enabled: boolean) {
  return useQuery({ queryKey: [...KEY, 'owner-choices'], queryFn: fetchOwnerChoices, enabled });
}

/**
 * No optimistic update. The item and the list are read again after the answer, an acceptance or a refusal: a stale version or an item somebody else
 * already handled then shows as it is now.
 */
function useDecision<Input>(send: (input: Input) => Promise<void>) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: KEY });
  return useMutation({ mutationFn: send, onSuccess: refresh, onError: () => void refresh() });
}

export function useUpholdAdjustment(permissionAdjustmentId: string) {
  return useDecision(({ version, reason, owner }: { version: string | undefined; reason: string; owner: string }) => upholdAdjustment(permissionAdjustmentId, version, reason, owner));
}

export function useMarkAdjusted(permissionAdjustmentId: string) {
  return useDecision(({ version, reference, referenceId }: { version: string | undefined; reference: ReferenceKind; referenceId: string }) =>
    markAdjusted(permissionAdjustmentId, version, reference, referenceId));
}
