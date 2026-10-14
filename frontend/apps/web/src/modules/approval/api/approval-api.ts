import { operations } from '@forcitis/api';
import { mergedInboxSchema } from '../model/inbox';
import { decisionResultSchema, taskDetailSchema } from '../model/task';

export async function fetchInbox() {
  return mergedInboxSchema.parse(await operations.getPlatformApprovalTasks());
}

export async function fetchTask(taskId: string) {
  return taskDetailSchema.parse(await operations.getPlatformApprovalTasksByTaskId({ path: { taskId } }));
}

export async function claimTask(taskId: string): Promise<void> {
  await operations.postPlatformApprovalTasksByTaskIdClaim({ path: { taskId } });
}

export async function decideTask(taskId: string, outcome: 'APPROVE' | 'REJECT', reason: string) {
  return decisionResultSchema.parse(await operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId }, body: { outcome, reason } }));
}

export async function returnTask(taskId: string, reason: string) {
  return decisionResultSchema.parse(await operations.postPlatformApprovalTasksByTaskIdReturn({ path: { taskId }, body: { reason } }));
}
