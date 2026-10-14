import { describe, expect, it } from 'vitest';
import { filterInbox, inboxRowId, taskPath } from './inbox';
import { type TaskDetail, availableActions, canConfirm } from './task';

const TASK: TaskDetail = {
  taskId: 't-1',
  instanceId: 'i-1',
  processKey: 'COST_TIMESHEET_APPROVAL',
  nodeKey: 'dept-head',
  initiator: '2002',
  candidates: ['1001'],
  canAct: true,
};

describe('availableActions', () => {
  it('有办理权限且服务端允许办理时，未认领的任务可认领、通过、驳回、退回', () => {
    expect(availableActions(TASK, true)).toEqual({ claim: true, approve: true, reject: true, returnToInitiator: true });
  });

  it('已认领的任务不再显示认领', () => {
    expect(availableActions({ ...TASK, assignee: '1001' }, true).claim).toBe(false);
  });

  it('没有办理权限，或服务端说现在不能办理时，一个按钮都没有', () => {
    const none = { claim: false, approve: false, reject: false, returnToInitiator: false };
    expect(availableActions(TASK, false)).toEqual(none);
    expect(availableActions({ ...TASK, canAct: false }, true)).toEqual(none);
  });
});

describe('canConfirm', () => {
  it('通过的理由选填，驳回和退回的理由必填（空白不算）', () => {
    expect(canConfirm('approve', '')).toBe(true);
    expect(canConfirm('reject', '')).toBe(false);
    expect(canConfirm('reject', '   ')).toBe(false);
    expect(canConfirm('reject', '不符')).toBe(true);
    expect(canConfirm('return', ' ')).toBe(false);
    expect(canConfirm('return', '补充说明')).toBe(true);
  });
});

describe('待办的筛选与跳转', () => {
  const items = [{ source: 'NEW' as const, taskId: 'a' }, { source: 'LEGACY' as const, taskId: 'a' }];

  it('按来源筛选', () => {
    expect(filterInbox(items, 'all')).toHaveLength(2);
    expect(filterInbox(items, 'new')).toEqual([items[0]]);
    expect(filterInbox(items, 'legacy')).toEqual([items[1]]);
  });

  it('新旧待办编号相同也不会互相覆盖，任务页地址带编码后的编号', () => {
    expect(inboxRowId(items[0] ?? {})).not.toBe(inboxRowId(items[1] ?? {}));
    expect(taskPath('a/b c')).toBe('/app/pages/task?taskId=a%2Fb%20c');
  });
});
