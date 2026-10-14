import type { Schema } from '@forcitis/api';
import { DOCUMENT_TYPE_LABELS, PROCESS_LABELS } from './labels';

export type InboxItem = Schema<'InboxItemResponse'>;
export type InboxResponse = Schema<'InboxResponse'>;
export type TaskDetail = Schema<'TaskDetailResponse'>;
export type TaskRecord = Schema<'TaskRecordResponse'>;
export type TaskRecordPage = Schema<'TaskRecordPageResponse'>;
export type DecisionResult = Schema<'DecisionResponse'>;

export type InboxFilterKey = 'all' | 'new' | 'legacy' | 'notice';

export interface Inbox {
  items: InboxItem[];
  newCount: number;
  legacyCount: number;
  noticeCount: number;
  totalCount: number;
  legacyAvailable: boolean;
}

/** The counts and the legacy flag are totals the server repeats on every page; the first page is the freshest. */
export function inboxOf(pages: readonly InboxResponse[]): Inbox {
  const first = pages[0];
  if (first === undefined) return { items: [], newCount: 0, legacyCount: 0, noticeCount: 0, totalCount: 0, legacyAvailable: true };
  return {
    items: pages.flatMap((page) => page.items),
    newCount: first.newCount,
    legacyCount: first.legacyCount,
    noticeCount: first.noticeCount,
    totalCount: first.totalCount,
    legacyAvailable: first.legacyAvailable,
  };
}

export function filterInbox(items: readonly InboxItem[], filter: InboxFilterKey): InboxItem[] {
  switch (filter) {
    case 'all':
      return [...items];
    case 'new':
      return items.filter((item) => item.source === 'NEW');
    case 'legacy':
      return items.filter((item) => item.source === 'LEGACY' && item.category === 'TODO');
    case 'notice':
      return items.filter((item) => item.category === 'NOTICE');
  }
}

export function inboxRowId(item: InboxItem): string {
  return `${item.source}:${item.taskId}`;
}

export const UNRECOGNISED_TASK_TITLE = '（无法显示的审批）';

/** The title of a task of the new system is built here from the process and the document; the server's `title` is only the old system's own text. */
export function inboxTitle(item: InboxItem): string {
  if (item.source === 'LEGACY') return item.title ?? '';
  if (item.processKey === undefined || item.documentId === undefined) return UNRECOGNISED_TASK_TITLE;
  return `${PROCESS_LABELS[item.processKey]} ${item.documentId}`;
}

export function documentText(task: Pick<TaskDetail, 'documentType' | 'documentId'> | Pick<TaskRecord, 'documentType' | 'documentId'>): string {
  return `${DOCUMENT_TYPE_LABELS[task.documentType]} ${task.documentId}`;
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
