import { afterEach, describe, expect, it, vi } from 'vitest';
import { ResponseShapeError, operations, setApiHandlers } from './index';

function stubFetch(status: number, body: unknown) {
  const calls: { url: string; init: RequestInit }[] = [];
  vi.stubGlobal('fetch', async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(JSON.stringify(body), { status });
  });
  return calls;
}

describe('generated operations over the default client', () => {
  afterEach(() => {
    setApiHandlers({});
    vi.unstubAllGlobals();
  });

  it('calls the path from openapi.json with the path parameter filled in and the CSRF header', async () => {
    document.cookie = 'XSRF-TOKEN=t-1; path=/';
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
