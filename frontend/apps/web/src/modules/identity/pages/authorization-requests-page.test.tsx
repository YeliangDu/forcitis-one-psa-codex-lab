import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { AuthorizationRequestsPage } from './authorization-requests-page';

const UPDATE = 'platform.identity:account-role:update';
const FIRST = '019a0000-0000-7000-8000-000000009001';
const SECOND = '019a0000-0000-7000-8000-000000009002';
const REQUESTER = '019a0000-0000-7000-8000-000000001001';
const SUBJECT = '019a0000-0000-7000-8000-000000001002';

const REQUESTS = [
  { requestId: FIRST, kind: 'UPDATE_ROLE', roleId: '019a0000-0000-7000-8000-000000008001', roleName: '合成角色甲', roleCodes: ['synthetic_a'], permissionCodes: ['platform.numbering:numbering-rule:approve'], requestedBy: REQUESTER, requestedAt: '2026-11-03T01:00:00Z' },
  { requestId: SECOND, kind: 'REPLACE_ROLES', roleCodes: ['synthetic_b'], permissionCodes: [], subjectUserId: SUBJECT, requestedBy: REQUESTER, requestedAt: '2026-11-03T02:00:00Z' },
];

function rowOf(text: string) {
  const row = screen.getByText(text).closest('tr');
  if (row === null) throw new Error(`no table row contains ${text}`);
  return within(row);
}

async function decide(row: string, button: '确认' | '驳回') {
  await userEvent.click(await rowOf(row).findByRole('button', { name: button }));
  const dialog = (await screen.findByRole('dialog')) as HTMLElement;
  await userEvent.click(within(dialog).getByRole('button', { name: button }));
  return dialog;
}

