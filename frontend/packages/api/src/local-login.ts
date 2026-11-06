import { request } from './runtime';

/**
 * The local test login of a developer's own machine (card #1478). The backend maps these two paths only in its profile local; elsewhere they answer "refused"
 * and the login page shows nothing. Hand-written and not in openapi.json on purpose: the contract is the shipped system's, and these paths are not part of it.
 */
export interface LocalAccount {
  userId: string;
  name: string;
  roleCodes: readonly string[];
}

function isLocalAccount(value: unknown): value is LocalAccount {
  if (typeof value !== 'object' || value === null) return false;
  const { userId, name, roleCodes } = value as Record<string, unknown>;
  return typeof userId === 'string' && typeof name === 'string' && Array.isArray(roleCodes) && roleCodes.every((code) => typeof code === 'string');
}

/** null (a query may not answer undefined): this backend has no local login (any answer but a list). The refusal of a backend without it must not look like an expired session, hence no guards. */
export async function fetchLocalAccounts(): Promise<readonly LocalAccount[] | null> {
  try {
    const body = await request<unknown>('get', '/api/v1/platform/local-accounts', {}, { guards: false });
    return Array.isArray(body) && body.every(isLocalAccount) ? body : null;
  } catch {
    return null;
  }
}

export async function loginAsLocalAccount(userId: string): Promise<void> {
  await request<unknown>('post', '/api/v1/platform/local-sessions', { body: { accountId: userId } }, { guards: false });
}
