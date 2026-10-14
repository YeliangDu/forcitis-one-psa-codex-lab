import { z } from 'zod';

export interface NavigationNode {
  name: string;
  routePath?: string | null | undefined;
  children?: NavigationNode[] | null | undefined;
}

const navigationSchema: z.ZodType<NavigationNode> = z.lazy(() => z.object({
  name: z.string(),
  routePath: z.string().nullish(),
  children: z.array(navigationSchema).nullish(),
}));

export const currentUserSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  orgUnitId: z.string().nullish(),
  orgUnitName: z.string().nullish(),
  permissionCodes: z.array(z.string()),
  navigation: z.array(navigationSchema),
});

export type CurrentUser = z.infer<typeof currentUserSchema>;

export const loginStateSchema = z.object({ state: z.string().min(1) });

export type SessionStatus = 'loading' | 'signed-in' | 'signed-out' | 'error';

export const PUBLIC_PATHS = ['/login', '/emergency', '/session-expired', '/forbidden'] as const;

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}
