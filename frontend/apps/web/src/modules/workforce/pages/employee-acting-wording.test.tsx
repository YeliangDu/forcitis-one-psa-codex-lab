import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { currentUserBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { EmployeeDetailPage } from './employee-detail-page';

const ID = '019a0000-0000-7000-8000-000000000a01';
const ACTOR = '019a0000-0000-7000-8000-000000000a02';
const UNIT = '019a0000-0000-7000-8000-000000000b01';
const POSITION = '019a0000-0000-7000-8000-000000000d01';
const CREATE = 'organization.workforce:personnel-command:create';
const PERSON = {
  employeeId: ID, employeeCode: 'E0001', displayName: '张三', employmentStatus: 'EMPLOYED',
  onUnpaidLeave: false, hireDate: '2024-03-01', assignments: [],
};
const ACTIVE = {
  actingDutyId: '019a0000-0000-7000-8000-000000000f01', employeeId: ID,
  coveredOrgUnitId: UNIT, coveredPositionId: POSITION, coveredEmployeeId: ACTOR,
  coveredEmployeeName: '李四', firstDay: '2026-10-01', lastDay: '2026-12-31',
  state: 'ACTIVE', endCause: null, reason: '合成代理安排',
};
const ENDED = {
  ...ACTIVE, actingDutyId: '019a0000-0000-7000-8000-000000000f02',
  firstDay: '2026-09-01', lastDay: null, state: 'ENDED', endCause: 'SEPARATION',
  coveredEmployeeName: null, reason: '合成已结束安排',
};
const COVERAGE = { ...ACTIVE, employeeId: ACTOR, coveredEmployeeId: ID, coveredEmployeeName: '张三' };
const ACTOR_PERSON = { ...PERSON, employeeId: ACTOR, employeeCode: 'E0002', displayName: '李四' };

function setup(options: {
  permissions?: string[]; duties?: Record<string, unknown>[]; coverage?: Record<string, unknown>[];
  employees?: { status?: number; body: unknown }; extra?: Parameters<typeof stubServer>[0];
} = {}) {
  const server = stubServer({
    'GET sessions:current': { body: currentUserBody(options.permissions ?? [CREATE]) },
    [`GET employees/${ID}`]: { body: PERSON },
    [`GET primary-assignments/${ID}`]: { body: { items: [] } },
    [`GET acting-duties/${ID}`]: { body: { items: options.duties ?? [] } },
    [`GET covered-acting-duties/${ID}`]: { body: { items: options.coverage ?? [] } },
    'GET employees': options.employees ?? { body: { items: [], nextCursor: null } },
    // A honey response proves that a new targeted name lookup is not permitted.
    [`GET employees/${ACTOR}`]: { body: { ...ACTOR_PERSON, displayName: '不得公开姓名' } },
    'GET org-units': { body: [{ orgUnitId: UNIT, name: '交付一部', code: 'D1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 }] },
    'GET positions': { body: { items: [{ positionId: POSITION, code: 'P1', name: '项目经理', status: 'ACTIVE', effectiveFromDate: '2024-01-01' }], nextCursor: null } },
    ...options.extra,
  });
  renderWithProviders(<EmployeeDetailPage />, { route: `/admin/employees/${ID}`, path: '/admin/employees/:employeeId' });
  return server;
}

function assertNoExpandedNameRead(server: ReturnType<typeof stubServer>) {
  const requests = server.calls.map((call) => ({ method: call.method, url: new URL(call.url, 'http://localhost') }));
  const choices = requests.filter(({ url }) => url.pathname === '/api/v1/organization/employees');
  expect(choices, 'APPROVED_NAMES_REUSE_ONE_EXISTING_AUTHORIZED_LIST').toHaveLength(1);
  expect(requests.some(({ url }) => url.pathname.endsWith(`/employees/${ACTOR}`)), 'NO_TARGETED_ACTOR_NAME_LOOKUP').toBe(false);
  expect(choices.some(({ url }) => [...url.searchParams.values()].some((value) => value.includes(ACTOR))), 'NO_SCOPE_EXPANDING_NAME_FILTER').toBe(false);
  expect(server.calls.filter((call) => call.method !== 'GET')).toHaveLength(0);
  expect(server.unmatched).toEqual([]);
  expect(document.body).not.toHaveTextContent('不得公开姓名');
}

describe('Issue #4 approved acting wording and privacy', () => {
  it('uses exactly 当前由{姓名}代理 only for the actor found in the authorized existing list', async () => {
    const server = setup({ coverage: [COVERAGE], employees: { body: { items: [ACTOR_PERSON], nextCursor: null } } });
    const line = await screen.findByText(/^当前/);
    expect(line.textContent, 'APPROVED_VISIBLE_ACTOR_SENTENCE').toBe('当前由李四代理');
    expect(line).not.toHaveTextContent('张三');
    expect(within(line).queryByRole('link')).not.toBeInTheDocument();
    expect(within(line).queryByRole('button')).not.toBeInTheDocument();
    assertNoExpandedNameRead(server);
  });

  it.each(['missing', 'out-of-scope', 'denied'] as const)('uses exact neutral wording for %s actor name with zero fallback reads', async (kind) => {
    const employees = kind === 'denied'
      ? { status: 403, body: { status: 403, errorCode: 'FORBIDDEN', title: 'x', detail: 'PRIVATE_NAME_DENIAL' } }
      : { body: { items: kind === 'out-of-scope' ? [PERSON] : [], nextCursor: null } };
    const server = setup({ permissions: [], coverage: [COVERAGE], employees });
    const line = await screen.findByText(/^当前/);
    expect(line.textContent, 'APPROVED_REDACTED_ACTOR_SENTENCE').toBe('当前有代理安排');
    expect(line).not.toHaveTextContent('李四');
    expect(line).not.toHaveTextContent('张三');
    expect(line).not.toHaveTextContent(ACTOR);
    expect(line).not.toHaveTextContent('—');
    expect(within(line).queryByRole('button')).not.toBeInTheDocument();
    expect(within(line).queryByRole('link')).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent('PRIVATE_NAME_DENIAL');
    assertNoExpandedNameRead(server);
  });

  it('uses the approved inclusive column and preserves blank end date, redaction and exact separation cause', async () => {
    setup({ duties: [ACTIVE, ENDED] });
    const table = await screen.findByRole('table', { name: '代理职务' });
    expect(within(table).queryByRole('columnheader', { name: '有效至（含当日）' }), 'APPROVED_INCLUSIVE_TABLE_HEADER').not.toBeNull();
    for (const name of ['被代理组织单元', '被代理岗位', '被代理员工']) {
      expect(within(table).getByRole('columnheader', { name })).toBeInTheDocument();
    }
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows[1]).toHaveTextContent('代理人离职');
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);
    const index = headers.indexOf('有效至（含当日）');
    expect(index).toBeGreaterThanOrEqual(0);
    expect(within(rows[1]).getAllByRole('cell')[index]?.textContent?.trim()).toMatch(/^(?:—)?$/);
    expect(rows[1]).not.toHaveTextContent(ACTOR);
    expect(rows[1]).not.toHaveTextContent('李四');
    expect(rows[1]).not.toHaveTextContent('2026-08-31');
  });

  it.each([true, false])('shows 延长代理职务 only on authorized active rows (authorized=%s)', async (authorized) => {
    setup({ permissions: authorized ? [CREATE] : [], duties: [ACTIVE, ENDED] });
    const table = await screen.findByRole('table', { name: '代理职务' });
    const rows = within(table).getAllByRole('row').slice(1);
    expect(within(rows[0]).queryByText('延长代理职务') !== null, 'APPROVED_EXTEND_ACTION_AND_PERMISSION').toBe(authorized);
    expect(within(rows[1]).queryByText('延长代理职务')).not.toBeInTheDocument();
    expect(within(rows[0]).queryByText('提前结束') !== null).toBe(authorized);
    expect(within(rows[1]).queryByText('提前结束')).not.toBeInTheDocument();
    expect(screen.queryByText('开始代理') !== null).toBe(authorized);
  });

  it.each(['acting-duties', 'covered-acting-duties'])('uses the same approved failure title for %s without server detail', async (endpoint) => {
    setup({ extra: { [`GET ${endpoint}/${ID}`]: { status: 500, body: { status: 500, errorCode: 'INTERNAL_ERROR', title: 'x', detail: 'PRIVATE_FAILURE_DETAIL' } } } });
    const title = await screen.findByText(/代理职务加载失败/);
    expect(title.textContent, 'APPROVED_UNIFIED_ACTING_READ_FAILURE').toBe('代理职务加载失败');
    expect(document.body).not.toHaveTextContent('PRIVATE_FAILURE_DETAIL');
  });
});
