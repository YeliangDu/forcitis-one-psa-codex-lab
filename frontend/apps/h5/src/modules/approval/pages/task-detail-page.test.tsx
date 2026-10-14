import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUserBody } from '@/shared/testing/fixtures';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { navigation, resetNavigation } from '@/shared/testing/taro-stub';
import { TaskDetailPage } from './task-detail-page';

const APPROVE = 'platform.approval:task:approve';
const TASK = {
  taskId: 't-1',
  instanceId: 'i-1',
  processKey: 'COST_TIMESHEET_APPROVAL',
  businessKey: 'TS202610140001',
  nodeKey: 'dept-head',
  nodeName: '部门负责人审核',
  initiator: '2002',
  candidates: ['1001', '1002'],
  createdAt: '2026-10-14T01:00:00Z',
  canAct: true,
};

function open(task: object = TASK, permissions: string[] = [APPROVE], extra: Parameters<typeof stubServer>[0] = {}) {
  resetNavigation({ taskId: 't-1' });
  return stubServer({
    'GET sessions:current': { body: currentUserBody(permissions) },
    'GET approval-tasks/t-1': { body: task },
    ...extra,
  });
}

async function showTask() {
  renderWithProviders(<TaskDetailPage />);
  await screen.findByText('部门负责人审核');
}

describe('审批详情', () => {
  it('显示任务信息：流程、当前节点、发起人、候选审批人', async () => {
    open();
    await showTask();

    expect(screen.getByText('工时单审批')).toBeInTheDocument();
    expect(screen.getByText('TS202610140001')).toBeInTheDocument();
    expect(screen.getByText('1001、1002')).toBeInTheDocument();
    expect(screen.getByText('待认领')).toBeInTheDocument();
  });

  it('有办理权限且服务端允许办理时，显示认领、通过、驳回、退回', async () => {
    open();
    await showTask();

    for (const name of ['认领', '通过', '驳回', '退回']) {
      expect(await screen.findByRole('button', { name })).toBeInTheDocument();
    }
  });

  it('已认领的任务不再显示认领', async () => {
    open({ ...TASK, assignee: '1001' });
    await showTask();

    expect(await screen.findByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '认领' })).not.toBeInTheDocument();
    expect(screen.getByText('已认领')).toBeInTheDocument();
  });

  it('没有办理权限时一个按钮都不显示', async () => {
    open(TASK, []);
    await showTask();

    await waitFor(() => expect(screen.queryByRole('button', { name: '通过' })).not.toBeInTheDocument());
    for (const name of ['认领', '通过', '驳回', '退回']) {
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    }
  });

  it('服务端说现在不能办理时一个按钮都不显示', async () => {
    open({ ...TASK, canAct: false });
    await showTask();

    await waitFor(() => expect(screen.queryByRole('button', { name: '认领' })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '通过' })).not.toBeInTheDocument();
  });

  it('认领：调用认领接口并刷新任务', async () => {
    const server = open(TASK, [APPROVE], { 'POST approval-tasks/t-1:claim': { body: {} } });
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '认领' }));
    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith('/approval-tasks/t-1:claim'))).toBe(true));
  });

  it('通过：理由选填，办理后回到待办', async () => {
    const server = open(TASK, [APPROVE], { 'POST approval-tasks/t-1:decide': { body: { instanceId: 'i-1', status: 'APPROVED' } } });
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '通过' }));
    expect(screen.getByText('理由（选填）')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '确认通过' }));

    await waitFor(() => expect(navigation.replaced).toEqual(['/app/pages/inbox']));
    expect(server.calls.find((call) => call.url.endsWith(':decide'))?.body).toEqual({ outcome: 'APPROVE', reason: '' });
  });

  it('驳回：必须填写理由才能确认', async () => {
    const server = open(TASK, [APPROVE], { 'POST approval-tasks/t-1:decide': { body: { instanceId: 'i-1', status: 'REJECTED' } } });
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '驳回' }));
    expect(screen.getByText('理由（必填）')).toBeInTheDocument();
    const confirm = screen.getByRole('button', { name: '确认驳回' });
    expect(confirm).toBeDisabled();

    await userEvent.type(screen.getByRole('textbox'), '  工时与项目周报不符  ');
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    await waitFor(() => expect(navigation.replaced).toEqual(['/app/pages/inbox']));
    expect(server.calls.find((call) => call.url.endsWith(':decide'))?.body).toEqual({ outcome: 'REJECT', reason: '工时与项目周报不符' });
  });

  it('退回：必须填写理由，办理后回到待办', async () => {
    const server = open(TASK, [APPROVE], { 'POST approval-tasks/t-1:return': { body: { instanceId: 'i-1', status: 'RETURNED' } } });
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '退回' }));
    const confirm = screen.getByRole('button', { name: '确认退回' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox'), '请补充说明');
    await userEvent.click(confirm);

    await waitFor(() => expect(navigation.replaced).toEqual(['/app/pages/inbox']));
    expect(server.calls.find((call) => call.url.endsWith(':return'))?.body).toEqual({ reason: '请补充说明' });
  });

  it('取消理由弹层：不提交任何办理请求', async () => {
    const server = open();
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '驳回' }));
    await userEvent.click(within(screen.getByText('驳回', { selector: '.bottom-sheet__title' }).closest('.bottom-sheet__panel') as HTMLElement).getByRole('button', { name: '取消' }));

    expect(screen.queryByText('理由（必填）')).not.toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('办理被服务端拒绝：显示原因，留在本页', async () => {
    open(TASK, [APPROVE], { 'POST approval-tasks/t-1:decide': { status: 409, body: { errorCode: 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED' } } });
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '通过' }));
    await userEvent.click(screen.getByRole('button', { name: '确认通过' }));

    expect(await screen.findByText('审批任务已被其他人认领')).toBeInTheDocument();
    expect(navigation.replaced).toEqual([]);
  });

  it('任务不存在或不是参与人：显示服务端给出的原因', async () => {
    resetNavigation({ taskId: 'gone' });
    stubServer({
      'GET sessions:current': { body: currentUserBody([APPROVE]) },
      'GET approval-tasks/gone': { status: 404, body: { errorCode: 'PLATFORM_APPROVAL_TASK_NOT_FOUND' } },
    });
    renderWithProviders(<TaskDetailPage />);

    expect(await screen.findByText('审批任务不存在，或你不是它的参与人')).toBeInTheDocument();
  });

  it('地址里没有任务编号：直接提示，不发请求', async () => {
    resetNavigation({});
    const server = stubServer({ 'GET sessions:current': { body: currentUserBody([APPROVE]) } });
    renderWithProviders(<TaskDetailPage />);

    expect(await screen.findByText('审批任务不存在，或你不是它的参与人')).toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes('approval-tasks'))).toBe(false);
  });
});
