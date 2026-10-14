import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUserBody } from '@/shared/testing/fixtures';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { TaskDetailPage } from './task-detail-page';

const APPROVE = 'platform.approval:task:approve';
const READ = 'platform.approval:task:read';

const TASK = {
  taskId: 't-1',
  instanceId: 'i-1',
  processKey: 'COST_TIMESHEET_APPROVAL',
  documentType: 'TIMESHEET',
  documentId: 'TS-FST-202610-0001',
  nodeKey: 'dept-head',
  nodeName: '部门负责人审核',
  initiator: '2002',
  assignee: null,
  candidates: ['1001', '1002'],
  createdAt: '2026-10-14T01:00:00Z',
  canAct: true,
};

function render() {
  return renderWithProviders(<TaskDetailPage />, { route: '/approval/tasks/t-1', path: '/approval/tasks/:taskId' });
}

const BUTTONS = ['认领', '通过', '驳回', '退回'];

describe('新引擎任务详情的操作按钮', () => {
  it('有办理权限时显示认领、通过、驳回、退回', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, APPROVE]) }, 'GET approval-tasks/t-1': { body: TASK } });
    render();

    await screen.findByText('部门负责人审核');
    for (const name of BUTTONS) {
      expect(await screen.findByRole('button', { name })).toBeInTheDocument();
    }
  });

  it('只有查看权限时不显示任何办理按钮', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET approval-tasks/t-1': { body: TASK } });
    render();

    await screen.findByText('部门负责人审核');
    await waitFor(() => expect(screen.queryByRole('button', { name: '认领' })).not.toBeInTheDocument());
    for (const name of BUTTONS) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('已认领的任务不显示认领，但仍可通过、驳回、退回', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, APPROVE]) }, 'GET approval-tasks/t-1': { body: { ...TASK, assignee: '1001' } } });
    render();

    expect(await screen.findByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '认领' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '驳回' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '退回' })).toBeInTheDocument();
  });

  it('服务端说不能办理（canAct 为否）时，即使有权限也不显示按钮', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, APPROVE]) }, 'GET approval-tasks/t-1': { body: { ...TASK, canAct: false } } });
    render();

    await screen.findByText('部门负责人审核');
    await waitFor(() => expect(screen.queryByRole('button', { name: '通过' })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '认领' })).not.toBeInTheDocument();
  });
});

describe('新引擎任务办理', () => {
  it('认领：带着 CSRF 令牌请求认领，令牌不在地址里', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-123; path=/';
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET approval-tasks/t-1': { body: TASK },
      'POST approval-tasks/t-1:claim': { status: 204 },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '认领' }));
    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    const claim = server.calls.find((call) => call.method === 'POST');
    expect(claim?.url.endsWith('approval-tasks/t-1:claim')).toBe(true);
    expect(claim?.headers.get('X-XSRF-TOKEN')).toBe('csrf-123');
    expect(claim?.url).not.toContain('csrf-123');
  });

  it('驳回必须填写理由；填写后提交 REJECT 和理由', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET approval-tasks/t-1': { body: TASK },
      'POST approval-tasks/t-1:decide': { body: { instanceId: 'i-1', status: 'REJECTED' } },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '驳回' }));
    const confirm = await screen.findByRole('button', { name: '确认驳回' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText('理由（必填）'), '工时与项目计划不符');
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith(':decide'))).toBe(true));
    expect(server.calls.find((call) => call.url.endsWith(':decide'))?.body).toEqual({ outcome: 'REJECT', reason: '工时与项目计划不符' });
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/inbox'));
  });

  it('退回给发起人也必须填写理由', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET approval-tasks/t-1': { body: TASK },
      'POST approval-tasks/t-1:return': { body: { instanceId: 'i-1', status: 'RETURNED' } },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '退回' }));
    expect(await screen.findByRole('button', { name: '确认退回' })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('理由（必填）'), '请补充说明');
    await userEvent.click(screen.getByRole('button', { name: '确认退回' }));

    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith(':return'))).toBe(true));
    expect(server.calls.find((call) => call.url.endsWith(':return'))?.body).toEqual({ reason: '请补充说明' });
  });

  it('其他人已认领时显示中文原因', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET approval-tasks/t-1': { body: TASK },
      'POST approval-tasks/t-1:claim': { status: 409, body: { errorCode: 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED', message: 'x' } },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '认领' }));
    expect(await screen.findByText('审批任务已被其他人认领')).toBeInTheDocument();
  });
});
