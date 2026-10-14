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

export async function claimTask(taskId: string): Promise<void> {
  await operations.postPlatformApprovalTasksByTaskIdClaim({ path: { taskId } });
}

export function decideTask(taskId: string, outcome: 'APPROVE' | 'REJECT', reason: string) {
  return operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId }, body: { outcome, reason } });
}

export function returnTask(taskId: string, reason: string) {
  return operations.postPlatformApprovalTasksByTaskIdReturn({ path: { taskId }, body: { reason } });
}
