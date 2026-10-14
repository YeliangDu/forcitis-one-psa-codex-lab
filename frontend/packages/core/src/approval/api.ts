import { DEFAULT_PAGE_LIMIT, checkedPage, operations } from '@forcitis/api';

function pageQuery(cursor: string | undefined) {
  return { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) };
}

export async function fetchInboxPage(cursor: string | undefined) {
  return checkedPage(await operations.getPlatformApprovalTasks({ query: pageQuery(cursor) }), cursor);
}

export async function fetchTaskRecordPage(cursor: string | undefined) {
  return checkedPage(await operations.getPlatformApprovalTaskRecords({ query: pageQuery(cursor) }), cursor);
}

export function fetchTask(taskId: string) {
  return operations.getPlatformApprovalTasksByTaskId({ path: { taskId } });
}

/** `version` is versionOf(the task that was read); `idempotencyKey` is the key of the user's intent, the same for every retry of it. */
export interface Attempt {
  version: string | undefined;
  idempotencyKey: string;
}

export async function claimTask(taskId: string, attempt: Attempt): Promise<void> {
  await operations.postPlatformApprovalTasksByTaskIdClaim({ path: { taskId } }, { ifMatch: attempt.version, idempotencyKey: attempt.idempotencyKey });
}

export function decideTask(taskId: string, outcome: 'APPROVE' | 'REJECT', reason: string, attempt: Attempt) {
  return operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId }, body: { outcome, reason } }, { ifMatch: attempt.version, idempotencyKey: attempt.idempotencyKey });
}

export function returnTask(taskId: string, reason: string, attempt: Attempt) {
  return operations.postPlatformApprovalTasksByTaskIdReturn({ path: { taskId }, body: { reason } }, { ifMatch: attempt.version, idempotencyKey: attempt.idempotencyKey });
}
