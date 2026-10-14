import { operations } from '@forcitis/api';
import { currentUserSchema, loginStateSchema } from '../model/current-user';

const UNGUARDED = { guards: false } as const;
const QR_ENTRY = 'QR_CODE_LOGIN' as const;

export async function fetchCurrentUser() {
  return currentUserSchema.parse(await operations.getPlatformSessionsCurrent(undefined, UNGUARDED));
}

export async function requestWecomLoginState(): Promise<string> {
  const reply = await operations.postPlatformLoginStates({ body: { entry: QR_ENTRY } }, UNGUARDED);
  return loginStateSchema.parse(reply).state;
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
