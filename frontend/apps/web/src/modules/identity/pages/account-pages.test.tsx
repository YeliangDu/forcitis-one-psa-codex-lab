import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { installApiGuards } from '@forcitis/core';
import { currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { AccountDetailPage } from './account-detail-page';
import { AccountListPage } from './account-list-page';
import { PermissionAdjustmentDetailPage } from './permission-adjustment-detail-page';

const ROLE_READ = 'platform.identity:role:read';
const UPDATE = 'platform.identity:account-role:update';
const USER = '019a0000-0000-7000-8000-000000001002';
const OTHER = '019a0000-0000-7000-8000-000000001003';
const REQUEST = '019a0000-0000-7000-8000-000000009101';
const ADJUSTMENT = '019a0000-0000-7000-8000-000000009a01';
const VERSION = { ETag: '"4"' };

const ROLES = [
  { roleId: '019a0000-0000-7000-8000-000000008001', code: 'synthetic_a', name: '合成角色甲', kind: 'CUSTOM', enabled: true, administrative: false, accessScope: 'ALL', permissionCodes: [], customOrgUnits: [] },
  { roleId: '019a0000-0000-7000-8000-000000008002', code: 'synthetic_b', name: '合成角色乙', kind: 'CUSTOM', enabled: true, administrative: false, accessScope: 'ALL', permissionCodes: [], customOrgUnits: [] },
  { roleId: '019a0000-0000-7000-8000-000000008003', code: 'synthetic_off', name: '合成停用角色', kind: 'CUSTOM', enabled: false, administrative: false, accessScope: 'ALL', permissionCodes: [], customOrgUnits: [] },
];
const ACCOUNTS = [
  { userId: USER, status: 'ENABLED', roleCodes: ['synthetic_a'], displayName: '合成当事人', employeeCode: 'E-001' },
  { userId: OTHER, status: 'DISABLED', roleCodes: [], displayName: '合成他人', employeeCode: 'E-002' },
];
const GRANTS = [{ roleCode: 'synthetic_a', channel: 'WECOM', effectiveFrom: '2026-10-01T01:00:00Z' }];

function renderDetail() {
  return renderWithProviders(<AccountDetailPage />, { route: `/admin/accounts/${USER}`, path: '/admin/accounts/:userId' });
}

function detailServer(extra: Record<string, Parameters<typeof stubServer>[0][string]> = {}, permissions: string[] = [UPDATE]) {
  return stubServer({
    'GET sessions:current': { body: currentUserBody(permissions) },
    [`GET user-accounts/${USER}`]: { body: { userId: USER, status: 'ENABLED', roleCodes: ['synthetic_a'] }, headers: VERSION },
    'GET account-role-grants': { body: { items: GRANTS } },
    'GET roles': { body: { items: ROLES } },
    ...extra,
  });
}

async function openDialog() {
  await userEvent.click(await screen.findByRole('button', { name: '调整角色' }));
  return (await screen.findByRole('dialog')) as HTMLElement;
}

describe('账号列表', () => {
  it('列出账号：姓名、员工编号、状态与角色名；状态筛选交给服务端', async () => {
    const server = stubServer({ 'GET user-accounts': { body: { items: ACCOUNTS } }, 'GET roles': { body: { items: ROLES } } });
    renderWithProviders(<AccountListPage />);

    expect(await screen.findByText('合成当事人')).toBeInTheDocument();
    const table = within(screen.getByRole('table'));
    expect(table.getByText('E-001')).toBeInTheDocument();
    expect(await table.findByText('合成角色甲')).toBeInTheDocument();
    expect(table.getByText('已启用')).toBeInTheDocument();
    expect(table.getByText('已停用')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('状态'), '已停用');
    await waitFor(() => expect(server.calls.some((call) => call.url.includes('/user-accounts') && call.url.includes('status=DISABLED'))).toBe(true));
  });

  it('搜索只在已显示的账号里按姓名、员工编号或账号编号查找', async () => {
    stubServer({ 'GET user-accounts': { body: { items: ACCOUNTS } }, 'GET roles': { body: { items: ROLES } } });
    renderWithProviders(<AccountListPage />);

    expect(await screen.findByText('合成他人')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/搜索/), 'e-001');
    expect(screen.queryByText('合成他人')).not.toBeInTheDocument();
    expect(screen.getByText('合成当事人')).toBeInTheDocument();
  });

  it('加载失败时给出可操作的提示，不显示服务端原文', async () => {
    stubServer({ 'GET user-accounts': { status: 500, body: problemBody(500, 'PLATFORM_WEB_INTERNAL', { detail: 'boom at line 3' }) }, 'GET roles': { body: { items: [] } } });
    renderWithProviders(<AccountListPage />);

    expect(await screen.findByText('账号加载失败')).toBeInTheDocument();
    expect(screen.queryByText(/boom/)).not.toBeInTheDocument();
  });
});

