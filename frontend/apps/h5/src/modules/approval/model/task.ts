import type { Schema } from '@forcitis/api';

export const TASK_APPROVE_PERMISSION = 'platform.approval:task:approve';

export type TaskDetail = Schema<'TaskDetail'>;
export type DecisionResult = Schema<'DecisionResult'>;

export interface TaskActions {
  claim: boolean;
  approve: boolean;
  reject: boolean;
  returnToInitiator: boolean;
}

/** The server decides who may act (canAct) and checks again on every call; this only decides which buttons to show. */
export function availableActions(task: TaskDetail, canApprove: boolean): TaskActions {
  const mayAct = canApprove && task.canAct === true;
  return {
    claim: mayAct && task.assignee === undefined,
    approve: mayAct,
    reject: mayAct,
    returnToInitiator: mayAct,
  };
}

export type ReasonAction = 'approve' | 'reject' | 'return';

export interface ReasonRule {
  title: string;
  confirm: string;
  required: boolean;
  danger: boolean;
}

export const REASON_RULES: Readonly<Record<ReasonAction, ReasonRule>> = {
  approve: { title: '通过', confirm: '确认通过', required: false, danger: false },
  reject: { title: '驳回', confirm: '确认驳回', required: true, danger: true },
  return: { title: '退回给发起人', confirm: '确认退回', required: true, danger: false },
};

export function canConfirm(action: ReasonAction, reason: string): boolean {
  return !REASON_RULES[action].required || reason.trim() !== '';
}

export const PROCESS_LABELS: Readonly<Record<string, string>> = {
  COST_TIMESHEET_APPROVAL: '工时单审批',
};

export const APPROVAL_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  PLATFORM_APPROVAL_TASK_NOT_FOUND: '审批任务不存在，或你不是它的参与人',
  PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED: '审批任务已被其他人认领',
  PLATFORM_APPROVAL_REASON_REQUIRED: '请填写理由',
  PLATFORM_APPROVAL_HOOK_BLOCKED: '业务校验未通过，暂时不能办理',
  PLATFORM_APPROVAL_DECISION_EMPTY: '没有可办理的审批任务',
  PLATFORM_APPROVAL_DECISION_SINGLE_ONLY: '该审批任务不支持批量办理',
};
