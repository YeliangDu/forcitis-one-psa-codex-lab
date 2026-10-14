import { operations } from '@forcitis/api';

const UNGUARDED = { guards: false } as const;
const QR_ENTRY = 'QR_CODE_LOGIN' as const;

export function fetchCurrentUser() {
  return operations.getPlatformSessionsCurrent(undefined, UNGUARDED);
}

function stateOf(reply: unknown): string {
  if (typeof reply === 'object' && reply !== null && 'state' in reply && typeof reply.state === 'string' && reply.state !== '') return reply.state;
  throw new Error('The login-state answer carries no state.');
}

export async function requestWecomLoginState(): Promise<string> {
  return stateOf(await operations.postPlatformLoginStates({ body: { entry: QR_ENTRY } }, UNGUARDED));
}

export async function loginWithWecom(code: string, state: string): Promise<void> {
  await operations.postPlatformSessionsWecomLogin({ body: { code, state, entry: QR_ENTRY } }, UNGUARDED);
}

export async function loginWithSso(ticket: string): Promise<void> {
  await operations.postPlatformSessionsSsoLogin({ body: { ticket } }, UNGUARDED);
}

export async function loginAsEmergencyAdmin(loginName: string, password: string, totpCode: string): Promise<void> {
  await operations.postPlatformSessionsEmergencyLogin({ body: { loginName, password, totpCode } }, UNGUARDED);
}

export async function logout(): Promise<void> {
  await operations.postPlatformSessionsLogout(undefined, UNGUARDED);
}
