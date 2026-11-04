import { DEFAULT_PAGE_LIMIT, collectPages, operations } from '@forcitis/api';

export function fetchAuthorizationRequests() {
  return collectPages((cursor) => operations.getPlatformAuthorizationRequests({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

/** The contract carries no version: a request that is no longer pending answers 409 (PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING). */
export async function confirmAuthorizationRequest(requestId: string, idempotencyKey: string): Promise<void> {
  await operations.postPlatformAuthorizationRequestsByRequestIdConfirm({ path: { requestId } }, { idempotencyKey });
}

export async function rejectAuthorizationRequest(requestId: string, idempotencyKey: string): Promise<void> {
  await operations.postPlatformAuthorizationRequestsByRequestIdReject({ path: { requestId } }, { idempotencyKey });
}
