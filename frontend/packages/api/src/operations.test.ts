import { afterEach, describe, expect, it, vi } from 'vitest';
import { forgetReportedViolations, setContractViolationSink } from './contract';
import { MissingVersionError, ResponseShapeError, operations, setApiHandlers, versionOf } from './index';

function stubFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
  const calls: { url: string; init: RequestInit }[] = [];
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status, headers });
  });
  return calls;
}

const TASK_DETAIL = {
  taskId: '019a0000-0000-7000-8000-000000000042',
  instanceId: '019a0000-0000-7000-8000-000000000009',
  processKey: 'cost_timesheet_approval',
  documentType: 'TIMESHEET',
  documentId: '019a0000-0000-7000-8000-000000000007',
  nodeKey: 'pm',
  nodeName: '项目经理',
  initiator: '019a0000-0000-7000-8000-000000000001',
  initiatorName: '李四',
  candidates: [],
  candidateNames: [],
  createdAt: '2026-10-14T01:00:00Z',
  canAct: true,
  overdue: false,
  status: 'OPEN',
  history: [],
};

describe('generated operations over the default client', () => {
  afterEach(() => {
    setApiHandlers({});
    vi.unstubAllGlobals();
  });

  it('calls the path from openapi.json with the path parameter filled in and the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=t-1; path=/';
    stubFetch(200, TASK_DETAIL, { ETag: '"1"' });
    const task = await operations.getPlatformApprovalTasksByTaskId({ path: { taskId: '42' } });
    const calls = stubFetch(200, { instanceId: '9', status: 'APPROVED' });
    const result = await operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId: '42' }, body: { outcome: 'APPROVE', reason: 'ok' } }, { ifMatch: versionOf(task) });
    expect(result).toEqual({ instanceId: '9', status: 'APPROVED' });
    expect(calls[0]?.url.endsWith('/platform/approval-tasks/42:decide')).toBe(true);
    expect(new Headers(calls[0]?.init.headers).get('X-XSRF-TOKEN')).toBe('t-1');
    expect(calls[0]?.init.credentials).toBe('include');
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('sends the query string of an operation', async () => {
    const calls = stubFetch(200, []);
    await operations.getOrganizationOrgUnits({ query: { includeRevoked: true } });
    expect(calls[0]?.url.endsWith('/organization/org-units?includeRevoked=true')).toBe(true);
  });

  it('routes a 401 and a 403 of any operation to the installed handlers', async () => {
    const onUnauthenticated = vi.fn();
    const onForbidden = vi.fn();
    setApiHandlers({ onUnauthenticated, onForbidden });

    stubFetch(401, {});
    await expect(operations.getPlatformApprovalTasks()).rejects.toMatchObject({ status: 401 });
    expect(onUnauthenticated).toHaveBeenCalledTimes(1);

    stubFetch(403, {});
    await expect(operations.getPlatformNumberingRules()).rejects.toMatchObject({ status: 403 });
    expect(onForbidden).toHaveBeenCalledTimes(1);
  });

  it('rejects an answer that does not match the schema of openapi.json instead of passing it on', async () => {
    stubFetch(200, { displayName: '张三', navigation: [] });
    const failure = await operations.getPlatformSessionsCurrent().catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ResponseShapeError);
    expect((failure as ResponseShapeError).problems.join(' ')).toContain('userId');
  });

  it('treats a null property like an absent one, because the server writes unset optional properties as null', async () => {
    stubFetch(200, { displayName: '张三', userId: '019a0000-0000-7000-8000-000000001001', permissionCodes: [], identityConfirmed: true, mode: 'NORMAL', navigation: [{ name: '待办', routePath: null, children: [] }], orgUnitId: null });
    const user = await operations.getPlatformSessionsCurrent();
    expect(user.orgUnitId).toBeUndefined();
    expect(user.navigation[0]).toEqual({ name: '待办', children: [] });
  });

  it('validates an answer nested inside a list', async () => {
    stubFetch(200, [{ orgUnitId: '019a0000-0000-7000-8000-000000000001', name: '交付一部', status: 'ACTIVE' }, { orgUnitId: 2 }]);
    await expect(operations.getOrganizationOrgUnits()).rejects.toBeInstanceOf(ResponseShapeError);
  });
});

describe('platform traits declared in openapi.json', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('an update sends the version of the data it is based on as If-Match, and a fresh Idempotency-Key', async () => {
    stubFetch(200, TASK_DETAIL, { ETag: '"5"' });
    const task = await operations.getPlatformApprovalTasksByTaskId({ path: { taskId: '42' } });
    const calls = stubFetch(200, { instanceId: '9', status: 'APPROVED' });
    await operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId: '42' }, body: { outcome: 'APPROVE' } }, { ifMatch: versionOf(task) });
    const headers = new Headers(calls[0]?.init.headers);
    expect(headers.get('If-Match')).toBe('"5"');
    expect(headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('an update without a version fails before anything is sent', async () => {
    const calls = stubFetch(200, {});
    await expect(operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId: '42' }, body: { outcome: 'APPROVE' } })).rejects.toBeInstanceOf(MissingVersionError);
    expect(calls).toHaveLength(0);
  });

  it('a login call carries neither header', async () => {
    const calls = stubFetch(200, {});
    await operations.postPlatformSessionsLogout();
    const headers = new Headers(calls[0]?.init.headers);
    expect(headers.has('If-Match')).toBe(false);
    expect(headers.has('Idempotency-Key')).toBe(false);
  });
});

describe('a value outside the contract', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    setContractViolationSink((message) => console.error(message));
  });

  it('an enum value a newer server added does not fail the whole list; it is kept and reported once', async () => {
    forgetReportedViolations();
    const reports: string[] = [];
    setContractViolationSink((message) => reports.push(message));
    const record = { recordId: 'r', taskId: 't', instanceId: 'i', processKey: 'cost_timesheet_approval', documentType: 'TIMESHEET', documentId: 'D', nodeKey: 'n', nodeName: 'N', actorId: '019a0000-0000-7000-8000-000000000001', actorName: 'A', decidedAt: '2026-10-14T00:00:00Z', round: 1 };
    const page = { items: [{ ...record, action: 'APPROVED' }, { ...record, recordId: 'r2', action: 'ESCALATED' }, { ...record, recordId: 'r3', action: 'ESCALATED' }] };
    stubFetch(200, page);
    const result = await operations.getPlatformApprovalTaskRecords();
    expect(result.items.map((item) => item.action)).toEqual(['APPROVED', 'ESCALATED', 'ESCALATED']);
    expect(reports).toHaveLength(1);
    expect(reports[0]).toContain('ESCALATED');
  });

  it('a required field that is missing still fails the answer', async () => {
    stubFetch(200, { items: [{ recordId: 'r' }] });
    await expect(operations.getPlatformApprovalTaskRecords()).rejects.toBeInstanceOf(ResponseShapeError);
  });
});
