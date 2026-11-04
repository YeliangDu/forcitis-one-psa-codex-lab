import type { Schema } from '@forcitis/api';

export const AUTHORIZATION_REQUEST_PERMISSION = 'platform.identity:account-role:update';

export type AuthorizationRequest = Schema<'AuthorizationRequestResponse'>;

export type Decision = 'confirm' | 'reject';

export interface Deciding {
  request: AuthorizationRequest;
  decision: Decision;
}
