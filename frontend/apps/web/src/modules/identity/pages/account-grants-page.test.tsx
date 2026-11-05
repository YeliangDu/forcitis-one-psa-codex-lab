import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { AccountGrantsPage } from './account-grants-page';

const USER = '019a0000-0000-7000-8000-000000001001';
const GRANTER = '019a0000-0000-7000-8000-000000001002';
const ACCOUNTS = { 'GET user-accounts': { body: { items: [{ userId: USER, status: 'ACTIVE', roleCodes: ['employee'], displayName: '合成员工甲', employeeCode: 'E0001' }], nextCursor: null } } };
const GRANTS = [
  { roleCode: 'employee', channel: 'DEFAULT_ROLE', effectiveFrom: '2026-01-05T00:00:00Z' },
  { roleCode: 'business_admin', channel: 'MANUAL', effectiveFrom: '2026-02-01T00:00:00Z', effectiveTo: '2026-03-01T00:00:00Z', revocationCause: 'MANUAL', grantedBy: GRANTER, revokedBy: GRANTER },
];

function rowOf(text: string) {
  const row = screen.getByText(text).closest('tr');
  if (row === null) throw new Error(`no table row contains ${text}`);
  return within(row);
}

describe('用户账号授权记录页', () => {
  it('选一个账号，每条授权显示来源（授予渠道的中文），列头可按名称找到', async () => {
    const server = stubServer({ ...ACCOUNTS, 'GET account-role-grants': { body: { items: GRANTS, nextCursor: null } } });
    renderWithProviders(<AccountGrantsPage />);

    await userEvent.selectOptions(await screen.findByLabelText('用户账号'), USER);

    expect(await screen.findByRole('columnheader', { name: '来源' })).toBeInTheDocument();
    expect(rowOf('employee').getByText('建档默认角色')).toBeInTheDocument();
    expect(rowOf('business_admin').getByText('人工授权')).toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes(`account-role-grants?userId=${USER}`))).toBe(true);
  });

  it('不显示授予人和撤销人：读取只给人的 id，页面不把 id 当名字显示', async () => {
    stubServer({ ...ACCOUNTS, 'GET account-role-grants': { body: { items: GRANTS, nextCursor: null } } });
    renderWithProviders(<AccountGrantsPage />);

    await userEvent.selectOptions(await screen.findByLabelText('用户账号'), USER);
    await screen.findByRole('columnheader', { name: '来源' });

    expect(screen.queryByText(GRANTER)).not.toBeInTheDocument();
  });

  it('没选账号时不读授权记录', async () => {
    const server = stubServer(ACCOUNTS);
    renderWithProviders(<AccountGrantsPage />);

    expect(await screen.findByLabelText('用户账号')).toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes('account-role-grants'))).toBe(false);
  });

  it('不认识的渠道原样显示，不丢来源', async () => {
    stubServer({ ...ACCOUNTS, 'GET account-role-grants': { body: { items: [{ roleCode: 'employee', channel: 'FUTURE_CHANNEL', effectiveFrom: '2026-01-05T00:00:00Z' }], nextCursor: null } } });
    renderWithProviders(<AccountGrantsPage />);

    await userEvent.selectOptions(await screen.findByLabelText('用户账号'), USER);

    expect(await screen.findByText('FUTURE_CHANNEL')).toBeInTheDocument();
  });
});
