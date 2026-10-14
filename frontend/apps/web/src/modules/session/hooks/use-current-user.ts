import { useQuery } from '@tanstack/react-query';
import { failureOf } from '@/shared/lib/failure';
import { fetchCurrentUser } from '../api/session-api';
import type { CurrentUser, SessionStatus } from '../model/current-user';

export const CURRENT_USER_KEY = ['session', 'current-user'] as const;

export interface Session {
  status: SessionStatus;
  user: CurrentUser | undefined;
  can: (permissionCode: string) => boolean;
}

export function useCurrentUser(): Session {
  const query = useQuery({ queryKey: CURRENT_USER_KEY, queryFn: fetchCurrentUser, retry: false, staleTime: 60_000 });
  const user = query.data;
  const permissions = new Set(user?.permissionCodes ?? []);
  let status: SessionStatus = 'loading';
  if (user !== undefined) status = 'signed-in';
  else if (query.isError) status = failureOf(query.error).status === 401 ? 'signed-out' : 'error';
  return { status, user, can: (permissionCode) => permissions.has(permissionCode) };
}
