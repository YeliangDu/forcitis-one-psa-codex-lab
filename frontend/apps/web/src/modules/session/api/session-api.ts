import { operations } from '@forcitis/api';
import { type LoginEntry, loginWithWecom, requestLoginState } from '@forcitis/core';

const UNGUARDED = { guards: false } as const;
const QR_ENTRY: LoginEntry = 'QR_CODE_LOGIN';

export function requestWecomLoginState(): Promise<string> {
  return requestLoginState(QR_ENTRY);
}

export function loginWithWecomCode(code: string, state: string): Promise<void> {
  return loginWithWecom(code, state, QR_ENTRY);
}

export async function loginWithSignedTicket(ticket: string): Promise<void> {
  await operations.postPlatformSessionsTicketLogin({ body: { ticket } }, UNGUARDED);
}

export async function loginAsEmergencyAdmin(loginName: string, password: string, totpCode: string): Promise<void> {
  await operations.postPlatformSessionsEmergencyLogin({ body: { loginName, password, totpCode } }, UNGUARDED);
}