describe('授权申请页', () => {
  it('列出待确认的授权申请：角色、权限、被授权用户、申请人', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([UPDATE]) }, 'GET authorization-requests': { body: { items: REQUESTS } } });
    renderWithProviders(<AuthorizationRequestsPage />);

    expect(await screen.findByText('合成角色甲')).toBeInTheDocument();
    expect(rowOf('合成角色甲').getByText(/platform\.numbering:numbering-rule:approve/)).toBeInTheDocument();
    expect(rowOf('synthetic_b').getByText(SUBJECT)).toBeInTheDocument();
    expect(screen.getAllByText(REQUESTER)).toHaveLength(2);
  });

  it('没有待确认的申请：显示空状态', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([UPDATE]) }, 'GET authorization-requests': { body: { items: [] } } });
    renderWithProviders(<AuthorizationRequestsPage />);

    expect(await screen.findByText('暂无待确认的授权申请')).toBeInTheDocument();
  });

  it('列表分页时读完所有页', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': (request) => (request.url.includes('cursor=c2') ? { body: { items: [REQUESTS[1]] } } : { body: { items: [REQUESTS[0]], nextCursor: 'c2' } }),
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    expect(await screen.findByText('合成角色甲')).toBeInTheDocument();
    expect(screen.getByText('synthetic_b')).toBeInTheDocument();
    expect(server.calls.filter((call) => call.url.includes('authorization-requests')).length).toBe(2);
  });

  it('加载失败：显示失败提示，不显示服务端原文', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([UPDATE]) }, 'GET authorization-requests': { status: 500, body: problemBody(500, 'PLATFORM_WEB_INTERNAL', { detail: 'boom at line 3' }) } });
    renderWithProviders(<AuthorizationRequestsPage />);

    expect(await screen.findByText('授权申请加载失败')).toBeInTheDocument();
    expect(screen.getByText('服务暂时不可用，请稍后重试')).toBeInTheDocument();
    expect(screen.queryByText(/boom/)).not.toBeInTheDocument();
  });

  it('确认：先显示申请内容，确认后带幂等键提交，成功后重新读取列表（不先改列表）', async () => {
    let confirmed = false;
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': () => ({ body: { items: confirmed ? [REQUESTS[1]] : REQUESTS } }),
      [`POST authorization-requests/${FIRST}:confirm`]: () => {
        confirmed = true;
        return { status: 204 };
      },
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    await userEvent.click(rowOf('合成角色甲').getByRole('button', { name: '确认' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/platform\.numbering:numbering-rule:approve/)).toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
    await userEvent.click(within(dialog).getByRole('button', { name: '确认' }));

    await waitFor(() => expect(screen.queryByText('合成角色甲')).not.toBeInTheDocument());
    const post = server.calls.find((call) => call.method === 'POST');
    expect(post?.url.endsWith(`${FIRST}:confirm`)).toBe(true);
    expect(post?.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(post?.headers.get('If-Match')).toBeNull();
    expect(screen.getByText('synthetic_b')).toBeInTheDocument();
  });

  it('驳回：提交到驳回接口', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': { body: { items: REQUESTS } },
      [`POST authorization-requests/${SECOND}:reject`]: { status: 204 },
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('synthetic_b');
    await decide('synthetic_b', '驳回');

    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith(`${SECOND}:reject`))).toBe(true));
    expect(server.calls.some((call) => call.url.endsWith(':confirm'))).toBe(false);
  });

  it.each([
    ['角色已有持有人', 409, 'PLATFORM_IDENTITY_ROLE_IN_USE', '这个角色还有用户账号在用，暂时不能这样处理'],
    ['申请人不能自己确认', 403, 'PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED', '申请人和被授权用户不能确认自己的授权申请，请让其他确认人处理'],
    ['申请已被处理', 409, 'PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING', '这个授权申请已经被确认或驳回，请刷新'],
    ['申请已不存在', 404, 'PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_FOUND', '找不到这个授权申请，可能已被撤销，请刷新'],
  ])('确认被服务端拒绝（%s）：对话框保留并显示提示，重新读取列表，不显示服务端原文', async (_name, status, code, message) => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': { body: { items: REQUESTS } },
      [`POST authorization-requests/${FIRST}:confirm`]: { status, body: problemBody(status, code, { detail: 'raw server detail' }) },
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    const dialog = await decide('合成角色甲', '确认');

    expect(await within(dialog).findByText(message)).toBeInTheDocument();
    expect(within(dialog).getByText('确认失败')).toBeInTheDocument();
    expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
    await waitFor(() => expect(server.calls.filter((call) => call.method === 'GET' && call.url.includes('authorization-requests')).length).toBe(2));
  });

  it('服务暂时不可用后重试同一个确认：沿用第一次的幂等键', async () => {
    let attempts = 0;
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': { body: { items: REQUESTS } },
      [`POST authorization-requests/${FIRST}:confirm`]: () => (++attempts === 1 ? { status: 503, body: problemBody(503, 'PLATFORM_WEB_UNAVAILABLE') } : { status: 204 }),
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    const dialog = await decide('合成角色甲', '确认');
    expect(await within(dialog).findByText('服务暂时不可用，请稍后重试')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '确认' }));

    await waitFor(() => expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(2));
    const keys = server.calls.filter((call) => call.method === 'POST').map((call) => call.headers.get('Idempotency-Key'));
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(keys[1]).toBe(keys[0]);
  });

  it('服务端拒绝后再试同一个确认：换新的幂等键', async () => {
    let attempts = 0;
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': { body: { items: REQUESTS } },
      [`POST authorization-requests/${FIRST}:confirm`]: () => (++attempts === 1 ? { status: 409, body: problemBody(409, 'PLATFORM_IDENTITY_ROLE_IN_USE') } : { status: 204 }),
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    const dialog = await decide('合成角色甲', '确认');
    expect(await within(dialog).findByText('这个角色还有用户账号在用，暂时不能这样处理')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '确认' }));

    await waitFor(() => expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(2));
    const keys = server.calls.filter((call) => call.method === 'POST').map((call) => call.headers.get('Idempotency-Key'));
    expect(keys[0]).not.toBeNull();
    expect(keys[1]).not.toBeNull();
    expect(keys[1]).not.toBe(keys[0]);
  });

  it('提交还没有得到答复时：申请仍在列表里（不做乐观更新）', async () => {
    let confirmed = false;
    stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': () => ({ body: { items: confirmed ? [REQUESTS[1]] : REQUESTS } }),
      [`POST authorization-requests/${FIRST}:confirm`]: () => {
        confirmed = true;
        return { status: 204 };
      },
    });
    const answer = vi.fn<() => void>();
    const gate = new Promise<void>((resolve) => answer.mockImplementation(resolve));
    const respond = globalThis.fetch;
    vi.stubGlobal('fetch', async (input: string, init?: RequestInit) => {
      if ((init?.method ?? 'GET').toUpperCase() === 'POST') await gate;
      return respond(input, init);
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    await decide('合成角色甲', '确认');
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.getByText('合成角色甲')).toBeInTheDocument();
    answer();
    await waitFor(() => expect(screen.queryByText('合成角色甲')).not.toBeInTheDocument());
  });

  it('驳回被服务端拒绝：显示驳回失败', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([UPDATE]) },
      'GET authorization-requests': { body: { items: REQUESTS } },
      [`POST authorization-requests/${FIRST}:reject`]: { status: 409, body: problemBody(409, 'PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING') },
    });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    const dialog = await decide('合成角色甲', '驳回');

    expect(await within(dialog).findByText('驳回失败')).toBeInTheDocument();
  });

  it('页面不按自己的权限判断隐藏动作：申请人看到自己申请的行也有确认按钮，由服务端拒绝', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([UPDATE]) }, 'GET authorization-requests': { body: { items: REQUESTS } } });
    renderWithProviders(<AuthorizationRequestsPage />);

    await screen.findByText('合成角色甲');
    expect(screen.getAllByRole('button', { name: '确认' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: '驳回' })).toHaveLength(2);
  });
});
