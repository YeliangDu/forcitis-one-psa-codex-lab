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
  inboxRowId,
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
    expect(availableActions({ ...TASK, assignee: '1001' })).toEqual({ claim: false, approve: true, reject: true, returnToInitiator: true });
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

const NEW_ITEM: InboxItem = { source: 'NEW', category: 'TODO', overdue: false, taskId: 'n-1', claimed: false, processKey: 'COST_TIMESHEET_APPROVAL', documentId: 'TS-1', title: 'COST_TIMESHEET_APPROVAL 42' };

describe('待办标题', () => {
  it('新系统的待办按流程和单据号在前端拼出标题，不显示服务端给的英文标题', () => {
    expect(inboxTitle(NEW_ITEM)).toBe(`${PROCESS_LABELS.COST_TIMESHEET_APPROVAL} TS-1`);
    expect(inboxTitle(NEW_ITEM)).not.toContain('COST_TIMESHEET_APPROVAL');
  });

  it('老系统的待办显示老系统自己的标题', () => {
    expect(inboxTitle({ source: 'LEGACY', category: 'TODO', overdue: false, taskId: 'l-1', claimed: false, title: '合同评审 HT-2026-001' })).toBe('合同评审 HT-2026-001');
  });

  it('新系统的待办缺少流程或单据号时，显示固定的提示而不是枚举名', () => {
    expect(inboxTitle({ source: 'NEW', category: 'TODO', overdue: false, taskId: 'n-2', claimed: false, title: 'COST_TIMESHEET_APPROVAL 42' })).toBe(UNRECOGNISED_TASK_TITLE);
  });

  it('单据显示为单据类型的名称加单据号', () => {
    expect(documentText(TASK)).toBe('工时单 TS-FST-202610-0001');
  });
});

describe('待办列表', () => {
  const page = (items: InboxItem[], counts = { newCount: 1, legacyCount: 1, noticeCount: 0, totalCount: 2, legacyAvailable: true }) => ({ items, ...counts });

  it('合并多页：数量取第一页，待办依次排列', () => {
    const legacy: InboxItem = { source: 'LEGACY', category: 'TODO', overdue: false, taskId: 'l-1', claimed: false, title: 'x' };
    const inbox = inboxOf([page([NEW_ITEM]), page([legacy], { newCount: 9, legacyCount: 9, noticeCount: 9, totalCount: 9, legacyAvailable: false })]);
    expect(inbox.items).toEqual([NEW_ITEM, legacy]);
    expect([inbox.newCount, inbox.legacyCount, inbox.totalCount, inbox.legacyAvailable]).toEqual([1, 1, 2, true]);
  });

  it('可以按来源筛选', () => {
    const legacy: InboxItem = { source: 'LEGACY', category: 'TODO', overdue: false, taskId: 'l-1', claimed: false, title: 'x' };
    const notice: InboxItem = { source: 'LEGACY', category: 'NOTICE', overdue: false, taskId: 'l-2', claimed: false, title: '知会 y' };
    const all = [NEW_ITEM, legacy, notice];
    expect(filterInbox(all, 'new')).toEqual([NEW_ITEM]);
    expect(filterInbox(all, 'legacy')).toEqual([legacy]);
    expect(filterInbox(all, 'notice')).toEqual([notice]);
    expect(filterInbox(all, 'all')).toHaveLength(3);
  });
});

describe('待办的行', () => {
  it('新旧待办的编号相同时，行的标识仍然不同', () => {
    const fresh: InboxItem = { ...NEW_ITEM, taskId: '42' };
    const old: InboxItem = { source: 'LEGACY', category: 'TODO', overdue: false, taskId: '42', claimed: false, title: 'x' };
    expect(inboxRowId(fresh)).not.toBe(inboxRowId(old));
    expect(new Set([inboxRowId(fresh), inboxRowId(old)]).size).toBe(2);
  });

  it('已超时盖过已认领；两者都没有就没有标记', () => {
    expect(inboxStateOf({ overdue: true, claimed: true })).toBe('已超时');
    expect(inboxStateOf({ overdue: false, claimed: true })).toBe('已认领');
    expect(inboxStateOf({ overdue: false, claimed: false })).toBeUndefined();
  });

  it('页签的值不认识时回到全部', () => {
    expect(['new', 'legacy', 'notice', 'done', 'all', 'other'].map(inboxViewOf)).toEqual(['new', 'legacy', 'notice', 'done', 'all', 'all']);
  });

  it('已办的标题和待办一样由流程名称与单据号拼出', () => {
    expect(recordTitle({ processKey: 'COST_TIMESHEET_APPROVAL', documentId: 'TS-9' })).toBe(`${PROCESS_LABELS.COST_TIMESHEET_APPROVAL} TS-9`);
  });

  it('服务端新加的流程或单据类型，名称显示为破折号而不是 undefined', () => {
    expect(labelOf(PROCESS_LABELS, 'FUTURE_PROCESS')).toBe('—');
    expect(inboxTitle({ ...NEW_ITEM, processKey: 'FUTURE_PROCESS' as 'COST_TIMESHEET_APPROVAL' })).toBe('— TS-1');
  });
});

describe('服务端新加的枚举值', () => {
  it('类型上就是开放的：直接用它查表无法通过类型检查，必须经过 labelOf', () => {
    const key = NEW_ITEM.processKey ?? 'COST_TIMESHEET_APPROVAL';
    // @ts-expect-error a value of a newer server may be outside the table; look it up with labelOf
    const direct: string = PROCESS_LABELS[key];
    expect(direct).toBe(PROCESS_LABELS.COST_TIMESHEET_APPROVAL);
    expect(labelOf(PROCESS_LABELS, key)).toBe(PROCESS_LABELS.COST_TIMESHEET_APPROVAL);
  });
});

