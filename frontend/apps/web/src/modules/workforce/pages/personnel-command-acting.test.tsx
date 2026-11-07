import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { businessToday } from '@forcitis/core';
import { currentUserBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { PersonnelCommandFormPage } from './personnel-command-form-page';

const EMPLOYEE = '019a0000-0000-7000-8000-000000000a01';
const COVERED = '019a0000-0000-7000-8000-000000000a02';
const UNIT = '019a0000-0000-7000-8000-000000000b01';
const POSITION = '019a0000-0000-7000-8000-000000000d01';
const DUTY = '019a0000-0000-7000-8000-000000000f01';
const COMMAND = '019a0000-0000-7000-8000-000000000c01';
const PERSON = { employeeId: EMPLOYEE, employeeCode: 'E0001', displayName: '张三', employmentStatus: 'EMPLOYED', onUnpaidLeave: false, hireDate: '2024-03-01', assignments: [] };

afterEach(() => vi.useRealTimers());

function setup() {
  const server = stubServer({
    'GET sessions:current': { body: currentUserBody(['organization.workforce:personnel-command:create']) },
    'GET employees': { body: { items: [PERSON, { ...PERSON, employeeId: COVERED, employeeCode: 'E0002', displayName: '李四' }], nextCursor: null } },
    [`GET employees/${EMPLOYEE}`]: { body: PERSON },
    'GET org-units': { body: [{ orgUnitId: UNIT, name: '交付一部', code: 'D1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 }] },
    'GET positions': { body: { items: [{ positionId: POSITION, code: 'P1', name: '项目经理', status: 'ACTIVE', effectiveFromDate: '2024-01-01' }], nextCursor: null } },
    [`GET acting-duties/${EMPLOYEE}`]: { body: { items: [{
      actingDutyId: DUTY, employeeId: EMPLOYEE, coveredOrgUnitId: UNIT, coveredPositionId: POSITION,
      coveredEmployeeId: COVERED, coveredEmployeeName: '李四', firstDay: '2026-01-01', lastDay: '2026-12-31',
      state: 'ACTIVE', endCause: null, reason: '代管交付',
    }] } },
    'POST personnel-commands': { body: { personnelCommandId: COMMAND } },
  });
  renderWithProviders(<PersonnelCommandFormPage />, {
    route: `/admin/personnel-commands/new?employeeId=${EMPLOYEE}`,
    path: '/admin/personnel-commands/new',
  });
  return server;
}

async function chooseKind(kind: string) {
  const select = await screen.findByLabelText('命令类型');
  // An explicit assertion gives CI a stable red point before interacting with missing options.
  expect(within(select).queryAllByRole('option').map((option) => option.getAttribute('value')),
    `Issue #4: 命令类型 must offer ${kind}`).toContain(kind);
  await userEvent.selectOptions(select, kind);
}

async function submit(server: ReturnType<typeof stubServer>, expected: Record<string, unknown>) {
  await userEvent.click(screen.getByRole('button', { name: '提交' }));
  expect(await screen.findByTestId('location')).toHaveTextContent(`/admin/personnel-commands/${COMMAND}`);
  expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(1);
  expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual(expected);
}

describe('Issue #4 acting command request bodies', () => {
  it('ACTING_START sends covered targets and converts inclusive leap-day last day to an exclusive date', async () => {
    const server = setup();
    await chooseKind('ACTING_START');
    await userEvent.selectOptions(await screen.findByLabelText('被代理组织单元'), UNIT);
    await userEvent.selectOptions(await screen.findByLabelText('被代理岗位'), POSITION);
    await userEvent.selectOptions(screen.getByLabelText('被代理员工'), COVERED);
    await userEvent.type(screen.getByLabelText('生效日期'), '2028-02-01');
    await userEvent.type(screen.getByLabelText('最后一天'), '2028-02-29');
    await userEvent.type(screen.getByLabelText('原因'), '代管交付');
    await submit(server, {
      commandKind: 'ACTING_START', employeeId: EMPLOYEE, effectiveDate: '2028-02-01',
      actingCoveredOrgUnitId: UNIT, actingCoveredPositionId: POSITION, actingCoveredEmployeeId: COVERED,
      actingEndsBeforeDate: '2028-03-01', reason: '代管交付',
    });
  });

  it('ACTING_END sends the selected duty ID, effective end date and reason without start targets', async () => {
    const server = setup();
    await chooseKind('ACTING_END');
    await userEvent.selectOptions(await screen.findByLabelText('代理职务'), DUTY);
    await userEvent.type(screen.getByLabelText('生效日期'), '2026-12-01');
    await userEvent.type(screen.getByLabelText('原因'), '提前交接');
    await submit(server, {
      commandKind: 'ACTING_END', employeeId: EMPLOYEE, targetActingDutyId: DUTY,
      effectiveDate: '2026-12-01', reason: '提前交接',
    });
  });

  it('ACTING_EXTEND sends the selected duty ID and next calendar day across a year boundary', async () => {
    // Mock Date without fake timers, so user-event and query scheduling keep running normally.
    vi.setSystemTime(new Date('2026-11-07T12:00:00Z'));
    const server = setup();
    await chooseKind('ACTING_EXTEND');
    await userEvent.selectOptions(await screen.findByLabelText('代理职务'), DUTY);
    // The backend requires the business day of entry for an immediate extension.
    const today = businessToday(new Date());
    const effectiveDate = screen.getByLabelText('生效日期');
    await userEvent.clear(effectiveDate);
    await userEvent.type(effectiveDate, today);
    await userEvent.type(screen.getByLabelText('最后一天'), '2028-12-31');
    await userEvent.type(screen.getByLabelText('原因'), '继续代管');
    await submit(server, {
      commandKind: 'ACTING_EXTEND', employeeId: EMPLOYEE, targetActingDutyId: DUTY,
      effectiveDate: today, actingEndsBeforeDate: '2029-01-01', reason: '继续代管',
    });
  });
});
