import { safeInternalPath } from '@forcitis/core';
import { useLocation } from 'react-router';

export function useReturnPath(): string {
  const location = useLocation();
  const state = location.state as { from?: unknown } | null;
  return safeInternalPath(typeof state?.from === 'string' ? state.from : undefined) ?? '/';
}
