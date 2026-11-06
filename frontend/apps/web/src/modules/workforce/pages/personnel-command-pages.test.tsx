import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { PersonnelCommandDetailPage } from './personnel-command-detail-page';
import { PersonnelCommandFormPage } from './personnel-command-form-page';
import { PersonnelCommandListPage } from './personnel-command-list-page';

const COMMAND_ID = '019a0000-0000-7000-8000-000000000c01';
const EMPLOYEE_ID = '019a0000-0000-7000-8000-000000000a01';
const UNIT_ID = '019a0000-0000-7000-8000-000000000b01';
const POSITION_ID = '019a0000-0000-7000-8000-000000000d01';
const ROW = {
  personnelCommandId: COMMAND_ID,
  employeeId: EMPLOYEE_ID,
  employeeCode: 'E0001',
  displayName: '张三',
  commandKind: 'TRANSFER',
  effectiveDate: '2026-12-01',
  status: 'PENDING',
  createdAt: '2026-11-04T08:00:00Z',
  createdBy: '019a0000-0000-7000-8000-000000000e01',
};
const EMPLOYEE = { employeeId: EMPLOYEE_ID, employeeCode: 'E0001', displayName: '张三', employmentStatus: 'EMPLOYED', hireDate: '2024-03-01', assignments: [] };
const EMPLOYER_ID = '019a0000-0000-7000-8000-000000000a11';
const CHOICES = {
  'GET employees': { body: { items: [EMPLOYEE], nextCursor: null } },
  'GET org-units': { body: [{ orgUnitId: UNIT_ID, name: '交付一部', code: 'D1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 }] },
  'GET positions': { body: { items: [{ positionId: POSITION_ID, code: 'P1', name: '项目经理', status: 'ACTIVE', effectiveFromDate: '2024-01-01' }], nextCursor: null } },
  'GET legal-entities': { body: { items: [{ legalEntityId: EMPLOYER_ID, code: 'LE1', name: '上海分公司', status: 'ACTIVE' }], nextCursor: null } },
};

describe('人事命令列表', () => {
  it('列出人事命令，筛选值原样交给服务端', async () => {
    const server = stubServer({ 'GET personnel-commands': { body: { items: [ROW], nextCursor: null } }, 'GET employees': CHOICES['GET employees'] });
    renderWithProviders(<PersonnelCommandListPage />);

    expect(await screen.findByText('张三')).toBeInTheDocument();
    const table = within(screen.getByRole('table'));
    expect(table.getByText('调岗')).toBeInTheDocument();
    expect(table.getByText('待生效')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('状态'), 'EFFECTIVE');
    await screen.findByText('张三');
    expect(server.calls.some((call) => call.url.includes('/personnel-commands') && call.url.includes('status=EFFECTIVE'))).toBe(true);
  });

  it('员工详情进来时按员工筛选', async () => {
    const server = stubServer({ 'GET personnel-commands': { body: { items: [ROW], nextCursor: null } }, 'GET employees': CHOICES['GET employees'] });
    renderWithProviders(<PersonnelCommandListPage />, { route: `/admin/personnel-commands?employeeId=${EMPLOYEE_ID}` });

    await screen.findByText('张三');
    expect(server.calls.some((call) => call.url.includes('/personnel-commands') && call.url.includes(`employeeId=${EMPLOYEE_ID}`))).toBe(true);
  });

  it('没有人事命令时显示空状态', async () => {
    stubServer({ 'GET personnel-commands': { body: { items: [], nextCursor: null } }, 'GET employees': CHOICES['GET employees'] });
    renderWithProviders(<PersonnelCommandListPage />);

    expect(await screen.findByText('暂无人事命令')).toBeInTheDocument();
  });

  it('服务端拒绝（403）时显示可操作的提示', async () => {
    stubServer({ 'GET personnel-commands': { status: 403, body: problemBody(403, 'PLATFORM_WEB_ACCESS_DENIED') }, 'GET employees': CHOICES['GET employees'] });
    renderWithProviders(<PersonnelCommandListPage />);

    expect(await screen.findByText('没有权限执行这个操作')).toBeInTheDocument();
  });
});

describe('人事命令详情与撤回', () => {
  function render() {
    return renderWithProviders(<PersonnelCommandDetailPage />, { route: `/admin/personnel-commands/${COMMAND_ID}`, path: '/admin/personnel-commands/:personnelCommandId' });
  }

  it('显示命令的内容，已撤回的命令不再给撤回按钮', async () => {
    stubServer({ [`GET personnel-commands/${COMMAND_ID}`]: { body: { ...ROW, status: 'WITHDRAWN', withdrawnAt: '2026-11-04T09:00:00Z', withdrawalReason: '填错了' } } });
    render();

    expect(await screen.findByText('填错了')).toBeInTheDocument();
    expect(screen.getByText('E0001')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '撤回' })).not.toBeInTheDocument();
  });

  it('撤回要填原因，原因交给服务端，成功后重新读取', async () => {
    let withdrawn = false;
    const server = stubServer({
      [`GET personnel-commands/${COMMAND_ID}`]: () => ({ body: withdrawn ? { ...ROW, status: 'WITHDRAWN', withdrawnAt: '2026-11-04T09:00:00Z', withdrawalReason: '填错了' } : ROW }),
      [`POST personnel-commands/${COMMAND_ID}:withdraw`]: () => { withdrawn = true; return { status: 204 }; },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '撤回' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('button', { name: '确认撤回' })).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('撤回原因'), '填错了');
    await userEvent.click(within(dialog).getByRole('button', { name: '确认撤回' }));

    expect(await screen.findByText('已撤回')).toBeInTheDocument();
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ reason: '填错了' });
  });

  it('服务端拒绝撤回时对话框里显示可操作的提示', async () => {
    stubServer({
      [`GET personnel-commands/${COMMAND_ID}`]: { body: { ...ROW, status: 'EFFECTIVE' } },
      [`POST personnel-commands/${COMMAND_ID}:withdraw`]: { status: 409, body: problemBody(409, 'ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL', { detail: 'raw server detail' }) },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '撤回' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('撤回原因'), '填错了');
    await userEvent.click(within(dialog).getByRole('button', { name: '确认撤回' }));

    expect(await within(dialog).findByText('当前状态不允许撤回，请刷新后重试')).toBeInTheDocument();
    expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
  });
});

