import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { problemBody, stubServer, taskRecordBody } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { InboxPage } from './inbox-page';

const INBOX = {
  newCount: 2,
  legacyCount: 2,
  noticeCount: 0,
  totalCount: 4,
  legacyAvailable: true,
  items: [
    { category: 'TODO', overdue: false, source: 'NEW', taskId: 'n-1', processKey: 'COST_TIMESHEET_APPROVAL', documentId: 'TS202610140001', title: 'COST_TIMESHEET_APPROVAL TS202610140001', nodeName: '部门负责人审核', createdAt: '2026-10-14T01:00:00Z', claimed: true, detailUrl: null },
    { category: 'TODO', overdue: false, source: 'NEW', taskId: 'n-2', processKey: 'COST_TIMESHEET_APPROVAL', documentId: 'TS202610140002', nodeName: '部门负责人审核', createdAt: '2026-10-14T02:00:00Z', claimed: false, detailUrl: null },
    { category: 'TODO', overdue: false, source: 'LEGACY', taskId: 'l-1', title: '合同评审 HT-2026-001', nodeName: '法务审核', createdAt: '2026-10-13T08:00:00Z', claimed: false, detailUrl: 'https://old.example.com/todo/detail?id=l-1' },
    { category: 'TODO', overdue: false, source: 'LEGACY', taskId: 'l-2', title: '异常链接的老待办', nodeName: '财务审核', createdAt: '2026-10-13T09:00:00Z', claimed: false, detailUrl: 'javascript:alert(1)' },
  ],
};

describe('合并待办收件箱', () => {
  it('同时显示新系统和老系统的待办，并标出来源与数量', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('工时单审批 TS202610140001')).toBeInTheDocument();
    expect(screen.getByText('合同评审 HT-2026-001')).toBeInTheDocument();
    expect(screen.getAllByText('新系统').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('老系统').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('全部 4')).toBeInTheDocument();
    expect(screen.getByText('新系统 2')).toBeInTheDocument();
    expect(screen.getByText('老系统 2')).toBeInTheDocument();
    expect(screen.getByText('已认领')).toBeInTheDocument();
  });

  it('新系统的待办标题由流程名称和单据号拼出，不显示流程的英文名', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('工时单审批 TS202610140001');
    expect(screen.queryByText(/COST_TIMESHEET_APPROVAL/)).not.toBeInTheDocument();
  });

  it('老待办是指向老系统的链接，在新窗口打开且不带来源信息', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    const link = await screen.findByRole('link', { name: '合同评审 HT-2026-001' });
    expect(link).toHaveAttribute('href', 'https://old.example.com/todo/detail?id=l-1');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    expect(link).toHaveAttribute('rel', expect.stringContaining('noreferrer'));
  });

  it('老待办的地址不是 http(s) 时不生成链接', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('异常链接的老待办');
    expect(screen.queryByRole('link', { name: '异常链接的老待办' })).not.toBeInTheDocument();
  });

  it('新待办在本系统内打开审批任务，不开新窗口', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    const link = await screen.findByRole('link', { name: '工时单审批 TS202610140002' });
    expect(link).toHaveAttribute('href', '/approval/tasks/n-2');
    expect(link).not.toHaveAttribute('target');
    await userEvent.click(link);
    expect(screen.getByTestId('location')).toHaveTextContent('/approval/tasks/n-2');
  });

  it('可以按来源筛选', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);
    await screen.findByText('工时单审批 TS202610140001');

    await userEvent.click(screen.getByText('老系统 2'));
    const list = screen.getByRole('list');
    expect(within(list).queryByText('工时单审批 TS202610140001')).not.toBeInTheDocument();
    expect(within(list).getByText('合同评审 HT-2026-001')).toBeInTheDocument();
  });

  it('老库读取失败时提示只显示了新系统的待办，而不是显示成没有待办', async () => {
    stubServer({ 'GET approval-tasks': { body: { ...INBOX, legacyAvailable: false, legacyCount: 0, totalCount: 2, items: INBOX.items.slice(0, 2) } } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('老系统的待办暂时无法读取')).toBeInTheDocument();
    expect(screen.queryByText('暂无待办')).not.toBeInTheDocument();
  });

  it('待办分页：服务端给出下一页的游标时显示“显示更多”，点击后带着游标读取并接在后面', async () => {
    const server = stubServer({
      'GET approval-tasks': (request) => request.url.includes('cursor=c2')
        ? { body: { ...INBOX, items: [INBOX.items[2]] } }
        : { body: { ...INBOX, items: [INBOX.items[0]], nextCursor: 'c2' } },
    });
    renderWithProviders(<InboxPage />);

    await screen.findByText('工时单审批 TS202610140001');
    expect(server.calls[0]?.url).toContain('limit=50');
    expect(screen.queryByText('合同评审 HT-2026-001')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '显示更多' }));

    expect(await screen.findByText('合同评审 HT-2026-001')).toBeInTheDocument();
    expect(screen.getByText('工时单审批 TS202610140001')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '显示更多' })).not.toBeInTheDocument();
  });

  it('服务端重复给出同一个游标时报错，而不是一直读下去', async () => {
    stubServer({ 'GET approval-tasks': { body: { ...INBOX, items: [INBOX.items[0]], nextCursor: 'c1' } } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('工时单审批 TS202610140001');
    await userEvent.click(screen.getByRole('button', { name: '显示更多' }));
    await waitFor(() => expect(screen.getByText('待办加载失败')).toBeInTheDocument());
  });
});

