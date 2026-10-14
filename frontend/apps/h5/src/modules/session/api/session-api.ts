import { operations } from '@forcitis/api';

const UNGUARDED = { guards: false } as const;
const WORKBENCH = 'WORKBENCH' as const;

export function fetchCurrentUser() {
  return operations.getPlatformSessionsCurrent(undefined, UNGUARDED);
}

function stateOf(reply: unknown): string {
  if (typeof reply === 'object' && reply !== null && 'state' in reply && typeof reply.state === 'string' && reply.state !== '') return reply.state;
  throw new Error('The login-state answer carries no state.');
}

export async function requestLoginState(): Promise<string> {
  return stateOf(await operations.postPlatformLoginStates({ body: { entry: WORKBENCH } }, UNGUARDED));
}

export async function loginWithWecom(code: string, state: string): Promise<void> {
  await operations.postPlatformSessionsWecomLogin({ body: { code, state, entry: WORKBENCH } }, UNGUARDED);
}
