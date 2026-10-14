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

export async function loginWithWecom(code: string, state: string, entry: LoginEntry): Promise<void> {
  await operations.postPlatformSessionsWecomLogin({ body: { code, state, entry } }, UNGUARDED);
}
