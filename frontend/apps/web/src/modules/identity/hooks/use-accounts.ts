import { failureOf, nextCursorOf, useIntentKeys, useVersionedQuery } from '@forcitis/core';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchAccount, fetchAccountPage, fetchGrants, fetchRoles, replaceRoles } from '../api/accounts-api';

const KEY = ['identity', 'accounts'] as const;

export function useAccounts(status: string) {
  const query = useInfiniteQuery({
    queryKey: [...KEY, 'list', status],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchAccountPage(status, pageParam),
    getNextPageParam: nextCursorOf,
  });
  return { ...query, items: query.data?.pages.flatMap((page) => page.items) };
}

export function useAccount(userId: string) {
  return useVersionedQuery({ queryKey: [...KEY, 'item', userId], queryFn: () => fetchAccount(userId), enabled: userId !== '' });
}

export function useAccountGrants(userId: string) {
  return useQuery({ queryKey: [...KEY, 'grants', userId], queryFn: () => fetchGrants(userId), enabled: userId !== '' });
}

export function useRoles() {
  return useQuery({ queryKey: ['identity', 'roles'], queryFn: fetchRoles });
}

/**
 * No optimistic update. The account and its grants are read again after the answer, an acceptance or a refusal: a changed version or an account that
 * somebody else already changed shows as it is now. The key of the intent is released only by an answer the server will not change (done, awaiting, or a 4xx).
 */
export function useReplaceRoles(userId: string) {
  const queryClient = useQueryClient();
  const keys = useIntentKeys();
  const refresh = () => queryClient.invalidateQueries({ queryKey: KEY });
  return useMutation({
    mutationFn: ({ roleCodes, version }: { roleCodes: readonly string[]; version: string | undefined }) =>
      replaceRoles(userId, roleCodes, version, keys.keyFor(`replace-roles:${userId}:${[...roleCodes].sort().join(',')}`)),
    onSuccess: () => {
      keys.done();
      return refresh();
    },
    onError: (error) => {
      if (isFinalRefusal(error)) keys.done();
      void refresh();
    },
  });
}

function isFinalRefusal(error: unknown): boolean {
  const { status } = failureOf(error);
  return status !== undefined && status >= 400 && status < 500;
}
