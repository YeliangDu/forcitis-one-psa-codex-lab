import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { SyncRunsPage } from './sync-runs-page';

const READ = 'organization.workforce:sync-run:read';
const EXECUTE = 'organization.workforce:sync-run:execute';

const RUNS = [
  { syncRunId: '019a0000-0000-7000-8000-000000000012', trigger: 'SCHEDULED', status: 'HELD', startedAt: '2026-10-14T01:00:00Z', orgUnitChangeCount: 3, employeeChangeCount: 40, positionChangeCount: 0, issueCount: 1, holdKinds: [], heldSeparations: [], releasedKinds: [], heldReason: '在职人数比上次减少 30%' },
  { syncRunId: '019a0000-0000-7000-8000-000000000011', trigger: 'SCHEDULED', status: 'SUCCEEDED', startedAt: '2026-10-13T01:00:00Z', orgUnitChangeCount: 0, employeeChangeCount: 2, positionChangeCount: 0, issueCount: 0, holdKinds: [], heldSeparations: [], releasedKinds: [] },
];

describe('同步记录页', () => {
  it('列出同步记录，被中止的同步有放行按钮', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, EXECUTE]) }, 'GET sync-runs': { body: { items: RUNS } } });
    renderWithProviders(<SyncRunsPage />);

    expect(await screen.findByText('被中止')).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('在职人数比上次减少 30%')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByRole('button', { name: '放行' })).toHaveLength(1));
  });

  it('没有放行权限时不显示放行', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET sync-runs': { body: { items: RUNS } } });
    renderWithProviders(<SyncRunsPage />);

    await screen.findByText('被中止');
    await waitFor(() => expect(screen.queryByRole('button', { name: '放行' })).not.toBeInTheDocument());
    expect(screen.getAllByRole('button', { name: '查看同步差异' })).toHaveLength(2);
  });

  it('放行：确认后请求放行该同步', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, EXECUTE]) },
      'GET sync-runs': { body: { items: RUNS } },
      'POST sync-runs/019a0000-0000-7000-8000-000000000012:release': { body: { outcome: 'RELEASED', message: null, syncRun: RUNS[0] } },
    });
    renderWithProviders(<SyncRunsPage />);

    await userEvent.click(await screen.findByRole('button', { name: '放行' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('button', { name: '确认放行' })).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('理由（必填）'), '已核对差异');
    await userEvent.click(within(dialog).getByRole('button', { name: '确认放行' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    const release = server.calls.find((call) => call.method === 'POST');
    expect(release?.url.endsWith('sync-runs/019a0000-0000-7000-8000-000000000012:release')).toBe(true);
    expect(release?.body).toEqual({ reason: '已核对差异' });
    expect(release?.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('放行被拒绝（状态已变）时显示中文原因', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, EXECUTE]) },
      'GET sync-runs': { body: { items: RUNS } },
      'POST sync-runs/019a0000-0000-7000-8000-000000000012:release': { status: 409, body: problemBody(409, 'ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL') },
    });
    renderWithProviders(<SyncRunsPage />);

    await userEvent.click(await screen.findByRole('button', { name: '放行' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('理由（必填）'), '已核对差异');
    await userEvent.click(within(dialog).getByRole('button', { name: '确认放行' }));

    expect(await screen.findByText('这个同步记录当前不是被中止状态，无法放行')).toBeInTheDocument();
  });

  it('查看同步差异：显示这次同步的变化', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ]) },
      'GET sync-runs/019a0000-0000-7000-8000-000000000011': { body: { syncRun: RUNS[1], diffs: [{ entityKind: 'EMPLOYEE', subjectKey: '1001', changeKind: 'UPDATED', changedFields: ['部门'], detail: '部门变更' }] } },
      'GET sync-runs': { body: { items: RUNS } },
    });
    renderWithProviders(<SyncRunsPage />);

    await screen.findByText('成功');
    const row = screen.getByText('成功').closest('tr') as HTMLElement;
    await userEvent.click(within(row).getByRole('button', { name: '查看同步差异' }));

    expect(await screen.findByText('部门变更')).toBeInTheDocument();
  });

  it('同步记录分页：有下一页的游标时显示“显示更多”', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ]) },
      'GET sync-runs': (request) => (request.url.includes('cursor=c2') ? { body: { items: [RUNS[1]] } } : { body: { items: [RUNS[0]], nextCursor: 'c2' } }),
    });
    renderWithProviders(<SyncRunsPage />);

    await screen.findByText('被中止');
    expect(screen.queryByText('成功')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '显示更多' }));

    expect(await screen.findByText('成功')).toBeInTheDocument();
    expect(server.calls.filter((call) => call.url.includes('sync-runs'))).toHaveLength(2);
  });
});
