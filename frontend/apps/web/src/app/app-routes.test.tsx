import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { AppRoutes } from './app-routes';

const NAVIGATION = [
  { name: '待办', routePath: '/inbox', children: [] },
  {
    name: '系统管理',
    routePath: null,
    children: [
      { name: '编号规则', routePath: '/admin/numbering-rules', children: [] },
    ],
  },
];

const EMPTY_INBOX = { items: [], newCount: 0, noticeCount: 0, totalCount: 0 };

describe('应用外壳与登录状态', () => {
  it('导航来自当前用户接口', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([], NAVIGATION) }, 'GET approval-tasks': { body: EMPTY_INBOX } });
    renderWithProviders(<AppRoutes />, { route: '/inbox' });

    expect(await screen.findByText('张三')).toBeInTheDocument();
    expect(screen.getByText('交付一部')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '待办' })).toBeInTheDocument();
    expect(await screen.findByText('系统管理')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '编号规则' })).toBeInTheDocument();
    expect(screen.queryByText('组织浏览')).not.toBeInTheDocument();
  });

  it('没有登录时进入登录页', async () => {
    stubServer({ 'GET sessions:current': { status: 401, body: {} } });
    renderWithProviders(<AppRoutes />, { route: '/inbox' });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
    expect(await screen.findByText('企业微信扫码登录')).toBeInTheDocument();
  });

  it('登录后使用中接口返回 401：进入会话过期页', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([], NAVIGATION) }, 'GET approval-tasks': { status: 401, body: {} } });
    renderWithProviders(<AppRoutes />, { route: '/inbox' });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/session-expired'));
    expect(await screen.findByText('登录已过期')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新登录' })).toBeInTheDocument();
  });

  it('授权申请页挂在 /admin/authorization-requests', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([], NAVIGATION) }, 'GET authorization-requests': { body: { items: [] } } });
    renderWithProviders(<AppRoutes />, { route: '/admin/authorization-requests' });

    expect(await screen.findByText('暂无待确认的授权申请')).toBeInTheDocument();
  });

  it('授权申请页里申请人自己确认被拒（403 带业务码）：留在本页并显示提示，不进权限不足页', async () => {
    const FIRST = '019a0000-0000-7000-8000-000000009001';
    stubServer({
      'GET sessions:current': { body: currentUserBody([], NAVIGATION) },
      'GET authorization-requests': { body: { items: [{ requestId: FIRST, kind: 'UPDATE_ROLE', roleName: '合成角色甲', roleCodes: ['synthetic_a'], roleLabels: [{ code: 'synthetic_a', name: '合成角色甲' }], permissionCodes: [], requestedBy: '019a0000-0000-7000-8000-000000001001', requestedAt: '2026-11-03T01:00:00Z' }] } },
      [`POST authorization-requests/${FIRST}:confirm`]: { status: 403, body: problemBody(403, 'PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED') },
    });
    renderWithProviders(<AppRoutes />, { route: '/admin/authorization-requests' });

    await userEvent.click(await screen.findByRole('button', { name: '确认' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: '确认' }));

    expect(await within(dialog).findByText('申请人和被授权用户不能确认自己的授权申请，请让其他确认人处理')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/admin/authorization-requests');
  });

  it('接口返回 403：进入权限不足页', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([], NAVIGATION) }, 'GET approval-tasks': { status: 403, body: {} } });
    renderWithProviders(<AppRoutes />, { route: '/inbox' });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'));
    expect(await screen.findByText('权限不足')).toBeInTheDocument();
  });

  it('尚未迁移的页面显示说明，而不是空白', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([], NAVIGATION) } });
    renderWithProviders(<AppRoutes />, { route: '/admin/roles' });

    expect(await screen.findByText('页面不存在')).toBeInTheDocument();
  });

  describe('确认身份（C-M7）', () => {
    const UNCONFIRMED = currentUserBody([], NAVIGATION, { identityConfirmed: false });

    it('身份没有确认之前，任何业务页都不加载：只显示“将以……身份登录”', async () => {
      const server = stubServer({ 'GET sessions:current': { body: UNCONFIRMED }, 'GET approval-tasks': { body: EMPTY_INBOX } });
      renderWithProviders(<AppRoutes />, { route: '/inbox' });

      expect(await screen.findByText('将以张三身份登录')).toBeInTheDocument();
      expect(screen.queryByRole('menuitem', { name: '待办' })).not.toBeInTheDocument();
      expect(server.calls.some((call) => call.url.includes('approval-tasks'))).toBe(false);
    });

    it.each([['ticket', 'sessions:ticket-login', '#ticket=T-1']])('%s 登录后响应要求确认身份：先显示确认页，业务页不加载', async (kind, endpoint, tail) => {
      window.history.replaceState(null, '', `/login/${kind}${tail}`);
      const server = stubServer({
        [`POST ${endpoint}`]: { body: { userId: '019a0000-0000-7000-8000-000000001001', absoluteExpiresAt: '2026-10-15T00:00:00Z', identityConfirmationRequired: true } },
        'GET sessions:current': { body: UNCONFIRMED },
        'GET approval-tasks': { body: EMPTY_INBOX },
      });
      renderWithProviders(<AppRoutes />, { route: `/login/${kind}` });

      expect(await screen.findByText('将以张三身份登录')).toBeInTheDocument();
      expect(server.calls.some((call) => call.url.includes('approval-tasks'))).toBe(false);
      window.history.replaceState(null, '', '/');
    });

    it('确认身份：请求确认接口，随后读取当前用户，业务页才加载', async () => {
      let confirmed = false;
      const server = stubServer({
        'GET sessions:current': () => ({ body: confirmed ? currentUserBody([], NAVIGATION) : UNCONFIRMED }),
        'POST sessions:confirm-identity': () => {
          confirmed = true;
          return { status: 204 };
        },
        'GET approval-tasks': { body: EMPTY_INBOX },
      });
      renderWithProviders(<AppRoutes />, { route: '/inbox' });

      await userEvent.click(await screen.findByRole('button', { name: '确认身份' }));

      expect(await screen.findByRole('menuitem', { name: '待办' })).toBeInTheDocument();
      expect(server.calls.filter((call) => call.method === 'POST').map((call) => call.url.split('/').pop())).toEqual(['sessions:confirm-identity']);
      expect(screen.queryByText('将以张三身份登录')).not.toBeInTheDocument();
    });

    it('确认失败时显示原因，仍然不加载业务页', async () => {
      stubServer({
        'GET sessions:current': { body: UNCONFIRMED },
        'POST sessions:confirm-identity': { status: 401, body: problemBody(401, 'PLATFORM_IDENTITY_NOT_SIGNED_IN') },
      });
      renderWithProviders(<AppRoutes />, { route: '/inbox' });

      await userEvent.click(await screen.findByRole('button', { name: '确认身份' }));

      await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/session-expired|login/));
    });

    it('不是本人：退出登录，回到登录页', async () => {
      const server = stubServer({ 'GET sessions:current': { body: UNCONFIRMED }, 'POST sessions:logout': { status: 204 } });
      renderWithProviders(<AppRoutes />, { route: '/inbox' });

      await userEvent.click(await screen.findByRole('button', { name: '退出登录' }));

      await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
      expect(server.calls.some((call) => call.url.endsWith('sessions:logout'))).toBe(true);
    });
  });

  it('管理模式：显示管理模式的结束时间', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([], NAVIGATION, { mode: 'ADMINISTRATOR', administratorModeExpiresAt: '2026-10-14T09:00:00Z' }) },
      'GET approval-tasks': { body: EMPTY_INBOX },
    });
    renderWithProviders(<AppRoutes />, { route: '/inbox' });

    expect(await screen.findByText('当前处于管理模式')).toBeInTheDocument();
    expect(screen.getByText(/2026\/10\/14 17:00/)).toBeInTheDocument();
  });
});
