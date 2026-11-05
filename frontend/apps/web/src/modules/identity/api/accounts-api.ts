import { DEFAULT_PAGE_LIMIT, checkedPage, collectPages, operations } from '@forcitis/api';
import type { ReplaceOutcome } from '../model/account';

type Query = NonNullable<NonNullable<Parameters<typeof operations.getPlatformUserAccounts>[0]>['query']>;

export function fetchAccountPage(status: string, cursor: string | undefined) {
  return operations.getPlatformUserAccounts({
    query: { limit: DEFAULT_PAGE_LIMIT, ...(status === '' ? {} : { status: status as NonNullable<Query['status']> }), ...(cursor === undefined ? {} : { cursor }) },
  }).then((page) => checkedPage(page, cursor));
}

export function fetchAccount(userId: string) {
  return operations.getPlatformUserAccountsByUserId({ path: { userId } });
}

export function fetchGrants(userId: string) {
  return collectPages((cursor) => operations.getPlatformAccountRoleGrants({ query: { userId, limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

export function fetchRoles() {
  return collectPages((cursor) => operations.getPlatformRoles({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

/**
 * `version` is versionOf(the account as read): another version answers 412. 204 = replaced; 202 = a second holder has to confirm the request with the returned id.
 * One key per intent, so a retry of the same click does not open a second request.
 */
export async function replaceRoles(userId: string, roleCodes: readonly string[], version: string | undefined, idempotencyKey: string): Promise<ReplaceOutcome> {
  // The contract types the 202 body ("*/*") as no result, so the answer is read as it is: nothing for 204, the awaiting body for 202.
  const answer: unknown = await operations.postPlatformUserAccountsByUserIdReplaceRoles(
    { path: { userId }, body: { roleCodes: [...roleCodes] } },
    { ifMatch: version, idempotencyKey, keepBusinessForbidden: true },
  );
  if (typeof answer === 'object' && answer !== null && 'requestId' in answer && typeof answer.requestId === 'string') return { kind: 'awaiting', requestId: answer.requestId };
  if (answer === undefined || answer === null || answer === '') return { kind: 'done' };
  throw new Error('The answer of replace-roles is neither empty nor an awaiting-confirmation body.');
}
