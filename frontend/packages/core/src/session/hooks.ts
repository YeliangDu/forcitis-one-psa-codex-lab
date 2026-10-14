import type { Schema } from '@forcitis/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { failureOf } from '../failure';
import { confirmIdentity, fetchCurrentUser } from './api';

export type CurrentUser = Schema<'CurrentUserResponse'>;
export type SessionStatus = 'loading' | 'signed-in' | 'signed-out' | 'error';

export const CURRENT_USER_KEY = ['session', 'current-user'] as const;

export interface Session {
  status: SessionStatus;
  user: CurrentUser | undefined;
  /** Signed in, but the identity is not confirmed yet: no business page may load, and the server answers with no permissions. */
  identityPending: boolean;
  can: (permissionCode: string) => boolean;
}

export function useCurrentUserQuery() {
  return useQuery({ queryKey: CURRENT_USER_KEY, queryFn: fetchCurrentUser, retry: false, staleTime: 60_000 });
}

export function useCurrentUser(): Session {
  const query = useCurrentUserQuery();
  const user = query.data;
  const permissions = new Set(user?.permissionCodes ?? []);
  let status: SessionStatus = 'loading';
  if (user !== undefined) status = 'signed-in';
  else if (query.isError) status = failureOf(query.error).status === 401 ? 'signed-out' : 'error';
  return { status, user, identityPending: user !== undefined && !user.identityConfirmed, can: (permissionCode) => permissions.has(permissionCode) };
}

export function useConfirmIdentity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: confirmIdentity,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CURRENT_USER_KEY }),
  });
}
