import { screen, within } from '@testing-library/react';
import { beforeAll, describe, expect, it } from 'vitest';
import { currentUserBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { EmployeeDetailPage } from './employee-detail-page';
import { fetchActingDuties, fetchCoveredActingDuties } from '../api/workforce-api';

const ID = '019a0000-0000-7000-8000-000000000a01';
const COVERED = '019a0000-0000-7000-8000-000000000a02';
const UNIT = '019a0000-0000-7000-8000-000000000b01';
const POSITION = '019a0000-0000-7000-8000-000000000d01';
const DUTY = '019a0000-0000-7000-8000-000000000f01';
const CREATE = 'organization.workforce:personnel-command:create';
const ACTIVE = {
  actingDutyId: DUTY, employeeId: ID, coveredOrgUnitId: UNIT, coveredPositionId: POSITION,
  coveredEmployeeId: COVERED, coveredEmployeeName: '李四', firstDay: '2026-10-01',
  lastDay: '2026-12-31', state: 'ACTIVE', endCause: null, reason: '代管交付',
};
const ENDED = {
  ...ACTIVE, actingDutyId: '019a0000-0000-7000-8000-000000000f02',
  firstDay: '2026-09-01', lastDay: null, state: 'ENDED', endCause: 'SEPARATION', reason: '离职当日关闭',
  coveredEmployeeName: null,
};

type Duty = Omit<typeof ACTIVE, 'coveredEmployeeId' | 'coveredEmployeeName' | 'lastDay' | 'endCause'> & {
  coveredEmployeeId: string | null; coveredEmployeeName: string | null; lastDay: string | null; endCause: string | null;
};

let expectedActingPaths: string[] = [];

beforeAll(async () => {
  // Resolve canonical full paths through existing generated-operation adapters.
  // This stub is separate from every rendered page's measured request log.
  const reference = stubServer({
    [`GET acting-duties/${ID}`]: { body: { items: [] } },
    [`GET covered-acting-duties/${ID}`]: { body: { items: [] } },
  });
  await fetchActingDuties(ID);
  await fetchCoveredActingDuties(ID);
  expect(reference.unmatched).toEqual([]);
  expect(reference.calls).toHaveLength(2);
  expect(reference.calls.map((call) => call.method)).toEqual(['GET', 'GET']);
  expectedActingPaths = reference.calls.map((call) => new URL(call.url, 'http://localhost').pathname).sort();
});

function requireRow(row: HTMLElement | undefined, label: string): HTMLElement {
  expect(row, `${label} acting-duty fixture row exists`).toBeDefined();
  if (row === undefined) throw new Error(`${label} acting-duty fixture row is missing`);
  return row;
}

function setup(permissions: string[] = [CREATE], duties: Duty[] = [ACTIVE, ENDED], coverage: Duty[] = [], extra: Parameters<typeof stubServer>[0] = {}) {
  const server = stubServer({
    'GET sessions:current': { body: currentUserBody(permissions) },
    [`GET employees/${ID}`]: { body: {
      employeeId: ID, employeeCode: 'E0001', displayName: '张三', employmentStatus: 'EMPLOYED',
      onUnpaidLeave: false, hireDate: '2024-03-01', assignments: [
        { assignmentKind: 'PRIMARY', orgUnitId: UNIT, orgUnitName: '交付一部', positionId: POSITION, positionName: '项目经理' },
      ],
    } },
    [`GET primary-assignments/${ID}`]: { body: { items: [] } },
    [`GET acting-duties/${ID}`]: { body: { items: duties } },
    [`GET covered-acting-duties/${ID}`]: { body: { items: coverage } },
    'GET org-units': { body: [{ orgUnitId: UNIT, name: '交付一部', code: 'D1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 }] },
    'GET positions': { body: { items: [{ positionId: POSITION, code: 'P1', name: '项目经理', status: 'ACTIVE', effectiveFromDate: '2024-01-01' }], nextCursor: null } },
    // The name of a hidden covered employee is deliberately unavailable from any authorized read.
    'GET employees': { body: { items: [], nextCursor: null } },
    ...extra,
  });
  renderWithProviders(<EmployeeDetailPage />, { route: `/admin/employees/${ID}`, path: '/admin/employees/:employeeId' });
  return server;
}

describe('Issue #4 employee acting duties', () => {
  it('reads both employee-scoped endpoints and renders newest-first duty facts beneath assignments', async () => {
    const server = setup();
    const table = await screen.findByRole('table', { name: '代理职务' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('代管交付');
    expect(rows[0]).toHaveTextContent('交付一部');
    expect(rows[0]).toHaveTextContent('项目经理');
    expect(rows[0]).toHaveTextContent('李四');
    expect(rows[0]).toHaveTextContent('2026-10-01');
    expect(rows[0]).toHaveTextContent('2026-12-31');
    expect(rows[0]).toHaveTextContent(/进行中|代理中|生效中|有效/);
    expect(rows[1]).toHaveTextContent('离职当日关闭');
    expect(rows[1]).toHaveTextContent('已结束');
    expect(rows[1]).toHaveTextContent('离职');
    expect(screen.getAllByRole('table').indexOf(table)).toBeGreaterThan(0);
    expect(server.calls.filter((call) => /\/organization\/(acting-duties|covered-acting-duties)\//.test(call.url)).map((call) => new URL(call.url, 'http://localhost').pathname).sort()).toEqual(expectedActingPaths);
  });

  it.each([null, COVERED])('keeps a missing or redacted covered name (%s) and a zero-length last day blank', async (coveredEmployeeId) => {
    const server = setup([], [{ ...ENDED, coveredEmployeeId }]);
    const table = await screen.findByRole('table', { name: '代理职务' });
    const row = requireRow(within(table).getAllByRole('row')[1], 'redacted');
    expect(row).toHaveTextContent('2026-09-01');
    expect(row).not.toHaveTextContent('2026-08-31');
    expect(row).not.toHaveTextContent('2026-12-31');
    expect(row).not.toHaveTextContent('李四');
    expect(row).not.toHaveTextContent(COVERED);
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);
    const cells = within(row).getAllByRole('cell');
    const lastDayColumn = headers.findIndex((header) => /有效至（含当日）/.test(header ?? ''));
    expect(lastDayColumn).toBeGreaterThanOrEqual(0);
    expect(cells[lastDayColumn]?.textContent?.trim()).toMatch(/^(?:—)?$/);
    expect(server.calls.some((call) => call.url.includes(`/employees/${COVERED}`))).toBe(false);
  });

  it.each([true, false])('only authorized ACTIVE rows expose end/extend (authorized=%s)', async (authorized) => {
    setup(authorized ? [CREATE] : []);
    const table = await screen.findByRole('table', { name: '代理职务' });
    const rows = within(table).getAllByRole('row').slice(1);
    const activeRow = requireRow(rows[0], 'active');
    const endedRow = requireRow(rows[1], 'ended');
    // Actions may be links to the existing command form or buttons opening it.
    for (const name of ['提前结束', '延长代理职务']) {
      expect(within(activeRow).queryByText(name) !== null).toBe(authorized);
      expect(within(endedRow).queryByText(name)).not.toBeInTheDocument();
    }
    expect(screen.queryByText('开始代理') !== null).toBe(authorized);
  });

  it('shows active coverage read-only and resolves the acting person only via an authorized employee read', async () => {
    const server = setup([CREATE], [], [{ ...ACTIVE, employeeId: COVERED, coveredEmployeeId: ID, coveredEmployeeName: '张三' }]);
    // The authorized list is empty: never substitute the covered person's name for the acting person's name.
    const line = await screen.findByText('当前有代理安排');
    expect(line).not.toHaveTextContent('张三');
    expect(line).not.toHaveTextContent(COVERED);
    expect(within(line).queryByText('提前结束')).not.toBeInTheDocument();
    expect(within(line).queryByText('延长代理职务')).not.toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes(`/covered-acting-duties/${ID}`))).toBe(true);
  });

  it('shows a visible acting employee name on the covered page and excludes ended coverage', async () => {
    setup([CREATE], [], [
      { ...ACTIVE, employeeId: COVERED, coveredEmployeeId: ID, coveredEmployeeName: '张三' },
      { ...ENDED, employeeId: COVERED, coveredEmployeeId: ID, reason: '不应显示的过期覆盖' },
    ], {
      'GET employees': { body: { items: [{ employeeId: COVERED, employeeCode: 'E0002', displayName: '李四', employmentStatus: 'EMPLOYED', onUnpaidLeave: false, hireDate: '2024-03-01', assignments: [] }], nextCursor: null } },
    });
    const line = await screen.findByText(/当前由.*代理/);
    expect(line).toHaveTextContent('李四');
    expect(screen.queryByText('不应显示的过期覆盖')).not.toBeInTheDocument();
    expect(within(line).queryByText('提前结束')).not.toBeInTheDocument();
    expect(within(line).queryByText('延长代理职务')).not.toBeInTheDocument();
  });
});
