import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { VERSION_HEADER, currentUserBody, problemBody, stubServer, taskDetailBody, taskRecordBody } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { TaskDetailPage } from './task-detail-page';

const APPROVE = 'platform.approval:task:approve';
const READ = 'platform.approval:task:read';

const TASK = taskDetailBody();

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

  it('办理不另需权限码（D-1a-179）：只有查看权限但服务端允许办理时，同样显示办理按钮', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET approval-tasks/t-1': { body: TASK } });
    render();

    for (const name of BUTTONS) {
      expect(await screen.findByRole('button', { name })).toBeInTheDocument();
    }
  });

  it('已认领的任务不显示认领，但仍可通过、驳回、退回', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, APPROVE]) }, 'GET approval-tasks/t-1': { body: { ...TASK, assignee: '1001', assigneeName: '张三' } } });
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
      'GET approval-tasks/t-1': { body: TASK, headers: VERSION_HEADER },
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
      'GET approval-tasks/t-1': { body: TASK, headers: VERSION_HEADER },
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
      'GET approval-tasks/t-1': { body: TASK, headers: VERSION_HEADER },
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
      'GET approval-tasks/t-1': { body: TASK, headers: VERSION_HEADER },
      'POST approval-tasks/t-1:claim': { status: 409, body: problemBody(409, 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED') },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '认领' }));
    expect(await screen.findByText('审批任务已被其他人认领')).toBeInTheDocument();
  });
});

describe('任务详情的审批意见', () => {
  const HISTORY = [
    taskRecordBody({ recordId: 'r-1', nodeName: '项目经理审核', action: 'APPROVED', actorName: '张三', reason: '工时与计划相符' }),
    taskRecordBody({ recordId: 'r-2', nodeName: '区域负责人审核', action: 'APPROVED', actorName: '王五' }),
    taskRecordBody({ recordId: 'r-3', nodeName: '总监审核', action: 'REJECTED', actorName: '赵六', reason: '工时不符' }),
  ];

  it('显示每一条审批记录的意见；通过时没有填写意见的，显示（未填写意见）而不是留空', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET approval-tasks/t-1': { body: taskDetailBody({ history: HISTORY }), headers: VERSION_HEADER } });
    render();

    const table = (await screen.findByText('工时与计划相符')).closest('table') as HTMLElement;
    expect(within(table).getByText('工时与计划相符')).toBeInTheDocument();
    expect(within(table).getByText('工时不符')).toBeInTheDocument();
    const row = within(table).getByText('区域负责人审核').closest('tr') as HTMLElement;
    expect(within(row).getByText('（未填写意见）')).toBeInTheDocument();
    expect(within(table).getAllByText('通过')).toHaveLength(2);
    expect(within(table).getByText('驳回')).toBeInTheDocument();
  });

  it('还没有审批记录时说明没有记录', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET approval-tasks/t-1': { body: TASK, headers: VERSION_HEADER } });
    render();

    expect(await screen.findByText('暂无审批记录')).toBeInTheDocument();
  });

  it('发起人、候选审批人显示姓名，单据显示单据类型的名称', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET approval-tasks/t-1': { body: TASK, headers: VERSION_HEADER } });
    render();

    expect(await screen.findByText('李四')).toBeInTheDocument();
    expect(screen.getByText('张三、王五')).toBeInTheDocument();
    expect(screen.getByText('工时单 TS-FST-202610-0001')).toBeInTheDocument();
    expect(screen.queryByText(/TIMESHEET/)).not.toBeInTheDocument();
  });
});

describe('版本冲突', () => {
  it('办理时别人已经改过任务（412），提示刷新，并立即重新读取任务拿到新版本', async () => {
    let reads = 0;
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET approval-tasks/t-1': () => {
        reads += 1;
        return { body: TASK, headers: VERSION_HEADER };
      },
      'POST approval-tasks/t-1:decide': { status: 412, body: problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH') },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '通过' }));
    await userEvent.click(await screen.findByRole('button', { name: '确认通过' }));

    expect(await screen.findByText('内容刚刚被其他人修改，请刷新后重试')).toBeInTheDocument();
    await waitFor(() => expect(reads).toBe(2));
  });

  it('办理请求带着读到的版本和幂等键', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET approval-tasks/t-1': { body: TASK, headers: { ETag: '"7"' } },
      'POST approval-tasks/t-1:decide': { body: { instanceId: 'i-1', status: 'APPROVED' } },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '通过' }));
    await userEvent.click(await screen.findByRole('button', { name: '确认通过' }));

    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith(':decide'))).toBe(true));
    const decide = server.calls.find((call) => call.url.endsWith(':decide'));
    expect(decide?.headers.get('If-Match')).toBe('"7"');
    expect(decide?.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
  });
});