describe('已办', () => {
  const DONE = [
    taskRecordBody({ recordId: 'r-1', documentId: 'TS1', action: 'APPROVED', reason: '工时与计划相符' }),
    taskRecordBody({ recordId: 'r-2', documentId: 'TS2', action: 'APPROVED' }),
    taskRecordBody({ recordId: 'r-3', documentId: 'TS3', action: 'REJECTED', reason: '工时不符' }),
  ];

  it('已办列表显示每条办理的结果和意见；通过时没有填写意见的显示（未填写意见）', async () => {
    stubServer({ 'GET approval-task-records': { body: { items: DONE } }, 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByText('已办'));

    expect(await screen.findByText('工时单审批 TS1')).toBeInTheDocument();
    expect(screen.getByText('意见：工时与计划相符')).toBeInTheDocument();
    expect(screen.getByText('意见：（未填写意见）')).toBeInTheDocument();
    expect(screen.getByText('意见：工时不符')).toBeInTheDocument();
    expect(screen.getAllByText('通过')).toHaveLength(2);
    expect(screen.getByText('驳回')).toBeInTheDocument();
  });

  it('服务端新加了办理结果：显示破折号，不显示 undefined，其余的行照常显示', async () => {
    stubServer({
      'GET approval-task-records': { body: { items: [taskRecordBody({ recordId: 'r-1', documentId: 'TS1', action: 'ADVISED' }), taskRecordBody({ recordId: 'r-2', documentId: 'TS2' })] } },
      'GET approval-tasks': { body: INBOX },
    });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByText('已办'));

    expect(await screen.findByText('工时单审批 TS2')).toBeInTheDocument();
    expect(screen.getByText('工时单审批 TS1')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
  });

  it('没有已办时说明没有已办', async () => {
    stubServer({ 'GET approval-task-records': { body: { items: [] } }, 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByText('已办'));
    expect(await screen.findByText('暂无已办')).toBeInTheDocument();
  });

  it('不打开已办时不读取已办', async () => {
    const server = stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('工时单审批 TS202610140001');
    expect(server.calls.some((call) => call.url.includes('approval-task-records'))).toBe(false);
  });
});

describe('知会与超时', () => {
  const WITH_NOTICE = {
    ...INBOX,
    noticeCount: 1,
    totalCount: 5,
    items: [
      ...INBOX.items,
      { category: 'NOTICE', overdue: false, source: 'LEGACY', taskId: 'z-1', title: '项目周报知会', nodeName: '知会', createdAt: '2026-10-12T08:00:00Z', claimed: false, detailUrl: 'https://old.example.com/notice?id=z-1' },
    ],
  };

  it('老系统的知会单列：有自己的筛选和数量，标为知会，点击跳老系统', async () => {
    stubServer({ 'GET approval-tasks': { body: WITH_NOTICE } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('项目周报知会');
    expect(screen.getByText('知会 1')).toBeInTheDocument();
    await userEvent.click(screen.getByText('知会 1'));
    const list = screen.getByRole('list');
    expect(within(list).getByRole('link', { name: '项目周报知会' })).toHaveAttribute('href', 'https://old.example.com/notice?id=z-1');
    expect(within(list).queryByText('合同评审 HT-2026-001')).not.toBeInTheDocument();
  });

  it('超过时限的新系统待办标出已超时', async () => {
    const items = [{ ...INBOX.items[1], overdue: true }];
    stubServer({ 'GET approval-tasks': { body: { ...INBOX, newCount: 1, legacyCount: 0, totalCount: 1, items } } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('已超时')).toBeInTheDocument();
  });
});

describe('刷新失败', () => {
  it('已经有数据时，刷新失败只在列表上方提示，列表仍然保留', async () => {
    let healthy = true;
    stubServer({ 'GET approval-tasks': () => (healthy ? { body: INBOX } : { status: 503, body: problemBody(503, 'PLATFORM_WEB_UNEXPECTED') }) });
    renderWithProviders(<InboxPage />);
    await screen.findByText('工时单审批 TS202610140001');

    healthy = false;
    act(() => { window.dispatchEvent(new Event('focus')); });

    expect(await screen.findByText('服务暂时不可用，请稍后重试')).toBeInTheDocument();
    expect(screen.getByText('工时单审批 TS202610140001')).toBeInTheDocument();
    expect(screen.getByText('合同评审 HT-2026-001')).toBeInTheDocument();
  });
});
