import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { EmployeeDetailPage } from './employee-detail-page';
import { EmployeeRosterPage } from './employee-roster-page';

const ID = '019a0000-0000-7000-8000-000000000a01';
const UNIT = '019a0000-0000-7000-8000-000000000b01';
const ROW = {
  employeeId: ID,
  employeeCode: 'E0001',
  displayName: '张三',
  employmentStatus: 'EMPLOYED',
  hireDate: '2024-03-01',
  assignments: [{ assignmentKind: 'PRIMARY', orgUnitId: UNIT, orgUnitName: '交付一部', positionId: null, positionName: '项目经理' }],
};

describe('员工名册', () => {
  it('列出员工，默认只要在职，切换在职状态按服务端的筛选值重新取', async () => {
    const server = stubServer({ 'GET employees': { body: { items: [ROW], nextCursor: null } } });
    renderWithProviders(<EmployeeRosterPage />);

    expect(await screen.findByText('张三')).toBeInTheDocument();
    expect(screen.getByText('E0001')).toBeInTheDocument();
    expect(screen.getByText('交付一部')).toBeInTheDocument();
    expect(server.calls.find((call) => call.url.includes('/employees'))?.url).toContain('employmentStatus=EMPLOYED');

    await userEvent.selectOptions(screen.getByLabelText('在职状态'), 'SEPARATED');
    await screen.findByText('张三');
    expect(server.calls.some((call) => call.url.includes('employmentStatus=SEPARATED'))).toBe(true);
  });

  it('按组织单元筛选时请求带着组织单元', async () => {
    const server = stubServer({
      'GET org-units': { body: [{ orgUnitId: UNIT, name: '交付一部', code: 'D1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 }] },
      'GET employees': { body: { items: [ROW], nextCursor: null } },
    });
    renderWithProviders(<EmployeeRosterPage />);

    await screen.findByText('张三');
    await userEvent.selectOptions(await screen.findByLabelText('组织单元'), UNIT);

    await screen.findByText('张三');
    expect(server.calls.some((call) => call.url.includes('/employees') && call.url.includes(`orgUnitId=${UNIT}`))).toBe(true);
  });

  it('有下一页时显示「显示更多」，点后带着服务端给的游标取下一页', async () => {
    const server = stubServer({
      'GET employees': (request) => request.url.includes('cursor=c2')
        ? { body: { items: [{ ...ROW, employeeId: '019a0000-0000-7000-8000-000000000a02', employeeCode: 'E0002', displayName: '李四' }], nextCursor: null } }
        : { body: { items: [ROW], nextCursor: 'c2' } },
    });
    renderWithProviders(<EmployeeRosterPage />);

    await userEvent.click(await screen.findByRole('button', { name: '显示更多' }));

    expect(await screen.findByText('李四')).toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes('cursor=c2'))).toBe(true);
    expect(screen.queryByRole('button', { name: '显示更多' })).not.toBeInTheDocument();
  });

  it('没有员工时显示空状态', async () => {
    stubServer({ 'GET employees': { body: { items: [], nextCursor: null } } });
    renderWithProviders(<EmployeeRosterPage />);

    expect(await screen.findByText('暂无员工')).toBeInTheDocument();
  });

  it('服务端拒绝（403）时显示可操作的提示，不显示服务端原文', async () => {
    stubServer({ 'GET employees': { status: 403, body: problemBody(403, 'PLATFORM_WEB_ACCESS_DENIED', { detail: 'raw server detail' }) } });
    renderWithProviders(<EmployeeRosterPage />);

    expect(await screen.findByText('没有权限执行这个操作')).toBeInTheDocument();
    expect(screen.queryByText(/raw server detail/)).not.toBeInTheDocument();
  });
});

describe('员工详情', () => {
  const DETAIL = { ...ROW, assignments: [{ ...ROW.assignments[0], reportsToEmployeeId: null, reportsToDisplayName: '王五' }] };

  function render() {
    return renderWithProviders(<EmployeeDetailPage />, { route: `/admin/employees/${ID}`, path: '/admin/employees/:employeeId' });
  }

  it('显示一个员工的基本信息和任职', async () => {
    stubServer({ [`GET employees/${ID}`]: { body: DETAIL } });
    render();

    expect(await screen.findByText('E0001')).toBeInTheDocument();
    expect(screen.getByText('2024-03-01')).toBeInTheDocument();
    expect(screen.getByText('交付一部')).toBeInTheDocument();
    expect(screen.getByText('项目经理')).toBeInTheDocument();
    expect(screen.getByText('王五')).toBeInTheDocument();
  });

  const HISTORY = {
    items: [
      { orgUnitId: UNIT, orgUnitName: '交付一部', positionId: null, positionName: '项目经理', effectiveFrom: '2025-09-01', effectiveTo: null, current: true },
      { orgUnitId: '019a0000-0000-7000-8000-000000000b02', orgUnitName: '咨询二部', positionId: null, positionName: null, effectiveFrom: '2024-06-01', effectiveTo: '2025-09-01', current: false },
    ],
  };

  it('主岗历史是一张按标题命名的表，按服务端给的顺序（新到旧）列出，当前一条标记「当前」，未结束的显示「至今」', async () => {
    const server = stubServer({ [`GET employees/${ID}`]: { body: DETAIL }, [`GET primary-assignments/${ID}`]: { body: HISTORY } });
    render();

    const table = await screen.findByRole('table', { name: '主岗历史' });
    expect(screen.getByRole('heading', { name: '主岗历史' })).toBeInTheDocument();
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows.map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))).toEqual([
      ['交付一部', '项目经理', '2025-09-01', '至今', '当前'],
      ['咨询二部', '—', '2024-06-01', '2025-09-01', '—'],
    ]);
    expect(server.calls.some((call) => call.url.includes(`/primary-assignments/${ID}`))).toBe(true);
  });

  it('主岗历史为空时显示空状态，不画空表', async () => {
    stubServer({ [`GET employees/${ID}`]: { body: DETAIL }, [`GET primary-assignments/${ID}`]: { body: { items: [] } } });
    render();

    expect(await screen.findByText('暂无主岗历史')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: '主岗历史' })).not.toBeInTheDocument();
  });

  it('主岗历史读取失败只在这一段提示，员工信息照常显示', async () => {
    stubServer({
      [`GET employees/${ID}`]: { body: DETAIL },
      [`GET primary-assignments/${ID}`]: { status: 403, body: problemBody(403, 'PLATFORM_WEB_ACCESS_DENIED') },
    });
    render();

    expect(await screen.findByText('主岗历史加载失败')).toBeInTheDocument();
    expect(screen.getByText('E0001')).toBeInTheDocument();
  });

  it('服务端拒绝（403）时显示可操作的提示', async () => {
    stubServer({ [`GET employees/${ID}`]: { status: 403, body: problemBody(403, 'PLATFORM_WEB_ACCESS_DENIED') } });
    render();

    expect(await screen.findByText('没有权限执行这个操作')).toBeInTheDocument();
  });
});
