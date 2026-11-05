import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { PermissionAdjustmentDetailPage } from './permission-adjustment-detail-page';
import { PermissionAdjustmentListPage } from './permission-adjustment-list-page';

const ID = '019a0000-0000-7000-8000-000000009a01';
const SECOND = '019a0000-0000-7000-8000-000000009a02';
const USER = '019a0000-0000-7000-8000-000000001001';
const OWNER = '019a0000-0000-7000-8000-000000001002';
const ROLE = '019a0000-0000-7000-8000-000000008001';
const UNIT = '019a0000-0000-7000-8000-000000007001';
const COMMAND = '019a0000-0000-7000-8000-000000006001';
const VERSION = { ETag: '"3"' };

const ASSIGNMENT = {
  permissionAdjustmentId: ID, kind: 'SENSITIVE_ROLE_REVIEW', originKind: 'ASSIGNMENT_CHANGE', status: 'OPEN', userId: USER,
  triggerPersonnelCommandId: COMMAND, triggerField: 'ORG_UNIT',
};
const UNPAID = { ...ASSIGNMENT, permissionAdjustmentId: SECOND, originKind: 'UNPAID_LEAVE_START', triggerField: undefined };
const ORG_STRUCTURE = {
  permissionAdjustmentId: SECOND, kind: 'SCOPE_REVIEW', originKind: 'ORG_UNIT_INACTIVE', status: 'OPEN', roleId: ROLE, roleName: '合成角色甲', orgUnitId: UNIT,
  orgUnitName: '合成失效部门', triggerOrgStructureCommandId: COMMAND,
};
const SCOPE = { ...ASSIGNMENT, kind: 'SCOPE_REVIEW' };

function renderDetail() {
  return renderWithProviders(<PermissionAdjustmentDetailPage />, { route: `/admin/permission-adjustments/${ID}`, path: '/admin/permission-adjustments/:permissionAdjustmentId' });
}

describe('待调整权限列表', () => {
  it('列出条目：人员只显示编号，不显示姓名；默认只看待处理', async () => {
    const bare = { ...ASSIGNMENT, permissionAdjustmentId: SECOND };
    const server = stubServer({ 'GET permission-adjustments': { body: { items: [ASSIGNMENT, bare] } } });
    renderWithProviders(<PermissionAdjustmentListPage />);

    expect((await screen.findAllByText(USER)).length).toBe(2);
    const table = within(screen.getByRole('table'));
    expect(table.getAllByText('敏感角色复核')).toHaveLength(2);
    expect(table.getAllByText('调岗')).toHaveLength(2);
    expect(server.calls.some((call) => call.url.includes('/permission-adjustments') && call.url.includes('status=OPEN'))).toBe(true);
  });

  it('筛选值交给服务端，类型筛选与状态筛选各一次', async () => {
    const server = stubServer({ 'GET permission-adjustments': { body: { items: [] } } });
    renderWithProviders(<PermissionAdjustmentListPage />);

    expect(await screen.findByText('暂无待调整权限')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('状态'), '已维持');
    await userEvent.selectOptions(screen.getByLabelText('类型'), '范围复核');
    await waitFor(() => expect(server.calls.some((call) => call.url.includes('status=UPHELD') && call.url.includes('kind=SCOPE_REVIEW'))).toBe(true));
  });

  it('组织单元失效的条目没有人员：对象显示角色与组织单元', async () => {
    stubServer({ 'GET permission-adjustments': { body: { items: [ORG_STRUCTURE] } } });
    renderWithProviders(<PermissionAdjustmentListPage />);

    expect(await screen.findByText('合成角色甲 · 合成失效部门')).toBeInTheDocument();
  });

  it('加载失败时显示失败提示', async () => {
    stubServer({ 'GET permission-adjustments': { status: 500, body: problemBody(500, 'PLATFORM_INTERNAL_ERROR') } });
    renderWithProviders(<PermissionAdjustmentListPage />);

    expect(await screen.findByText('待调整权限加载失败')).toBeInTheDocument();
  });
});

