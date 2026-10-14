import { safeInternalPath } from '@forcitis/core';

const KEY = 'forcitis.login.return-path';

export function rememberReturnPath(path: string): void {
  try {
    sessionStorage.setItem(KEY, path);
  } catch {
    return;
  }
}

export function takeReturnPath(): string {
  try {
    const stored = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return safeInternalPath(stored) ?? '/';
  } catch {
    return '/';
  }
}
