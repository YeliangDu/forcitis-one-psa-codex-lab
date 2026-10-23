import { describe, expect, it } from 'vitest';
import { taskDetailBody } from '../testing/fixtures';
import { PROCESS_LABELS, labelOf } from './labels';
import {
  type InboxItem,
  type TaskDetail,
  UNRECOGNISED_TASK_TITLE,
  availableActions,
  canConfirm,
  documentText,
  filterInbox,
  inboxOf,
  inboxStateOf,
  inboxTitle,
  inboxViewOf,
  opinionText,
  recordTitle,
} from './model';

const TASK = taskDetailBody() as unknown as TaskDetail;

describe('availableActions', () => {
  it('服务端允许办理时（处理人或候选人，不另需权限码），未认领的任务可认领、通过、驳回、退回', () => {
    expect(availableActions(TASK)).toEqual({ claim: true, approve: true, reject: true, returnToInitiator: true });
  });

  it('已认领的任务不再显示认领', () => {
    expect(availableActions({ ...TASK, assignee: '019a0000-0000-7000-8000-000000001001' })).toEqual({ claim: false, approve: true, reject: true, returnToInitiator: true });
  });

  it('服务端说现在不能办理（例如只是发起人）时一个按钮都没有', () => {
    expect(availableActions({ ...TASK, canAct: false })).toEqual({ claim: false, approve: false, reject: false, returnToInitiator: false });
  });
});

describe('办理意见', () => {
  it('RULE-280 留空的意见显示为（未填写意见），不改动数据', () => {
    expect(opinionText(undefined)).toBe('（未填写意见）');
    expect(opinionText('  ')).toBe('（未填写意见）');
    expect(opinionText('同意')).toBe('同意');
  });

  it('通过可以不填理由，驳回和退回必须填写理由', () => {
    expect(canConfirm('approve', '')).toBe(true);
    for (const action of ['reject', 'return'] as const) {
      expect(canConfirm(action, '')).toBe(false);
      expect(canConfirm(action, '   ')).toBe(false);
      expect(canConfirm(action, '工时不符')).toBe(true);
    }
  });
});

const NEW_ITEM: InboxItem = { category: 'TODO', overdue: false, taskId: 'n-1', claimed: false, processKey: 'cost_timesheet_approval', documentId: 'TS-1', title: 'cost_timesheet_approval 42' };

describe('待办标题', () => {
  it('新系统的待办按流程和单据号在前端拼出标题，不显示服务端给的英文标题', () => {
    expect(inboxTitle(NEW_ITEM)).toBe(`${PROCESS_LABELS.cost_timesheet_approval} TS-1`);
    expect(inboxTitle(NEW_ITEM)).not.toContain('cost_timesheet_approval');
  });

  it('新系统的待办缺少流程或单据号时，显示固定的提示而不是枚举名', () => {
    expect(inboxTitle({ category: 'TODO', overdue: false, taskId: 'n-2', claimed: false, title: 'cost_timesheet_approval 42' })).toBe(UNRECOGNISED_TASK_TITLE);
  });

  it('单据显示为单据类型的名称加单据号', () => {
    expect(documentText(TASK)).toBe('工时单 TS-FST-202610-0001');
  });
});

describe('待办列表', () => {
  const page = (items: InboxItem[], counts = { newCount: 1, noticeCount: 0, totalCount: 2 }) => ({ items, ...counts });

  it('合并多页：数量取第一页，待办依次排列', () => {
    const second: InboxItem = { ...NEW_ITEM, taskId: 'n-2' };
    const inbox = inboxOf([page([NEW_ITEM]), page([second], { newCount: 9, noticeCount: 9, totalCount: 9 })]);
    expect(inbox.items).toEqual([NEW_ITEM, second]);
    expect([inbox.newCount, inbox.totalCount]).toEqual([1, 2]);
  });

  it('可以筛选知会', () => {
    const second: InboxItem = { ...NEW_ITEM, taskId: 'n-2' };
    const notice: InboxItem = { ...NEW_ITEM, category: 'NOTICE', taskId: 'n-3' };
    const all = [NEW_ITEM, second, notice];
    expect(filterInbox(all, 'new')).toEqual(all);
    expect(filterInbox(all, 'notice')).toEqual([notice]);
    expect(filterInbox(all, 'all')).toHaveLength(3);
  });
});

describe('待办的行', () => {
  it('已超时盖过已认领；两者都没有就没有标记', () => {
    expect(inboxStateOf({ overdue: true, claimed: true })).toBe('已超时');
    expect(inboxStateOf({ overdue: false, claimed: true })).toBe('已认领');
    expect(inboxStateOf({ overdue: false, claimed: false })).toBeUndefined();
  });

  it('页签的值不认识时回到全部', () => {
    expect(['new', 'notice', 'done', 'all', 'other'].map(inboxViewOf)).toEqual(['new', 'notice', 'done', 'all', 'all']);
  });

  it('已办的标题和待办一样由流程名称与单据号拼出', () => {
    expect(recordTitle({ processKey: 'cost_timesheet_approval', documentId: 'TS-9' })).toBe(`${PROCESS_LABELS.cost_timesheet_approval} TS-9`);
  });

  it('服务端新加的流程或单据类型，名称显示为破折号而不是 undefined', () => {
    expect(labelOf(PROCESS_LABELS, 'FUTURE_PROCESS')).toBe('—');
    expect(inboxTitle({ ...NEW_ITEM, processKey: 'future_process' })).toBe('— TS-1');
  });
});

describe('服务端新加的枚举值', () => {
  it('类型上就是开放的：直接用它查表无法通过类型检查，必须经过 labelOf', () => {
    const key = NEW_ITEM.processKey ?? 'cost_timesheet_approval';
    // @ts-expect-error a value of a newer server may be outside the table; look it up with labelOf
    const direct: string = PROCESS_LABELS[key];
    expect(direct).toBe(PROCESS_LABELS.cost_timesheet_approval);
    expect(labelOf(PROCESS_LABELS, key)).toBe(PROCESS_LABELS.cost_timesheet_approval);
  });
});

