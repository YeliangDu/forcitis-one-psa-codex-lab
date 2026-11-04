import type { Schema } from '@forcitis/api';

export type AuthorizationRequest = Schema<'AuthorizationRequestResponse'>;

export type Decision = 'confirm' | 'reject';

export interface Deciding {
  request: AuthorizationRequest;
  decision: Decision;
}
