import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { VERSION_HEADER, currentUserBody, problemBody, stubServer, taskDetailBody, taskRecordBody } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { navigation, resetNavigation } from '@/shared/testing/taro-stub';
import { TaskDetailPage } from './task-detail-page';

const APPROVE = 'platform.approval:task:approve';
const TASK = taskDetailBody();

function open(task: object = TASK, permissions: string[] = [APPROVE], extra: Parameters<typeof stubServer>[0] = {}) {
  resetNavigation({ taskId: 't-1' });
  return stubServer({
    'GET sessions:current': { body: currentUserBody(permissions) },
    'GET approval-tasks/t-1': { body: task, headers: VERSION_HEADER },
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
    expect(screen.getByText('工时单 TS-FST-202610-0001')).toBeInTheDocument();
    expect(screen.getByText('李四')).toBeInTheDocument();
    expect(screen.getByText('张三、王五')).toBeInTheDocument();
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
    open({ ...TASK, assignee: '019a0000-0000-7000-8000-000000001001', assigneeName: '张三' });
    await showTask();

    expect(await screen.findByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '认领' })).not.toBeInTheDocument();
    expect(screen.getByText('已认领')).toBeInTheDocument();
  });

  it('办理不另需权限码（D-1a-179）：没有办理权限码但服务端允许办理时，同样显示办理按钮', async () => {
    open(TASK, []);
    await showTask();

    for (const name of ['认领', '通过', '驳回', '退回']) {
      expect(await screen.findByRole('button', { name })).toBeInTheDocument();
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
    open(TASK, [APPROVE], { 'POST approval-tasks/t-1:decide': { status: 409, body: problemBody(409, 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED') } });
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
      'GET approval-tasks/gone': { status: 404, body: problemBody(404, 'PLATFORM_APPROVAL_TASK_NOT_FOUND') },
    });
    renderWithProviders(<TaskDetailPage />);

    expect(await screen.findByText('审批任务不存在，或者你不是参与人')).toBeInTheDocument();
  });

  it('地址里没有任务编号：直接提示，不发请求', async () => {
    resetNavigation({});
    const server = stubServer({ 'GET sessions:current': { body: currentUserBody([APPROVE]) } });
    renderWithProviders(<TaskDetailPage />);

    expect(await screen.findByText('审批任务不存在，或者你不是参与人')).toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes('approval-tasks'))).toBe(false);
  });
});

describe('审批记录与意见', () => {
  it('显示每条审批记录的意见；通过时没有填写意见的显示（未填写意见）', async () => {
    open(taskDetailBody({
      history: [
        taskRecordBody({ recordId: 'r-1', nodeName: '项目经理审核', action: 'APPROVED', reason: '工时与计划相符' }),
        taskRecordBody({ recordId: 'r-2', nodeName: '区域负责人审核', action: 'APPROVED' }),
        taskRecordBody({ recordId: 'r-3', nodeName: '总监审核', action: 'REJECTED', reason: '工时不符' }),
      ],
    }));
    await showTask();

    expect(await screen.findByText('工时与计划相符')).toBeInTheDocument();
    expect(screen.getByText('工时不符')).toBeInTheDocument();
    expect(screen.getByText('（未填写意见）')).toBeInTheDocument();
  });

  it('还没有审批记录时说明没有记录', async () => {
    open();
    await showTask();

    expect(await screen.findByText('暂无审批记录')).toBeInTheDocument();
  });

  it('办理请求带着读到的版本和幂等键，版本冲突（412）时提示刷新', async () => {
    const server = open(TASK, [APPROVE], { 'POST approval-tasks/t-1:decide': { status: 412, body: problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH') } });
    await showTask();

    await userEvent.click(await screen.findByRole('button', { name: '通过' }));
    await userEvent.click(screen.getByRole('button', { name: '确认通过' }));

    expect(await screen.findByText('内容刚刚被其他人修改，请刷新后重试')).toBeInTheDocument();
    const decide = server.calls.find((call) => call.url.endsWith(':decide'));
    expect(decide?.headers.get('If-Match')).toBe('"1"');
    expect(decide?.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(navigation.replaced).toEqual([]);
  });
});
