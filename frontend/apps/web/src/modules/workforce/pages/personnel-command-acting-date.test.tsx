import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { currentUserBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { PersonnelCommandFormPage } from './personnel-command-form-page';

const EMPLOYEE = '019a0000-0000-7000-8000-000000000a01';
const UNIT = '019a0000-0000-7000-8000-000000000b01';
const DUTY = '019a0000-0000-7000-8000-000000000f01';
const COMMAND = '019a0000-0000-7000-8000-000000000c01';

afterEach(() => vi.useRealTimers());

describe('Issue #4 acting extension business date', () => {
  it('ACTING_EXTEND uses the Shanghai business date at submission after the form crosses midnight', async () => {
    // Mock Date only: user-event and query scheduling continue to use real timers.
    vi.setSystemTime(new Date('2026-11-07T15:59:30Z'));
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody(['organization.workforce:personnel-command:create']) },
      'GET employees': { body: { items: [{
        employeeId: EMPLOYEE, employeeCode: 'E0001', displayName: '张三', employmentStatus: 'EMPLOYED',
        onUnpaidLeave: false, hireDate: '2024-03-01', assignments: [],
      }], nextCursor: null } },
      [`GET acting-duties/${EMPLOYEE}`]: { body: { items: [{
        actingDutyId: DUTY, employeeId: EMPLOYEE, coveredOrgUnitId: UNIT,
        coveredPositionId: null, coveredEmployeeId: null, coveredEmployeeName: null,
        firstDay: '2026-01-01', lastDay: '2026-12-31', state: 'ACTIVE', endCause: null, reason: '代管交付',
      }] } },
      'POST personnel-commands': { body: { personnelCommandId: COMMAND } },
    });
    renderWithProviders(<PersonnelCommandFormPage />, {
      route: `/admin/personnel-commands/new?employeeId=${EMPLOYEE}&commandKind=ACTING_EXTEND`,
      path: '/admin/personnel-commands/new',
    });

    await userEvent.selectOptions(await screen.findByLabelText('代理职务'), DUTY);
    expect(screen.getByLabelText('生效日期')).toHaveValue('2026-11-07');
    await userEvent.type(screen.getByLabelText('最后一天'), '2027-01-31');
    await userEvent.type(screen.getByLabelText('原因'), '继续代管');

    // 16:00 UTC is midnight in Shanghai; the UTC calendar date has not changed.
    vi.setSystemTime(new Date('2026-11-07T16:00:30Z'));
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    await waitFor(() => expect(server.calls.filter((call) => call.method === 'POST')).toHaveLength(1));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({
      commandKind: 'ACTING_EXTEND', employeeId: EMPLOYEE, targetActingDutyId: DUTY,
      effectiveDate: '2026-11-08', actingEndsBeforeDate: '2027-02-01', reason: '继续代管',
    });
  });
});
