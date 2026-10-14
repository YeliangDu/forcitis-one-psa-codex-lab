import { operations } from '@forcitis/api';

export function fetchInbox() {
  return operations.getPlatformApprovalTasks();
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