describe('待调整权限详情', () => {
  it('调岗触发：显示人事命令和触发字段', async () => {
    stubServer({ [`GET permission-adjustments/${ID}`]: { body: ASSIGNMENT, headers: VERSION } });
    renderDetail();

    expect(await screen.findByText(COMMAND)).toBeInTheDocument();
    expect(screen.getByText('部门')).toBeInTheDocument();
    expect(screen.getByText('人事命令')).toBeInTheDocument();
    expect(screen.queryByText('组织结构命令')).not.toBeInTheDocument();
  });

  it('停薪留职触发：只有人事命令，没有触发字段', async () => {
    stubServer({ [`GET permission-adjustments/${ID}`]: { body: { ...UNPAID, permissionAdjustmentId: ID }, headers: VERSION } });
    renderDetail();

    expect(await screen.findByText(COMMAND)).toBeInTheDocument();
    expect(screen.getByText('停薪留职开始')).toBeInTheDocument();
    expect(screen.queryByText('触发字段')).not.toBeInTheDocument();
  });

  it('组织结构触发：显示组织结构命令、角色和组织单元', async () => {
    stubServer({ [`GET permission-adjustments/${ID}`]: { body: { ...ORG_STRUCTURE, permissionAdjustmentId: ID }, headers: VERSION } });
    renderDetail();

    expect(await screen.findByText('组织结构命令')).toBeInTheDocument();
    expect(screen.getByText('合成失效部门')).toBeInTheDocument();
    expect(screen.getAllByText('合成角色甲').length).toBeGreaterThan(0);
    expect(screen.queryByText('人事命令')).not.toBeInTheDocument();
  });

  it('没有触发信息的条目也能显示：页面不报错', async () => {
    const bare = { permissionAdjustmentId: ID, kind: 'SENSITIVE_ROLE_REVIEW', originKind: 'UPHOLD_EXPIRY', status: 'OPEN', userId: USER };
    stubServer({ [`GET permission-adjustments/${ID}`]: { body: bare, headers: VERSION } });
    renderDetail();

    expect(await screen.findByText('维持到期')).toBeInTheDocument();
    expect(screen.queryByText('人事命令')).not.toBeInTheDocument();
    expect(screen.queryByText('触发字段')).not.toBeInTheDocument();
    expect(screen.getByText(USER)).toBeInTheDocument();
  });

  it('已办理的条目不再给出操作', async () => {
    stubServer({ [`GET permission-adjustments/${ID}`]: { body: { ...ASSIGNMENT, status: 'UPHELD', businessOwnerEmployeeId: OWNER, upholdExpiresAt: '2027-01-01T00:00:00Z' }, headers: VERSION } });
    renderDetail();

    expect(await screen.findByText(OWNER)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '维持授权' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '标记已调整' })).not.toBeInTheDocument();
  });

  it('敏感角色复核的维持要填业务负责人编号，If-Match 带详情的版本，成功后重新读取', async () => {
    let upheld = false;
    const server = stubServer({
      [`GET permission-adjustments/${ID}`]: () => ({ body: upheld ? { ...ASSIGNMENT, status: 'UPHELD' } : ASSIGNMENT, headers: VERSION }),
      [`POST permission-adjustments/${ID}:uphold`]: () => { upheld = true; return { body: { ...ASSIGNMENT, status: 'UPHELD' }, headers: { ETag: '"4"' } }; },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '维持授权' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: '维持授权' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('业务负责人编号'), OWNER);
    await userEvent.type(within(dialog).getByLabelText('理由（选填）'), '仍需保留');
    await userEvent.click(confirm);

    expect(await screen.findByText('已维持')).toBeInTheDocument();
    const call = server.calls.find((entry) => entry.method === 'POST');
    expect(call?.body).toEqual({ reason: '仍需保留', businessOwnerEmployeeId: OWNER });
    expect(call?.headers.get('If-Match')).toBe('"3"');
  });

  it('范围复核的维持不选业务负责人，也不去读员工', async () => {
    const server = stubServer({
      [`GET permission-adjustments/${ID}`]: { body: SCOPE, headers: VERSION },
      [`POST permission-adjustments/${ID}:uphold`]: { body: { ...SCOPE, status: 'UPHELD' }, headers: { ETag: '"4"' } },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '维持授权' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByLabelText('业务负责人编号')).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '维持授权' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({});
    expect(server.calls.some((call) => call.url.includes('/employees'))).toBe(false);
  });

  it('标记已调整：选一种编号并填写，交给服务端', async () => {
    const REQUEST = '019a0000-0000-7000-8000-000000005001';
    const server = stubServer({
      [`GET permission-adjustments/${ID}`]: { body: ASSIGNMENT, headers: VERSION },
      [`POST permission-adjustments/${ID}:mark-adjusted`]: { body: { ...ASSIGNMENT, status: 'ADJUSTED' }, headers: { ETag: '"4"' } },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '标记已调整' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: '标记已调整' });
    expect(confirm).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('授权申请编号'), REQUEST);
    await userEvent.click(confirm);

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    const call = server.calls.find((entry) => entry.method === 'POST');
    expect(call?.body).toEqual({ authorizationRequestId: REQUEST });
    expect(call?.headers.get('If-Match')).toBe('"3"');
  });

  it('改用审计日志编号时只交审计日志编号', async () => {
    const ENTRY = '019a0000-0000-7000-8000-000000005002';
    const server = stubServer({
      [`GET permission-adjustments/${ID}`]: { body: ASSIGNMENT, headers: VERSION },
      [`POST permission-adjustments/${ID}:mark-adjusted`]: { body: { ...ASSIGNMENT, status: 'ADJUSTED' }, headers: { ETag: '"4"' } },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '标记已调整' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.selectOptions(within(dialog).getByLabelText('编号类型'), '审计日志编号');
    await userEvent.type(within(dialog).getByLabelText('审计日志编号'), ENTRY);
    await userEvent.click(within(dialog).getByRole('button', { name: '标记已调整' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ auditEntryId: ENTRY });
  });

  it('版本已过期（412）：对话框提示刷新，并重新读取条目', async () => {
    let reads = 0;
    stubServer({
      [`GET permission-adjustments/${ID}`]: () => { reads += 1; return { body: SCOPE, headers: VERSION }; },
      [`POST permission-adjustments/${ID}:uphold`]: { status: 412, body: problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH', { detail: 'raw server detail' }) },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '维持授权' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: '维持授权' }));

    expect(await within(dialog).findByText(/刷新后重试/)).toBeInTheDocument();
    expect(within(dialog).queryByText('raw server detail')).not.toBeInTheDocument();
    await waitFor(() => expect(reads).toBeGreaterThan(1));
  });

  it('条目已被别人办理（409）：提示已办理，不显示服务端原文', async () => {
    stubServer({
      [`GET permission-adjustments/${ID}`]: { body: SCOPE, headers: VERSION },
      [`POST permission-adjustments/${ID}:mark-adjusted`]: { status: 409, body: problemBody(409, 'PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_NOT_OPEN', { detail: 'raw server detail' }) },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '标记已调整' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('授权申请编号'), '019a0000-0000-7000-8000-000000005001');
    await userEvent.click(within(dialog).getByRole('button', { name: '标记已调整' }));

    expect(await within(dialog).findByText('待调整权限条目已办理')).toBeInTheDocument();
    expect(within(dialog).queryByText('raw server detail')).not.toBeInTheDocument();
  });

  it('无权办理（403）：显示无权办理待调整权限条目', async () => {
    stubServer({
      [`GET permission-adjustments/${ID}`]: { body: SCOPE, headers: VERSION },
      [`POST permission-adjustments/${ID}:uphold`]: { status: 403, body: problemBody(403, 'PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_HANDLER_NOT_ALLOWED') },
    });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: '维持授权' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: '维持授权' }));

    expect(await within(dialog).findByText('无权办理待调整权限条目')).toBeInTheDocument();
  });

  it('条目不存在或不在范围内（404）：显示加载失败和不存在的说明', async () => {
    stubServer({ [`GET permission-adjustments/${ID}`]: { status: 404, body: problemBody(404, 'PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_NOT_FOUND') } });
    renderDetail();

    expect(await screen.findByText('待调整权限加载失败')).toBeInTheDocument();
    expect(screen.getByText('待调整权限条目不存在')).toBeInTheDocument();
  });
});
