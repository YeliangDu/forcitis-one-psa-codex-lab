import { expect, test, type APIRequestContext } from '@playwright/test';

// Card #1499: the administrator mode on a developer's machine. Synthetic Platform Admin (super_admin) is ordinary after the local login and reads the role list only after the explicit step; Synthetic Admin never can.
const ROLES = '/api/v1/platform/roles';

async function signIn(request: APIRequestContext, name: string): Promise<(path: string) => Promise<number>> {
  const accounts = (await (await request.get('/api/v1/platform/local-accounts')).json()) as { userId: string; name: string }[];
  const account = accounts.find((a) => a.name === name);
  if (!account) throw new Error(`${name} is not listed`);
  const xsrf = async () => (await request.storageState()).cookies.find((c) => c.name === 'XSRF-TOKEN')?.value ?? '';
  const post = async (path: string, data?: object) => request.post(path, { data, headers: { 'X-XSRF-TOKEN': await xsrf() } });
  expect((await post('/api/v1/platform/local-sessions', { accountId: account.userId })).status()).toBe(200);
  await request.get('/api/v1/platform/sessions:current'); // refreshes the XSRF cookie after the login
  return async (path) => (await post(path)).status();
}

test('Synthetic Platform Admin reads the role list only after the explicit administrator-mode step', async ({ request }) => {
  const enter = await signIn(request, 'Synthetic Platform Admin');
  expect((await request.get(ROLES)).status()).toBe(403);
  expect(await enter('/api/v1/platform/sessions:enter-local-administrator-mode')).toBe(200);
  expect((await request.get(ROLES)).status()).toBe(200);
});

test('Synthetic Admin stays ordinary: no role list, and the step is refused', async ({ request }) => {
  const enter = await signIn(request, 'Synthetic Admin');
  expect(await enter('/api/v1/platform/sessions:enter-local-administrator-mode')).toBe(403);
  expect((await request.get(ROLES)).status()).toBe(403);
});
