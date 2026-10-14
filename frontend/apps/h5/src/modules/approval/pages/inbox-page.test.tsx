import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { problemBody, reactVersion, stubServer, taskRecordBody } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { navigation } from '@/shared/testing/taro-stub';
import { InboxPage } from './inbox-page';

const leaveTo = vi.hoisted(() => vi.fn());
vi.mock('@/shared/platform/browser', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/shared/platform/browser')>()), leaveTo }));

const INBOX = {
  newCount: 2,
  legacyCount: 2,
  noticeCount: 0,
  totalCount: 4,
  legacyAvailable: true,
  items: [
    { category: 'TODO', overdue: false, source: 'NEW', taskId: 'n-1', processKey: 'COST_TIMESHEET_APPROVAL', documentId: 'TS202610140001', title: 'COST_TIMESHEET_APPROVAL 1', nodeName: '部门负责人审核', createdAt: '2026-10-14T01:00:00Z', claimed: true },
    { category: 'TODO', overdue: false, source: 'NEW', taskId: 'n 2', processKey: 'COST_TIMESHEET_APPROVAL', documentId: 'TS202610140002', nodeName: '部门负责人审核', createdAt: '2026-10-14T02:00:00Z', claimed: false },
    { category: 'TODO', overdue: false, source: 'LEGACY', taskId: 'l-1', title: '合同评审 HT-2026-001', nodeName: '法务审核', createdAt: '2026-10-13T08:00:00Z', claimed: false, detailUrl: 'https://old.example.com/todo/detail?id=l-1' },
    { category: 'TODO', overdue: false, source: 'LEGACY', taskId: 'l-2', title: '异常链接的老待办', nodeName: '财务审核', createdAt: '2026-10-13T09:00:00Z', claimed: false, detailUrl: 'javascript:alert(1)' },
  ],
};

describe('待办列表', () => {
  it('合并显示新系统和老系统的待办，并标出来源与数量', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('工时单审批 TS202610140001')).toBeInTheDocument();
    expect(screen.getByText('合同评审 HT-2026-001')).toBeInTheDocument();
    expect(screen.getAllByText('新系统')).toHaveLength(2);
    expect(screen.getAllByText('老系统')).toHaveLength(2);
    expect(screen.getByRole('button', { name: '全部 4' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新系统 2' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '老系统 2' })).toBeInTheDocument();
    expect(screen.getByText('已认领')).toBeInTheDocument();
  });

  it('按来源筛选', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);
    await screen.findByText('工时单审批 TS202610140001');

    await userEvent.click(screen.getByRole('button', { name: '老系统 2' }));
    expect(screen.queryByText('工时单审批 TS202610140001')).not.toBeInTheDocument();
    expect(screen.getByText('合同评审 HT-2026-001')).toBeInTheDocument();
  });

  it('点新待办：进入本系统的审批任务页，任务编号经过编码', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByText('工时单审批 TS202610140002'));
    expect(navigation.opened).toEqual(['/app/pages/task?taskId=n%202']);
    expect(leaveTo).not.toHaveBeenCalled();
  });

  it('点老待办：跳转老系统的详情页', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByText('合同评审 HT-2026-001'));
    expect(leaveTo).toHaveBeenCalledWith('https://old.example.com/todo/detail?id=l-1');
    expect(navigation.opened).toEqual([]);
  });

  it('老待办的地址不是 http(s) 时点了没有反应', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByText('异常链接的老待办'));
    expect(leaveTo).not.toHaveBeenCalled();
    expect(navigation.opened).toEqual([]);
  });

  it('老系统读不出来时只给新系统的待办，并提示', async () => {
    stubServer({ 'GET approval-tasks': { body: { ...INBOX, legacyAvailable: false, legacyCount: 0, totalCount: 2, items: INBOX.items.slice(0, 2) } } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('老系统的待办暂时无法读取')).toBeInTheDocument();
    expect(screen.getByText('工时单审批 TS202610140001')).toBeInTheDocument();
  });

  it('没有待办时给出空状态', async () => {
    stubServer({ 'GET approval-tasks': { body: { items: [], newCount: 0, legacyCount: 0, noticeCount: 0, totalCount: 0, legacyAvailable: true } } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('暂无待办')).toBeInTheDocument();
  });

  it('加载失败时提示原因，可以刷新重试', async () => {
    let healthy = false;
    stubServer({ 'GET approval-tasks': () => (healthy ? { body: INBOX } : { status: 503, body: problemBody(503, 'PLATFORM_WEB_UNEXPECTED') }) });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('服务暂时不可用，请稍后重试')).toBeInTheDocument();
    healthy = true;
    await userEvent.click(screen.getByRole('button', { name: '刷新' }));
    expect(await screen.findByText('工时单审批 TS202610140001')).toBeInTheDocument();
  });

  it('服务返回的数据与接口约定不符时不显示半截数据', async () => {
    stubServer({ 'GET approval-tasks': { body: { items: [{ category: 'TODO', overdue: false, source: 'OTHER', taskId: 7 }] } } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('服务返回的数据与约定不符，请稍后重试')).toBeInTheDocument();
  });
});

describe('已办', () => {
  it('显示每条办理的结果和意见；通过时没有填写意见的显示（未填写意见）', async () => {
    stubServer({
      'GET approval-task-records': { body: { items: [taskRecordBody({ recordId: 'r-1', documentId: 'TS1', reason: '工时与计划相符' }), taskRecordBody({ recordId: 'r-2', documentId: 'TS2' })] } },
      'GET approval-tasks': { body: INBOX },
    });
    renderWithProviders(<InboxPage />);

    await userEvent.click(await screen.findByRole('button', { name: '已办' }));

    expect(await screen.findByText('工时单审批 TS1')).toBeInTheDocument();
    expect(screen.getByText('意见：工时与计划相符')).toBeInTheDocument();
    expect(screen.getByText('意见：（未填写意见）')).toBeInTheDocument();
  });

  it('待办标题不显示流程的英文名', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('工时单审批 TS202610140001');
    expect(screen.queryByText(/COST_TIMESHEET_APPROVAL/)).not.toBeInTheDocument();
  });

  it('服务端给出下一页的游标时显示“显示更多”，点击后接在后面', async () => {
    stubServer({
      'GET approval-tasks': (request) => request.url.includes('cursor=c2')
        ? { body: { ...INBOX, items: [INBOX.items[2]] } }
        : { body: { ...INBOX, items: [INBOX.items[0]], nextCursor: 'c2' } },
    });
    renderWithProviders(<InboxPage />);

    await screen.findByText('工时单审批 TS202610140001');
    await userEvent.click(screen.getByRole('button', { name: '显示更多' }));
    expect(await screen.findByText('合同评审 HT-2026-001')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '显示更多' })).not.toBeInTheDocument();
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
    expect(screen.getByRole('button', { name: '刷新' })).toBeInTheDocument();
  });
});

describe('运行期的 React 版本', () => {
  it('共享包的 hooks 用的是 H5 自己的 React 18，没有带进 React 19', () => {
    expect(reactVersion).toMatch(/^18\./);
  });
});
