import { useQuery } from '@tanstack/react-query';
import { fetchCurrentUser } from '../api/session-api';
import type { CurrentUser } from '../model/wecom-login';

export const CURRENT_USER_KEY = ['session', 'current-user'] as const;

export function useCurrentUserQuery() {
  return useQuery({ queryKey: CURRENT_USER_KEY, queryFn: fetchCurrentUser, retry: false, staleTime: 60_000 });
}

export interface Session {
  user: CurrentUser | undefined;
  can: (permissionCode: string) => boolean;
}

export function useCurrentUser(): Session {
  const user = useCurrentUserQuery().data;
  const permissions = new Set(user?.permissionCodes ?? []);
  return { user, can: (permissionCode) => permissions.has(permissionCode) };
}