describe('账号详情与调整角色', () => {
  it('显示当前角色与授予记录；没有账号角色更新权限的人看不到“调整角色”', async () => {
    detailServer({}, []);
    renderDetail();

    expect((await screen.findAllByText('合成角色甲')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: '调整角色' })).not.toBeInTheDocument();
  });

  it('提交后角色生效：带着读到的版本与所选角色码，成功后提示已调整', async () => {
    const server = detailServer({ [`POST user-accounts/${USER}:replace-roles`]: { status: 204 } });
    renderDetail();

    const dialog = await openDialog();
    await userEvent.selectOptions(within(dialog).getByLabelText('添加角色'), '合成角色乙');
    expect(within(dialog).queryByRole('option', { name: '合成停用角色' })).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '提交' }));

    expect(await screen.findByText('角色已调整')).toBeInTheDocument();
    const call = server.calls.find((candidate) => candidate.method === 'POST' && candidate.url.includes(':replace-roles'));
    expect(call?.body).toEqual({ roleCodes: ['synthetic_a', 'synthetic_b'] });
    expect(call?.headers.get('If-Match')).toBe('"4"');
  });

  it('需要第二位授权人确认：显示授权申请编号并链到授权申请页，角色此时不算已调整', async () => {
    detailServer({ [`POST user-accounts/${USER}:replace-roles`]: { status: 202, body: { requestId: REQUEST } } });
    renderDetail();

    const dialog = await openDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: '移除 合成角色甲' }));
    await userEvent.click(within(dialog).getByRole('button', { name: '提交' }));

    expect(await screen.findByText('等待另一位确认')).toBeInTheDocument();
    expect(screen.getByText(new RegExp(REQUEST))).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '前往授权申请页确认' })).toHaveAttribute('href', '/admin/authorization-requests');
    expect(screen.queryByText('角色已调整')).not.toBeInTheDocument();
  });

  it('被拒绝时给出服务端分类对应的提示，不显示服务端原文，对话框保持打开', async () => {
    detailServer({ [`POST user-accounts/${USER}:replace-roles`]: { status: 403, body: problemBody(403, 'PLATFORM_IDENTITY_EMPLOYEE_SEPARATED', { detail: 'raw server detail' }) } });
    renderDetail();

    const dialog = await openDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: '提交' }));

    expect(await within(dialog).findByText('调整角色失败')).toBeInTheDocument();
    expect(within(dialog).getByText('员工已离职，无法登录')).toBeInTheDocument();
    expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('403 属业务拒绝：留在页面上，不触发无权限跳转', async () => {
    const onForbidden = vi.fn();
    const uninstall = installApiGuards({ onUnauthenticated: vi.fn(), onForbidden });
    detailServer({ [`POST user-accounts/${USER}:replace-roles`]: { status: 403, body: problemBody(403, 'PLATFORM_IDENTITY_EMPLOYEE_SEPARATED') } });
    renderDetail();

    const dialog = await openDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: '提交' }));

    expect(await within(dialog).findByText('调整角色失败')).toBeInTheDocument();
    uninstall();
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it('服务暂时不可用后重试同一选择：沿用第一次的幂等键', async () => {
    let attempts = 0;
    const server = detailServer({ [`POST user-accounts/${USER}:replace-roles`]: () => (++attempts === 1 ? { status: 503, body: problemBody(503, 'PLATFORM_WEB_UNAVAILABLE') } : { status: 204 }) });
    renderDetail();

    const dialog = await openDialog();
    await userEvent.click(within(dialog).getByRole('button', { name: '提交' }));
    expect(await within(dialog).findByText('服务暂时不可用，请稍后重试')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '提交' }));

    await waitFor(() => expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(2));
    const keys = server.calls.filter((call) => call.method === 'POST').map((call) => call.headers.get('Idempotency-Key'));
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(keys[1]).toBe(keys[0]);
  });

  it('角色列表加载失败：提示原因，并且不能提交', async () => {
    detailServer({ 'GET roles': { status: 500, body: problemBody(500, 'PLATFORM_WEB_UNAVAILABLE') } });
    renderDetail();

    expect(await screen.findByText('角色加载失败')).toBeInTheDocument();
    const dialog = await openDialog();
    expect(within(dialog).getByRole('button', { name: '提交' })).toBeDisabled();
  });
});

describe('从待调整权限进入账号', () => {
  it('没有读取角色权限的人看不到账号链接', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody(['platform.identity:permission-adjustment:read']) },
      [`GET permission-adjustments/${ADJUSTMENT}`]: { headers: VERSION, body: { permissionAdjustmentId: ADJUSTMENT, kind: 'SENSITIVE_ROLE_REVIEW', originKind: 'ASSIGNMENT_CHANGE', status: 'OPEN', userId: USER, userName: '合成当事人' } },
    });
    renderWithProviders(<PermissionAdjustmentDetailPage />, { route: `/admin/permission-adjustments/${ADJUSTMENT}`, path: '/admin/permission-adjustments/:permissionAdjustmentId' });

    expect(await screen.findByText('合成当事人')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '查看账号并调整角色' })).not.toBeInTheDocument();
  });

  it('待调整权限详情有指向当事人账号的链接', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([ROLE_READ]) },
      [`GET permission-adjustments/${ADJUSTMENT}`]: { headers: VERSION, body: { permissionAdjustmentId: ADJUSTMENT, kind: 'SENSITIVE_ROLE_REVIEW', originKind: 'ASSIGNMENT_CHANGE', status: 'OPEN', userId: USER, userName: '合成当事人' } },
    });
    renderWithProviders(<PermissionAdjustmentDetailPage />, { route: `/admin/permission-adjustments/${ADJUSTMENT}`, path: '/admin/permission-adjustments/:permissionAdjustmentId' });

    expect(await screen.findByRole('link', { name: '查看账号并调整角色' })).toHaveAttribute('href', `/admin/accounts/${USER}`);
  });
});
