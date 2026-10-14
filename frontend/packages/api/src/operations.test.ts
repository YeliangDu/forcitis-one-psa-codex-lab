import { afterEach, describe, expect, it, vi } from 'vitest';
import { ERROR_CODES, MissingVersionError, ResponseShapeError, forgetVersions, operations, setApiHandlers } from './index';

function stubFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
  const calls: { url: string; init: RequestInit }[] = [];
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status, headers });
  });
  return calls;
}

const TASK_DETAIL = {
  taskId: '42',
  instanceId: '9',
  processKey: 'COST_TIMESHEET_APPROVAL',
  documentType: 'TIMESHEET',
  documentId: '7',
  nodeKey: 'pm',
  nodeName: '项目经理',
  initiator: '1',
  candidates: [],
  createdAt: '2026-10-14T01:00:00Z',
  canAct: true,
  history: [],
};

describe('generated operations over the default client', () => {
  afterEach(() => {
    setApiHandlers({});
    forgetVersions();
    vi.unstubAllGlobals();
  });

  it('calls the path from openapi.json with the path parameter filled in and the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=t-1; path=/';
    stubFetch(200, TASK_DETAIL, { ETag: '"1"' });
    await operations.getPlatformApprovalTasksByTaskId({ path: { taskId: '42' } });
    const calls = stubFetch(200, { instanceId: '9', status: 'APPROVED' });
    const result = await operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId: '42' }, body: { outcome: 'APPROVE', reason: 'ok' } });
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
    stubFetch(200, { displayName: '张三', userId: '1001', permissionCodes: [], navigation: [{ name: '待办', routePath: null, children: [] }], orgUnitId: null });
    const user = await operations.getPlatformSessionsCurrent();
    expect(user.orgUnitId).toBeUndefined();
    expect(user.navigation[0]).toEqual({ name: '待办', children: [] });
  });

  it('validates an answer nested inside a list', async () => {
    stubFetch(200, [{ orgUnitId: '1', name: '交付一部', status: 'ACTIVE' }, { orgUnitId: 2 }]);
    await expect(operations.getOrganizationOrgUnits()).rejects.toBeInstanceOf(ResponseShapeError);
  });
});

describe('platform traits declared in openapi.json', () => {
  afterEach(() => {
    forgetVersions();
    vi.unstubAllGlobals();
  });

  it('an update of a resource that was read sends its ETag as If-Match and a fresh Idempotency-Key', async () => {
    stubFetch(200, TASK_DETAIL, { ETag: '"5"' });
    await operations.getPlatformApprovalTasksByTaskId({ path: { taskId: '42' } });
    const calls = stubFetch(200, { instanceId: '9', status: 'APPROVED' });
    await operations.postPlatformApprovalTasksByTaskIdDecide({ path: { taskId: '42' }, body: { outcome: 'APPROVE' } });
    const headers = new Headers(calls[0]?.init.headers);
    expect(headers.get('If-Match')).toBe('"5"');
    expect(headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('an update without a prior read fails before anything is sent', async () => {
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

  it('the error codes of the document are available as a list', () => {
    expect(ERROR_CODES).toContain('PLATFORM_WEB_VERSION_MISMATCH');
    expect(new Set(ERROR_CODES).size).toBe(ERROR_CODES.length);
  });
});
