import type { Schema } from '@forcitis/api';

export type CurrentUser = Schema<'CurrentUserResponse'>;
export type NavigationNode = Schema<'NavigationResponse'>;

export type SessionStatus = 'loading' | 'signed-in' | 'signed-out' | 'error';

export const PUBLIC_PATHS = ['/login', '/emergency', '/session-expired', '/forbidden'] as const;

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}
