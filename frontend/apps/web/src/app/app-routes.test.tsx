import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { currentUserBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { AppRoutes } from './app-routes';

const NAVIGATION = [
  { name: '待办', routePath: '/inbox', children: [] },
  {
    name: '系统管理',
    routePath: null,
    children: [
      { name: '编号规则', routePath: '/admin/numbering-rules', children: [] },
      { name: '同步记录', routePath: '/admin/sync-runs', children: [] },
    ],
  },
];

const EMPTY_INBOX = { items: [], newCount: 0, legacyCount: 0, totalCount: 0, legacyAvailable: true };

describe('应用外壳与登录状态', () => {
  it('导航来自当前用户接口', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([], NAVIGATION) }, 'GET approval-tasks': { body: EMPTY_INBOX } });
    renderWithProviders(<AppRoutes />, { route: '/inbox' });

    expect(await screen.findByText('张三')).toBeInTheDocument();
    expect(screen.getByText('交付一部')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '待办' })).toBeInTheDocument();
    expect(await screen.findByText('系统管理')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '编号规则' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: '同步记录' })).toBeInTheDocument();
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
});
