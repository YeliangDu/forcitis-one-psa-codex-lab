import { failureOf, useIntentKeys } from '@forcitis/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { confirmAuthorizationRequest, fetchAuthorizationRequests, rejectAuthorizationRequest } from '../api/authorization-requests-api';
import type { Decision } from '../model/authorization-request';

const KEY = ['identity', 'authorization-requests'] as const;

export function useAuthorizationRequests() {
  return useQuery({ queryKey: KEY, queryFn: fetchAuthorizationRequests });
}

/**
 * No optimistic update: the list is read again after the answer, whether it was an acceptance or a refusal, so the confirmer sees the requests as the server
 * has them now (a refused one is usually already decided or gone).
 */
export function useDecideAuthorizationRequest() {
  const queryClient = useQueryClient();
  const keys = useIntentKeys();
  return useMutation({
    mutationFn: ({ requestId, decision }: { requestId: string; decision: Decision }) =>
      (decision === 'confirm' ? confirmAuthorizationRequest : rejectAuthorizationRequest)(requestId, keys.keyFor(`${decision}:${requestId}`)),
    onSuccess: () => {
      keys.done();
      return queryClient.invalidateQueries({ queryKey: KEY });
    },
    onError: (error) => {
      if (isFinalRefusal(error)) keys.done();
      void queryClient.invalidateQueries({ queryKey: KEY });
    },
  });
}

function isFinalRefusal(error: unknown): boolean {
  const { status } = failureOf(error);
  return status !== undefined && status >= 400 && status < 500;
}
