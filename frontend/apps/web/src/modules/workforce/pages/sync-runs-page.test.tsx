import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUserBody } from '@/shared/testing/fixtures';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { SyncRunsPage } from './sync-runs-page';

const READ = 'organization.workforce:sync-run:read';
const EXECUTE = 'organization.workforce:sync-run:execute';

const RUNS = [
  { syncRunId: '12', trigger: 'SCHEDULED', status: 'HELD', startedAt: '2026-10-14T01:00:00Z', orgUnitChangeCount: 3, employeeChangeCount: 40, positionChangeCount: 0, issueCount: 1, heldReason: '在职人数比上次减少 30%' },
  { syncRunId: '11', trigger: 'SCHEDULED', status: 'SUCCEEDED', startedAt: '2026-10-13T01:00:00Z', orgUnitChangeCount: 0, employeeChangeCount: 2, positionChangeCount: 0, issueCount: 0 },
];

describe('同步记录页', () => {
  it('列出同步记录，被中止的同步有放行按钮', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, EXECUTE]) }, 'GET sync-runs': { body: RUNS } });
    renderWithProviders(<SyncRunsPage />);

    expect(await screen.findByText('被中止')).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('在职人数比上次减少 30%')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByRole('button', { name: '放行' })).toHaveLength(1));
  });

  it('没有放行权限时不显示放行', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET sync-runs': { body: RUNS } });
    renderWithProviders(<SyncRunsPage />);

    await screen.findByText('被中止');
    await waitFor(() => expect(screen.queryByRole('button', { name: '放行' })).not.toBeInTheDocument());
    expect(screen.getAllByRole('button', { name: '查看差异' })).toHaveLength(2);
  });

  it('放行：确认后请求放行该同步', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, EXECUTE]) },
      'GET sync-runs': { body: RUNS },
      'POST sync-runs/12:release': { body: { outcome: 'RELEASED', message: null, syncRun: RUNS[0] } },
    });
    renderWithProviders(<SyncRunsPage />);

    await userEvent.click(await screen.findByRole('button', { name: '放行' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '确认放行' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.url.endsWith('sync-runs/12:release')).toBe(true);
  });

  it('放行被拒绝（状态已变）时显示中文原因', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, EXECUTE]) },
      'GET sync-runs': { body: RUNS },
      'POST sync-runs/12:release': { status: 409, body: { outcome: 'NOT_HELD', message: 'the run is RELEASED' } },
    });
    renderWithProviders(<SyncRunsPage />);

    await userEvent.click(await screen.findByRole('button', { name: '放行' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '确认放行' }));

    expect(await screen.findByText('该同步记录当前不是被中止状态，无法放行')).toBeInTheDocument();
  });

  it('查看差异：显示这次同步的变化', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ]) },
      'GET sync-runs/11': { body: { syncRun: RUNS[1], diffs: [{ entityKind: 'EMPLOYEE', subjectKey: '1001', changeKind: 'UPDATED', changedFields: ['部门'], detail: '部门变更' }] } },
      'GET sync-runs': { body: RUNS },
    });
    renderWithProviders(<SyncRunsPage />);

    await screen.findByText('成功');
    const row = screen.getByText('成功').closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: '查看差异' }));

    expect(await screen.findByText('部门变更')).toBeInTheDocument();
  });
});
