import type { Schema } from '@forcitis/api';
import { DOCUMENT_TYPE_LABELS, labelOf, processNameText } from './labels';

export type InboxItem = Schema<'InboxItemResponse'>;
export type InboxResponse = Schema<'InboxResponse'>;
export type TaskDetail = Schema<'TaskDetailResponse'>;
export type TaskRecord = Schema<'HandledTaskResponse'>;
export type TaskHistoryEntry = Schema<'HistoryEntry'>;
export type TaskRecordPage = Schema<'HandledTaskPageResponse'>;
export type DecisionResult = Schema<'DecisionResponse'>;

export type InboxFilterKey = 'all' | 'new' | 'notice';
/** The tabs of the inbox page: the filters of the open tasks, and the tasks already handled. */
export type InboxView = InboxFilterKey | 'done';

export function inboxViewOf(key: string): InboxView {
  return key === 'new' || key === 'notice' || key === 'done' ? key : 'all';
}

export interface Inbox {
  items: InboxItem[];
  newCount: number;
  noticeCount: number;
  totalCount: number;
}

/** The counts are totals the server repeats on every page; the first page is the freshest. */
export function inboxOf(pages: readonly InboxResponse[]): Inbox {
  const first = pages[0];
  if (first === undefined) return { items: [], newCount: 0, noticeCount: 0, totalCount: 0 };
  return {
    items: pages.flatMap((page) => page.items),
    newCount: first.newCount,
    noticeCount: first.noticeCount,
    totalCount: first.totalCount,
  };
}

export function filterInbox(items: readonly InboxItem[], filter: InboxFilterKey): InboxItem[] {
  switch (filter) {
    case 'all':
      return [...items];
    case 'new':
      return [...items];
    case 'notice':
      return items.filter((item) => item.category === 'NOTICE');
  }
}

export function inboxRowId(item: InboxItem): string {
  return item.taskId;
}

export const UNRECOGNISED_TASK_TITLE = '（无法显示的审批）';

/** The process is named by the server (processName), never looked up here by its key. */
export function inboxTitle(item: InboxItem): string {
  if (item.processKey === undefined || item.documentId === undefined) return UNRECOGNISED_TASK_TITLE;
  return `${processNameText(item.processName)} ${item.documentId}`;
}

export function recordTitle(record: Pick<TaskRecord, 'processName' | 'documentId'>): string {
  return `${processNameText(record.processName)} ${record.documentId}`;
}

/** What marks an open task besides its title; overdue wins over claimed. */
export function inboxStateOf(item: Pick<InboxItem, 'overdue' | 'claimed'>): string | undefined {
  if (item.overdue) return '已超时';
  return item.claimed ? '已认领' : undefined;
}

export function documentText(task: Pick<TaskDetail, 'documentType' | 'documentId'> | Pick<TaskRecord, 'documentType' | 'documentId'>): string {
  return `${labelOf(DOCUMENT_TYPE_LABELS, task.documentType)} ${task.documentId}`;
}

export function handlerText(record: Pick<TaskRecord, 'actorName' | 'onBehalfOfName'>): string {
  return record.onBehalfOfName === undefined ? record.actorName : `${record.actorName}（代理 ${record.onBehalfOfName}）`;
}

export interface TaskActions {
  claim: boolean;
  approve: boolean;
  reject: boolean;
  returnToInitiator: boolean;
}

/** D-1a-179: acting needs no permission code, only being the handler or a candidate. The server says so (canAct) and checks again on every call; this only decides which buttons to show. */
export function availableActions(task: TaskDetail): TaskActions {
  const mayAct = task.canAct;
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

export const EMPTY_OPINION = '（未填写意见）';

/** Display only: an empty opinion stays empty in the data (RULE-280). */
export function opinionText(opinion: string | undefined): string {
  return opinion === undefined || opinion.trim() === '' ? EMPTY_OPINION : opinion;
}
