import { type Schema, operations } from '@forcitis/api';

const UNGUARDED = { guards: false } as const;

export type LoginEntry = NonNullable<Schema<'LoginStateRequest'>['entry']>;

export function fetchCurrentUser() {
  return operations.getPlatformSessionsCurrent(undefined, UNGUARDED);
}

export async function requestLoginState(entry: LoginEntry): Promise<string> {
  const reply = await operations.postPlatformLoginStates({ body: { entry } }, UNGUARDED);
  return reply.state;
}

/** C-M7: the person who signed in by ticket on a browser the server does not know confirms who they are; the answer (204) sends the session and identity cookies again. */
export async function confirmIdentity(): Promise<void> {
  await operations.postPlatformSessionsConfirmIdentity();
}

export async function logout(): Promise<void> {
  await operations.postPlatformSessionsLogout(undefined, UNGUARDED);
}

export async function loginWithWecom(code: string, state: string, entry: LoginEntry): Promise<void> {
  await operations.postPlatformSessionsWecomLogin({ body: { code, state, entry } }, UNGUARDED);
}
