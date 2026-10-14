
export const PUBLIC_PATHS = ['/login', '/emergency', '/session-expired', '/forbidden'] as const;

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}
