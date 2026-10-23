export const VERSION_HEADER = { ETag: '"1"' } as const;

export function currentUserBody(permissionCodes: string[], navigation: unknown[] = [], overrides: Record<string, unknown> = {}) {
  return {
    identityConfirmed: true,
    mode: 'ORDINARY',
    userId: '019a0000-0000-7000-8000-000000001001',
    displayName: '张三',
    orgUnitId: '019a0000-0000-7000-8000-000000000010',
    orgUnitName: '交付一部',
    permissionCodes,
    navigation,
    ...overrides,
  };
}

export function taskDetailBody(overrides: Record<string, unknown> = {}) {
  return {
    taskId: 't-1',
    instanceId: 'i-1',
    processKey: 'cost_timesheet_approval',
    documentType: 'TIMESHEET',
    documentId: 'TS-FST-202610-0001',
    nodeKey: 'dept-head',
    nodeName: '部门负责人审核',
    initiator: '019a0000-0000-7000-8000-000000002002',
    initiatorName: '李四',
    candidates: ['019a0000-0000-7000-8000-000000001001', '019a0000-0000-7000-8000-000000001002'],
    candidateNames: ['张三', '王五'],
    createdAt: '2026-10-14T01:00:00Z',
    canAct: true,
    status: 'OPEN',
    overdue: false,
    history: [],
    ...overrides,
  };
}

export function taskRecordBody(overrides: Record<string, unknown> = {}) {
  return {
    recordId: 'r-1',
    taskId: 't-0',
    instanceId: 'i-1',
    processKey: 'cost_timesheet_approval',
    documentType: 'TIMESHEET',
    documentId: 'TS-FST-202610-0001',
    nodeKey: 'project-manager',
    nodeName: '项目经理审核',
    action: 'APPROVED',
    actorId: '019a0000-0000-7000-8000-000000001001',
    actorName: '张三',
    decidedAt: '2026-10-14T02:00:00Z',
    round: 1,
    ...overrides,
  };
}
