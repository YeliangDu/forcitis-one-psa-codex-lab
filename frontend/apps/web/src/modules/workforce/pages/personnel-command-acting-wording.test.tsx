import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { currentUserBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { PersonnelCommandFormPage } from './personnel-command-form-page';

const ID = '019a0000-0000-7000-8000-000000000a01';
const COVERED = '019a0000-0000-7000-8000-000000000a02';
const UNIT = '019a0000-0000-7000-8000-000000000b01';
const POSITION = '019a0000-0000-7000-8000-000000000d01';
const DUTY = '019a0000-0000-7000-8000-000000000f01';
const COMMAND = '019a0000-0000-7000-8000-000000000c01';
const UNTIL = '有效至（含当日）';
const PERSON = { employeeId: ID, employeeCode: 'E0001', displayName: '张三', employmentStatus: 'EMPLOYED', onUnpaidLeave: false, hireDate: '2024-03-01', assignments: [] };

afterEach(() => vi.useRealTimers());

function setup(kind: 'ACTING_START' | 'ACTING_EXTEND', authorized = true) {
  const server = stubServer({
    'GET sessions:current': { body: currentUserBody(authorized ? ['organization.workforce:personnel-command:create'] : []) },
    'GET employees': { body: { items: [PERSON, { ...PERSON, employeeId: COVERED, employeeCode: 'E0002', displayName: '李四' }], nextCursor: null } },
    [`GET employees/${ID}`]: { body: PERSON },
    'GET org-units': { body: [{ orgUnitId: UNIT, name: '交付一部', code: 'D1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 }] },
    'GET positions': { body: { items: [{ positionId: POSITION, code: 'P1', name: '项目经理', status: 'ACTIVE', effectiveFromDate: '2024-01-01' }], nextCursor: null } },
    [`GET acting-duties/${ID}`]: { body: { items: [{ actingDutyId: DUTY, employeeId: ID, coveredOrgUnitId: UNIT,
      coveredPositionId: POSITION, coveredEmployeeId: COVERED, coveredEmployeeName: '李四', firstDay: '2026-01-01',
      lastDay: '2026-12-31', state: 'ACTIVE', endCause: null, reason: '合成代理安排' }] } },
    'POST personnel-commands': { body: { personnelCommandId: COMMAND } },
  });
  renderWithProviders(<PersonnelCommandFormPage />, { route: `/admin/personnel-commands/new?employeeId=${ID}&commandKind=${kind}`, path: '/admin/personnel-commands/new' });
  return server;
}

async function assertPosted(server: ReturnType<typeof stubServer>, expected: Record<string, unknown>) {
  await userEvent.click(screen.getByRole('button', { name: '提交' }));
  await waitFor(() => expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(1));
  expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual(expected);
  expect(server.unmatched).toEqual([]);
}

describe('Issue #4 approved acting date form wording', () => {
  it.each([
    ['2028-02-29', '2028-03-01'],
    ['2028-12-31', '2029-01-01'],
  ])('start uses approved inclusive label and preserves %s -> %s conversion', async (lastDay, endsBefore) => {
    const server = setup('ACTING_START');
    await userEvent.selectOptions(await screen.findByLabelText('被代理组织单元'), UNIT);
    await userEvent.selectOptions(screen.getByLabelText('被代理岗位'), POSITION);
    await userEvent.selectOptions(screen.getByLabelText('被代理员工'), COVERED);
    expect(screen.queryByLabelText(UNTIL), 'APPROVED_INCLUSIVE_START_FORM_LABEL').not.toBeNull();
    await userEvent.type(screen.getByLabelText('生效日期'), '2028-02-01');
    await userEvent.type(screen.getByLabelText(UNTIL), lastDay);
    await userEvent.type(screen.getByLabelText('原因'), '合成代理开始');
    await assertPosted(server, { commandKind: 'ACTING_START', employeeId: ID, effectiveDate: '2028-02-01',
      actingCoveredOrgUnitId: UNIT, actingCoveredPositionId: POSITION, actingCoveredEmployeeId: COVERED,
      actingEndsBeforeDate: endsBefore, reason: '合成代理开始' });
  });

  it('extension keeps the approved label, selected duty and submission business day after Shanghai midnight', async () => {
    vi.setSystemTime(new Date('2026-11-07T15:59:30Z'));
    const server = setup('ACTING_EXTEND');
    await userEvent.selectOptions(await screen.findByLabelText('代理职务'), DUTY);
    expect(screen.getByLabelText('生效日期')).toHaveValue('2026-11-07');
    expect(screen.queryByLabelText(UNTIL), 'APPROVED_INCLUSIVE_EXTEND_FORM_LABEL').not.toBeNull();
    await userEvent.type(screen.getByLabelText(UNTIL), '2028-12-31');
    await userEvent.type(screen.getByLabelText('原因'), '合成代理延长');
    vi.setSystemTime(new Date('2026-11-07T16:00:30Z'));
    await assertPosted(server, { commandKind: 'ACTING_EXTEND', employeeId: ID, targetActingDutyId: DUTY,
      effectiveDate: '2026-11-08', actingEndsBeforeDate: '2029-01-01', reason: '合成代理延长' });
  });

  it('approved wording does not grant extension permission or produce a request', async () => {
    const server = setup('ACTING_EXTEND', false);
    expect(await screen.findByText('没有权限执行这个操作')).toBeInTheDocument();
    expect(screen.queryByLabelText(UNTIL)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '提交' })).not.toBeInTheDocument();
    expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(0);
  });
});