describe('发起人事命令', () => {
  function render(route = '/admin/personnel-commands/new') {
    return renderWithProviders(<PersonnelCommandFormPage />, { route, path: '/admin/personnel-commands/new' });
  }

  it('调岗：只交出填了的字段，成功后到新命令的详情', async () => {
    const server = stubServer({ ...CHOICES, 'POST personnel-commands': { body: { personnelCommandId: COMMAND_ID } } });
    render(`/admin/personnel-commands/new?employeeId=${EMPLOYEE_ID}`);

    await userEvent.selectOptions(await screen.findByLabelText('命令类型'), 'TRANSFER');
    await userEvent.selectOptions(await screen.findByLabelText('目标组织单元'), UNIT_ID);
    await userEvent.type(screen.getByLabelText('生效日期'), '2026-12-01');
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    expect(await screen.findByTestId('location')).toHaveTextContent(`/admin/personnel-commands/${COMMAND_ID}`);
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ commandKind: 'TRANSFER', employeeId: EMPLOYEE_ID, effectiveDate: '2026-12-01', targetOrgUnitId: UNIT_ID });
  });

  it('入职：填新人的资料，不带员工', async () => {
    const server = stubServer({ ...CHOICES, 'POST personnel-commands': { body: { personnelCommandId: COMMAND_ID } } });
    render();

    await userEvent.selectOptions(await screen.findByLabelText('命令类型'), 'HIRE');
    expect(screen.queryByLabelText('员工')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('姓名'), '李四');
    await userEvent.selectOptions(screen.getByLabelText('性别'), 'FEMALE');
    await userEvent.type(screen.getByLabelText('企业邮箱'), 'li@example.com');
    await userEvent.type(screen.getByLabelText('生效日期'), '2026-12-01');
    await userEvent.selectOptions(screen.getByLabelText('目标组织单元'), UNIT_ID);
    await userEvent.selectOptions(screen.getByLabelText('目标岗位'), POSITION_ID);
    await userEvent.selectOptions(screen.getByLabelText('是否计费人员'), 'true');
    await userEvent.selectOptions(screen.getByLabelText('任职阶段'), 'PROBATION');
    await userEvent.selectOptions(screen.getByLabelText('用人单位'), EMPLOYER_ID);
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    await screen.findByTestId('location');
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({
      commandKind: 'HIRE', displayName: '李四', gender: 'FEMALE', email: 'li@example.com', effectiveDate: '2026-12-01',
      targetOrgUnitId: UNIT_ID, targetPositionId: POSITION_ID, isTargetFeeEarner: true, targetEmploymentPhaseKind: 'PROBATION',
      targetEmployerLegalEntityId: EMPLOYER_ID,
    });
  });

  it('打开时只读员工列表：人事专员没有组织单元和法人的读权限，读了就被服务端拒绝', async () => {
    const server = stubServer({ 'GET employees': CHOICES['GET employees'] });
    render();

    expect(await screen.findByLabelText('员工')).toBeInTheDocument();
    expect(server.calls.some((call) => /org-units|positions|legal-entities/.test(call.url))).toBe(false);
  });

  it('离职填最后工作日，不填生效日期', async () => {
    stubServer(CHOICES);
    render();

    await userEvent.selectOptions(await screen.findByLabelText('命令类型'), 'SEPARATE');

    expect(screen.getByLabelText('最后工作日')).toBeInTheDocument();
    expect(screen.queryByLabelText('生效日期')).not.toBeInTheDocument();
  });

  it('服务端拒绝时显示可操作的提示，不显示服务端原文', async () => {
    stubServer({ ...CHOICES, 'POST personnel-commands': { status: 400, body: problemBody(400, 'ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID', { detail: 'raw server detail' }) } });
    render(`/admin/personnel-commands/new?employeeId=${EMPLOYEE_ID}`);

    await userEvent.click(await screen.findByRole('button', { name: '提交' }));

    expect(await screen.findByText('人事命令的内容不完整，请检查后重试')).toBeInTheDocument();
    expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
  });
});
