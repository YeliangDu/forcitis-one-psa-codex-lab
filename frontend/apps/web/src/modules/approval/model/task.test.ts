import { describe, expect, it } from 'vitest';
import { type TaskDetail, availableActions, opinionText } from './task';

const TASK: TaskDetail = {
  taskId: 't-1',
  instanceId: 'i-1',
  processKey: 'COST_TIMESHEET_APPROVAL',
  documentType: 'TIMESHEET',
  documentId: 'TS1',
  nodeKey: 'dept-head',
  nodeName: '部门负责人审核',
  initiator: '2002',
  candidates: ['1001', '1002'],
  createdAt: '2026-10-14T01:00:00Z',
  canAct: true,
};

describe('availableActions', () => {
  it('有办理权限且服务端允许办理时，未认领的任务可认领、通过、驳回、退回', () => {
    expect(availableActions(TASK, true)).toEqual({ claim: true, approve: true, reject: true, returnToInitiator: true });
  });

  it('已认领的任务不再显示认领', () => {
    expect(availableActions({ ...TASK, assignee: '1001' }, true)).toEqual({ claim: false, approve: true, reject: true, returnToInitiator: true });
  });

  it('没有办理权限时一个按钮都没有', () => {
    expect(availableActions(TASK, false)).toEqual({ claim: false, approve: false, reject: false, returnToInitiator: false });
  });

  it('服务端说现在不能办理（例如只是发起人）时一个按钮都没有', () => {
    expect(availableActions({ ...TASK, canAct: false }, true)).toEqual({ claim: false, approve: false, reject: false, returnToInitiator: false });
  });
});

describe('opinionText', () => {
  it('RULE-280 留空的意见显示为（未填写意见），不改动数据', () => {
    expect(opinionText(undefined)).toBe('（未填写意见）');
    expect(opinionText('  ')).toBe('（未填写意见）');
    expect(opinionText('同意')).toBe('同意');
  });
});
